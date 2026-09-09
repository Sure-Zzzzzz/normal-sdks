package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.TrustedApplicationIcon;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.MenuItemResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalIntegrationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationCreatedResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationDetailResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationMenuEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 可信应用管理服务（应用维度）
 *
 * <p>一应用可关联多个 OAuth2 客户端（区分可信前端接入 Portal 侧边栏 / 可信后端驱动授权码流程）。
 * 本服务负责应用主表 CRUD + Portal 集成配置 + 菜单管理；client 维度的增删改查委托
 * {@link TrustedApplicationClientService}。
 *
 * <p>创建应用必须带 {@code initialClient}；CONFIDENTIAL 初始客户端的明文 secret
 * 留空时由服务端生成，随创建响应一次性返回。
 *
 * <p>删除应用按固定顺序级联清理：菜单 -> Portal -> 权限清单 -> 每个 client 的授权记录 ->
 * consent 投影 -> client 本身 -> 应用主表，全部走 repository / JdbcTemplate。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class TrustedApplicationService {

    private static final String SQL_LIST_CLIENT_IDS_BY_APP =
            "SELECT client_id FROM oauth2_registered_client WHERE application_id = ?";
    private static final String SQL_LIST_REGISTERED_CLIENT_IDS_BY_APP =
            "SELECT id, client_id FROM oauth2_registered_client WHERE application_id = ?";
    private static final String SQL_COUNT_CLIENTS_BY_APP =
            "SELECT COUNT(*) FROM oauth2_registered_client WHERE application_id = ?";
    private static final String SQL_DELETE_AUTHORIZATION =
            "DELETE FROM oauth2_authorization WHERE registered_client_id = ?";
    private static final String SQL_DELETE_AUTHORIZATION_CONSENT =
            "DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?";
    private static final String SQL_DELETE_IAM_CONSENT_BY_CLIENT =
            "DELETE FROM iam_consent WHERE client_id = ?";
    private static final String SQL_DELETE_REGISTERED_CLIENT_BY_APP =
            "DELETE FROM oauth2_registered_client WHERE application_id = ?";

    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamTrustedApplicationPortalRepository trustedApplicationPortalRepository;
    private final IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final TrustedApplicationClientService trustedApplicationClientService;
    private final JdbcTemplate jdbcTemplate;
    private final IamAuditEventPublisher auditEventPublisher;
    private final SimpleIamServerProperties properties;

    /**
     * 创建应用（必须带 initialClient）
     *
     * @return 应用摘要 + 初始客户端一次性密钥（CONFIDENTIAL 时；PUBLIC 初始客户端为 null）
     */
    @Transactional
    public TrustedApplicationCreatedResponse createApplication(CreateTrustedApplicationRequest request) {
        String applicationCode = normalizeRequired(request.getApplicationCode(),
                ServerErrorMessage.TRUSTED_APPLICATION_CODE_EMPTY);
        if (trustedApplicationRepository.existsByApplicationCode(applicationCode)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CODE_EXISTS,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_CODE_EXISTS, applicationCode));
        }
        String applicationName = normalizeRequired(request.getApplicationName(),
                ServerErrorMessage.TRUSTED_APPLICATION_NAME_EMPTY);
        if (request.getInitialClient() == null) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLIENT_REQUIRED,
                    ServerErrorMessage.TRUSTED_APPLICATION_CLIENT_REQUIRED);
        }

        Instant now = Instant.now();
        IamTrustedApplicationEntity app = new IamTrustedApplicationEntity();
        app.setApplicationCode(applicationCode);
        app.setApplicationName(applicationName);
        app.setDescription(request.getDescription());
        app.setIcon(normalizeIcon(request.getIcon()));
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        IamTrustedApplicationEntity saved = trustedApplicationRepository.save(app);

        boolean portalEnabled = savePortalConfig(saved.getId(), applicationCode, request.getPortal(), now);

        TrustedApplicationClientSecretResponse initialClientSecret =
                trustedApplicationClientService.addClient(saved.getId(), request.getInitialClient());

        saveManifestIfPresent(saved.getId(), request);

        log.info("可信应用创建成功：id={}, code={}", saved.getId(), applicationCode);
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.APPLICATION,
                String.valueOf(saved.getId()), applicationCode, null);
        return TrustedApplicationCreatedResponse.builder()
                .application(toSummary(saved, 1, portalEnabled))
                .initialClientSecret(initialClientSecret.getClientSecret())
                .build();
    }

    /**
     * 应用详情（聚合 clients + portal + menus）
     */
    public TrustedApplicationDetailResponse getApplication(Long applicationId) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        Optional<IamTrustedApplicationPortalEntity> portalOpt =
                trustedApplicationPortalRepository.findById(applicationId);
        PortalIntegrationResponse portal = portalOpt.map(this::toPortalResponse).orElse(null);
        return TrustedApplicationDetailResponse.builder()
                .id(app.getId())
                .applicationCode(app.getApplicationCode())
                .applicationName(app.getApplicationName())
                .description(app.getDescription())
                .icon(app.getIcon())
                .builtIn(isBuiltInApplication(app.getApplicationCode()))
                .portal(portal)
                .clients(trustedApplicationClientService.listClients(applicationId))
                .build();
    }

    /**
     * 列出全部应用（不含 client 明细，只含统计）
     */
    public List<TrustedApplicationResponse> listApplications() {
        List<IamTrustedApplicationEntity> apps = trustedApplicationRepository.findAll();
        List<TrustedApplicationResponse> result = new ArrayList<>();
        for (IamTrustedApplicationEntity app : apps) {
            result.add(toSummary(app, countClients(app.getId()), isPortalEnabled(app.getId())));
        }
        return result;
    }

    /**
     * 管理台分页查询可信应用
     *
     * @param keyword 应用编码、名称或描述关键词，为空时不过滤
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @return 可信应用分页结果
     */
    public Page<TrustedApplicationResponse> listApplicationPage(String keyword, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return trustedApplicationRepository.searchForConsole(normalizedKeyword, pageable)
                .map(app -> toSummary(app, countClients(app.getId()), isPortalEnabled(app.getId())));
    }

    /**
     * 更新应用基本信息 + Portal 配置 + 菜单
     *
     * <p>{@code portal} 非空时整体覆盖（先清旧 portal + 菜单再写新）；为空时保持现状不动。
     */
    @Transactional
    public TrustedApplicationResponse updateApplication(Long applicationId, UpdateTrustedApplicationRequest request) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        if (StringUtils.hasText(request.getApplicationName())) {
            app.setApplicationName(request.getApplicationName().trim());
        }
        if (request.getDescription() != null) {
            app.setDescription(request.getDescription());
        }
        if (request.getIcon() != null) {
            app.setIcon(normalizeIcon(request.getIcon()));
        }
        app.setUpdatedAt(Instant.now());
        trustedApplicationRepository.save(app);

        boolean portalEnabled;
        if (request.getPortal() != null) {
            trustedApplicationMenuRepository.deleteByApplicationId(applicationId);
            if (trustedApplicationPortalRepository.existsById(applicationId)) {
                trustedApplicationPortalRepository.deleteById(applicationId);
            }
            portalEnabled = savePortalConfig(applicationId, app.getApplicationCode(),
                    request.getPortal(), Instant.now());
        } else {
            portalEnabled = isPortalEnabled(applicationId);
        }
        log.info("可信应用更新成功：id={}", applicationId);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                String.valueOf(applicationId), app.getApplicationCode(), null);
        return toSummary(app, countClients(applicationId), portalEnabled);
    }

    /**
     * 删除应用（级联清理，顺序固定）；内置应用（引导注册，如 IAM 管理台）禁止删除
     *
     * <ol>
     *   <li>iam_trusted_application_menu 按 application_id 删</li>
     *   <li>iam_trusted_application_portal 按 application_id 删</li>
     *   <li>iam_application_permission_manifest 按 application_id 删</li>
     *   <li>iam_role_authorization_rule 按 application_id 删</li>
     *   <li>iam_application_authorization（授权投影）按 application_id 删</li>
     *   <li>oauth2_authorization 按每个 client 的 registered_client_id 删</li>
     *   <li>oauth2_authorization_consent 按每个 client 的 registered_client_id 删</li>
     *   <li>iam_consent 按 client_id 删（投影表）</li>
     *   <li>oauth2_registered_client 按 application_id 删其下全部 client</li>
     *   <li>iam_trusted_application 按 id 删</li>
     * </ol>
     */
    @Transactional
    public void deleteApplication(Long applicationId) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        if (isBuiltInApplication(app.getApplicationCode())) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_DELETE_BLOCKED,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_DELETE_BLOCKED, app.getApplicationCode()));
        }
        trustedApplicationMenuRepository.deleteByApplicationId(applicationId);
        if (trustedApplicationPortalRepository.existsById(applicationId)) {
            trustedApplicationPortalRepository.deleteById(applicationId);
        }
        manifestRepository.deleteByApplicationId(applicationId);
        ruleRepository.deleteByApplicationId(applicationId);
        applicationAuthorizationRepository.deleteByApplicationId(applicationId);

        List<ClientRow> rows = jdbcTemplate.query(SQL_LIST_REGISTERED_CLIENT_IDS_BY_APP, (rs, n) -> {
            ClientRow row = new ClientRow();
            row.registeredClientId = rs.getString("id");
            row.clientId = rs.getString("client_id");
            return row;
        }, applicationId);
        for (ClientRow row : rows) {
            jdbcTemplate.update(SQL_DELETE_AUTHORIZATION, row.registeredClientId);
            jdbcTemplate.update(SQL_DELETE_AUTHORIZATION_CONSENT, row.registeredClientId);
            jdbcTemplate.update(SQL_DELETE_IAM_CONSENT_BY_CLIENT, row.clientId);
        }
        jdbcTemplate.update(SQL_DELETE_REGISTERED_CLIENT_BY_APP, applicationId);

        trustedApplicationRepository.deleteById(applicationId);
        log.info("可信应用删除成功（级联）：id={}", applicationId);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.APPLICATION,
                String.valueOf(applicationId), app.getApplicationCode(), null);
    }

    private IamTrustedApplicationEntity requireApplication(Long applicationId) {
        return trustedApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId)));
    }

    /**
     * 保存 Portal 集成配置 + 菜单项。{@code portal} 为空时跳过，返回 false。
     */
    private boolean savePortalConfig(Long applicationId, String applicationCode,
                                     PortalIntegrationRequest portal, Instant now) {
        if (portal == null) {
            return false;
        }
        boolean enabled = Boolean.TRUE.equals(portal.getEnabled());
        if (enabled && !StringUtils.hasText(portal.getEntry())) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_CONFIG_INVALID,
                    ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_CONFIG_INVALID);
        }
        String routePrefix = String.format(SimpleIamServerConstant.PORTAL_ROUTE_PREFIX_TEMPLATE, applicationCode);
        IamTrustedApplicationPortalEntity portalEntity = new IamTrustedApplicationPortalEntity();
        portalEntity.setApplicationId(applicationId);
        portalEntity.setEnabled(enabled
                ? SimpleIamServerConstant.STATUS_ACTIVE : SimpleIamServerConstant.STATUS_INACTIVE);
        portalEntity.setRoutePrefix(routePrefix);
        portalEntity.setEntry(StringUtils.hasText(portal.getEntry()) ? portal.getEntry().trim() : null);
        portalEntity.setApiBase(StringUtils.hasText(portal.getApiBase()) ? portal.getApiBase().trim() : null);
        portalEntity.setUpdatedAt(now);
        trustedApplicationPortalRepository.save(portalEntity);
        saveMenus(applicationId, portal.getMenus());
        return enabled;
    }

    private void saveMenus(Long applicationId, List<MenuItemRequest> menus) {
        if (menus == null || menus.isEmpty()) {
            return;
        }
        int autoOrder = SimpleIamServerConstant.DEFAULT_SORT_ORDER;
        for (MenuItemRequest menu : menus) {
            IamTrustedApplicationMenuEntity menuEntity = new IamTrustedApplicationMenuEntity();
            menuEntity.setApplicationId(applicationId);
            menuEntity.setCode(normalizeRequired(menu.getCode(), "菜单编码不能为空"));
            menuEntity.setName(normalizeRequired(menu.getName(), "菜单名称不能为空"));
            menuEntity.setRoute(normalizeRequired(menu.getRoute(), "菜单路由不能为空"));
            menuEntity.setSortOrder(menu.getSortOrder() != null ? menu.getSortOrder() : autoOrder);
            trustedApplicationMenuRepository.save(menuEntity);
            autoOrder++;
        }
    }

    private PortalIntegrationResponse toPortalResponse(IamTrustedApplicationPortalEntity portal) {
        List<IamTrustedApplicationMenuEntity> menuEntities = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(portal.getApplicationId());
        List<MenuItemResponse> menus = menuEntities.stream()
                .map(m -> MenuItemResponse.builder()
                        .code(m.getCode())
                        .name(m.getName())
                        .route(m.getRoute())
                        .sortOrder(m.getSortOrder())
                        .build())
                .collect(Collectors.toList());
        return PortalIntegrationResponse.builder()
                .enabled(portal.getEnabled() == SimpleIamServerConstant.STATUS_ACTIVE)
                .routePrefix(portal.getRoutePrefix())
                .entry(portal.getEntry())
                .apiBase(portal.getApiBase())
                .menus(menus)
                .build();
    }

    private boolean isPortalEnabled(Long applicationId) {
        return trustedApplicationPortalRepository.findById(applicationId)
                .map(p -> p.getEnabled() == SimpleIamServerConstant.STATUS_ACTIVE)
                .orElse(false);
    }

    /**
     * 是否内置应用：以引导配置列表（admin.bootstrap.built-in-applications）为唯一口径，
     * 内置应用禁止删除；下线走关闭门户集成。
     */
    private boolean isBuiltInApplication(String applicationCode) {
        return properties.getBootstrap().getBuiltInApplications().stream()
                .anyMatch(config -> config.getApplicationCode() != null
                        && config.getApplicationCode().equals(applicationCode));
    }

    private int countClients(Long applicationId) {
        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_CLIENTS_BY_APP, Integer.class, applicationId);
        return count != null ? count : 0;
    }

    private TrustedApplicationResponse toSummary(IamTrustedApplicationEntity app, int clientCount,
                                                 boolean portalEnabled) {
        return TrustedApplicationResponse.builder()
                .id(app.getId())
                .applicationCode(app.getApplicationCode())
                .applicationName(app.getApplicationName())
                .description(app.getDescription())
                .icon(app.getIcon())
                .clientCount(clientCount)
                .portalEnabled(portalEnabled)
                .builtIn(isBuiltInApplication(app.getApplicationCode()))
                .build();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SimpleIamServerException(message);
        }
        return value.trim();
    }

    private String normalizeIcon(String icon) {
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        String normalized = icon.trim();
        if (!TrustedApplicationIcon.isSupported(normalized)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_ICON_INVALID,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_ICON_INVALID, normalized));
        }
        return normalized;
    }

    /**
     * 创建应用时同步登记清单（若请求带清单字段）
     */
    private void saveManifestIfPresent(Long applicationId, CreateTrustedApplicationRequest request) {
        if (hasManifestFields(request)) {
            io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest manifestRequest =
                    new io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest();
            manifestRequest.setRoles(request.getRoles() != null ? request.getRoles() : java.util.Collections.emptyList());
            manifestRequest.setPagePermissions(request.getPagePermissions() != null ? request.getPagePermissions() : java.util.Collections.emptyList());
            manifestRequest.setApiPermissions(request.getApiPermissions() != null ? request.getApiPermissions() : java.util.Collections.emptyList());
            manifestRequest.setDataResources(request.getDataResources() != null
                    ? request.getDataResources() : java.util.Collections.emptyList());
            manifestService.putManifest(applicationId, manifestRequest);
        }
    }

    private boolean hasManifestFields(CreateTrustedApplicationRequest request) {
        return (request.getRoles() != null && !request.getRoles().isEmpty())
                || (request.getPagePermissions() != null && !request.getPagePermissions().isEmpty())
                || (request.getApiPermissions() != null && !request.getApiPermissions().isEmpty())
                || (request.getDataResources() != null && !request.getDataResources().isEmpty());
    }

    /**
     * 删除级联用的轻量行对象
     */
    private static final class ClientRow {
        private String registeredClientId;
        private String clientId;
    }
}
