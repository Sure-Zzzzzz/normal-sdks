package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.cases;

import com.jayway.jsonpath.JsonPath;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.SimpleIamOidcAdapterTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.support.KeycloakLoginFormSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OIDC 登录端到端测试（真实 Keycloak + MySQL + Redis）
 *
 * <p>使用固定 Keycloak 容器（iam-local-keycloak，端口 8280，realm sure-iam-test），
 * fixture 用户 e2e-user；测试内驱动真实授权码流程（授权地址 → 登录表单 → 回调），
 * ID token 经 JWKS 真实验签。凭据经 application-local.yml 注入，测试代码不含凭据。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamOidcAdapterTestApplication.class)
@AutoConfigureMockMvc
class IamOidcLoginEndToEndTest {

    /**
     * fixture 用户名（Keycloak realm sure-iam-test 固定用户）
     */
    private static final String OIDC_FIXTURE_USERNAME = "e2e-user";

    @Value("${io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.oidc.user-password}")
    private String oidcUserPassword;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTokenRepository redisTokenRepository;

    /**
     * 取响应上的会话 cookie（spring-session 模式下跨请求以 JSESSIONID cookie 传会话，与浏览器一致）
     */
    private static Cookie sessionOf(MvcResult result) {
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }

    @AfterEach
    void cleanup() {
        Optional<IamUserEntity> provisioned = userRepository.findByUsername(OIDC_FIXTURE_USERNAME);
        provisioned.ifPresent(user -> userRepository.delete(user));
        userRepository.findByUsername("oidc-local-contrast").ifPresent(userRepository::delete);
        // provider 维度失败计数固定键，不清会跨次运行锁死 oidc 登录方式
        redisTokenRepository.deleteExternalLoginFailure("oidc", "oidc");
        redisTokenRepository.deleteLoginFailure(OIDC_FIXTURE_USERNAME);
    }

    @Test
    @DisplayName("providers 应包含 sso 类型的 oidc 登录方式")
    void testProvidersContainOidc() throws Exception {
        mockMvc.perform(get("/iam/web/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("local-password"))
                .andExpect(jsonPath("$.providers.length()").value(2))
                .andExpect(jsonPath("$.providers[1].code").value("oidc"))
                .andExpect(jsonPath("$.providers[1].type").value("sso"))
                .andExpect(jsonPath("$.providers[1].enabled").value(true))
                .andExpect(jsonPath("$.providers[1].authorizeUrl")
                        .value("/iam/web/auth/authorize/oidc"));
    }

    @Test
    @DisplayName("真实授权码流程：authorize → Keycloak 登录 → callback 应建会话并 JIT 开号")
    void testOidcBrowserLogin() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/oidc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").isNotEmpty())
                .andReturn();
        // state 上下文存 PendingStateStore（多实例语义）；防重放 state 经 spring-session 会话 cookie 传递
        Cookie sessionCookie = sessionOf(authorizeResult);
        assertNotNull(sessionCookie, "authorize 应建立 spring-session 会话并下发 JSESSIONID");
        String authorizeUrl =
                JsonPath.read(authorizeResult.getResponse().getContentAsString(), "$.authorizeUrl");
        String state = authorizeUrl.substring(
                authorizeUrl.lastIndexOf("state=") + "state=".length());
        state = state.contains("&") ? state.substring(0, state.indexOf('&')) : state;

        Map<String, String> callback = KeycloakLoginFormSupport.completeLogin(
                authorizeUrl, OIDC_FIXTURE_USERNAME, oidcUserPassword);
        assertEquals(state, callback.get("state"), "回传 state 应与授权时一致");

        mockMvc.perform(get("/iam/web/auth/callback/oidc")
                        .cookie(sessionCookie)
                        .param("code", callback.get("code"))
                        .param("state", callback.get("state")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));

        mockMvc.perform(get("/iam/web/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(OIDC_FIXTURE_USERNAME));

        Optional<IamUserEntity> saved = userRepository.findByUsername(OIDC_FIXTURE_USERNAME);
        assertTrue(saved.isPresent(), "OIDC 首登应 JIT 开号");
        assertEquals("oidc", saved.get().getIdentitySource());
        assertNotNull(saved.get().getExternalId(), "externalId 应取自 ID token sub");
    }

    @Test
    @DisplayName("外部 OIDC 账号不得使用本地密码登录")
    void testOidcAccountLocalPasswordRejected() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/oidc"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = sessionOf(authorizeResult);
        assertNotNull(sessionCookie, "authorize 应建立 spring-session 会话并下发 JSESSIONID");
        String authorizeUrl =
                JsonPath.read(authorizeResult.getResponse().getContentAsString(), "$.authorizeUrl");
        String state = authorizeUrl.substring(
                authorizeUrl.lastIndexOf("state=") + "state=".length());
        state = state.contains("&") ? state.substring(0, state.indexOf('&')) : state;
        Map<String, String> callback = KeycloakLoginFormSupport.completeLogin(
                authorizeUrl, OIDC_FIXTURE_USERNAME, oidcUserPassword);
        mockMvc.perform(get("/iam/web/auth/callback/oidc")
                        .cookie(sessionCookie)
                        .param("code", callback.get("code"))
                        .param("state", callback.get("state")))
                .andExpect(redirectedUrl("/app/"));

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + OIDC_FIXTURE_USERNAME
                                + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("本地密码登录对照：装配 OIDC 适配器后本地登录方式不受影响")
    void testLocalPasswordLoginStillWorks() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("oidc-local-contrast");
        request.setPassword("Admin@1234");
        request.setDisplayName("本地登录对照账号");
        userService.createUser(request);

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"oidc-local-contrast\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("oidc-local-contrast"));
    }
}
