package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
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
 * 可信应用权限清单管理 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminApplicationPermissionManifestApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "apm-admin-" + suffix;
    private final String userUsername = "apm-user-" + suffix;
    private final String applicationCode = "apm-app-" + suffix;
    private Cookie adminSession;
    private Cookie userSession;
    private Long applicationId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

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
        userRequest.setUsername(userUsername);
        userRequest.setPassword("Admin@1234");
        userRequest.setDisplayName(userUsername);
        userService.createUser(userRequest);
        userSession = loginSession(userUsername);

        applicationId = createApplication(applicationCode, applicationCode + "-web");
    }

    @AfterEach
    void cleanup() {
        trustedApplicationService.deleteApplication(applicationId);
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("登记与替换应单调递增版本，去重回显，空数组合法，非法输入 400")
    void testPutManifestContract() throws Exception {
        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content(manifestBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manifestVersion").value(1))
                .andExpect(jsonPath("$.manifestDigest").isNotEmpty())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.pagePermissions[0]").value("app:order:page"))
                .andExpect(jsonPath("$.apiPermissions[0]").value("app:order:api"));

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content(manifestBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion").value(2));

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[],\"pagePermissions\":[],\"apiPermissions\":[],\"dataResources\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion").value(3))
                .andExpect(jsonPath("$.roles.length()").value(0));

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"  \"],\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/iam/admin/trusted-applications/999999999/permission-manifest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifestBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content(manifestBody()).cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());

        log.info("清单登记 / 替换契约断言完成：applicationId={}", applicationId);
    }

    @Test
    @DisplayName("未登记清单 GET 404，登记后投影三类码与摘要")
    void testGetManifestContract() throws Exception {
        mockMvc.perform(get(basePath()).cookie(adminSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content(manifestBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get(basePath()).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.manifestVersion").value(1))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.pagePermissions.length()").value(1))
                .andExpect(jsonPath("$.apiPermissions.length()").value(1));

        mockMvc.perform(get(basePath()).cookie(userSession))
                .andExpect(status().isForbidden());

        log.info("清单查询契约断言完成：applicationId={}", applicationId);
    }

    private Cookie loginSession(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(session, "登录必须成功并建立会话：" + username);
        return session;
    }

    private Long createApplication(String code, String oauthClientId) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(oauthClientId);
        client.setClientName("Permission Manifest Public Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://permission-manifest.example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Permission Manifest Application");
        request.setInitialClient(client);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }

    private String basePath() {
        return "/iam/admin/trusted-applications/" + applicationId + "/permission-manifest";
    }

    private String manifestBody() {
        return "{\"roles\":[\"app-admin\",\"app-admin\",\"app-user\"],"
                + "\"pagePermissions\":[\"app:order:page\"],"
                + "\"apiPermissions\":[\"app:order:api\"],"
                + "\"dataResources\":[]}";
    }
}
