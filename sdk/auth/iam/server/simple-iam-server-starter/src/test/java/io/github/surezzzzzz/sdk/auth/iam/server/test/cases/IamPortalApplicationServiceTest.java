package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamPortalApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门户侧边栏可达列表测试（授权行过滤 + 平台管理员特权直通）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamPortalApplicationServiceTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "portal-admin-" + suffix;
    private final String userUsername = "portal-user-" + suffix;
    private final String applicationCodeA = "portal-a-" + suffix;
    private final String applicationCodeB = "portal-b-" + suffix;

    @Autowired
    private IamPortalApplicationService portalApplicationService;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;

    private Long adminUserId;
    private Long targetUserId;
    private Long applicationIdA;
    private Long applicationIdB;

    @BeforeEach
    void prepare() {
        CreateUserRequest adminRequest = new CreateUserRequest();
        adminRequest.setUsername(adminUsername);
        adminRequest.setPassword("Admin@1234");
        adminRequest.setDisplayName(adminUsername);
        adminUserId = userService.createUser(adminRequest).getId();
        roleService.assignRole(adminUserId,
                roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());

        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(userUsername);
        userRequest.setPassword("Admin@1234");
        userRequest.setDisplayName(userUsername);
        targetUserId = userService.createUser(userRequest).getId();

        applicationIdA = createPortalApplication(applicationCodeA);
        applicationIdB = createPortalApplication(applicationCodeB);
        grantAdmitted(targetUserId, applicationIdA);
    }

    @AfterEach
    void cleanup() {
        authorizationRepository.findByUserId(adminUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        authorizationRepository.findByUserId(targetUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        trustedApplicationService.deleteApplication(applicationIdA);
        trustedApplicationService.deleteApplication(applicationIdB);
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("平台管理员无授权行特权直通全部启用应用，普通用户按授权行过滤")
    void testPortalFilteringAndPlatformAdminPassThrough() {
        List<String> adminCodes = accessibleCodes(adminUserId);
        assertTrue(adminCodes.contains(applicationCodeA), "平台管理员应直通应用 A（无授权行）");
        assertTrue(adminCodes.contains(applicationCodeB), "平台管理员应直通应用 B（无授权行）");

        List<String> userCodes = accessibleCodes(targetUserId);
        assertTrue(userCodes.contains(applicationCodeA), "普通用户应看到已授权应用 A");
        assertFalse(userCodes.contains(applicationCodeB), "普通用户不得看到未授权应用 B（特权不外溢）");

        log.info("门户可达列表断言完成：adminCodes={}, userCodes={}", adminCodes, userCodes);
    }

    @Test
    @DisplayName("摘除 iam_admin 后特权直通立即失效，退回授权行口径")
    void testPortalPrivilegeRevokedWithRole() {
        roleService.revokeRole(adminUserId,
                roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());

        List<String> adminCodes = accessibleCodes(adminUserId);
        assertFalse(adminCodes.contains(applicationCodeA), "摘角色后无授权行的应用 A 应不可见");
        assertFalse(adminCodes.contains(applicationCodeB), "摘角色后无授权行的应用 B 应不可见");

        log.info("摘除 iam_admin 门户直通失效断言完成：adminCodes={}", adminCodes);
    }

    private List<String> accessibleCodes(Long userId) {
        return portalApplicationService.listPortalAccessibleApplications(userId).stream()
                .map(PortalAccessibleApplication::getApplicationCode)
                .collect(Collectors.toList());
    }

    private Long createPortalApplication(String code) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(code + "-web");
        client.setClientName(code + "-web");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://" + code + ".example.test/callback"));
        client.setScopes(List.of("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        PortalIntegrationRequest portal = new PortalIntegrationRequest();
        portal.setEnabled(true);
        portal.setEntry("//localhost:9xxx/" + code + "/");
        portal.setApiBase("/" + code);
        MenuItemRequest menu = new MenuItemRequest();
        menu.setCode("home");
        menu.setName("首页");
        menu.setRoute("/home");
        menu.setSortOrder(1);
        portal.setMenus(Collections.singletonList(menu));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Portal Test " + code);
        request.setInitialClient(client);
        request.setPortal(portal);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }

    private void grantAdmitted(Long userId, Long applicationId) {
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setUserId(userId);
        authorization.setApplicationId(applicationId);
        authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setPagePermissionsJson(
                IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
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
