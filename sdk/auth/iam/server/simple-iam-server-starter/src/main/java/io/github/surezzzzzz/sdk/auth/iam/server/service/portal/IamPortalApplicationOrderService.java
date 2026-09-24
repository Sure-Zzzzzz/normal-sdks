package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalApplicationOrderRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalApplicationOrderItem;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalApplicationOrderResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalSettingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamPortalSettingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.util.*;

/**
 * Portal 应用根节点顺序服务。
 *
 * <p>应用排序是全局快照，不与单应用 Portal 配置版本或菜单节点排序混用。首次产生或删除
 * Portal 集成同样会改变快照成员，必须通过本服务竞争 Portal 全局设置版本。
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPortalApplicationOrderService {

    private static final long SORT_STEP = 10L;
    private static final String SQL_STAGE_SORT_ORDER =
            "UPDATE iam_trusted_application_portal SET sort_order = -application_id";
    private static final String SQL_UPDATE_SORT_ORDER =
            "UPDATE iam_trusted_application_portal SET sort_order = ? WHERE application_id = ?";

    private final IamTrustedApplicationPortalRepository trustedApplicationPortalRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamPortalSettingRepository portalSettingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final IamAuditEventPublisher auditEventPublisher;
    private final EntityManager entityManager;
    private final IamTrustedApplicationMutationGuard mutationGuard;

    /**
     * 读取供管理端编辑的全部 Portal 集成快照，停用项仍保留在列表中。
     */
    @Transactional(readOnly = true)
    public PortalApplicationOrderResponse getApplicationOrder() {
        return toResponse(requiredPortalSetting(), orderedPortals());
    }

    /**
     * 原子替换全局 Portal 应用顺序。
     */
    @Transactional
    public PortalApplicationOrderResponse updateApplicationOrder(PortalApplicationOrderRequest request) {
        IamPortalSettingEntity setting = requiredPortalSetting();
        if (request == null || request.getVersion() == null
                || !Objects.equals(request.getVersion(), versionOf(setting))) {
            throw orderConflict();
        }
        List<IamTrustedApplicationPortalEntity> portals = orderedPortals();
        validateFullSnapshot(request.getApplicationIds(), portals);
        for (Long applicationId : request.getApplicationIds()) {
            mutationGuard.requireMutable(applicationId);
        }
        setting = lockGlobalSetting(setting);
        rewriteNormalizedOrder(portals, request.getApplicationIds());

        log.debug("Portal 应用顺序更新成功：count={}, version={}", portals.size(), setting.getVersion());
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                null, null, "portalApplicationOrderCount=" + portals.size() + ", version=" + setting.getVersion());
        return toResponse(setting, orderedPortals());
    }

    /**
     * 为首次持久化的 Portal 集成分配末尾顺序。调用方必须在同一事务内随后保存该实体。
     */
    @Transactional
    public void assignInitialSortOrder(IamTrustedApplicationPortalEntity portal) {
        if (portal == null || portal.getApplicationId() == null) {
            throw orderInvalid("应用 ID 不能为空");
        }
        lockGlobalSetting(requiredPortalSetting());
        List<IamTrustedApplicationPortalEntity> portals = orderedPortals();
        long nextOrder = nextSortOrder(portals);
        portal.setSortOrder(nextOrder);
        log.debug("Portal 集成加入全局顺序：applicationId={}, sortOrder={}", portal.getApplicationId(), nextOrder);
    }

    /**
     * Portal 集成删除前调用。它和清除登录首页引用共用一次版本递增。
     */
    @Transactional
    public void preparePortalRemoval(Long applicationId) {
        IamPortalSettingEntity setting = requiredPortalSetting();
        boolean landingCleared = Objects.equals(setting.getLoginLandingApplicationId(), applicationId);
        if (landingCleared) {
            setting.setLoginLandingApplicationId(null);
        }
        lockGlobalSetting(setting);
        log.debug("Portal 集成移出全局顺序：applicationId={}, landingCleared={}", applicationId, landingCleared);
    }

    private long nextSortOrder(List<IamTrustedApplicationPortalEntity> portals) {
        if (portals.isEmpty()) {
            return SORT_STEP;
        }
        Long currentMax = portals.get(portals.size() - 1).getSortOrder();
        if (currentMax == null || currentMax > Long.MAX_VALUE - SORT_STEP) {
            rewriteNormalizedOrder(portals, applicationIds(portals));
            return multiplyStep(portals.size() + 1L);
        }
        return currentMax + SORT_STEP;
    }

    /**
     * 唯一索引下先写入保留负值再写规范正值，避免两项交换时短暂违反唯一约束。
     */
    private void rewriteNormalizedOrder(List<IamTrustedApplicationPortalEntity> portals, List<Long> applicationIds) {
        if (portals.isEmpty()) {
            return;
        }
        jdbcTemplate.update(SQL_STAGE_SORT_ORDER);
        long position = 1L;
        for (Long applicationId : applicationIds) {
            jdbcTemplate.update(SQL_UPDATE_SORT_ORDER, multiplyStep(position), applicationId);
            position += 1L;
        }
        // 排序通过 JDBC 更新，清除已托管的旧排序实体，确保本事务的返回快照从数据库重新读取。
        entityManager.clear();
    }

    private long multiplyStep(long position) {
        try {
            return Math.multiplyExact(position, SORT_STEP);
        } catch (ArithmeticException exception) {
            throw orderInvalid("应用数量超过 Portal 排序容量");
        }
    }

    private void validateFullSnapshot(List<Long> applicationIds,
                                      List<IamTrustedApplicationPortalEntity> portals) {
        if (applicationIds == null || applicationIds.size() != portals.size()) {
            throw orderInvalid("必须提交全部 Portal 集成");
        }
        Set<Long> requested = new LinkedHashSet<>();
        for (Long applicationId : applicationIds) {
            if (applicationId == null || !requested.add(applicationId)) {
                throw orderInvalid("应用 ID 不能为空且不能重复");
            }
        }
        Set<Long> actual = new HashSet<>(applicationIds(portals));
        if (!actual.equals(requested)) {
            throw orderInvalid("提交应用集合与当前 Portal 集成不一致");
        }
    }

    private List<Long> applicationIds(List<IamTrustedApplicationPortalEntity> portals) {
        List<Long> result = new ArrayList<>();
        for (IamTrustedApplicationPortalEntity portal : portals) {
            result.add(portal.getApplicationId());
        }
        return result;
    }

    private List<IamTrustedApplicationPortalEntity> orderedPortals() {
        return trustedApplicationPortalRepository.findAllByOrderBySortOrderAscApplicationIdAsc();
    }

    private IamPortalSettingEntity requiredPortalSetting() {
        return portalSettingRepository.findById(IamPortalSettingEntity.SINGLETON_ID)
                .orElseThrow(() -> orderInvalid("Portal 全局设置缺失"));
    }

    /**
     * 修改无业务字段的更新时间，迫使 @Version 在事务内成为排序集合的唯一写入闸门。
     */
    private IamPortalSettingEntity lockGlobalSetting(IamPortalSettingEntity setting) {
        setting.setUpdatedAt(Instant.now());
        try {
            return portalSettingRepository.saveAndFlush(setting);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw orderConflict();
        }
    }

    private PortalApplicationOrderResponse toResponse(IamPortalSettingEntity setting,
                                                      List<IamTrustedApplicationPortalEntity> portals) {
        List<Long> applicationIds = applicationIds(portals);
        Map<Long, IamTrustedApplicationEntity> applications = new HashMap<>();
        for (IamTrustedApplicationEntity application : trustedApplicationRepository.findAllById(applicationIds)) {
            applications.put(application.getId(), application);
        }
        List<PortalApplicationOrderItem> items = new ArrayList<>();
        for (IamTrustedApplicationPortalEntity portal : portals) {
            IamTrustedApplicationEntity application = applications.get(portal.getApplicationId());
            if (application == null) {
                throw orderInvalid("Portal 集成引用的可信应用不存在：" + portal.getApplicationId());
            }
            items.add(PortalApplicationOrderItem.builder()
                    .applicationId(application.getId())
                    .applicationCode(application.getApplicationCode())
                    .applicationName(application.getApplicationName())
                    .icon(application.getIcon())
                    .enabled(portal.getEnabled() != null && portal.getEnabled() == 1)
                    .build());
        }
        return PortalApplicationOrderResponse.builder()
                .version(versionOf(setting))
                .applications(items)
                .build();
    }

    private long versionOf(IamPortalSettingEntity setting) {
        return setting.getVersion() == null ? 0L : setting.getVersion();
    }

    private SimpleIamServerException orderConflict() {
        return new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_CONFLICT,
                ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_CONFLICT);
    }

    private SimpleIamServerException orderInvalid(String detail) {
        return new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_INVALID,
                String.format(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_INVALID, detail));
    }
}
