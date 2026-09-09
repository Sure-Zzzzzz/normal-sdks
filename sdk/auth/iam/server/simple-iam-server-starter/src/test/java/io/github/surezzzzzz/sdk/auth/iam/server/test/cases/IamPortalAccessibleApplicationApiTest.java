package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationPermissionManifestService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portal 可访问应用按用户授权过滤测试（DESIGN.portal-accessible-application-filtering.md）。
 *
 * <p>侧边栏仅显示 admitted=1 且授权状态有效、且启用 Portal 集成的应用；
 * 授权管理面变更立即生效。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamPortalAccessibleApplicationApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "portal-admin-" + suffix;
    private final String username = "portal-user-" + suffix;
    private final String applicationXCode = "portal-x-" + suffix;
    private final String applicationYCode = "portal-y-" + suffix;
    private Cookie adminSession;
    private Cookie userSession;
    private Long targetUserId;
    private Long applicationXId;
    private Long applicationYId;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;
    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;
    @Autowired
    private IamUserRepository userRepository;

    @BeforeEach
    void prepare() throws Exception {
        CreateUserRequest adminRequest = new CreateUserRequest();
        adminRequest.setUsername(adminUsername);
        adminRequest.setPassword("Admin@1234");
        adminRequest.setDisplayName(adminUsername);
        Long adminId = userService.createUser(adminRequest).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(adminId, adminRole.getId());
        adminSession = loginSession(adminUsername);

        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword("Admin@1234");
        userRequest.setDisplayName(username);
        targetUserId = userService.createUser(userRequest).getId();
        userSession = loginSession(username);

        applicationXId = createPortalApplication(applicationXCode);
        applicationYId = createPortalApplication(applicationYCode);
    }

    @AfterEach
    void cleanup() {
        authorizationRepository.findByUserId(targetUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        trustedApplicationService.deleteApplication(applicationXId);
        trustedApplicationService.deleteApplication(applicationYId);
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(username).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("侧边栏按 admitted+有效授权过滤，撤销立即生效，未准入不可见，恢复后回归")
    void portalListFiltersByUserAuthorization() throws Exception {
        mockMvc.perform(get("/iam/web/portal/accessible-applications").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationXCode + "')]").isEmpty())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationYCode + "')]").isEmpty());

        putAuthorization(applicationXId, true);

        mockMvc.perform(get("/iam/web/portal/accessible-applications").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationXCode + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationYCode + "')]").isEmpty());

        mockMvc.perform(delete(authorizationPath(applicationXId)).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/iam/web/portal/accessible-applications").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationXCode + "')]").isEmpty());

        putAuthorization(applicationXId, false);

        mockMvc.perform(get("/iam/web/portal/accessible-applications").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationXCode + "')]").isEmpty());

        putAuthorization(applicationXId, true);

        mockMvc.perform(get("/iam/web/portal/accessible-applications").cookie(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationXCode + "')]").isNotEmpty());
        log.info("Portal 按授权过滤断言完成：userId={}, applicationX={}", targetUserId, applicationXCode);
    }

    /**
     * PUT 授权为版本化替换语义：首次创建（version=1）返回 201，撤销后重放为替换返回 200。
     */
    private void putAuthorization(Long applicationId, boolean admitted) throws Exception {
        mockMvc.perform(put(authorizationPath(applicationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"admitted\":" + admitted + ",\"roles\":[],\"pagePermissions\":[],"
                                + "\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().is2xxSuccessful());
    }

    private String authorizationPath(Long applicationId) {
        return "/iam/admin/users/" + targetUserId + "/application-authorizations/" + applicationId;
    }

    private Long createPortalApplication(String applicationCode) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(applicationCode + "-client");
        client.setClientName("Portal Filter Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://" + applicationCode + ".example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        PortalIntegrationRequest portal = new PortalIntegrationRequest();
        portal.setEnabled(true);
        portal.setEntry("https://" + applicationCode + ".example.test/index.html");
        portal.setApiBase("https://" + applicationCode + ".example.test/api");
        MenuItemRequest menu = new MenuItemRequest();
        menu.setCode("home");
        menu.setName("首页");
        menu.setRoute("/home");
        menu.setSortOrder(1);
        portal.setMenus(Collections.singletonList(menu));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(applicationCode);
        request.setApplicationName("Portal Filter Application " + applicationCode);
        request.setInitialClient(client);
        request.setPortal(portal);
        Long applicationId = trustedApplicationService.createApplication(request).getApplication().getId();
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.emptyList());
        manifest.setApiPermissions(Collections.emptyList());
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, manifest);
        return applicationId;
    }

    private Cookie loginSession(String loginUsername) throws Exception {
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + loginUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(session, "登录必须成功并建立会话：" + loginUsername);
        return session;
    }
}
