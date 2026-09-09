package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamSessionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM Web 登录态 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamWebAuthApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "web-admin-" + suffix;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamSessionRepository iamSessionRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(adminUsername).ifPresent(user -> userRepository.delete(user));
    }

    @Test
    @DisplayName("Web CSRF API 应返回 headerName、parameterName 和 token")
    void testCsrf() throws Exception {
        mockMvc.perform(get("/iam/web/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Web 登录方式 API 应返回本地登录主路径；未装配外部适配器时不出现外部条目")
    void testProviders() throws Exception {
        mockMvc.perform(get("/iam/web/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("local-password"))
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0].code").value("local-password"))
                .andExpect(jsonPath("$.providers[0].type").value("password"))
                .andExpect(jsonPath("$.providers[0].description").value("使用账号和密码登录"))
                .andExpect(jsonPath("$.providers[0].description").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("MySQL"))))
                .andExpect(jsonPath("$.providers[0].enabled").value(true));
    }

    @Test
    @DisplayName("Web 登录成功后应写入 Session，并可读取当前管理员信息")
    void testLoginAndMe() throws Exception {
        createAdminUser();

        MvcResult loginResult = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.user.username").value(adminUsername))
                .andExpect(jsonPath("$.user.admin").value(true))
                .andExpect(jsonPath("$.user.authorities", hasItem(SimpleIamServerConstant.ROLE_IAM_ADMIN)))
                .andReturn();

        Cookie session = loginResult.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        Session stored = sessionRepository.findById(sessionIdOf(session));
        assertNotNull(stored, "登录后 Redis 必须存在该会话");
        assertEquals(Duration.ofSeconds(SimpleIamServerConstant.DEFAULT_SESSION_EXPIRES_IN),
                stored.getMaxInactiveInterval(), "Web 登录 Session 超时应使用 session.expires-in 配置");
        mockMvc.perform(get("/iam/web/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(adminUsername))
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.authorities", hasItem(SimpleIamServerConstant.ROLE_IAM_ADMIN)));
    }

    @Test
    @DisplayName("节流窗外的活跃请求应触发滑动续期：DB 行 expiresAt 前移")
    void testActiveRequestRenewsSessionBeyondThrottleWindow() throws Exception {
        createAdminUser();
        MvcResult loginResult = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = loginResult.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);

        // 回拨 DB 行：模拟上次活跃在 600s 前（超出 300s 节流窗）
        IamSessionEntity iamSession = sessionService.findActiveByServletSessionId(sessionIdOf(session));
        Instant now = Instant.now();
        iamSession.setLastActiveAt(now.minusSeconds(600));
        iamSession.setExpiresAt(now.minusSeconds(600).plusSeconds(1800));
        iamSessionRepository.save(iamSession);

        mockMvc.perform(get("/iam/web/auth/me").cookie(session))
                .andExpect(status().isOk());

        IamSessionEntity reloaded = iamSessionRepository.findById(iamSession.getId()).orElseThrow();
        assertTrue(reloaded.getExpiresAt().isAfter(now.plusSeconds(1500)),
                "节流窗外的活跃请求应把 expiresAt 续到约 now+1800s");
    }

    @Test
    @DisplayName("IAM 会话撤销后旧 Servlet 会话不得继续访问当前用户 API")
    void testRevokedIamSessionInvalidatesServletSession() throws Exception {
        createAdminUser();
        MvcResult loginResult = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = loginResult.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        Session stored = sessionRepository.findById(sessionIdOf(session));
        assertNotNull(stored, "登录后 Redis 必须存在该会话");
        String iamSessionId = stored.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_IAM_SESSION_ID);
        sessionService.revokeSession(iamSessionId);

        mockMvc.perform(get("/iam/web/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Web 登录密码错误应返回 401 JSON 且不创建登录态")
    void testLoginBadCredential() throws Exception {
        createAdminUser();

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Wrong@0000\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    @DisplayName("Web 登录畸形 JSON 应返回脱敏 400")
    void testLoginMalformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @DisplayName("未登录读取当前用户应返回 401")
    void testMeUnauthorized() throws Exception {
        mockMvc.perform(get("/iam/web/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private void createAdminUser() {
        Long userId = userService.createUser(createReq()).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(userId, adminRole.getId());
    }

    private CreateUserRequest createReq() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName(adminUsername);
        return request;
    }

    /**
     * DefaultCookieSerializer 对会话 ID 做 base64 编码，读 Redis 会话前须先还原。
     */
    private String sessionIdOf(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }
}
