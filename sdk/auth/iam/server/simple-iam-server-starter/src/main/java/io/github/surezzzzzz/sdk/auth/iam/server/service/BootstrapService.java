package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.PortalMenuNodeType;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalMenuTreeNodeRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.*;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
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
public class BootstrapService implements ApplicationRunner {

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
    private final PortalMenuTreeService portalMenuTreeService;
    private final IamAuthorizationProjectionService projectionService;
    private final UserService userService;
    private final RoleService roleService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final SimpleRedisLock redisLock;
    private final RedisKeyHelper redisKeyHelper;
    private final TransactionTemplate transactionTemplate;
    private final DeploymentPasswordRecoveryService deploymentPasswordRecoveryService;

    private static BuiltInPermission[] builtInPermissions() {
        return new BuiltInPermission[]{
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE, "IAM 用户管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_PAGE, "IAM 角色管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_PAGE, "IAM 站内信管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE, "IAM 可信应用管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_PAGE, "IAM 部门管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_PAGE, "IAM 协作组管理页面", "page"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API, "IAM 用户管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API, "IAM 角色管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API, "IAM 权限管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API, "IAM 站内信接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API, "IAM 可信应用管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API, "IAM 部门管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API, "IAM 协作组管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DASHBOARD_API, "IAM 仪表盘统计接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API, "IAM 会话管理接口", "api"),
                new BuiltInPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_DATA_ALL, "IAM 全量数据范围", "data")
        };
    }

    /**
     * 启动引导入口：幂等初始化内置角色 / 权限 / 管理员，并消费部署级恢复码
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.TRUE.equals(properties.getBootstrap().getEnabled())) {
            return;
        }
        String lockKey = redisKeyHelper.buildLockKey(SimpleIamServerConstant.LOCK_KEY_BOOTSTRAP);
        Optional<RedisLockLease> lease = redisLock.tryLockWithLease(lockKey,
                SimpleIamServerConstant.BOOTSTRAP_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        if (!lease.isPresent()) {
            log.info(ServerErrorMessage.BOOTSTRAP_LOCK_UNAVAILABLE);
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ensureAdminRole();
                ensureBuiltInPermissions();
                bindAdminRolePermissions();
                ensureRootDepartment();
                ensureBuiltInApplications();
                ensureBuiltInApplicationManifests();
                ensureBuiltInApplicationMenus();
                ensureAdminRoleAuthorizationRules();
                String bootstrapInitialPassword = ensureAdminUser();
                ensureAdminApplicationAuthorizations();
                deploymentPasswordRecoveryService.recoverBootstrapAdministratorIfRequested(bootstrapInitialPassword);
            });
        } finally {
            lease.get().close();
        }
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
            application.setCreatedAt(Instant.now());
            application.setUpdatedAt(Instant.now());
            IamTrustedApplicationEntity saved = trustedApplicationRepository.save(application);

            IamTrustedApplicationPortalEntity portal = new IamTrustedApplicationPortalEntity();
            portal.setApplicationId(saved.getId());
            portal.setEnabled(1);
            portal.setRoutePrefix(config.getRoutePrefix() != null && !config.getRoutePrefix().trim().isEmpty()
                    ? config.getRoutePrefix().trim()
                    : "/micro/" + config.getApplicationCode());
            portal.setEntry(entry);
            portal.setApiBase(config.getApiBase());
            portal.setUpdatedAt(Instant.now());
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
            if (menu.getParentId() != null || menu.getNodeType() != PortalMenuNodeType.PAGE || menu.getIcon() != null
                    || menu.getRequiredPagePermission() != null || !Integer.valueOf(index).equals(menu.getSortOrder())
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
    private void ensureBuiltInApplicationManifests() {
        IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                .findByApplicationCode("iam").orElse(null);
        if (iamApp == null) {
            return;
        }

        // 检查是否已存在清单
        IamApplicationPermissionManifestEntity existingManifest = null;
        try {
            existingManifest = manifestService.requireManifest(iamApp.getId());
        } catch (Exception e) {
            // 清单不存在，需要创建
        }

        if (existingManifest != null && existingManifest.getManifestVersion() > 0L) {
            log.info("IAM 应用权限清单已存在，跳过引导：manifestVersion={}",
                    existingManifest.getManifestVersion());
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
        request.setDataResources(Collections.singletonList(iamUserDataResourceDeclaration()));

        manifestService.putManifest(iamApp.getId(), request);
        log.info("IAM 应用权限清单引导创建成功：roles={}, pages={}, apis={}, dataResources={}",
                request.getRoles().size(), pagePermissions.size(), apiPermissions.size(),
                request.getDataResources().size());
    }

    /**
     * 为内置 iam_admin 角色创建应用授权规则（幂等）。
     * 管理员角色拥有 IAM 应用的全部权限。
     */
    private void ensureAdminRoleAuthorizationRules() {
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        IamTrustedApplicationEntity iamApp = trustedApplicationRepository
                .findByApplicationCode("iam").orElse(null);
        if (iamApp == null) {
            return;
        }

        if (roleAuthorizationRuleRepository.findByRoleIdAndApplicationId(
                adminRole.getId(), iamApp.getId()).isPresent()) {
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
        rule.setDataGrantTemplateJson(iamUserDataGrantAllTemplateJson());
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());
        roleAuthorizationRuleRepository.save(rule);

        log.info("iam_admin 角色授权规则引导创建成功：pages={}, apis={}, dataGrantTemplate=all",
                pagePermissions.size(), apiPermissions.size());
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
     * iam_admin 角色的 DATA 模板：iam:user 全量放行（all=true，与该角色全权限哲学一致）。
     */
    private String iamUserDataGrantAllTemplateJson() {
        return IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(new DataGrantDocument(
                SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(new DataGrant(
                        SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
                        Arrays.asList(
                                SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ,
                                SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE),
                        true,
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
