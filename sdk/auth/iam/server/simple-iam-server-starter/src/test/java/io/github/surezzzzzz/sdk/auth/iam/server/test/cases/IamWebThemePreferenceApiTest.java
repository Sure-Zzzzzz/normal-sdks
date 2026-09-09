package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserThemePreferenceEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserThemePreferenceRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM Web 当前用户主题偏好 API 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamWebThemePreferenceApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String firstUsername = "theme-first-" + suffix;
    private final String secondUsername = "theme-second-" + suffix;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamUserThemePreferenceRepository themePreferenceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        deleteUser(firstUsername);
        deleteUser(secondUsername);
    }

    @Test
    @DisplayName("未登录读取主题偏好应返回401")
    void getThemePreferenceUnauthorized() throws Exception {
        mockMvc.perform(get("/iam/web/theme-preference"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("首次读取返回浅色默认偏好且不创建记录")
    void getDefaultThemePreferenceDoesNotPersist() throws Exception {
        IamUserEntity user = createUser(firstUsername);

        mockMvc.perform(get("/iam/web/theme-preference").cookie(authenticatedSession(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.mode").value("light"))
                .andExpect(jsonPath("$.customTokens").isEmpty())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        log.info("首次读取主题偏好未创建记录，userId={}", user.getId());
        org.junit.jupiter.api.Assertions.assertFalse(themePreferenceRepository.existsById(user.getId()),
                "首次读取默认主题不应创建偏好记录");
    }

    @Test
    @DisplayName("主题偏好应按当前用户保存并隔离")
    void saveThemePreferenceIsScopedToCurrentUser() throws Exception {
        IamUserEntity firstUser = createUser(firstUsername);
        IamUserEntity secondUser = createUser(secondUsername);
        Cookie firstSession = authenticatedSession(firstUser);
        Cookie secondSession = authenticatedSession(secondUser);

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(firstSession)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"contractVersion\":1,\"mode\":\"dark\",\"customTokens\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value(1))
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.customTokens").isEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/iam/web/theme-preference").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("dark"));
        mockMvc.perform(get("/iam/web/theme-preference").cookie(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("light"));

        log.info("主题偏好已按用户隔离，firstUserId={}, secondUserId={}", firstUser.getId(), secondUser.getId());
        org.junit.jupiter.api.Assertions.assertTrue(themePreferenceRepository.existsById(firstUser.getId()),
                "已保存主题的当前用户应具有偏好记录");
        org.junit.jupiter.api.Assertions.assertFalse(themePreferenceRepository.existsById(secondUser.getId()),
                "未保存主题的其他用户不应具有偏好记录");
    }

    @Test
    @DisplayName("自定义主题应规范化后保存并返回")
    void saveCustomThemePreferenceNormalizesAndPersists() throws Exception {
        IamUserEntity user = createUser(firstUsername);
        Cookie session = authenticatedSession(user);

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(customThemeRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("custom"))
                .andExpect(jsonPath("$.customTokens.primary").value("#1D4ED8"));
        mockMvc.perform(get("/iam/web/theme-preference").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("custom"))
                .andExpect(jsonPath("$.customTokens.primary").value("#1D4ED8"));
    }

    @Test
    @DisplayName("主题更新缺少 CSRF 应返回403")
    void saveThemePreferenceWithoutCsrfReturnsForbidden() throws Exception {
        IamUserEntity user = createUser(firstUsername);

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(authenticatedSession(user))
                        .contentType("application/json")
                        .content("{\"contractVersion\":1,\"mode\":\"dark\",\"customTokens\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("非法主题模式或自定义令牌应返回400")
    void saveInvalidThemePreferenceReturnsBadRequest() throws Exception {
        IamUserEntity user = createUser(firstUsername);
        Cookie session = authenticatedSession(user);

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"contractVersion\":1,\"mode\":\"brand\",\"customTokens\":{}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"contractVersion\":1,\"mode\":\"custom\",\"customTokens\":{\"unknown\":\"#123456\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"contractVersion\":1,\"mode\":\"light\",\"customTokens\":{\"primary\":\"#1D4ED8\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("切换浅色/深色应保留已保存的自定义色板并在读取时返回")
    void switchingLightOrDarkKeepsSavedCustomPalette() throws Exception {
        IamUserEntity user = createUser(firstUsername);
        Cookie session = authenticatedSession(user);

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(customThemeRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("custom"));

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"contractVersion\":1,\"mode\":\"dark\",\"customTokens\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.customTokens.primary").value("#1D4ED8"));

        mockMvc.perform(get("/iam/web/theme-preference").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("dark"))
                .andExpect(jsonPath("$.customTokens.primary").value("#1D4ED8"));

        log.info("切换深色后自定义色板仍保留并随读取返回，userId={}", user.getId());
    }

    @Test
    @DisplayName("自定义色板缺少后补的 overlayScrim 键应补默认值保存")
    void saveCustomThemeWithoutOverlayScrimFillsDefault() throws Exception {
        IamUserEntity user = createUser(firstUsername);
        Cookie session = authenticatedSession(user);

        mockMvc.perform(put("/iam/web/theme-preference")
                        .cookie(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content(customThemeRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customTokens.overlayScrim").value("#0D192F"));

        mockMvc.perform(get("/iam/web/theme-preference").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customTokens.overlayScrim").value("#0D192F"));
    }

    @Test
    @DisplayName("存量23键自定义记录读取应补 overlayScrim 默认值且不报错")
    void readLegacy23KeyCustomRecordFillsOverlayScrim() throws Exception {
        IamUserEntity user = createUser(firstUsername);
        insertThemePreference(user.getId(), "custom", customTokensJson());

        mockMvc.perform(get("/iam/web/theme-preference").cookie(authenticatedSession(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("custom"))
                .andExpect(jsonPath("$.customTokens.primary").value("#1D4ED8"))
                .andExpect(jsonPath("$.customTokens.overlayScrim").value("#0D192F"));
    }

    @Test
    @DisplayName("存量色板损坏时读取应兜底空表而不报错")
    void readCorruptedCustomRecordFallsBackToEmptyTokens() throws Exception {
        IamUserEntity user = createUser(firstUsername);
        insertThemePreference(user.getId(), "custom", "{not-json");

        mockMvc.perform(get("/iam/web/theme-preference").cookie(authenticatedSession(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("custom"))
                .andExpect(jsonPath("$.customTokens").isEmpty());
    }

    private String customThemeRequestBody() {
        return "{\"contractVersion\":1,\"mode\":\"custom\",\"customTokens\":" + customTokensJson() + "}";
    }

    private String customTokensJson() {
        return "{"
                + "\"primary\":\"#1d4ed8\",\"primaryHover\":\"#1E40AF\",\"primaryActive\":\"#1E3A8A\","
                + "\"primaryWeak\":\"#E0EAFF\",\"primaryText\":\"#FFFFFF\",\"canvas\":\"#F4F7FB\","
                + "\"surface\":\"#FFFFFF\",\"surfaceRaised\":\"#FFFFFF\",\"surfaceSoft\":\"#EEF3F9\","
                + "\"textPrimary\":\"#182230\",\"textSecondary\":\"#5F6B7A\",\"textDisabled\":\"#98A2B3\","
                + "\"border\":\"#D9E1EC\",\"borderStrong\":\"#B7C4D6\",\"focusRing\":\"#2563EB\","
                + "\"success\":\"#157347\",\"successBg\":\"#DEF7E8\",\"warning\":\"#9A5B00\","
                + "\"warningBg\":\"#FFF4D6\",\"danger\":\"#B42318\",\"dangerBg\":\"#FEE4E2\","
                + "\"info\":\"#175CD3\",\"infoBg\":\"#E8F1FF\"}";
    }

    private void insertThemePreference(Long userId, String mode, String customTokensJson) {
        IamUserThemePreferenceEntity entity = new IamUserThemePreferenceEntity();
        entity.setUserId(userId);
        entity.setContractVersion(1);
        entity.setMode(mode);
        entity.setCustomTokensJson(customTokensJson);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        themePreferenceRepository.save(entity);
    }

    private IamUserEntity createUser(String username) {
        Instant now = Instant.now();
        IamUserEntity user = new IamUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("Theme@1234"));
        user.setDisplayName(username);
        user.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    /**
     * 真实登录取会话 cookie：spring-session 下登录态由 Redis 会话承载，
     * 不再伪造 SecurityContext（伪造 principal 不经序列化/校验链，测不到真实登录态）。
     */
    private Cookie authenticatedSession(IamUserEntity user) throws Exception {
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.getUsername() + "\",\"password\":\"Theme@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(cookie, "登录成功必须返回会话 cookie");
        return cookie;
    }

    private void deleteUser(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (themePreferenceRepository.existsById(user.getId())) {
                themePreferenceRepository.deleteById(user.getId());
            }
            userRepository.delete(user);
        });
    }
}
