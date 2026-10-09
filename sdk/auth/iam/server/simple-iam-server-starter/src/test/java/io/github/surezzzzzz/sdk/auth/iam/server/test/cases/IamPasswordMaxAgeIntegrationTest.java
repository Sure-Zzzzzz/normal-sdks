package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 口令最长生存期集成测试（1.3.6）：MockMvc 全链——过期置位登录、受限期 /me 白名单可读原因、
 * 非白名单 403、新密码不得等于当前密码、改密后恢复与临期剩余天数携带。
 * 策略经测试属性开启（90 天 / 提醒 7 天）。
 *
 * @author surezzzzzz
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = SimpleIamServerTestApplication.class,
        properties = {"io.github.surezzzzzz.sdk.auth.iam.server.password.max-age-days=90",
                "io.github.surezzzzzz.sdk.auth.iam.server.password.warn-before-days=7",
                "io.github.surezzzzzz.sdk.auth.iam.server.password.must-change-enforcement=true",
                "io.github.surezzzzzz.sdk.auth.iam.server.captcha.enabled=false"})
class IamPasswordMaxAgeIntegrationTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "max-age-" + suffix;
    private final String initialPassword = "MaxAge-Init@2026";
    private final String newPassword = "MaxAge-Renew@2026";
    private final String thirdPassword = "MaxAge-Third@2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamUserService userService;

    @Autowired
    private IamUserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(username)
                .ifPresent(user -> userService.deleteUser(user.getId()));
        assertFalse(userRepository.findByUsername(username).isPresent(), "测试清理后不得残留账号：" + username);
    }

    @Test
    @DisplayName("过期登录置位 PASSWORD_EXPIRED：受限会话 /me 可读原因，改密后恢复并携带剩余天数")
    void expiredLoginMarksReasonAndRecoversAfterChange() throws Exception {
        createUserWithPasswordAge(91L);

        MvcResult login = loginExpectingOk(initialPassword);
        Cookie session = sessionOf(login);

        // 受限期非白名单（用户列表）403 引导先改密
        mockMvc.perform(get("/iam/admin/users").cookie(session).with(csrf()))
                .andExpect(status().isForbidden());
        // /me 在强制改密白名单内：受限期可读，携带过期原因（改密页刷新恢复文案的数据源）
        mockMvc.perform(get("/iam/web/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePasswordReason").value("PASSWORD_EXPIRED"))
                .andExpect(jsonPath("$.passwordExpiresInDays").value(0));

        // 新密码=当前密码：400 拒绝（假换密拦截）
        changePassword(session, initialPassword, initialPassword, 400);
        // 改成不同密码：成功，须改密解除
        changePassword(session, initialPassword, newPassword, 204);

        MvcResult renewed = loginExpectingOk(newPassword);
        Cookie renewedSession = sessionOf(renewed);
        mockMvc.perform(get("/iam/web/auth/me").cookie(renewedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePasswordReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.passwordExpiresInDays").value(org.hamcrest.Matchers.nullValue()));

        IamUserEntity after = userRepository.findByUsername(username).orElseThrow(AssertionError::new);
        assertNotNull(after.getPasswordUpdatedAt(), "改密后时间戳必须刷新");
        assertEquals(Boolean.FALSE, after.getMustChangePassword());
    }

    @Test
    @DisplayName("临期登录携带剩余天数不拦截；admin 重置同密码 400、不同密码置 PASSWORD_RESET")
    void warnWindowCarriesDaysAndAdminResetSemantics() throws Exception {
        // 先改掉初始密码完成首登闭环（清 FIRST_LOGIN 置位），再回拨 85 天进入临期窗口
        createUserWithPasswordAge(0L);
        userService.changePassword(userRepository.findByUsername(username).orElseThrow(AssertionError::new).getId(),
                initialPassword, newPassword, null);
        backdatePassword(85L);

        MvcResult login = loginExpectingOk(newPassword);
        // 临期：顶层携带剩余天数（回拨 85 天，TIMESTAMP 存读时区毫秒可致 5/6 跨天，窗口内即可）
        com.fasterxml.jackson.databind.JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(login.getResponse().getContentAsString());
        int remaining = body.path("passwordExpiresInDays").asInt(-1);
        org.springframework.test.util.AssertionErrors.assertTrue("临期应携带 1..7 剩余天数，实际=" + remaining,
                remaining >= 1 && remaining <= 7);
        assertFalse(body.path("mustChangePassword").asBoolean(), "临期不得置须改密");
        assertFalse(body.path("mustChangePasswordReason") != null
                        && !body.path("mustChangePasswordReason").isNull(),
                "临期不得携带须改密原因");
        // 受限期外正常访问（登录页自身白名单外的管理面按既有权限——本账号无权限 403 属预期权限语义，
        // 此处只证明不是 MUST_CHANGE_PASSWORD 拦截体）
        mockMvc.perform(get("/iam/admin/users").cookie(sessionOf(login)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString()
                        .contains("MUST_CHANGE_PASSWORD"), "临期不落须改密拦截"));

        IamUserEntity user = userRepository.findByUsername(username).orElseThrow(AssertionError::new);
        // admin 重置同密码：400（假换密拦截，时间戳不刷新）
        try {
            userService.resetPassword(user.getId(), newPassword, "admin-test");
            throw new AssertionError("新密码=当前密码必须被拒绝");
        } catch (io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException expected) {
            org.springframework.test.util.AssertionErrors.assertTrue("必须因新=旧被拒，实际=" + expected.getMessage(),
                    "新密码不得与当前密码相同".equals(expected.getMessage()));
        }
        // admin 重置不同密码（第三个密码，区别于夹具改密后的当前密码）：置位 PASSWORD_RESET 并刷时间戳
        userService.resetPassword(user.getId(), thirdPassword, "admin-test");
        IamUserEntity resetUser = userRepository.findByUsername(username).orElseThrow(AssertionError::new);
        assertEquals(Boolean.TRUE, resetUser.getMustChangePassword());
        assertEquals(io.github.surezzzzzz.sdk.auth.iam.server.entity.user.MustChangePasswordReason.PASSWORD_RESET,
                resetUser.getMustChangePasswordReason());
        assertNotNull(resetUser.getPasswordUpdatedAt());

        MvcResult resetLogin = loginExpectingOk(thirdPassword);
        org.springframework.test.util.AssertionErrors.assertTrue("reset reason",
                resetLogin.getResponse().getContentAsString().contains("\"mustChangePasswordReason\":\"PASSWORD_RESET\""));
    }

    @Test
    @DisplayName("未激活行（时间戳 null）策略开启后首登回填并按满额正常登录")
    void nullTimestampActivatesOnFirstLoginUnderPolicy() throws Exception {
        createUserWithPasswordAge(0L);

        MvcResult login = loginExpectingOk(initialPassword);
        org.springframework.test.util.AssertionErrors.assertTrue("建号首登须改密原因=FIRST_LOGIN（既有置位）",
                login.getResponse().getContentAsString().contains("\"mustChangePasswordReason\":\"FIRST_LOGIN\""));
        // 建号首登本身即须改密（既有行为），改密后时间戳回填即激活基线
        changePassword(sessionOf(login), initialPassword, newPassword, 204);
        IamUserEntity after = userRepository.findByUsername(username).orElseThrow(AssertionError::new);
        assertNotNull(after.getPasswordUpdatedAt(), "改密刷新时间戳（策略开启下的激活与起算）");
    }

    private MvcResult loginExpectingOk(String password) throws Exception {
        return mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void changePassword(Cookie session, String oldPassword, String nextPassword, int expectedStatus)
            throws Exception {
        mockMvc.perform(put("/iam/web/auth/password").cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + nextPassword + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private Cookie sessionOf(MvcResult result) {
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }

    private void backdatePassword(long daysAgo) {
        IamUserEntity user = userRepository.findByUsername(username).orElseThrow(AssertionError::new);
        user.setPasswordUpdatedAt(Instant.now().minusSeconds(daysAgo * 24 * 60 * 60));
        userRepository.save(user);
    }

    private Long createUserWithPasswordAge(long daysAgo) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword(initialPassword);
        request.setDisplayName("生存期验收");
        IamUserEntity user = userService.createUser(request);
        if (daysAgo > 0) {
            user.setPasswordUpdatedAt(Instant.now().minusSeconds(daysAgo * 24 * 60 * 60));
            userRepository.save(user);
        }
        return user.getId();
    }
}
