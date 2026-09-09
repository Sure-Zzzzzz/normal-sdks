package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM Web 登录渐进验证码测试
 *
 * <p>真 Redis / 真 provider（captcha 适配器全链路）。验证码默认启用，
 * 本类 {@code @TestPropertySource} 显式声明 threshold（与默认开关保持一致）；
 * 裸试循环到锁定上限的既有用例（事件发布 / LDAP 计数隔离）在本类外关闭验证码自测。
 * threshold=2、maxAttempts=5；验证码答案经 redis-route 门面读取挑战 key 构造正向用例。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.auth.iam.server.captcha.enabled=true",
        "io.github.surezzzzzz.sdk.auth.iam.server.captcha.threshold=2"
})
class IamWebAuthCaptchaApiTest {

    private static final String KEY_PREFIX = "sure-auth-captcha:challenge:{default}::";

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "captcha-user-" + suffix;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(username).ifPresent(user -> userRepository.delete(user));
    }

    @Test
    @DisplayName("用户不存在连错达到阈值后同样要求验证码：防爆破不豁免未知用户名")
    void shouldRequireCaptchaForUnknownUsernameAfterThresholdFailures() throws Exception {
        String unknown = "no-such-user-" + suffix;
        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/iam/web/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + unknown + "\",\"password\":\"Wrong@0000\"}")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.captchaRequired").value(false));
        }
        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + unknown + "\",\"password\":\"Wrong@0000\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(true));
    }

    @Test
    @DisplayName("人机验证挑战 API 应返回 captchaId 与图片 data URI")
    void shouldIssueCaptchaChallenge() throws Exception {
        mockMvc.perform(get("/iam/web/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaId").isNotEmpty())
                .andExpect(jsonPath("$.type").value("image"))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    @DisplayName("失败达到阈值后登录应要求验证码：401 + captchaRequired=true")
    void shouldRequireCaptchaAfterThresholdFailures() throws Exception {
        createUser();
        loginWrongPassword();
        loginWrongPassword();

        loginNoCaptcha()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(true));
    }

    @Test
    @DisplayName("验证码通过且密码正确应登录成功")
    void shouldPassLoginWithCorrectCaptcha() throws Exception {
        createUser();
        loginWrongPassword();
        loginWrongPassword();

        String captchaId = fetchCaptchaId();
        String answer = readAnswer(captchaId);

        loginWithCaptcha(captchaId, answer, "Captcha@1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaRequired").value(false))
                .andExpect(jsonPath("$.user.username").value(username));
    }

    @Test
    @DisplayName("验证码拦截不递增密码失败计数：连续裸试始终 captchaRequired 而非锁定")
    void shouldNotIncreaseFailureCountOnCaptchaRejection() throws Exception {
        createUser();
        loginWrongPassword();
        loginWrongPassword();

        for (int attempt = 3; attempt <= 6; attempt++) {
            String body = loginNoCaptcha()
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.captchaRequired").value(true))
                    .andReturn().getResponse().getContentAsString();
            log.info("第 {} 次裸试响应：{}", attempt, body);
        }
    }

    @Test
    @DisplayName("验证码通过但密码错误应按凭据失败处理：401 且 captchaRequired=false")
    void shouldIncreaseFailureCountWhenCaptchaPassedButPasswordWrong() throws Exception {
        createUser();
        loginWrongPassword();
        loginWrongPassword();

        String captchaId = fetchCaptchaId();
        String answer = readAnswer(captchaId);

        loginWithCaptcha(captchaId, answer, "Wrong@0000")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(false));

        // 凭据失败已递增计数，裸试仍被验证码拦截
        loginNoCaptcha()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(true));
    }

    @Test
    @DisplayName("带码连续凭据失败达到上限仍锁定：验证码通过后仍被 401 拦（锁定脱敏为凭据错误）")
    void shouldStillLockAfterMaxAttemptsWithCaptchaPassed() throws Exception {
        createUser();
        for (int attempt = 1; attempt <= SimpleIamServerConstant.DEFAULT_LOGIN_MAX_ATTEMPTS; attempt++) {
            String captchaId = fetchCaptchaId();
            String answer = readAnswer(captchaId);
            loginWithCaptcha(captchaId, answer, "Wrong@0000")
                    .andExpect(status().isUnauthorized());
            log.info("带码凭据失败第 {} 次", attempt);
        }

        String captchaId = fetchCaptchaId();
        String answer = readAnswer(captchaId);
        loginWithCaptcha(captchaId, answer, "Captcha@1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(false))
                .andExpect(jsonPath("$.message").value("账号已锁定，请稍后重试或联系管理员"));

        // 锁定写库走独立事务：登录事务随后回滚不得吞掉 locked_until（管理端锁定徽标/解锁按钮依赖此列）
        userRepository.findByUsername(username).ifPresent(user -> {
            assertNotNull(user.getLockedUntil(), "锁定后 locked_until 应已落库");
            assertTrue(user.getLockedUntil().isAfter(Instant.now()), "locked_until 应指向未来");
        });
    }

    private void createUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Captcha@1234");
        request.setDisplayName(username);
        userService.createUser(request);
    }

    private void loginWrongPassword() throws Exception {
        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"Wrong@0000\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.captchaRequired").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions loginNoCaptcha() throws Exception {
        return mockMvc.perform(post("/iam/web/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"Captcha@1234\"}")
                .with(csrf()));
    }

    private org.springframework.test.web.servlet.ResultActions loginWithCaptcha(
            String captchaId, String answer, String password) throws Exception {
        return mockMvc.perform(post("/iam/web/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                        + "\",\"captchaId\":\"" + captchaId
                        + "\",\"captchaAnswer\":\"" + answer + "\"}")
                .with(csrf()));
    }

    /**
     * 经 redis-route 门面读取挑战答案（测试正向用例构造）
     */
    private String readAnswer(String captchaId) {
        String key = KEY_PREFIX + captchaId;
        String answer = redisRouteTemplate.stringTemplateByKey(key).opsForValue().get(key);
        assertNotNull(answer, "挑战答案应已写入 Redis");
        return answer;
    }

    private String fetchCaptchaId() throws Exception {
        MvcResult result = mockMvc.perform(get("/iam/web/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int index = body.indexOf("\"captchaId\":\"") + "\"captchaId\":\"".length();
        String captchaId = body.substring(index, body.indexOf('"', index));
        assertEquals(32, captchaId.length(), "captchaId 应为 32 位无横线 UUID");
        return captchaId;
    }
}
