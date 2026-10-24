package io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.*;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalMenuTreeNodeRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMenuTreeNodeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.*;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.department.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationMenuEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.*;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.department.IamDepartmentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationMenuRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamAuthorizationProjectionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalApplicationOrderService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMenuTreeService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.validator.PasswordPolicyValidator;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 启动引导服务：首次启动时创建内置 iam_admin 角色和管理员账号。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamBootstrapService implements ApplicationRunner {

    private final SimpleIamServerProperties properties;
    private final IamUserRepository userRepository;
    private final IamRoleRepository roleRepository;
    private final IamDepartmentRepository departmentRepository;
    private final IamPermissionRepository permissionRepository;
    private final IamRolePermissionRepository rolePermissionRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamTrustedApplicationPortalRepository trustedApplicationPortalRepository;
    private final IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final IamRoleAuthorizationRuleRepository roleAuthorizationRuleRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamPortalMenuTreeService portalMenuTreeService;
    private final IamPortalApplicationOrderService portalApplicationOrderService;
    private final IamAuthorizationProjectionService projectionService;
    private final IamApplicationAuthorizationStateService authorizationStateService;
    private final IamUserService userService;
    private final IamTrustedApplicationMutationGuard mutationGuard;
    private final IamRoleService roleService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final SimpleRedisLock redisLock;
    private final RedisKeyHelper redisKeyHelper;
    private final TransactionTemplate transactionTemplate;
    private final IamDeploymentPasswordRecoveryService deploymentPasswordRecoveryService;
    private final IamOpenRoleManifestUpgradeService openRoleManifestUpgradeService;
    private final IamBootstrapProjectionReconciliationService bootstrapProjectionReconciliationService;

    private static BuiltInPermission[] builtInPermissions() {
        return new BuiltInPermission[]{
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE, "IAM 用户管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_PAGE, "IAM 角色管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_PAGE, "IAM 站内信管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE, "IAM 可信应用管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_PAGE, "IAM 部门管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_PAGE, "IAM 协作组管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE, "IAM 仪表盘页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API, "IAM 用户管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API, "IAM 角色管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API, "IAM 权限管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API, "IAM 站内信接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API, "IAM 可信应用管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API, "IAM 部门管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API, "IAM 协作组管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_API, "IAM 仪表盘统计接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_PORTAL_API, "IAM 门户准入查询接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_APPLICATION_API, "IAM 可信应用目录查询接口", "api"),
                new BuiltInPermission(IamPortalMachineDirectoryConstant.API_PERMISSION, "IAM Portal 可信应用路由投影接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API, "IAM 会话管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DATA_ALL, "IAM 全量数据范围", "data")
        };
    }

    /**
     * 启动引导入口：幂等初始化内置角色 / 权限 / 管理员，并消费部署级恢复码
     */
    @Override
    public void run(ApplicationArguments args) {
        String lockKey = redisKeyHelper.buildLockKey(SimpleIamServerConstant.LOCK_KEY_BOOTSTRAP);
        Optional<RedisLockLease> lease = redisLock.tryLockWithLease(lockKey,
                SimpleIamServerConstant.BOOTSTRAP_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        if (!lease.isPresent()) {
            log.info(ServerErrorMessage.BOOTSTRAP_LOCK_UNAVAILABLE);
            return;
        }
        RedisLockLease bootstrapLease = lease.get();
        try {
            requireBootstrapLease(bootstrapLease);
            boolean fullBootstrapEnabled = Boolean.TRUE.equals(properties.getBootstrap().getEnabled());
            if (fullBootstrapEnabled) {
                transactionTemplate.executeWithoutResult(status -> {
                    ensureAdminRole();
                    ensureBuiltInPermissions();
                    bindAdminRolePermissions();
                    ensureRootDepartment();
                    ensureBuiltInApplications();
                    String bootstrapInitialPassword = ensureAdminUser();
                    deploymentPasswordRecoveryService
                            .recoverBootstrapAdministratorIfRequested(bootstrapInitialPassword);
                });
            }

            requireBootstrapLease(bootstrapLease);
            IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                    .findByApplicationCode(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM).orElse(null);
            if (iamApp != null && (authorizationStateService.isRecomputePending(iamApp.getId())
                    || requiresBootstrapReconciliation(iamApp.getId(), fullBootstrapEnabled))) {
                IamApplicationAuthorizationStateService.RecomputeToken token =
                        authorizationStateService.beginRecompute(iamApp.getId());
                requireBootstrapLease(bootstrapLease);
                transactionTemplate.executeWithoutResult(status -> {
                    if (fullBootstrapEnabled) {
                        ensureBuiltInApplicationManifests(token);
                        upgradeIamDashboardPermissionIfNeeded(false, token);
                        ensureAdminRoleAuthorizationRules(token);
                        upgradeIamDashboardPermissionIfNeeded(true, token);
                        // 菜单引用的页面权限必须在清单和角色规则完成后再写入。
                        ensureBuiltInApplicationMenus();
                        openRoleManifestUpgradeService.upgradeForBootstrap(token);
                    } else {
                        upgradeExistingIamPortalManifest(token);
                    }
                });
                bootstrapProjectionReconciliationService.reconcile(iamApp.getId(), token,
                        () -> bootstrapLease.renew(SimpleIamServerConstant.BOOTSTRAP_LOCK_LEASE_SECONDS,
                                TimeUnit.SECONDS));
                requireBootstrapLease(bootstrapLease);
                authorizationStateService.finishRecompute(iamApp.getId(), token);
            }

            requireBootstrapLease(bootstrapLease);
            if (fullBootstrapEnabled) {
                transactionTemplate.executeWithoutResult(status -> ensureAdminApplicationAuthorizations());
            }
        } finally {
            bootstrapLease.close();
        }
    }

    private void requireBootstrapLease(RedisLockLease bootstrapLease) {
        if (!bootstrapLease.renew(SimpleIamServerConstant.BOOTSTRAP_LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
            log.warn("IAM 启动引导租约续期失败，本实例停止后续写入与授权投影对账");
            throw new IamOpenRoleException(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT);
        }
    }

    /**
     * 引导关闭时仍补齐已存在 IAM 应用清单的新声明，不创建管理员、应用或其他引导数据。
     */
    private void upgradeExistingIamPortalManifest(
            IamApplicationAuthorizationStateService.RecomputeToken token) {
        IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                .findByApplicationCode(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM).orElse(null);
        if (iamApp == null) {
            return;
        }
        IamApplicationPermissionManifestEntity manifest = manifestRepository
                .findByApplicationId(iamApp.getId()).orElse(null);
        if (manifest == null || manifest.getManifestVersion() <= 0L) {
            return;
        }
        ensurePortalMachineDirectoryPermission();
        ensureAdminRolePortalPermissionBinding();
        upgradePortalMachineDirectoryManifest(iamApp.getId(), manifest, token);
        upgradeIamAdminPortalAuthorizationRule(iamApp.getId(), token);
    }

    /**
     * 幂等插入内置权限码（存在即跳过，不更新自定义改动）。
     */
    private void ensureBuiltInPermissions() {
        for (BuiltInPermission builtIn : builtInPermissions()) {
            if (permissionRepository.existsByCode(builtIn.code)) {
                continue;
            }
            IamPermissionEntity permission = new IamPermissionEntity();
            permission.setCode(builtIn.code);
            permission.setName(builtIn.name);
            permission.setType(builtIn.type);
            permission.setBuiltIn(1);
            permission.setCreatedAt(Instant.now());
            permission.setUpdatedAt(Instant.now());
            permissionRepository.save(permission);
            log.info("内置权限创建成功：{}", builtIn.code);
        }
    }

    /**
     * 为内置 iam_admin 角色补齐内置权限绑定（只增不减：管理员手工解绑的内置权限
     * 绑定会在下次启动自动补回，防误降权导致管理入口缺失）。
     */
    private void bindAdminRolePermissions() {
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        Set<Long> bound = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .map(IamRolePermissionEntity::getPermissionId)
                .collect(Collectors.toSet());
        for (BuiltInPermission builtIn : builtInPermissions()) {
            IamPermissionEntity permission = permissionRepository.findByCode(builtIn.code).orElse(null);
            if (permission == null || bound.contains(permission.getId())) {
                continue;
            }
            IamRolePermissionEntity binding = new IamRolePermissionEntity();
            binding.setRoleId(adminRole.getId());
            binding.setPermissionId(permission.getId());
            binding.setCreatedAt(Instant.now());
            rolePermissionRepository.save(binding);
        }
    }

    private void ensureAdminRole() {
        ensureRole(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN, "IAM 管理员",
                "IAM 系统内置管理员角色，拥有全部权限");
        ensureRole(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_USER, "普通用户",
                "IAM 系统内置普通用户角色，无管理权限，供业务账号分配基础身份");
    }

    /**
     * 幂等创建默认根部门（存在即跳过，不更新自定义改动），供引导管理员直接挂载。
     */
    private void ensureRootDepartment() {
        if (departmentRepository.existsByCode(SimpleIamServerConstant.BUILT_IN_DEPARTMENT_ROOT)) {
            return;
        }
        IamDepartmentEntity department = new IamDepartmentEntity();
        department.setCode(SimpleIamServerConstant.BUILT_IN_DEPARTMENT_ROOT);
        department.setName("默认根部门");
        department.setCreatedAt(Instant.now());
        department.setUpdatedAt(Instant.now());
        departmentRepository.save(department);
        log.info("内置根部门创建成功：{}", SimpleIamServerConstant.BUILT_IN_DEPARTMENT_ROOT);
    }

    /**
     * 幂等注册内置可信应用列表（平台自身子应用，含门户集成；
     * 应用编码已存在即跳过，不更新自定义改动——管理员改过的 entry/菜单不被引导覆盖）。
     * 仅 IAM 的 1.0 官方默认扁平菜单会被精确识别并升级为 1.1 分组树。
     * 单项 entry 置空则跳过该项。
     */
    private void ensureBuiltInApplications() {
        for (SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config
                : properties.getBootstrap().getBuiltInApplications()) {
            String entry = config.getEntry() == null ? "" : config.getEntry().trim();
            if (entry.isEmpty()) {
                log.info("内置可信应用引导跳过（entry 为空）：{}", config.getApplicationCode());
                continue;
            }
            IamTrustedApplicationEntity existing = trustedApplicationRepository
                    .findByApplicationCode(config.getApplicationCode()).orElse(null);
            if (existing != null) {
                continue;
            }

            IamTrustedApplicationEntity application = new IamTrustedApplicationEntity();
            application.setApplicationCode(config.getApplicationCode());
            application.setApplicationName(config.getApplicationName());
            application.setDescription(config.getDescription());
            application.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
            application.setApplicationSecurityEpoch(1L);
            application.setCreatedAt(Instant.now());
            application.setUpdatedAt(Instant.now());
            IamTrustedApplicationEntity saved = trustedApplicationRepository.save(application);
            authorizationStateService.ensureInitialState(saved.getId());

            IamTrustedApplicationPortalEntity portal = new IamTrustedApplicationPortalEntity();
            portal.setApplicationId(saved.getId());
            portal.setEnabled(1);
            portal.setRoutePrefix(config.getRoutePrefix() != null && !config.getRoutePrefix().trim().isEmpty()
                    ? config.getRoutePrefix().trim()
                    : "/app/" + config.getApplicationCode());
            portal.setEntry(entry);
            portal.setApiBase(config.getApiBase());
            portal.setUpdatedAt(Instant.now());
            portalApplicationOrderService.assignInitialSortOrder(portal);
            trustedApplicationPortalRepository.save(portal);

            log.info("内置可信应用创建成功：{}（entry={}）", config.getApplicationCode(), entry);
        }
    }

    /**
     * 在应用及 IAM 权限清单均就绪后写入默认菜单。配置与管理面共用树校验，非法配置会使本次引导事务回滚。
     */
    private void ensureBuiltInApplicationMenus() {
        for (SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config
                : properties.getBootstrap().getBuiltInApplications()) {
            String entry = config.getEntry() == null ? "" : config.getEntry().trim();
            if (entry.isEmpty()) {
                continue;
            }
            IamTrustedApplicationEntity application = trustedApplicationRepository
                    .findByApplicationCode(config.getApplicationCode()).orElse(null);
            if (application == null) {
                continue;
            }
            List<IamTrustedApplicationMenuEntity> current = trustedApplicationMenuRepository
                    .findByApplicationIdOrderBySortOrderAsc(application.getId());
            if (current.isEmpty()) {
                saveBuiltInMenuTree(application.getId(), builtInMenuTree(config));
                continue;
            }
            upgradeLegacyDefaultIamMenuIfNeeded(config, application);
        }
    }

    /**
     * 仅升级精确等于 1.0 官方 IAM 默认菜单的实例。任意手工调整均保留，避免启动引导覆盖管理配置。
     */
    private void upgradeLegacyDefaultIamMenuIfNeeded(
            SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config,
            IamTrustedApplicationEntity application) {
        if (!SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM.equals(config.getApplicationCode())) {
            return;
        }
        if (!isLegacyDefaultIamMenu(application.getId())) {
            log.info("IAM 内置菜单已定制，保留现有配置：applicationId={}", application.getId());
            return;
        }
        saveBuiltInMenuTree(application.getId(), builtInMenuTree(config));
        log.info("IAM 内置默认菜单已从 1.0 扁平形态升级为 1.1 分组树");
    }

    private List<SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig> builtInMenuTree(
            SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config) {
        if (config.getMenuTree() != null && !config.getMenuTree().isEmpty()) {
            return config.getMenuTree();
        }
        return config.getMenus() == null ? Collections.emptyList() : config.getMenus();
    }

    private boolean isLegacyDefaultIamMenu(Long applicationId) {
        List<IamTrustedApplicationMenuEntity> current = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(applicationId);
        List<LegacyMenu> expected = legacyDefaultIamMenus();
        if (current.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            IamTrustedApplicationMenuEntity menu = current.get(index);
            LegacyMenu legacy = expected.get(index);
            String expectedPagePermission = "dashboard".equals(legacy.code)
                    ? SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE : null;
            boolean dashboardPermissionAccepted = "dashboard".equals(legacy.code)
                    && (menu.getRequiredPagePermission() == null
                    || expectedPagePermission.equals(menu.getRequiredPagePermission()));
            if (menu.getParentId() != null || menu.getNodeType() != PortalMenuNodeType.PAGE || menu.getIcon() != null
                    || (!dashboardPermissionAccepted
                    && !Objects.equals(expectedPagePermission, menu.getRequiredPagePermission()))
                    || !Integer.valueOf(index).equals(menu.getSortOrder())
                    || !legacy.code.equals(menu.getCode()) || !legacy.name.equals(menu.getName())
                    || !legacy.route.equals(menu.getRoute())) {
                return false;
            }
        }
        return true;
    }

    private List<LegacyMenu> legacyDefaultIamMenus() {
        return Arrays.asList(
                new LegacyMenu("dashboard", "仪表盘", "/"),
                new LegacyMenu("users", "用户管理", "/users"),
                new LegacyMenu("organizations", "组织与成员", "/organizations"),
                new LegacyMenu("user-groups", "协作组管理", "/user-groups"),
                new LegacyMenu("roles", "角色管理", "/roles"),
                new LegacyMenu("trusted-applications", "可信应用", "/trusted-applications"),
                new LegacyMenu("messages", "站内信", "/messages"));
    }

    private void saveBuiltInMenuTree(Long applicationId,
                                     List<SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig> menus) {
        portalMenuTreeService.updateMenus(applicationId, null, toMenuTreeRequests(menus));
    }

    private List<PortalMenuTreeNodeRequest> toMenuTreeRequests(
            List<SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig> menus) {
        List<PortalMenuTreeNodeRequest> result = new ArrayList<>();
        if (menus == null) {
            return result;
        }
        for (int index = 0; index < menus.size(); index++) {
            SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig source = menus.get(index);
            PortalMenuTreeNodeRequest target = new PortalMenuTreeNodeRequest();
            target.setCode(source == null ? null : source.getCode());
            target.setName(source == null ? null : source.getName());
            PortalMenuNodeType nodeType = source == null ? null : source.getNodeType();
            target.setNodeType((nodeType == null ? PortalMenuNodeType.PAGE : nodeType).name());
            target.setIcon(source == null ? null : source.getIcon());
            target.setRoute(source == null ? null : source.getRoute());
            target.setRequiredPagePermission(source == null ? null : source.getRequiredPagePermission());
            // GROUP 的展示模式由树校验器固定为 STANDARD，不能作为请求字段传入。
            target.setPresentationMode(source == null || source.getNodeType() != PortalMenuNodeType.PAGE
                    || source.getPresentationMode() == null ? null : source.getPresentationMode().name());
            target.setSortOrder(index);
            target.setChildren(toMenuTreeRequests(source == null ? null : source.getChildren()));
            result.add(target);
        }
        return result;
    }

    /**
     * 幂等为引导管理员补齐内置应用准入授权（门户可达列表按 admitted 授权过滤，
     * 未授权则门户空列表）；用户对某应用已有任何授权记录（含已撤销）则跳过，
     * 不覆盖管理员后续的手工授权管理。
     *
     * <p>已申报权限清单的应用走投影机制：准入 + 从角色规则计算权限；
     * 未申报清单的应用（yml 追加的内置应用）无法投影，退化为空权限手工行，
     * 仅保门户可达，权限待应用申报清单并定义规则后由投影重算补齐。
     */
    private void ensureAdminApplicationAuthorizations() {
        Long adminUserId = userRepository.findByUsername(properties.getBootstrap().getUsername())
                .map(IamUserEntity::getId)
                .orElse(null);
        if (adminUserId == null) {
            return;
        }
        for (SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config
                : properties.getBootstrap().getBuiltInApplications()) {
            IamTrustedApplicationEntity application = trustedApplicationRepository
                    .findByApplicationCode(config.getApplicationCode()).orElse(null);
            if (application == null
                    || applicationAuthorizationRepository.findByUserIdAndApplicationId(
                    adminUserId, application.getId()).isPresent()) {
                continue;
            }
            mutationGuard.requireMutable(application.getId());
            authorizationStateService.requireNoActiveRecompute(application.getId());
            boolean manifestDeclared = manifestRepository
                    .findByApplicationId(application.getId()).isPresent();
            if (manifestDeclared) {
                projectionService.grantApplicationAdmission(adminUserId, application.getId());
                log.info("内置应用准入授权已创建（投影模式）：username={}, applicationCode={}",
                        properties.getBootstrap().getUsername(), config.getApplicationCode());
            } else {
                IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
                authorization.setUserId(adminUserId);
                authorization.setApplicationId(application.getId());
                authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
                authorization.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
                authorization.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
                authorization.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
                authorization.setDataGrantDocumentJson(null);
                authorization.setAuthorizationVersion(1L);
                authorization.setManifestVersion("manual");
                authorization.setManifestDigest("manual");
                authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
                authorization.setCreatedAt(Instant.now());
                authorization.setUpdatedAt(Instant.now());
                applicationAuthorizationRepository.save(authorization);
                log.info("内置应用准入授权已创建（无清单，空权限手工行）：username={}, applicationCode={}",
                        properties.getBootstrap().getUsername(), config.getApplicationCode());
            }
        }
    }

    private void ensureRole(String code, String name, String description) {
        if (!roleRepository.existsByCode(code)) {
            IamRoleEntity role = new IamRoleEntity();
            role.setCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setBuiltIn(1);
            role.setCreatedAt(Instant.now());
            role.setUpdatedAt(Instant.now());
            roleRepository.save(role);
            log.info("内置角色创建成功：{}", code);
        }
    }

    private String ensureAdminUser() {
        String adminUsername = properties.getBootstrap().getUsername();
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("管理员账号已存在，跳过引导：username={}", adminUsername);
            return null;
        }

        String initialPassword = generateInitialPassword();
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword(initialPassword);
        request.setDisplayName("IAM 管理员");
        departmentRepository.findByCode(SimpleIamServerConstant.BUILT_IN_DEPARTMENT_ROOT)
                .ifPresent(root -> request.setDepartmentId(root.getId()));

        IamUserEntity admin = userService.createUser(request);
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(admin.getId(), adminRole.getId());
        // 首次引导密码只输出一次；账号已存在时不会再次生成或输出。
        log.info("管理员账号引导完成：username={}, id={}, initialPassword={}",
                admin.getUsername(), admin.getId(), initialPassword);
        return initialPassword;
    }

    private String generateInitialPassword() {
        SecureRandom random = new SecureRandom();
        SimpleIamServerProperties.PasswordConfig password = properties.getPassword();
        String uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lowercase = "abcdefghijkmnopqrstuvwxyz";
        String digit = "23456789";
        String special = "!@#$%&*+-_=";
        StringBuilder alphabet = new StringBuilder();
        StringBuilder builder = new StringBuilder();
        appendRequiredCharacter(builder, alphabet, uppercase, password.getRequireUppercase(), random);
        appendRequiredCharacter(builder, alphabet, lowercase, password.getRequireLowercase(), random);
        appendRequiredCharacter(builder, alphabet, digit, password.getRequireDigit(), random);
        appendRequiredCharacter(builder, alphabet, special, password.getRequireSpecial(), random);
        if (alphabet.length() == 0) {
            alphabet.append(uppercase).append(lowercase).append(digit).append(special);
        }
        int length = Math.min(password.getMaxLength(), Math.max(password.getMinLength(), 16));
        while (builder.length() < length) {
            builder.append(randomCharacter(alphabet.toString(), random));
        }
        String generated = shuffle(builder, random);
        passwordPolicyValidator.validate(generated);
        return generated;
    }

    private void appendRequiredCharacter(StringBuilder builder, StringBuilder alphabet,
                                         String characters, Boolean required, SecureRandom random) {
        alphabet.append(characters);
        if (Boolean.TRUE.equals(required)) {
            builder.append(randomCharacter(characters, random));
        }
    }

    private char randomCharacter(String characters, SecureRandom random) {
        return characters.charAt(random.nextInt(characters.length()));
    }

    private String shuffle(StringBuilder source, SecureRandom random) {
        for (int index = source.length() - 1; index > 0; index--) {
            int target = random.nextInt(index + 1);
            char current = source.charAt(index);
            source.setCharAt(index, source.charAt(target));
            source.setCharAt(target, current);
        }
        return source.toString();
    }

    /**
     * 为内置可信应用创建权限清单（幂等）。
     * IAM 应用清单包含当前所有内置权限码。
     */
    private void ensureBuiltInApplicationManifests(
            IamApplicationAuthorizationStateService.RecomputeToken token) {
        IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                .findByApplicationCode("iam").orElse(null);
        if (iamApp == null) {
            return;
        }

        // 检查是否已存在清单
        IamApplicationPermissionManifestEntity existingManifest = manifestRepository
                .findByApplicationId(iamApp.getId()).orElse(null);

        if (existingManifest != null && existingManifest.getManifestVersion() > 0L) {
            upgradePortalMachineDirectoryManifest(iamApp.getId(), existingManifest, token);
            return;
        }

        BuiltInPermission[] builtInPerms = builtInPermissions();
        List<String> pagePermissions = new ArrayList<>();
        List<String> apiPermissions = new ArrayList<>();

        for (BuiltInPermission perm : builtInPerms) {
            if ("page".equals(perm.type)) {
                pagePermissions.add(perm.code);
            } else if ("api".equals(perm.type)) {
                apiPermissions.add(perm.code);
            }
        }

        List<String> manifestRoles = new ArrayList<>();
        manifestRoles.add(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        manifestRoles.add(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_USER);

        PutApplicationPermissionManifestRequest request = new PutApplicationPermissionManifestRequest();
        request.setRoles(manifestRoles);
        request.setPagePermissions(pagePermissions);
        request.setApiPermissions(apiPermissions);
        request.setDataResources(iamDataResourceDeclarations());

        manifestService.putManifestForBootstrap(iamApp.getId(), request, token);
        log.info("IAM 应用权限清单引导创建成功：roles={}, pages={}, apis={}, dataResources={}",
                request.getRoles().size(), pagePermissions.size(), apiPermissions.size(),
                request.getDataResources().size());
    }

    /**
     * 1.3.8 为存量 IAM 应用清单幂等补齐 Portal 可信应用路由投影的 API 与 DATA 声明，
     * 不覆盖调用方已配置的其他权限。
     */
    private void upgradePortalMachineDirectoryManifest(Long applicationId,
                                                       IamApplicationPermissionManifestEntity existingManifest,
                                                       IamApplicationAuthorizationStateService.RecomputeToken token) {
        List<String> roles = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                .readStringList(existingManifest.getRolesJson(), "roles"));
        List<String> pages = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                .readStringList(existingManifest.getPagePermissionsJson(), "pagePermissions"));
        List<String> apis = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                .readStringList(existingManifest.getApiPermissionsJson(), "apiPermissions"));
        List<DataResourceDeclaration> resources = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                .readDataResourceList(existingManifest.getDataResourcesJson()));
        boolean changed = false;
        if (!apis.contains(IamPortalMachineDirectoryConstant.API_PERMISSION)) {
            apis.add(IamPortalMachineDirectoryConstant.API_PERMISSION);
            changed = true;
        }
        List<DataResourceDeclaration> portalResources = resources.stream()
                .filter(item -> IamPortalMachineDirectoryConstant.DATA_RESOURCE.equals(item.getResource()))
                .collect(Collectors.toList());
        DataResourceDeclaration expectedPortalResource = iamPortalMachineApplicationDataResourceDeclaration();
        if (portalResources.isEmpty()) {
            resources.add(expectedPortalResource);
            changed = true;
        } else if (portalResources.size() != 1 || !sameDataResourceDeclaration(
                portalResources.get(0), expectedPortalResource)) {
            throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
        }
        if (!changed) {
            log.info("IAM 应用权限清单已包含 Portal 可信应用路由投影：manifestVersion={}",
                    existingManifest.getManifestVersion());
            return;
        }
        PutApplicationPermissionManifestRequest request = new PutApplicationPermissionManifestRequest();
        request.setRoles(roles);
        request.setPagePermissions(pages);
        request.setApiPermissions(apis);
        request.setDataResources(resources);
        manifestService.putManifestForBootstrap(applicationId, request, token);
        log.info("IAM 应用权限清单已补齐 Portal 可信应用路由投影：applicationId={}", applicationId);
    }

    /**
     * 为内置 iam_admin 角色创建应用授权规则（幂等）。
     * 管理员角色拥有 IAM 应用的全部权限。
     */
    private void ensureAdminRoleAuthorizationRules(
            IamApplicationAuthorizationStateService.RecomputeToken token) {
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                .findByApplicationCode("iam").orElse(null);
        if (iamApp == null) {
            return;
        }

        IamRoleAuthorizationRuleEntity existingRule = roleAuthorizationRuleRepository.findByRoleIdAndApplicationId(
                adminRole.getId(), iamApp.getId()).orElse(null);
        if (existingRule != null) {
            upgradeIamAdminPortalAuthorizationRule(iamApp.getId(), token);
            return;
        }

        BuiltInPermission[] builtInPerms = builtInPermissions();
        List<String> pagePermissions = new ArrayList<>();
        List<String> apiPermissions = new ArrayList<>();

        for (BuiltInPermission perm : builtInPerms) {
            if ("page".equals(perm.type)) {
                pagePermissions.add(perm.code);
            } else if ("api".equals(perm.type)) {
                apiPermissions.add(perm.code);
            }
        }

        IamRoleAuthorizationRuleEntity rule = new IamRoleAuthorizationRuleEntity();
        rule.setRoleId(adminRole.getId());
        rule.setApplicationId(iamApp.getId());
        rule.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        rule.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(apiPermissions));
        rule.setDataGrantTemplateJson(iamDataGrantAllTemplateJson());
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        authorizationStateService.requireActiveRecomputeToken(iamApp.getId(), token);
        roleAuthorizationRuleRepository.save(rule);

        log.info("iam_admin 角色授权规则引导创建成功：pages={}, apis={}, dataGrantTemplate=all",
                pagePermissions.size(), apiPermissions.size());
    }

    /**
     * 为存量 iam_admin 规则增量补齐 Portal 路由投影的 API 与 DATA 授权，只增不减。
     */
    private void upgradeIamAdminPortalAuthorizationRule(
            Long applicationId, IamApplicationAuthorizationStateService.RecomputeToken token) {
        IamRoleEntity adminRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).orElse(null);
        if (adminRole == null) {
            return;
        }
        IamRoleAuthorizationRuleEntity rule = roleAuthorizationRuleRepository
                .findByRoleIdAndApplicationId(adminRole.getId(), applicationId).orElse(null);
        if (rule == null) {
            return;
        }
        List<String> apiPermissions = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                .readStringList(rule.getApiPermissionsJson(), "apiPermissions"));
        DataGrantDocument document = IamApplicationAuthorizationJsonCodec
                .readDataGrantDocument(rule.getDataGrantTemplateJson());
        List<DataGrant> grants = document == null ? new ArrayList<>() : new ArrayList<>(document.getGrants());
        boolean changed = false;
        if (!apiPermissions.contains(IamPortalMachineDirectoryConstant.API_PERMISSION)) {
            apiPermissions.add(IamPortalMachineDirectoryConstant.API_PERMISSION);
            changed = true;
        }
        DataGrant portalGrant = grants.stream()
                .filter(item -> IamPortalMachineDirectoryConstant.DATA_RESOURCE.equals(item.getResource()))
                .findFirst().orElse(null);
        if (portalGrant == null) {
            grants.add(new DataGrant(IamPortalMachineDirectoryConstant.DATA_RESOURCE,
                    Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), true,
                    Collections.emptyList()));
            changed = true;
        } else if (!portalGrant.isAll()
                || !portalGrant.getActions().contains(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)) {
            grants.remove(portalGrant);
            grants.add(new DataGrant(IamPortalMachineDirectoryConstant.DATA_RESOURCE,
                    Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), true,
                    Collections.emptyList()));
            changed = true;
        }
        if (!changed) {
            return;
        }
        rule.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(apiPermissions));
        rule.setDataGrantTemplateJson(IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION, grants)));
        rule.setUpdatedAt(Instant.now());
        authorizationStateService.requireActiveRecomputeToken(applicationId, token);
        roleAuthorizationRuleRepository.save(rule);
        log.info("iam_admin 角色规则已补齐 Portal 路由投影授权：applicationId={}, api={}, dataResource={}",
                applicationId, IamPortalMachineDirectoryConstant.API_PERMISSION,
                IamPortalMachineDirectoryConstant.DATA_RESOURCE);
    }

    /**
     * 存量应用已存在时补齐新增内置权限，不触发 bootstrap 的其他创建行为。
     */
    private void ensurePortalMachineDirectoryPermission() {
        if (permissionRepository.existsByCode(IamPortalMachineDirectoryConstant.API_PERMISSION)) {
            return;
        }
        IamPermissionEntity permission = new IamPermissionEntity();
        permission.setCode(IamPortalMachineDirectoryConstant.API_PERMISSION);
        permission.setName("IAM Portal 可信应用路由投影接口");
        permission.setType("api");
        permission.setBuiltIn(1);
        permission.setCreatedAt(Instant.now());
        permission.setUpdatedAt(Instant.now());
        permissionRepository.save(permission);
        log.info("内置权限创建成功：{}", IamPortalMachineDirectoryConstant.API_PERMISSION);
    }

    /**
     * 引导关闭时只补齐本版本新增的管理员权限绑定，不重新执行完整引导。
     */
    private void ensureAdminRolePortalPermissionBinding() {
        IamRoleEntity adminRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN)
                .orElse(null);
        IamPermissionEntity permission = permissionRepository
                .findByCode(IamPortalMachineDirectoryConstant.API_PERMISSION).orElse(null);
        if (adminRole == null || permission == null) {
            return;
        }
        boolean bound = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .anyMatch(item -> permission.getId().equals(item.getPermissionId()));
        if (bound) {
            return;
        }
        IamRolePermissionEntity binding = new IamRolePermissionEntity();
        binding.setRoleId(adminRole.getId());
        binding.setPermissionId(permission.getId());
        binding.setCreatedAt(Instant.now());
        rolePermissionRepository.save(binding);
        log.info("iam_admin 角色已补齐 Portal 路由投影权限绑定：roleId={}, permission={}",
                adminRole.getId(), IamPortalMachineDirectoryConstant.API_PERMISSION);
    }

    private boolean requiresBootstrapReconciliation(Long applicationId, boolean includeOpenRole) {
        if (includeOpenRole && requiresBuiltInApplicationMenuBootstrap()) {
            return true;
        }
        if (includeOpenRole && isManagedDefaultIamMenu(applicationId)
                && trustedApplicationMenuRepository.findByApplicationIdOrderBySortOrderAsc(applicationId).stream()
                .anyMatch(menu -> "dashboard".equals(menu.getCode())
                        && menu.getNodeType() == PortalMenuNodeType.PAGE
                        && menu.getRequiredPagePermission() == null)) {
            return true;
        }
        IamApplicationPermissionManifestEntity manifest = manifestRepository
                .findByApplicationId(applicationId).orElse(null);
        if (manifest == null) {
            return includeOpenRole;
        }
        List<String> requiredPages = new ArrayList<>();
        List<String> requiredApis = new ArrayList<>();
        if (includeOpenRole) {
            for (BuiltInPermission permission : builtInPermissions()) {
                if ("page".equals(permission.type)) {
                    requiredPages.add(permission.code);
                } else if ("api".equals(permission.type)) {
                    requiredApis.add(permission.code);
                }
            }
        } else {
            requiredApis.add(IamPortalMachineDirectoryConstant.API_PERMISSION);
        }
        if (!IamApplicationAuthorizationJsonCodec.readStringList(manifest.getPagePermissionsJson(), "pagePermissions")
                .containsAll(requiredPages)
                || !IamApplicationAuthorizationJsonCodec.readStringList(manifest.getApiPermissionsJson(), "apiPermissions")
                .containsAll(requiredApis)) {
            return true;
        }
        if (includeOpenRole) {
            requiredApis.addAll(IamOpenRoleManifestUpgradeService.apiCodes());
        }
        List<DataResourceDeclaration> requiredResources = includeOpenRole
                ? iamDataResourceDeclarations()
                : new ArrayList<>(Collections.singletonList(iamPortalMachineApplicationDataResourceDeclaration()));
        if (includeOpenRole) {
            requiredResources.addAll(IamOpenRoleManifestUpgradeService.declarations());
        }
        List<DataResourceDeclaration> manifestResources = IamApplicationAuthorizationJsonCodec
                .readDataResourceList(manifest.getDataResourcesJson());
        for (DataResourceDeclaration required : requiredResources) {
            List<DataResourceDeclaration> matching = manifestResources.stream()
                    .filter(item -> required.getResource().equals(item.getResource()))
                    .collect(Collectors.toList());
            if (matching.size() != 1 || !sameDataResourceDeclaration(matching.get(0), required)) {
                return true;
            }
        }
        if (!IamApplicationAuthorizationJsonCodec.readStringList(manifest.getApiPermissionsJson(), "apiPermissions")
                .containsAll(requiredApis)) {
            return true;
        }

        IamRoleEntity adminRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN)
                .orElse(null);
        if (adminRole == null) {
            return false;
        }
        IamRoleAuthorizationRuleEntity rule = roleAuthorizationRuleRepository
                .findByRoleIdAndApplicationId(adminRole.getId(), applicationId).orElse(null);
        if (rule == null) {
            return false;
        }
        if (!IamApplicationAuthorizationJsonCodec.readStringList(rule.getPagePermissionsJson(), "pagePermissions")
                .containsAll(requiredPages)
                || !IamApplicationAuthorizationJsonCodec.readStringList(rule.getApiPermissionsJson(), "apiPermissions")
                .containsAll(requiredApis)) {
            return true;
        }
        DataGrantDocument grantDocument = IamApplicationAuthorizationJsonCodec
                .readDataGrantDocument(rule.getDataGrantTemplateJson());
        List<DataGrant> grants = grantDocument == null ? Collections.emptyList() : grantDocument.getGrants();
        for (DataResourceDeclaration required : requiredResources) {
            boolean covered = grants.stream().anyMatch(grant -> required.getResource().equals(grant.getResource())
                    && grant.isAll() && grant.getActions().containsAll(required.getActions()));
            if (!covered) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresBuiltInApplicationMenuBootstrap() {
        for (SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config
                : properties.getBootstrap().getBuiltInApplications()) {
            String entry = config.getEntry() == null ? "" : config.getEntry().trim();
            if (entry.isEmpty()) {
                continue;
            }
            IamTrustedApplicationEntity application = trustedApplicationRepository
                    .findByApplicationCode(config.getApplicationCode()).orElse(null);
            if (application != null && trustedApplicationMenuRepository
                    .findByApplicationIdOrderBySortOrderAsc(application.getId()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean sameDataResourceDeclaration(DataResourceDeclaration actual,
                                                DataResourceDeclaration expected) {
        return Objects.equals(actual.getResource(), expected.getResource())
                && new HashSet<>(safeList(actual.getActions())).equals(new HashSet<>(expected.getActions()))
                && new HashSet<>(safeList(actual.getDimensions())).equals(new HashSet<>(expected.getDimensions()));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * IAM 用户 DATA 资源声明：read / write 动作，departmentId 约束维度（开放 API 用户族消费）。
     */
    private DataResourceDeclaration iamUserDataResourceDeclaration() {
        DataResourceDeclaration declaration = new DataResourceDeclaration();
        declaration.setResource(SimpleIamServerConstant.DATA_RESOURCE_IAM_USER);
        declaration.setActions(Arrays.asList(
                SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ,
                SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE));
        declaration.setDimensions(Collections.singletonList(
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID));
        return declaration;
    }

    /**
     * Portal 可信应用路由投影 DATA 资源声明：按应用 ID 或编码受限，亦支持 all=true。
     */
    private DataResourceDeclaration iamPortalMachineApplicationDataResourceDeclaration() {
        DataResourceDeclaration declaration = new DataResourceDeclaration();
        declaration.setResource(IamPortalMachineDirectoryConstant.DATA_RESOURCE);
        declaration.setActions(Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ));
        declaration.setDimensions(Arrays.asList("applicationId", "applicationCode"));
        return declaration;
    }

    private List<DataResourceDeclaration> iamDataResourceDeclarations() {
        return new ArrayList<>(Arrays.asList(iamUserDataResourceDeclaration(),
                iamPortalMachineApplicationDataResourceDeclaration()));
    }

    /**
     * 存量实例补齐仪表盘页面权限门禁（1.3.0 前仪表盘菜单节点无权限要求，
     * 任何持有 IAM 应用准入授权的用户都能看到该节点并被门户当作落地页，
     * 进入管理台后却是无权限空壳页）。
     *
     * <p>回填顺序固定：清单先声明，角色规则后补，菜单节点最后落权限，
     * 保证任何时刻不存在引用未声明权限的菜单。只做增量补齐，
     * 不覆盖管理员对其他权限/菜单的裁剪。
     */
    private void upgradeIamDashboardPermissionIfNeeded(boolean updateMenu,
                                                       IamApplicationAuthorizationStateService.RecomputeToken token) {
        IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                .findByApplicationCode(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM).orElse(null);
        if (iamApp == null) {
            return;
        }
        if (!isManagedDefaultIamMenu(iamApp.getId())) {
            log.info("IAM 菜单已定制，跳过仪表盘权限引导回填：applicationId={}", iamApp.getId());
            return;
        }
        IamApplicationPermissionManifestEntity manifest = manifestRepository.findByApplicationId(iamApp.getId())
                .orElse(null);
        if (manifest == null) {
            return;
        }
        boolean manifestUpgraded = false;
        List<String> pages = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                .readStringList(manifest.getPagePermissionsJson(), "pagePermissions"));
        if (!pages.contains(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE)) {
            pages.add(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE);
            PutApplicationPermissionManifestRequest request = new PutApplicationPermissionManifestRequest();
            request.setRoles(new ArrayList<>(IamApplicationAuthorizationJsonCodec
                    .readStringList(manifest.getRolesJson(), "roles")));
            request.setPagePermissions(pages);
            request.setApiPermissions(new ArrayList<>(IamApplicationAuthorizationJsonCodec
                    .readStringList(manifest.getApiPermissionsJson(), "apiPermissions")));
            request.setDataResources(new ArrayList<>(IamApplicationAuthorizationJsonCodec
                    .readDataResourceList(manifest.getDataResourcesJson())));
            manifestService.putManifestForBootstrap(iamApp.getId(), request, token);
            manifestUpgraded = true;
            log.info("IAM 应用清单已补齐仪表盘页面权限：applicationId={}", iamApp.getId());
        }

        boolean ruleUpgraded = false;
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        IamRoleAuthorizationRuleEntity adminRule = roleAuthorizationRuleRepository
                .findByRoleIdAndApplicationId(adminRole.getId(), iamApp.getId()).orElse(null);
        if (adminRule != null) {
            List<String> rulePages = new ArrayList<>(IamApplicationAuthorizationJsonCodec
                    .readStringList(adminRule.getPagePermissionsJson(), "pagePermissions"));
            if (!rulePages.contains(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE)) {
                rulePages.add(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE);
                adminRule.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(rulePages));
                adminRule.setUpdatedAt(Instant.now());
                authorizationStateService.requireActiveRecomputeToken(iamApp.getId(), token);
                roleAuthorizationRuleRepository.save(adminRule);
                ruleUpgraded = true;
                log.info("iam_admin 角色规则已补齐仪表盘页面权限：roleId={}, applicationId={}",
                        adminRole.getId(), iamApp.getId());
            }
        }

        boolean menuUpgraded = false;
        if (updateMenu) {
            for (IamTrustedApplicationMenuEntity menu : trustedApplicationMenuRepository
                    .findByApplicationIdOrderBySortOrderAsc(iamApp.getId())) {
                if ("dashboard".equals(menu.getCode()) && menu.getNodeType() == PortalMenuNodeType.PAGE
                        && menu.getRequiredPagePermission() == null) {
                    menu.setRequiredPagePermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_PAGE);
                    trustedApplicationMenuRepository.save(menu);
                    menuUpgraded = true;
                    log.info("IAM 仪表盘菜单节点已补齐页面权限：applicationId={}", iamApp.getId());
                }
            }
        }

        if (manifestUpgraded || ruleUpgraded || menuUpgraded) {
            log.info("IAM 启动授权 source 升级完成：applicationId={}, manifestUpdated={}, ruleUpdated={}, menuUpdated={}",
                    iamApp.getId(), manifestUpgraded, ruleUpgraded, menuUpgraded);
        }
    }

    /**
     * 仅识别官方默认菜单：精确 1.0 扁平菜单或与当前引导配置一致的树。
     * 管理员改过任一节点时完全跳过回填，启动过程不能借权限升级覆盖人工配置。
     */
    private boolean isManagedDefaultIamMenu(Long applicationId) {
        if (isLegacyDefaultIamMenu(applicationId)) {
            return true;
        }
        SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig config = properties.getBootstrap()
                .getBuiltInApplications().stream()
                .filter(item -> SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM.equals(item.getApplicationCode()))
                .findFirst().orElse(null);
        return config != null && isSameBuiltInMenuTree(
                portalMenuTreeService.getManagementTree(applicationId),
                toMenuTreeRequests(builtInMenuTree(config)));
    }

    private boolean isSameBuiltInMenuTree(List<PortalMenuTreeNodeResponse> actual,
                                          List<PortalMenuTreeNodeRequest> expected) {
        List<PortalMenuTreeNodeResponse> actualNodes = actual == null
                ? Collections.<PortalMenuTreeNodeResponse>emptyList() : actual;
        List<PortalMenuTreeNodeRequest> expectedNodes = expected == null
                ? Collections.<PortalMenuTreeNodeRequest>emptyList() : expected;
        if (actualNodes.size() != expectedNodes.size()) {
            return false;
        }
        for (int index = 0; index < expectedNodes.size(); index++) {
            PortalMenuTreeNodeRequest expectedNode = expectedNodes.get(index);
            PortalMenuTreeNodeResponse actualNode = actualNodes.get(index);
            if (expectedNode == null || actualNode == null
                    || !Objects.equals(expectedNode.getCode(), actualNode.getCode())
                    || !Objects.equals(expectedNode.getName(), actualNode.getName())
                    || !Objects.equals(expectedNode.getNodeType(), actualNode.getNodeType())
                    || !Objects.equals(expectedNode.getIcon(), actualNode.getIcon())
                    || !Objects.equals(expectedNode.getRoute(), actualNode.getRoute())
                    || !Objects.equals(expectedNode.getSortOrder(), actualNode.getSortOrder())
                    || !Objects.equals(expectedPresentationMode(expectedNode), actualNode.getPresentationMode())) {
                return false;
            }
            boolean missingDashboardPermission = "dashboard".equals(expectedNode.getCode())
                    && actualNode.getRequiredPagePermission() == null;
            if (!missingDashboardPermission
                    && !Objects.equals(expectedNode.getRequiredPagePermission(), actualNode.getRequiredPagePermission())) {
                return false;
            }
            if (!isSameBuiltInMenuTree(actualNode.getChildren(), expectedNode.getChildren())) {
                return false;
            }
        }
        return true;
    }

    private String expectedPresentationMode(PortalMenuTreeNodeRequest node) {
        if (PortalMenuNodeType.GROUP.name().equals(node.getNodeType())) {
            return PortalPresentationMode.STANDARD.name();
        }
        return node.getPresentationMode() == null
                ? PortalPresentationMode.STANDARD.name() : node.getPresentationMode();
    }

    /**
     * iam_admin 角色的 DATA 模板：iam:user 全量放行（all=true，与该角色全权限哲学一致）。
     */
    private String iamDataGrantAllTemplateJson() {
        return IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(new DataGrantDocument(
                SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION,
                Arrays.asList(
                        new DataGrant(SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
                                Arrays.asList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ,
                                        SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE), true,
                                Collections.emptyList()),
                        new DataGrant(IamPortalMachineDirectoryConstant.DATA_RESOURCE,
                                Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), true,
                                Collections.emptyList()))));
    }

    /**
     * 内置权限：编码 / 名称 / 类型（page 页面 / api 接口 / data 数据范围）
     */
    private static final class BuiltInPermission {

        private final String code;
        private final String name;
        private final String type;

        private BuiltInPermission(String code, String name, String type) {
            this.code = code;
            this.name = name;
            this.type = type;
        }
    }

    /**
     * 1.0 官方 IAM 默认菜单的不可变指纹项。
     */
    private static final class LegacyMenu {

        private final String code;
        private final String name;
        private final String route;

        private LegacyMenu(String code, String name, String route) {
            this.code = code;
            this.name = name;
            this.route = route;
        }
    }
}
