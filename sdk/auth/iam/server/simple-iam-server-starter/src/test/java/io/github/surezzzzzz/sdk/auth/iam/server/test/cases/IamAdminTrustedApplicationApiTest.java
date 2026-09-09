package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;

import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可信应用管理 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminTrustedApplicationApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "trusted-application-admin-" + suffix;
    private final String userUsername = "trusted-application-user-" + suffix;
    private final String applicationCode = "admin-api-app-" + suffix;
    private final String clientId = applicationCode + "-web";
    private final String clientSecret = "ClientSecret@" + suffix;
    private Cookie adminSession;
    private Cookie userSession;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName(adminUsername);
        Long userId = userService.createUser(request).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(userId, adminRole.getId());
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = sessionCookie(login);
        assertNotNull(adminSession);
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(userUsername);
        userRequest.setPassword("Admin@1234");
        userRequest.setDisplayName(userUsername);
        userService.createUser(userRequest);
        MvcResult userLogin = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + userUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        userSession = sessionCookie(userLogin);
        assertNotNull(userSession);
    }

    /**
     * spring-session 下会话由 JSESSIONID cookie 标识，跨请求以 cookie 传递（与浏览器行为一致）。
     */
    private Cookie sessionCookie(MvcResult result) {
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }

    @AfterEach
    void cleanup() {
        Long applicationId = findApplicationId();
        if (applicationId != null) {
            jdbcTemplate.update("DELETE FROM iam_application_authorization WHERE application_id = ?", applicationId);
            trustedApplicationService.deleteApplication(applicationId);
        }
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("创建应用后应通过客户端子资源仅一次返回密钥")
    void testCreateApplicationAndSecretNonLeakContract() throws Exception {
        Long applicationId = createApplication("https://example.com/callback");

        mockMvc.perform(get("/iam/admin/trusted-applications/" + applicationId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCode").value(applicationCode))
                .andExpect(jsonPath("$.clients[0].clientId").value(clientId))
                .andExpect(jsonPath("$.clients[0].secretPresent").value(true))
                .andExpect(jsonPath("$.clients[0].clientSecret").doesNotExist());

        mockMvc.perform(post("/iam/admin/trusted-applications/" + applicationId + "/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createClientJson(applicationCode + "-service", "https://example.com/service-callback"))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(applicationCode + "-service"))
                .andExpect(jsonPath("$.clientSecret").value(clientSecret));

        log.info("管理 API 创建应用和客户端成功：applicationId={}, applicationCode={}", applicationId, applicationCode);
    }

    @Test
    @DisplayName("创建应用留空机密密钥应由服务端生成并随响应一次性返回")
    void testCreateApplicationGeneratesSecretWhenBlank() throws Exception {
        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson("https://example.com/callback")
                                .replace(",\"clientSecret\":\"" + clientSecret + "\"", ""))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.applicationCode").value(applicationCode))
                .andExpect(jsonPath("$.initialClientSecret").isNotEmpty());

        Long applicationId = findApplicationId();
        mockMvc.perform(get("/iam/admin/trusted-applications/" + applicationId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].secretPresent").value(true))
                .andExpect(jsonPath("$.clients[0].clientSecret").doesNotExist());

        log.info("管理 API 服务端生成密钥成功：applicationId={}", applicationId);
    }

    @Test
    @DisplayName("可信应用分页接口应返回从 1 开始的页码")
    void testTrustedApplicationPageContract() throws Exception {
        createApplication("https://example.com/callback");

        mockMvc.perform(get("/iam/admin/trusted-applications/page?page=1&size=20&keyword=" + applicationCode)
                        .cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.content[0].applicationCode").value(applicationCode));
    }

    @Test
    @DisplayName("客户端子资源更新应去重重定向地址且不回显密钥")
    void testUpdateClientContract() throws Exception {
        Long applicationId = createApplication("https://example.com/callback");

        mockMvc.perform(put("/iam/admin/trusted-applications/" + applicationId + "/clients/" + clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"可信应用更新客户端\",\"requireConsent\":true,"
                                + "\"redirectUris\":[\"https://example.com/callback2\","
                                + "\"https://example.com/callback2\"],\"scopes\":[\"openid\",\"profile\"]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("可信应用更新客户端"))
                .andExpect(jsonPath("$.redirectUris.length()").value(1))
                .andExpect(jsonPath("$.redirectUris[0]").value("https://example.com/callback2"))
                .andExpect(jsonPath("$.scopes[?(@ == 'profile')]").exists())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());

        mockMvc.perform(get("/iam/admin/trusted-applications/" + Long.MAX_VALUE + "/clients/" + clientId)
                        .cookie(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("不属于应用")));

        log.info("管理 API 更新客户端成功：applicationId={}, clientId={}", applicationId, clientId);
    }

    @Test
    @DisplayName("管理 API 应拒绝非法客户端配置")
    void testRejectInvalidClientRequests() throws Exception {
        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson("/relative/callback"))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("重定向 URI 非法")));

        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson("https://example.com/callback"))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson("https://example.com/callback"))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("应用编码已存在")));

        mockMvc.perform(post("/iam/admin/trusted-applications/" + findApplicationId() + "/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createClientJson(applicationCode + "-machine", "https://example.com/machine")
                                .replace("authorization_code", "client_credentials"))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("机器凭证")));

        log.info("管理 API 客户端策略校验成功：applicationCode={}", applicationCode);
    }

    @Test
    @DisplayName("Portal API 未登录访问应返回 401")
    void testPortalAccessibleApplicationsRequiresAuthentication() throws Exception {
        mockMvc.perform(get(SimpleIamServerConstant.PATH_WEB_PORTAL_ACCESSIBLE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Portal API 应返回启用应用的完整菜单路由")
    void testPortalAccessibleApplicationsContract() throws Exception {
        Long applicationId = createPortalApplication();
        admitApplicationToAdmin(applicationId);

        mockMvc.perform(get(SimpleIamServerConstant.PATH_WEB_PORTAL_ACCESSIBLE).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationCode + "')]").exists())
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationCode
                        + "')].routePrefix").value(hasItem("/app/" + applicationCode)))
                .andExpect(jsonPath("$[?(@.applicationCode == '" + applicationCode
                        + "')].menus[0].route").value(hasItem("/app/" + applicationCode + "/workspace")));

        log.info("Portal 可访问应用 API 成功：applicationId={}, applicationCode={}", applicationId, applicationCode);
    }

    @Test
    @DisplayName("删除应用应级联删除客户端并无法再读取")
    void testDeleteApplicationContract() throws Exception {
        Long applicationId = createApplication("https://example.com/callback");

        mockMvc.perform(delete("/iam/admin/trusted-applications/" + applicationId).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/iam/admin/trusted-applications/" + applicationId).cookie(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("可信应用不存在")));
        log.info("管理 API 删除应用成功：applicationId={}", applicationId);
    }

    @Test
    @DisplayName("删除内置可信应用应返回 409 且应用保持可读")
    void testDeleteBuiltInApplicationBlocked() throws Exception {
        java.util.List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM iam_trusted_application WHERE application_code = ?",
                Long.class, SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM);
        org.junit.jupiter.api.Assumptions.assumeTrue(!ids.isEmpty(), "引导未注册内置应用，跳过");
        Long builtInId = ids.get(0);

        mockMvc.perform(delete("/iam/admin/trusted-applications/" + builtInId).cookie(adminSession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("内置可信应用不可删除")));

        mockMvc.perform(get("/iam/admin/trusted-applications/" + builtInId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationCode").value(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM))
                .andExpect(jsonPath("$.builtIn").value(true));
        log.info("内置可信应用删除保护生效：applicationId={}", builtInId);
    }

    @Test
    @DisplayName("可信应用管理 API 畸形 JSON 应返回脱敏 400")
    void testMalformedRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationCode\":")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @DisplayName("可信应用管理 API 非管理员访问应返回 403")
    void testAdminApiRequiresAdminAuthority() throws Exception {
        mockMvc.perform(get("/iam/admin/trusted-applications").cookie(userSession))
                .andExpect(status().isForbidden());
    }

    private Long createApplication(String redirectUri) throws Exception {
        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createApplicationJson(redirectUri))
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.applicationCode").value(applicationCode))
                .andExpect(jsonPath("$.initialClientSecret").value(clientSecret));
        Long applicationId = findApplicationId();
        assertNotNull(applicationId, "创建后必须可查询到应用ID");
        return applicationId;
    }

    private Long createPortalApplication() throws Exception {
        mockMvc.perform(post("/iam/admin/trusted-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPortalApplicationJson())
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.applicationCode").value(applicationCode));
        Long applicationId = findApplicationId();
        assertNotNull(applicationId, "创建后必须可查询到应用ID");
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.emptyList());
        manifest.setApiPermissions(Collections.emptyList());
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, manifest);
        return applicationId;
    }

    /**
     * Portal 列表对所有人（含 admin）一律按 admitted+active 授权过滤，测试须先给 admin 准入。
     */
    private void admitApplicationToAdmin(Long applicationId) throws Exception {
        Long adminUserId = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new AssertionError("管理员用户必须存在：" + adminUsername)).getId();
        mockMvc.perform(put("/iam/admin/users/" + adminUserId + "/application-authorizations/" + applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"admitted\":true,\"roles\":[],\"pagePermissions\":[],"
                                + "\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());
    }

    private Long findApplicationId() {
        java.util.List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM iam_trusted_application WHERE application_code = ?", Long.class, applicationCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String createApplicationJson(String redirectUri) {
        return "{\"applicationCode\":\"" + applicationCode + "\","
                + "\"applicationName\":\"可信应用管理测试\",\"initialClient\":"
                + createClientJson(clientId, redirectUri) + "}";
    }

    private String createPortalApplicationJson() {
        return "{\"applicationCode\":\"" + applicationCode + "\","
                + "\"applicationName\":\"可信应用管理测试\",\"portal\":{\"enabled\":true,"
                + "\"entry\":\"https://example.com/entry.js\",\"menus\":[{\"code\":\"workspace\","
                + "\"name\":\"工作台\",\"route\":\"workspace\",\"sortOrder\":1}]},\"initialClient\":"
                + createClientJson(clientId, "https://example.com/callback") + "}";
    }

    private String createClientJson(String targetClientId, String redirectUri) {
        return "{\"clientId\":\"" + targetClientId + "\","
                + "\"clientName\":\"可信应用测试客户端\","
                + "\"clientType\":\"CONFIDENTIAL\",\"clientSecret\":\"" + clientSecret + "\","
                + "\"redirectUris\":[\"" + redirectUri + "\"],\"scopes\":[\"openid\"],"
                + "\"grantTypes\":[\"authorization_code\"],"
                + "\"authenticationMethods\":[\"client_secret_basic\"]}";
    }
}
