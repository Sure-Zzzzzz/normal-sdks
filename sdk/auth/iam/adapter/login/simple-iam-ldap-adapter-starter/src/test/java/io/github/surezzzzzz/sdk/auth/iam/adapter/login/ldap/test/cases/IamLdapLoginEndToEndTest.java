package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.test.SimpleIamLdapAdapterTestApplication;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LDAP 登录端到端测试（真实 openldap 容器 + MySQL + Redis）
 *
 * <p>复用 smart-middleware-ops-server-starter 的固定 LDAP 容器（middleware-ops-local-ldap，
 * 端口 1389），fixture 用户 ops-user；凭据经 application-local.yml 注入，测试代码不含凭据。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamLdapAdapterTestApplication.class)
@AutoConfigureMockMvc
class IamLdapLoginEndToEndTest {

    /**
     * fixture 用户名（middleware-ops 本地 LDAP 容器固定用户）
     */
    private static final String LDAP_FIXTURE_USERNAME = "ops-user";

    @Value("${io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.test.ldap.user-password}")
    private String ldapUserPassword;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTokenRepository redisTokenRepository;

    @AfterEach
    void cleanup() {
        Optional<IamUserEntity> provisioned = userRepository.findByUsername(LDAP_FIXTURE_USERNAME);
        provisioned.ifPresent(user -> userRepository.delete(user));
        userRepository.findByUsername("ldap-local-contrast").ifPresent(userRepository::delete);
        // fixture 用户名固定，外部失败计数不清会跨次运行累计达限导致假红
        redisTokenRepository.deleteExternalLoginFailure("ldap-password", LDAP_FIXTURE_USERNAME);
        redisTokenRepository.deleteLoginFailure(LDAP_FIXTURE_USERNAME);
    }

    @Test
    @DisplayName("providers 应包含启用状态的 ldap-password 登录方式")
    void testProvidersContainLdap() throws Exception {
        mockMvc.perform(get("/iam/web/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("local-password"))
                .andExpect(jsonPath("$.providers.length()").value(2))
                .andExpect(jsonPath("$.providers[1].code").value("ldap-password"))
                .andExpect(jsonPath("$.providers[1].type").value("ldap"))
                .andExpect(jsonPath("$.providers[1].enabled").value(true));
    }

    @Test
    @DisplayName("LDAP bind 成功应 JIT 开号、建立会话并回填显示名")
    void testLdapLoginSuccess() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"provider\":\"ldap-password\",\"username\":\"" + LDAP_FIXTURE_USERNAME
                                + "\",\"password\":\"" + ldapUserPassword + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(LDAP_FIXTURE_USERNAME))
                .andReturn();

        // spring-session 模式下跨请求以 JSESSIONID cookie 传会话（与浏览器一致）
        javax.servlet.http.Cookie sessionCookie =
                loginResult.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(sessionCookie, "登录成功应下发会话 cookie");
        mockMvc.perform(get("/iam/web/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(LDAP_FIXTURE_USERNAME));

        Optional<IamUserEntity> saved = userRepository.findByUsername(LDAP_FIXTURE_USERNAME);
        assertTrue(saved.isPresent(), "LDAP 首登应 JIT 开号");
        assertEquals("ldap-password", saved.get().getIdentitySource());
        assertNotNull(saved.get().getExternalId(), "externalId 应取自 LDAP DN/属性");
    }

    @Test
    @DisplayName("LDAP 密码错误应返回 401 且不开号")
    void testLdapLoginBadPassword() throws Exception {
        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"provider\":\"ldap-password\",\"username\":\"" + LDAP_FIXTURE_USERNAME
                                + "\",\"password\":\"wrong-ldap-password\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        assertTrue(!userRepository.findByUsername(LDAP_FIXTURE_USERNAME).isPresent());
    }

    @Test
    @DisplayName("本地密码登录对照：装配 LDAP 适配器后本地登录方式不受影响")
    void testLocalPasswordLoginStillWorks() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("ldap-local-contrast");
        request.setPassword("Admin@1234");
        request.setDisplayName("本地登录对照账号");
        userService.createUser(request);

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"ldap-local-contrast\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("ldap-local-contrast"));
    }
}
