package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataAccessPlanRestrictionVerifier;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamPortalMachineDirectoryConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMachineApplicationPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMachineApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Portal 机器路由目录读模型。
 *
 * <p>该服务只投影 ACTIVE 且已启用 Portal 的应用；AKP 的 API 与 DATA 三权由
 * Resource Server 和 DATA 注解链先行校验，本服务只执行受限 DATA 计划的应用范围裁剪。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPortalMachineApplicationDirectoryService {

    private final IamTrustedApplicationPortalRepository portalRepository;
    private final IamTrustedApplicationRepository applicationRepository;
    private final IamPortalCatalogRevisionService revisionService;
    private final TrustedApplicationBuiltInResolver builtInResolver;

    /**
     * 读取一个稳定 revision 的可信应用路由投影页。
     *
     * @param page             页码（0 起）
     * @param size             页大小
     * @param expectedRevision 调用方携带的目录版本，可空
     * @param plan             当前 DATA 计划
     * @return 显式的成功或 revision 冲突结果；冲突不再使用 null 表示
     */
    @Transactional(readOnly = true)
    public ReadResult read(int page, int size, Long expectedRevision,
                           DataAccessPlan plan) {
        requireReadable(plan);
        long before = revisionService.current();
        if (expectedRevision != null && expectedRevision.longValue() != before) {
            return ReadResult.conflict("before-read", expectedRevision, before, before);
        }
        List<PortalMachineApplicationResponse> all = readAll(plan);
        long after = revisionService.current();
        if (before != after) {
            return ReadResult.conflict("after-read", expectedRevision, before, after);
        }
        long offset = (long) page * size;
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + size, all.size());
        return ReadResult.success(PortalMachineApplicationPageResponse.builder()
                .catalogRevision(before)
                .totalElements(all.size())
                .page(page)
                .size(size)
                .items(Collections.unmodifiableList(new ArrayList<PortalMachineApplicationResponse>(
                        all.subList(from, to))))
                .build());
    }

    private List<PortalMachineApplicationResponse> readAll(DataAccessPlan plan) {
        List<IamTrustedApplicationPortalEntity> portals = portalRepository
                .findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE);
        Map<Long, IamTrustedApplicationEntity> applications = new HashMap<Long, IamTrustedApplicationEntity>();
        List<Long> applicationIds = new ArrayList<Long>();
        for (IamTrustedApplicationPortalEntity portal : portals) {
            applicationIds.add(portal.getApplicationId());
        }
        for (IamTrustedApplicationEntity application : applicationRepository.findAllById(applicationIds)) {
            applications.put(application.getId(), application);
        }
        List<PortalMachineApplicationResponse> result = new ArrayList<PortalMachineApplicationResponse>();
        for (IamTrustedApplicationPortalEntity portal : portals) {
            IamTrustedApplicationEntity application = applications.get(portal.getApplicationId());
            if (application == null || application.getStatus() == null) {
                throw unavailable(portal.getApplicationId(), "trusted application");
            }
            if (application.getStatus().intValue() == SimpleIamServerConstant.STATUS_INACTIVE) {
                continue;
            }
            if (application.getStatus().intValue() != SimpleIamServerConstant.STATUS_ACTIVE) {
                throw unavailable(portal.getApplicationId(), "application status");
            }
            // 内置应用由 IAM Nginx 的固定入口承载，不进入 Gateway 动态机器目录。
            if (builtInResolver.isBuiltIn(application)) {
                continue;
            }
            if (!allowed(plan, application)) {
                continue;
            }
            validateProjection(portal, application);
            result.add(PortalMachineApplicationResponse.builder()
                    .applicationCode(application.getApplicationCode())
                    .applicationName(application.getApplicationName())
                    .status(application.getStatus())
                    .portalEnabled(Boolean.TRUE)
                    .routePrefix(portal.getRoutePrefix())
                    .entry(portal.getEntry())
                    .apiBase(portal.getApiBase())
                    .targetOrigin(portal.getTargetOrigin())
                    .configVersion(portal.getConfigVersion())
                    .forwardAuthorization(IamPortalMachineDirectoryConstant.FORWARD_AUTHORIZATION_REQUEST)
                    .forwardCookieNames(Collections.<String>emptyList())
                    .build());
        }
        return result;
    }

    private void requireReadable(DataAccessPlan plan) {
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            throw new DataPermissionAccessDeniedException("Portal 可信应用路由投影数据权限不足");
        }
    }

    private void validateProjection(IamTrustedApplicationPortalEntity portal,
                                    IamTrustedApplicationEntity application) {
        String code = application.getApplicationCode();
        if (!StringUtils.hasText(code) || code.length() > 64
                || !code.matches("[A-Za-z0-9_-]{1,64}")) {
            throw unavailable(application.getId(), "application code");
        }
        String name = application.getApplicationName();
        if (!StringUtils.hasText(name) || name.trim().length() > 128) {
            throw unavailable(application.getId(), "application name");
        }
        if (!IamPortalRouteValidator.isValidRoutePrefix(portal.getRoutePrefix())
                || !IamPortalRouteValidator.isValidPublicEntry(portal.getRoutePrefix(), portal.getEntry())
                || !IamPortalRouteValidator.isValidPublicApiBase(portal.getApiBase())
                || !IamPortalRouteValidator.isValidTargetOrigin(portal.getTargetOrigin())
                || portal.getConfigVersion() == null || portal.getConfigVersion() < 1L) {
            throw unavailable(application.getId(), "portal route configuration");
        }
    }

    private IamPortalMachineDirectoryUnavailableException unavailable(
            Long applicationId, String field) {
        log.error("Portal 可信应用路由投影包含无效存量：applicationId={}, field={}", applicationId, field);
        return new IamPortalMachineDirectoryUnavailableException("Portal 可信应用路由投影暂不可用");
    }

    private boolean allowed(DataAccessPlan plan, IamTrustedApplicationEntity application) {
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return true;
        }
        Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put("applicationId", String.valueOf(application.getId()));
        dimensions.put("applicationCode", application.getApplicationCode());
        return DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions);
    }

    /**
     * 目录读取结果，调用方可以区分成功与并发冲突，而不依赖 null 语义。
     */
    public static final class ReadResult {
        private final Status status;
        private final PortalMachineApplicationPageResponse page;
        private final String stage;
        private final Long expectedRevision;
        private final long beforeRevision;
        private final long afterRevision;
        private ReadResult(Status status, PortalMachineApplicationPageResponse page, String stage,
                           Long expectedRevision, long beforeRevision, long afterRevision) {
            this.status = status;
            this.page = page;
            this.stage = stage;
            this.expectedRevision = expectedRevision;
            this.beforeRevision = beforeRevision;
            this.afterRevision = afterRevision;
        }

        public static ReadResult success(PortalMachineApplicationPageResponse page) {
            return new ReadResult(Status.SUCCESS, page, null, null,
                    page.getCatalogRevision(), page.getCatalogRevision());
        }

        public static ReadResult conflict(String stage, Long expectedRevision,
                                          long beforeRevision, long afterRevision) {
            return new ReadResult(Status.CONFLICT, null, stage, expectedRevision,
                    beforeRevision, afterRevision);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public PortalMachineApplicationPageResponse getPage() {
            return page;
        }

        public String getStage() {
            return stage;
        }

        public Long getExpectedRevision() {
            return expectedRevision;
        }

        public long getBeforeRevision() {
            return beforeRevision;
        }

        public long getAfterRevision() {
            return afterRevision;
        }

        public enum Status {SUCCESS, CONFLICT}
    }
}
