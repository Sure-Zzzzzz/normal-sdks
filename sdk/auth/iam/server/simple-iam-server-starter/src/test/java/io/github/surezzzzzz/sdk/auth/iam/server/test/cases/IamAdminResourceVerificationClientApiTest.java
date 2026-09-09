package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request.CreateResourceVerificationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamResourceVerificationClientRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamResourceVerificationClientService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资源验证客户端管理 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminResourceVerificationClientApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "rvc-admin-" + suffix;
    private final String userUsername = "rvc-user-" + suffix;
    private final String applicationCode = "rvc-app-" + suffix;
    private final String rvcClientId = "rvc-client-" + suffix;
    private final String otherRvcClientId = "rvc-other-" + suffix;
    private Cookie adminSession;
    private Cookie userSession;
    private Long applicationId;
    private Long otherApplicationId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private IamResourceVerificationClientService verificationClientService;

    @Autowired
    private IamResourceVerificationClientRepository verificationClientRepository;

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
        Long userId = userService.createUser(adminRequest).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(userId, adminRole.getId());
        MvcResult adminLogin = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = adminLogin.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(adminSession, "管理员登录必须成功并建立会话");
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
        userSession = userLogin.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(userSession, "普通用户登录必须成功并建立会话");
        applicationId = createApplication(applicationCode, applicationCode + "-web");
        otherApplicationId = createApplication("rvc-other-app-" + suffix, "rvc-other-app-" + suffix + "-web");
    }

    @AfterEach
    void cleanup() {
        verificationClientRepository.findByApplicationId(applicationId)
                .forEach(client -> verificationClientRepository.delete(client));
        verificationClientRepository.findByApplicationId(otherApplicationId)
                .forEach(client -> verificationClientRepository.delete(client));
        trustedApplicationService.deleteApplication(applicationId);
        trustedApplicationService.deleteApplication(otherApplicationId);
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("创建客户端应一次性返回密钥，非法与冲突输入按语义状态拒绝")
    void testCreateClientContract() throws Exception {
        String secret = createClientAndReturnSecret(rvcClientId);

        log.info("创建返回明文 secret 长度：{}", secret.length());
        assertNotNull(secret, "创建响应必须一次性返回明文 secret");

        mockMvc.perform(post(adminBasePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + rvcClientId + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post(adminBasePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"bad:id\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(adminBasePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"   \"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/iam/admin/trusted-applications/999999999/resource-verification-clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"no-app-" + suffix + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(adminBasePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + userUsername + "\"}")
                        .cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("列表与详情不得回显任何密钥形态")
    void testListAndDetailNeverLeakSecret() throws Exception {
        createClientAndReturnSecret(rvcClientId);

        mockMvc.perform(get(adminBasePath()).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value(rvcClientId))
                .andExpect(jsonPath("$[0].clientSecretHash").doesNotExist())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist());

        mockMvc.perform(get(adminBasePath() + "/" + rvcClientId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(rvcClientId))
                .andExpect(jsonPath("$.clientSecretHash").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    @DisplayName("轮换密钥后新密钥生效且旧密钥立即失效，已撤销客户端轮换应冲突")
    void testRotateSecretContract() throws Exception {
        String oldSecret = createClientAndReturnSecret(rvcClientId);

        String newSecret = mockMvc.perform(post(adminBasePath() + "/" + rvcClientId + "/secret")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(rvcClientId))
                .andReturn().getResponse().getContentAsString();
        log.info("轮换响应已返回新密钥，响应体不进入断言日志");

        assertNotNull(verificationClientService.authenticate(rvcClientId,
                extractSecret(newSecret)), "轮换后新密钥必须能通过认证");
        assertNull(verificationClientService.authenticate(rvcClientId, oldSecret),
                "轮换后旧密钥必须立即失效");

        mockMvc.perform(delete(adminBasePath() + "/" + rvcClientId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(adminBasePath() + "/" + rvcClientId + "/secret")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("撤销后认证立即失效且重复撤销幂等，跨应用访问应 404")
    void testRevokeAndCrossApplicationContract() throws Exception {
        createClientAndReturnSecret(rvcClientId);
        createClientAndReturnSecret(otherAdminBasePath(), otherRvcClientId);

        mockMvc.perform(get(adminBasePath() + "/" + otherRvcClientId).cookie(adminSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(adminBasePath() + "/" + rvcClientId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertNull(verificationClientService.authenticate(rvcClientId, "any"),
                "撤销后认证必须立即失败");

        mockMvc.perform(delete(adminBasePath() + "/" + rvcClientId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(adminBasePath() + "/" + rvcClientId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SimpleIamServerConstant.STATUS_INACTIVE))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
    }

    private String adminBasePath() {
        return "/iam/admin/trusted-applications/" + applicationId + "/resource-verification-clients";
    }

    private String otherAdminBasePath() {
        return "/iam/admin/trusted-applications/" + otherApplicationId + "/resource-verification-clients";
    }

    private String createClientAndReturnSecret(String clientId) throws Exception {
        return createClientAndReturnSecret(adminBasePath(), clientId);
    }

    private String createClientAndReturnSecret(String basePath, String clientId) throws Exception {
        CreateResourceVerificationClientRequest request = new CreateResourceVerificationClientRequest();
        request.setClientId(clientId);
        String body = mockMvc.perform(post(basePath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andReturn().getResponse().getContentAsString();
        return extractSecret(body);
    }

    private String extractSecret(String responseBody) {
        try {
            String secret = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(responseBody).path("clientSecret").asText(null);
            assertNotNull(secret, "响应必须包含 clientSecret 字段");
            return secret;
        } catch (java.io.IOException exception) {
            throw new AssertionError("响应体不是合法 JSON", exception);
        }
    }

    private Long createApplication(String code, String oauthClientId) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(oauthClientId);
        client.setClientName("Resource Verification Public Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://resource.example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Resource Verification Application");
        request.setInitialClient(client);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }
}
