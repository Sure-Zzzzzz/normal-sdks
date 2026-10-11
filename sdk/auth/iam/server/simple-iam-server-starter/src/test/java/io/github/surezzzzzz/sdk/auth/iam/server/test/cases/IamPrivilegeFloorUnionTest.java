package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.test.helper.IamTrustedApplicationTestCleanupHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 特权短路缺陷修复回归（1.3.7 A 件，GA dev 实报复刻）：平台管理员对内置应用存在
 * 有效空准入行时，introspect 侧（loadActiveContext）不得劣化为空权限——特权保底
 * 与行投影取并集；非特权/非内置语义零变化。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamPrivilegeFloorUnionTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "priv-floor-admin-" + suffix;
    private final String userUsername = "priv-floor-user-" + suffix;
    private final String applicationCode = "priv-floor-app-" + suffix;

    @Autowired
    private IamUserService userService;

    @Autowired
    private IamRoleService roleService;

    @Autowired
    private IamTrustedApplicationService trustedApplicationService;

    @Autowired
    private IamApplicationAuthorizationService authorizationService;

    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamTrustedApplicationRepository trustedApplicationRepository;

    @Autowired
    private IamTrustedApplicationTestCleanupHelper cleanup;

    @Autowired
    private io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService manifestService;

    private Long adminUserId;
    private Long targetUserId;
    private Long applicationId;

    @AfterEach
    void cleanupEntities() {
        for (Long userId : new Long[]{adminUserId, targetUserId}) {
            if (userId != null) {
                authorizationRepository.findByUserId(userId)
                        .forEach(authorization -> authorizationRepository.delete(authorization));
            }
        }
        if (adminUserId != null) userRepository.findById(adminUserId)
                .ifPresent(user -> userService.deleteUser(user.getId()));
        if (targetUserId != null) userRepository.findById(targetUserId)
                .ifPresent(user -> userService.deleteUser(user.getId()));
        if (applicationId != null) {
            // 内置标记为测试直改，删除前先摘除（内置应用禁删是产品语义）
            trustedApplicationRepository.findById(applicationId).ifPresent(app -> {
                app.setBuiltIn(Boolean.FALSE);
                trustedApplicationRepository.save(app);
            });
            cleanup.deleteAndAwaitCompletion(applicationId);
        }
    }

    @Test
    @DisplayName("GA 现场复刻：空准入行 + 平台管理员 + 内置应用 → 保底并集（清单全量），不再空权限")
    void emptyAdmissionRowGetsPrivilegeFloorUnion() throws InterruptedException {
        prepareAdminUserAndBuiltInApplication();
        // GA 现场同款行：admitted=1、status=1、权限列全空、roles_json=用户全角色
        grantRow(adminUserId, applicationId, Collections.emptyList());

        ApplicationAuthorizationContext context = authorizationService.loadActiveContext(
                adminUserId, applicationId, Instant.now(), Instant.now().plusSeconds(60));

        assertNotNull(context, "行有效必须构建上下文");
        assertTrue(context.isAdmitted());
        // 保底并集：行空 + 清单全量 → 并集=清单全量（GA 现场为空是缺陷）
        assertTrue(context.getPagePermissions().containsAll(java.util.Arrays.asList("floor-page-x", "floor-page-y")),
                "页面权限=清单全量（空行不再劣化），实际=" + context.getPagePermissions());
        assertTrue(context.getApiPermissions().contains("floor-api-x"),
                "API 权限=清单全量");
        assertNotNull(context.getDataGrantDocument(), "DATA=特权全量合成（不再 null）");
        assertTrue(context.getDataGrantDocument().getGrants().stream()
                        .anyMatch(grant -> "floor-resource".equals(grant.getResource())),
                "DATA 授权项含清单申报资源");
    }

    @Test
    @DisplayName("行有部分码时并集保留行码（行可以更细，不劣化也不丢行）")
    void partialRowKeepsRowCodesInUnion() throws InterruptedException {
        prepareAdminUserAndBuiltInApplication();
        grantRow(adminUserId, applicationId, Collections.singletonList("floor-page-x"));

        ApplicationAuthorizationContext context = authorizationService.loadActiveContext(
                adminUserId, applicationId, Instant.now(), Instant.now().plusSeconds(60));

        assertTrue(context.getPagePermissions().contains("floor-page-x"), "行已有码保留");
        assertTrue(context.getPagePermissions().contains("floor-page-y"), "清单码保底补齐");
        assertEquals(2, context.getPagePermissions().size(), "并集无重复无丢失");
    }

    @Test
    @DisplayName("非特权用户空行仍为空投影（修复不误放）；无行管理员走既有特权兜底不变")
    void nonPrivilegedEmptyRowStaysEmptyAndNoRowAdminKeepsFallback() throws InterruptedException {
        prepareAdminUserAndBuiltInApplication();

        // 非特权用户空行：空投影（不能因修复误放）
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(userUsername);
        userRequest.setPassword("Floor-User@2026");
        userRequest.setDisplayName("保底对照用户");
        targetUserId = userService.createUser(userRequest).getId();
        grantRow(targetUserId, applicationId, Collections.emptyList());
        ApplicationAuthorizationContext userContext = authorizationService.loadActiveContext(
                targetUserId, applicationId, Instant.now(), Instant.now().plusSeconds(60));
        assertNotNull(userContext);
        assertTrue(userContext.getPagePermissions().isEmpty(), "非特权用户空行=空投影（不误放）");

        // 无行管理员：既有兜底路径不变（buildPrivilegedContext 全量）
        authorizationRepository.findByUserId(adminUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        ApplicationAuthorizationContext adminContext = authorizationService.loadActiveContext(
                adminUserId, applicationId, Instant.now(), Instant.now().plusSeconds(60));
        assertNotNull(adminContext, "无行管理员仍走特权兜底");
        assertTrue(adminContext.getPagePermissions().contains("floor-page-x"), "兜底=清单全量");
    }

    @Test
    @DisplayName("非内置应用：特权保底不适用（1.3.1 语义零变化）")
    void nonBuiltInApplicationDoesNotGetFloor() throws InterruptedException {
        prepareAdminUserAndNonBuiltInApplication();
        grantRow(adminUserId, applicationId, Collections.emptyList());

        ApplicationAuthorizationContext context = authorizationService.loadActiveContext(
                adminUserId, applicationId, Instant.now(), Instant.now().plusSeconds(60));

        assertNotNull(context, "非内置行有效仍构建上下文");
        assertTrue(context.getPagePermissions().isEmpty(),
                "非内置应用无特权保底（1.3.1），空行=空投影");
    }

    private void prepareAdminUserAndBuiltInApplication() {
        adminUserId = createAdminUser();
        applicationId = createApplication(true);
        putManifest(applicationId);
    }

    private void prepareAdminUserAndNonBuiltInApplication() throws InterruptedException {
        // 先建内置应用拿 manifest，再换非内置应用复用同一管理员
        if (adminUserId == null) {
            adminUserId = createAdminUser();
        }
        applicationId = createApplication(false);
        putManifest(applicationId);
    }

    private Long createAdminUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword("Floor-Admin@2026");
        request.setDisplayName("保底验收管理员");
        Long userId = userService.createUser(request).getId();
        roleService.assignRole(userId,
                roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());
        return userId;
    }

    private Long createApplication(boolean builtIn) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(applicationCode + "-web");
        client.setClientName(applicationCode + "-web");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://" + applicationCode + ".example.test/callback"));
        client.setScopes(java.util.Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));
        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(applicationCode);
        request.setApplicationName("保底验收应用 " + applicationCode);
        request.setInitialClient(client);
        Long created = trustedApplicationService.createApplication(request).getApplication().getId();
        if (builtIn) {
            trustedApplicationRepository.findById(created).ifPresent(app -> {
                app.setBuiltIn(Boolean.TRUE);
                trustedApplicationRepository.save(app);
            });
        }
        return created;
    }

    private void putManifest(Long applicationId) {
        io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest request =
                new io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest();
        request.setRoles(Collections.emptyList());
        request.setPagePermissions(java.util.Arrays.asList("floor-page-x", "floor-page-y"));
        request.setApiPermissions(Collections.singletonList("floor-api-x"));
        io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration resource =
                new io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration();
        resource.setResource("floor-resource");
        resource.setActions(Collections.singletonList("read"));
        resource.setDimensions(Collections.emptyList());
        request.setDataResources(Collections.singletonList(resource));
        manifestService.putManifest(applicationId, request);
    }

    private void grantRow(Long userId, Long applicationId, java.util.List<String> pagePermissions) {
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setUserId(userId);
        authorization.setApplicationId(applicationId);
        authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        authorization.setApiPermissionsJson(
                IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setDataGrantDocumentJson(null);
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion("manual");
        authorization.setManifestDigest("manual");
        authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setCreatedAt(Instant.now());
        authorization.setUpdatedAt(Instant.now());
        authorizationRepository.save(authorization);
    }
}
