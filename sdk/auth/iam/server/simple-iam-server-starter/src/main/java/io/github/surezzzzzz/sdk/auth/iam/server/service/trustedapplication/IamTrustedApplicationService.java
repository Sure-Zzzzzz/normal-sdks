package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.TrustedApplicationIcon;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalConfigurationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalDefaultEntryRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalLoginLandingRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalDefaultEntryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalIntegrationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalLoginLandingResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationCreatedResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationDetailResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalSettingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamPortalSettingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationMenuRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalApplicationOrderService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMenuTreeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 可信应用管理服务（应用维度）
 *
 * <p>一应用可关联多个 OAuth2 客户端（区分可信前端接入 Portal 侧边栏 / 可信后端驱动授权码流程）。
 * 本服务负责应用主表 CRUD + Portal 集成配置 + 菜单管理；client 维度的增删改查委托
 * {@link IamTrustedApplicationClientService}。
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
public class IamTrustedApplicationService {

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
    private final IamPortalSettingRepository portalSettingRepository;
    private final IamPortalApplicationOrderService portalApplicationOrderService;
    private final IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;
    private final IamPortalMenuTreeService portalMenuTreeService;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final IamTrustedApplicationClientService trustedApplicationClientService;
    private final IamTrustedApplicationLifecycleService lifecycleService;
    private final IamApplicationAuthorizationStateService authorizationStateService;
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
        app.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        app.setApplicationSecurityEpoch(1L);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        IamTrustedApplicationEntity saved = trustedApplicationRepository.save(app);
        authorizationStateService.ensureInitialState(saved.getId());

        saveManifestIfPresent(saved.getId(), request);
        IamTrustedApplicationPortalEntity portal = savePortalConfig(saved.getId(), applicationCode,
                request.getPortal(), now, true);
        boolean portalEnabled = isEnabled(portal);

        TrustedApplicationClientSecretResponse initialClientSecret =
                trustedApplicationClientService.addClient(saved.getId(), request.getInitialClient());

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
     * <p>{@code portal} 非空时更新 Portal 标量字段和指定的菜单来源；为空时保持现状不动。
     */
    @Transactional
    public TrustedApplicationResponse updateApplication(Long applicationId, UpdateTrustedApplicationRequest request) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        lifecycleService.requireMutable(applicationId);
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
            portalEnabled = isEnabled(savePortalConfig(applicationId, app.getApplicationCode(),
                    request.getPortal(), Instant.now(), true));
        } else {
            portalEnabled = isPortalEnabled(applicationId);
        }
        log.info("可信应用更新成功：id={}", applicationId);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                String.valueOf(applicationId), app.getApplicationCode(), null);
        return toSummary(app, countClients(applicationId), portalEnabled);
    }

    /**
     * 异步删除应用。受理时先安全停用，后台再经 OAuth 授权服务完成可恢复的物理清理。
     */
    @Transactional
    public io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationCleanupOperationResponse
    deleteApplication(Long applicationId) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        if (isBuiltInApplication(app.getApplicationCode())) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_DELETE_BLOCKED,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_DELETE_BLOCKED, app.getApplicationCode()));
        }
        return lifecycleService.acceptDelete(app);
    }

    /**
     * 安全停用可信应用。
     */
    @Transactional
    public TrustedApplicationResponse suspendApplication(Long applicationId) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        assertNotBuiltIn(app);
        lifecycleService.suspend(app);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                String.valueOf(applicationId), app.getApplicationCode(), "status=SUSPENDED");
        return toSummary(app, countClients(applicationId), isPortalEnabled(applicationId));
    }

    /**
     * 恢复可信应用；旧 OAuth 授权不会因恢复而复活。
     */
    @Transactional
    public TrustedApplicationResponse resumeApplication(Long applicationId) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        assertNotBuiltIn(app);
        lifecycleService.resume(app);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                String.valueOf(applicationId), app.getApplicationCode(), "status=ACTIVE");
        return toSummary(app, countClients(applicationId), isPortalEnabled(applicationId));
    }

    private IamTrustedApplicationEntity requireApplication(Long applicationId) {
        return trustedApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId)));
    }

    /**
     * 保存 Portal 集成配置和指定菜单快照。旧写模型不修改默认入口，但必须保持其有效。
     */
    private IamTrustedApplicationPortalEntity savePortalConfig(Long applicationId, String applicationCode,
                                                               PortalIntegrationRequest portal, Instant now, boolean validateExistingDefault) {
        if (portal == null) {
            return null;
        }
        boolean enabled = Boolean.TRUE.equals(portal.getEnabled());
        if (enabled && !StringUtils.hasText(portal.getEntry())) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_CONFIG_INVALID,
                    ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_CONFIG_INVALID);
        }
        String routePrefix = String.format(SimpleIamServerConstant.PORTAL_ROUTE_PREFIX_TEMPLATE, applicationCode);
        Optional<IamTrustedApplicationPortalEntity> current = trustedApplicationPortalRepository.findById(applicationId);
        IamTrustedApplicationPortalEntity portalEntity = current.orElseGet(IamTrustedApplicationPortalEntity::new);
        portalEntity.setApplicationId(applicationId);
        portalEntity.setEnabled(enabled
                ? SimpleIamServerConstant.STATUS_ACTIVE : SimpleIamServerConstant.STATUS_INACTIVE);
        portalEntity.setRoutePrefix(routePrefix);
        portalEntity.setEntry(StringUtils.hasText(portal.getEntry()) ? portal.getEntry().trim() : null);
        portalEntity.setApiBase(StringUtils.hasText(portal.getApiBase()) ? portal.getApiBase().trim() : null);
        portalEntity.setUpdatedAt(now);
        portalMenuTreeService.updateMenus(applicationId, portal.getMenus(), portal.getMenuTree());
        if (validateExistingDefault) {
            validateConfiguredDefaultEntry(applicationId, portalEntity);
            validateGlobalLandingReference(applicationId, portalEntity);
        }
        if (!current.isPresent()) {
            portalApplicationOrderService.assignInitialSortOrder(portalEntity);
        }
        return trustedApplicationPortalRepository.save(portalEntity);
    }

    private PortalIntegrationResponse toPortalResponse(IamTrustedApplicationPortalEntity portal) {
        java.util.List<io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMenuTreeNodeResponse> menuTree =
                portalMenuTreeService.getManagementTree(portal.getApplicationId());
        return PortalIntegrationResponse.builder()
                .enabled(portal.getEnabled() == SimpleIamServerConstant.STATUS_ACTIVE)
                .routePrefix(portal.getRoutePrefix())
                .entry(portal.getEntry())
                .apiBase(portal.getApiBase())
                .menus(portalMenuTreeService.flattenManagement(menuTree))
                .menuTree(menuTree)
                .defaultEntry(toDefaultEntry(portal))
                .configVersion(portal.getConfigVersion())
                .build();
    }

    /**
     * 新管理台的完整 Portal 配置写入口。
     *
     * <p>菜单树和默认入口在同一事务内提交，避免快照重建后留下默认 PAGE 悬挂引用。
     */
    @Transactional
    public PortalIntegrationResponse updatePortalConfiguration(Long applicationId, PortalConfigurationRequest request) {
        IamTrustedApplicationEntity app = requireApplication(applicationId);
        lifecycleService.requireMutable(applicationId);
        IamTrustedApplicationPortalEntity current = trustedApplicationPortalRepository.findById(applicationId)
                .orElseGet(() -> newPortal(applicationId, app.getApplicationCode()));
        if (request == null || request.getConfigVersion() == null
                || !Objects.equals(request.getConfigVersion(), versionOf(current.getConfigVersion()))) {
            throw portalConfigurationConflict(applicationId);
        }

        PortalIntegrationRequest legacyShape = new PortalIntegrationRequest();
        legacyShape.setEnabled(request.getEnabled());
        legacyShape.setEntry(request.getEntry());
        legacyShape.setApiBase(request.getApiBase());
        legacyShape.setMenuTree(request.getMenuTree());
        IamTrustedApplicationPortalEntity saved = savePortalConfig(applicationId, app.getApplicationCode(),
                legacyShape, Instant.now(), false);
        applyDefaultEntry(applicationId, saved, request.getDefaultEntry());
        validateConfiguredDefaultEntry(applicationId, saved);
        validateGlobalLandingReference(applicationId, saved);
        try {
            IamTrustedApplicationPortalEntity persisted = trustedApplicationPortalRepository.saveAndFlush(saved);
            int rootMenuCount = request.getMenuTree() == null ? 0 : request.getMenuTree().size();
            boolean defaultEntryConfigured = toDefaultEntry(persisted) != null;
            log.info("可信应用 Portal 配置更新成功：applicationId={}, code={}, enabled={}, configVersion={}, rootMenuCount={}, defaultEntryConfigured={}",
                    applicationId, app.getApplicationCode(), isEnabled(persisted), persisted.getConfigVersion(),
                    rootMenuCount, defaultEntryConfigured);
            auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                    String.valueOf(applicationId), app.getApplicationCode(),
                    "portalEnabled=" + isEnabled(persisted) + ", configVersion=" + persisted.getConfigVersion()
                            + ", rootMenuCount=" + rootMenuCount + ", defaultEntryConfigured=" + defaultEntryConfigured);
            return toPortalResponse(persisted);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw portalConfigurationConflict(applicationId);
        }
    }

    /**
     * 平台管理员读取当前全局登录首页配置。
     */
    public PortalLoginLandingResponse getPortalLoginLanding() {
        IamPortalSettingEntity setting = portalSettingRepository.findById(IamPortalSettingEntity.SINGLETON_ID)
                .orElseGet(this::newPortalSetting);
        String applicationCode = setting.getLoginLandingApplicationId() == null ? null
                : trustedApplicationRepository.findById(setting.getLoginLandingApplicationId())
                .map(IamTrustedApplicationEntity::getApplicationCode).orElse(null);
        return PortalLoginLandingResponse.builder()
                .applicationCode(applicationCode)
                .version(versionOf(setting.getVersion()))
                .build();
    }

    /**
     * 平台管理员更新或关闭无深链登录首页。
     */
    @Transactional
    public PortalLoginLandingResponse updatePortalLoginLanding(PortalLoginLandingRequest request) {
        IamPortalSettingEntity setting = portalSettingRepository.findById(IamPortalSettingEntity.SINGLETON_ID)
                .orElseGet(this::newPortalSetting);
        if (request == null || request.getVersion() == null
                || !Objects.equals(request.getVersion(), versionOf(setting.getVersion()))) {
            throw portalLoginLandingInvalid("配置版本已变化，请刷新后重试");
        }
        String applicationCode = StringUtils.hasText(request.getApplicationCode())
                ? request.getApplicationCode().trim() : null;
        Long auditApplicationId = setting.getLoginLandingApplicationId();
        String auditApplicationCode = auditApplicationId == null ? null : trustedApplicationRepository
                .findById(auditApplicationId).map(IamTrustedApplicationEntity::getApplicationCode).orElse(null);
        if (applicationCode == null) {
            setting.setLoginLandingApplicationId(null);
        } else {
            IamTrustedApplicationEntity app = trustedApplicationRepository.findByApplicationCode(applicationCode)
                    .orElseThrow(() -> portalLoginLandingInvalid("应用不存在：" + applicationCode));
            IamTrustedApplicationPortalEntity portal = trustedApplicationPortalRepository.findById(app.getId())
                    .orElseThrow(() -> portalLoginLandingInvalid("应用未启用 Portal：" + applicationCode));
            if (!isEnabled(portal) || toDefaultEntry(portal) == null) {
                throw portalLoginLandingInvalid("应用没有可用默认入口：" + applicationCode);
            }
            validateConfiguredDefaultEntry(app.getId(), portal);
            setting.setLoginLandingApplicationId(app.getId());
            auditApplicationId = app.getId();
            auditApplicationCode = app.getApplicationCode();
        }
        setting.setUpdatedAt(Instant.now());
        try {
            setting = portalSettingRepository.saveAndFlush(setting);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw portalLoginLandingInvalid("配置版本已变化，请刷新后重试");
        }
        boolean loginLandingEnabled = applicationCode != null;
        log.info("Portal 全局登录首页更新成功：applicationId={}, code={}, enabled={}, version={}",
                auditApplicationId, auditApplicationCode, loginLandingEnabled, setting.getVersion());
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                auditApplicationId == null ? null : String.valueOf(auditApplicationId), auditApplicationCode,
                "loginLandingEnabled=" + loginLandingEnabled + ", version=" + setting.getVersion());
        return PortalLoginLandingResponse.builder()
                .applicationCode(applicationCode)
                .version(versionOf(setting.getVersion()))
                .build();
    }

    private IamTrustedApplicationPortalEntity newPortal(Long applicationId, String applicationCode) {
        IamTrustedApplicationPortalEntity portal = new IamTrustedApplicationPortalEntity();
        portal.setApplicationId(applicationId);
        portal.setRoutePrefix(String.format(SimpleIamServerConstant.PORTAL_ROUTE_PREFIX_TEMPLATE, applicationCode));
        return portal;
    }

    private IamPortalSettingEntity newPortalSetting() {
        IamPortalSettingEntity setting = new IamPortalSettingEntity();
        setting.setId(IamPortalSettingEntity.SINGLETON_ID);
        setting.setUpdatedAt(Instant.now());
        return setting;
    }

    private void applyDefaultEntry(Long applicationId, IamTrustedApplicationPortalEntity portal,
                                   PortalDefaultEntryRequest defaultEntry) {
        if (defaultEntry == null) {
            portal.setDefaultPageMenuCode(null);
            portal.setDefaultEntryPath(null);
            return;
        }
        String pageCode = StringUtils.hasText(defaultEntry.getPageMenuCode())
                ? defaultEntry.getPageMenuCode().trim() : null;
        String path = portalMenuTreeService.resolveDefaultEntryPath(applicationId, pageCode,
                defaultEntry.getEntryPath());
        portal.setDefaultPageMenuCode(pageCode);
        portal.setDefaultEntryPath(path);
    }

    private void validateConfiguredDefaultEntry(Long applicationId, IamTrustedApplicationPortalEntity portal) {
        if (portal.getDefaultPageMenuCode() == null) {
            if (portal.getDefaultEntryPath() != null) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID,
                                "未选择默认 PAGE 时不能填写入口路径"));
            }
            return;
        }
        String path = portalMenuTreeService.resolveDefaultEntryPath(applicationId,
                portal.getDefaultPageMenuCode(), portal.getDefaultEntryPath());
        portal.setDefaultEntryPath(path);
    }

    private void validateGlobalLandingReference(Long applicationId, IamTrustedApplicationPortalEntity portal) {
        IamPortalSettingEntity setting = portalSettingRepository.findById(IamPortalSettingEntity.SINGLETON_ID)
                .orElse(null);
        if (setting == null || !Objects.equals(setting.getLoginLandingApplicationId(), applicationId)) {
            return;
        }
        if (!isEnabled(portal) || toDefaultEntry(portal) == null) {
            throw portalLoginLandingInvalid("全局首页应用必须保持启用且有默认入口");
        }
    }

    private PortalDefaultEntryResponse toDefaultEntry(IamTrustedApplicationPortalEntity portal) {
        if (portal == null || !StringUtils.hasText(portal.getDefaultPageMenuCode())) {
            return null;
        }
        return PortalDefaultEntryResponse.builder()
                .pageMenuCode(portal.getDefaultPageMenuCode())
                .path(portal.getDefaultEntryPath())
                .build();
    }

    private long versionOf(Long version) {
        return version == null ? 0L : version;
    }

    private boolean isEnabled(IamTrustedApplicationPortalEntity portal) {
        return portal != null && portal.getEnabled() != null
                && portal.getEnabled() == SimpleIamServerConstant.STATUS_ACTIVE;
    }

    private SimpleIamServerException portalConfigurationConflict(Long applicationId) {
        return new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_CONFIGURATION_CONFLICT,
                String.format(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_CONFIGURATION_CONFLICT, applicationId));
    }

    private SimpleIamServerException portalLoginLandingInvalid(String detail) {
        return new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_LOGIN_LANDING_INVALID,
                String.format(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_LOGIN_LANDING_INVALID, detail));
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

    private void assertNotBuiltIn(IamTrustedApplicationEntity application) {
        if (isBuiltInApplication(application.getApplicationCode())) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_DELETE_BLOCKED,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_DELETE_BLOCKED,
                            application.getApplicationCode()));
        }
    }

    private long countClients(Long applicationId) {
        Long count = jdbcTemplate.queryForObject(SQL_COUNT_CLIENTS_BY_APP, Long.class, applicationId);
        return count != null ? count : 0L;
    }

    private TrustedApplicationResponse toSummary(IamTrustedApplicationEntity app, long clientCount,
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
