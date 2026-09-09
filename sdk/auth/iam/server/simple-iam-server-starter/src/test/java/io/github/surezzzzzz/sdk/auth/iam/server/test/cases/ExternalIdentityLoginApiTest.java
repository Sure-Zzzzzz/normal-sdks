package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.jayway.jsonpath.JsonPath;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.test.support.FakeExternalIdentitySupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 外部身份源登录 API 测试（fake SPI 适配器）
 *
 * <p>本类关闭登录渐进验证码：LDAP 失败计数隔离用例为裸试循环到失败上限，
 * 默认启用下第 3 次起即被 CAPTCHA_REQUIRED 拦截导致计数不可达；
 * 验证码与分维度判定行为由 IamWebAuthCaptchaApiTest 专项覆盖。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleIamServerTestApplication.class,
        ExternalIdentityLoginApiTest.FakeProviderConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "io.github.surezzzzzz.sdk.auth.iam.server.captcha.enabled=false")
class ExternalIdentityLoginApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String ldapUsername = "ldap-user-" + suffix;
    private final String ssoUsername = "sso-user-" + suffix;
    private final String preBoundUsername = "prebound-user-" + suffix;
    private final String conflictingUsername = "conflict-user-" + suffix;
    private final String adminUsername = "ext-admin-" + suffix;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private RedisTokenRepository redisTokenRepository;

    @Autowired
    private SimpleIamServerProperties properties;
    private Cookie adminSessionCache;

    @AfterEach
    void cleanup() {
        for (String username : new String[]{ldapUsername, ssoUsername, preBoundUsername,
                conflictingUsername, adminUsername}) {
            userRepository.findByUsername(username).ifPresent(userRepository::delete);
        }
        // 跳转型失败计数按 provider 维度固定，不清会跨用例/跨次运行锁死登录方式
        redisTokenRepository.deleteExternalLoginFailure(
                FakeExternalIdentitySupport.SSO_PROVIDER_CODE, FakeExternalIdentitySupport.SSO_PROVIDER_CODE);
    }

    @Test
    @DisplayName("providers 应包含已装配的凭证型与跳转型登录方式")
    void testProvidersWithAdapters() throws Exception {
        mockMvc.perform(get("/iam/web/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("local-password"))
                .andExpect(jsonPath("$.providers.length()").value(3))
                .andExpect(jsonPath("$.providers[1].code").value(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE))
                .andExpect(jsonPath("$.providers[1].type").value("ldap"))
                .andExpect(jsonPath("$.providers[1].enabled").value(true))
                .andExpect(jsonPath("$.providers[2].code").value(FakeExternalIdentitySupport.SSO_PROVIDER_CODE))
                .andExpect(jsonPath("$.providers[2].type").value("sso"))
                .andExpect(jsonPath("$.providers[2].enabled").value(true))
                .andExpect(jsonPath("$.providers[2].authorizeUrl").value("/iam/web/auth/authorize/fake-sso"));
    }

    @Test
    @DisplayName("LDAP 凭证登录成功应 JIT 开号、建立会话且拒绝本地密码登录")
    void testLdapLoginJitProvisioning() throws Exception {
        MvcResult loginResult = loginWithProvider(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE,
                ldapUsername, FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL);
        mockMvc.perform(get("/iam/web/auth/me").cookie(sessionOf(loginResult)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ldapUsername));

        Optional<IamUserEntity> saved = userRepository.findByUsername(ldapUsername);
        assertTrue(saved.isPresent(), "JIT 应创建本地账号");
        assertEquals(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE, saved.get().getIdentitySource());
        assertNotNull(saved.get().getExternalId());

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + ldapUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("LDAP 凭证错误应返回 401 且不开号")
    void testLdapLoginBadCredential() throws Exception {
        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"provider\":\"ldap-password\",\"username\":\"" + ldapUsername
                                + "\",\"password\":\"Wrong@0000\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        assertTrue(!userRepository.findByUsername(ldapUsername).isPresent());
    }

    @Test
    @DisplayName("pre-bound-only 模式下未绑定外部身份登录应被拒绝，绑定后放行")
    void testPreBoundOnlyMode() throws Exception {
        String originalMode = properties.getExternalIdentity().getProvisioningMode();
        properties.getExternalIdentity().setProvisioningMode(SimpleIamServerConstant.PROVISIONING_MODE_PRE_BOUND_ONLY);
        try {
            mockMvc.perform(post("/iam/web/auth/login")
                            .contentType("application/json")
                            .content("{\"provider\":\"ldap-password\",\"username\":\"" + preBoundUsername
                                    + "\",\"password\":\"" + FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL + "\"}")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());

            CreateUserRequest request = new CreateUserRequest();
            request.setUsername(preBoundUsername);
            request.setPassword("Admin@1234");
            request.setDisplayName("预绑定账号");
            Long userId = userService.createUser(request).getId();
            IamUserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new AssertionError("预绑定账号创建后必须可查到：" + userId));
            user.setIdentitySource(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE);
            user.setExternalId("uid=" + preBoundUsername + ",ou=people,dc=example,dc=org");
            userRepository.save(user);

            loginWithProvider(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE, preBoundUsername,
                    FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL);
        } finally {
            properties.getExternalIdentity().setProvisioningMode(originalMode);
        }
    }

    @Test
    @DisplayName("外部身份与本地账号同名时不得自动并号")
    void testSameNameLocalAccountNotMerged() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(conflictingUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName("本地同名账号");
        userService.createUser(request);

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"provider\":\"ldap-password\",\"username\":\"" + conflictingUsername
                                + "\",\"password\":\"" + FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL + "\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        Optional<IamUserEntity> local = userRepository.findByUsername(conflictingUsername);
        assertTrue(local.isPresent());
        assertNull(local.get().getIdentitySource(), "同名本地账号不得被隐式绑定");
    }

    @Test
    @DisplayName("跳转型登录 authorize + callback 应建立会话")
    void testBrowserLoginCallback() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").isNotEmpty())
                .andReturn();
        Cookie session = sessionOf(authorizeResult);
        String authorizeUrl = JsonPath.read(authorizeResult.getResponse().getContentAsString(), "$.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.lastIndexOf("state=") + "state=".length());

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(session)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", state)
                        .param("username", ssoUsername))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));

        mockMvc.perform(get("/iam/web/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ssoUsername));
        assertTrue(userRepository.findByUsername(ssoUsername).isPresent(), "SSO 首登应 JIT 开号");
    }

    @Test
    @DisplayName("跳转型登录回调 state 不符应拒绝并重定向登录页")
    void testBrowserCallbackInvalidState() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = sessionOf(authorizeResult);

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(session)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", "tampered-state")
                        .param("username", ssoUsername))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=login-failed"));

        mockMvc.perform(get("/iam/web/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理员应可预绑定外部身份并解绑")
    void testAdminBindExternalIdentity() throws Exception {
        Long userId = createAdminAndLogin();
        Cookie adminSession = adminSession();

        mockMvc.perform(post("/iam/admin/users/" + userId + "/external-identity")
                        .cookie(adminSession)
                        .contentType("application/json")
                        .content("{\"providerCode\":\"ldap-password\",\"externalId\":\"uid=bound-" + suffix
                                + ",ou=people,dc=example,dc=org\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identitySource").value(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE));

        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/iam/admin/users/" + userId + "/external-identity")
                        .cookie(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertNull(userRepository.findById(userId).map(IamUserEntity::getIdentitySource).orElse(null));
    }

    @Test
    @DisplayName("绑定未装配的登录方式应被拒绝")
    void testAdminBindUnknownProviderRejected() throws Exception {
        Long userId = createAdminAndLogin();
        Cookie adminSession = adminSession();

        mockMvc.perform(post("/iam/admin/users/" + userId + "/external-identity")
                        .cookie(adminSession)
                        .contentType("application/json")
                        .content("{\"providerCode\":\"not-installed\",\"externalId\":\"whatever\"}")
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("authorize 未装配的登录方式应返回 404")
    void testAuthorizeUnknownProviderReturns404() throws Exception {
        mockMvc.perform(get("/iam/web/auth/authorize/not-installed"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("authorize 携带的站内 redirect 应保持到回调成功落地，站外地址忽略回退默认页")
    void testAuthorizeRedirectHeldThroughCallback() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso")
                        .param("redirect", "/app/some-app/"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = sessionOf(authorizeResult);
        String authorizeUrl = JsonPath.read(authorizeResult.getResponse().getContentAsString(), "$.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.lastIndexOf("state=") + "state=".length());

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(session)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", state)
                        .param("username", ssoUsername))
                .andExpect(redirectedUrl("/app/some-app/"));

        MvcResult openRedirectResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso")
                        .param("redirect", "//evil.example.com"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie secondSession = sessionOf(openRedirectResult);
        String secondUrl = JsonPath.read(openRedirectResult.getResponse().getContentAsString(), "$.authorizeUrl");
        String secondState = secondUrl.substring(secondUrl.lastIndexOf("state=") + "state=".length());

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(secondSession)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", secondState)
                        .param("username", ssoUsername + "b"))
                .andExpect(redirectedUrl("/app/"));
    }

    @Test
    @DisplayName("回调缺失 state 参数应拒绝并重定向登录页")
    void testBrowserCallbackStateMissingRejected() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = sessionOf(authorizeResult);

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(session)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("username", ssoUsername))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=login-failed"));
    }

    @Test
    @DisplayName("同一 state 二次回调应被拒绝（控制器级防重放）")
    void testBrowserCallbackStateReplayRejected() throws Exception {
        MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = sessionOf(authorizeResult);
        String authorizeUrl = JsonPath.read(authorizeResult.getResponse().getContentAsString(), "$.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.lastIndexOf("state=") + "state=".length());

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(session)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", state)
                        .param("username", ssoUsername))
                .andExpect(redirectedUrl("/app/"));

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(session)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", state)
                        .param("username", ssoUsername))
                .andExpect(redirectedUrl("/login?error=login-failed"));
    }

    @Test
    @DisplayName("LDAP 失败计数达限后应拒绝外部登录且不污染同名本地账号的本地登录")
    void testLdapFailureCountIsolationAndLockout() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(ldapUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName("计数隔离对照账号");
        userService.createUser(request);
        int maxAttempts = properties.getLogin().getMaxAttempts();
        try {
            for (int i = 0; i < maxAttempts; i++) {
                mockMvc.perform(post("/iam/web/auth/login")
                                .contentType("application/json")
                                .content("{\"provider\":\"ldap-password\",\"username\":\"" + ldapUsername
                                        + "\",\"password\":\"Wrong@0000\"}")
                                .with(csrf()))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(post("/iam/web/auth/login")
                            .contentType("application/json")
                            .content("{\"provider\":\"ldap-password\",\"username\":\"" + ldapUsername
                                    + "\",\"password\":\"" + FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL + "\"}")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/iam/web/auth/login")
                            .contentType("application/json")
                            .content("{\"username\":\"" + ldapUsername + "\",\"password\":\"Admin@1234\"}")
                            .with(csrf()))
                    .andExpect(status().isOk());
        } finally {
            redisTokenRepository.deleteExternalLoginFailure(
                    FakeExternalIdentitySupport.LDAP_PROVIDER_CODE, ldapUsername);
        }
    }

    @Test
    @DisplayName("跳转型回调失败计数达限后应锁定该登录方式")
    void testBrowserCallbackFailureLockout() throws Exception {
        int maxAttempts = properties.getLogin().getMaxAttempts();
        // state 一次性消费：每轮失败都要重新 authorize 拿新 state（authorize 公开端点，
        // 攻击者同样可反复发起，计数键按 provider 维度累计）
        for (int i = 0; i < maxAttempts; i++) {
            MvcResult authorizeResult = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso"))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie session = sessionOf(authorizeResult);
            String authorizeUrl =
                    JsonPath.read(authorizeResult.getResponse().getContentAsString(), "$.authorizeUrl");
            String state = authorizeUrl.substring(authorizeUrl.lastIndexOf("state=") + "state=".length());

            mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                            .cookie(session)
                            .param("code", "forged-code")
                            .param("state", state))
                    .andExpect(redirectedUrl("/login?error=" + ErrorCode.EXTERNAL_CALLBACK_INVALID));
        }

        MvcResult finalAuthorize = mockMvc.perform(get("/iam/web/auth/authorize/fake-sso"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie finalSession = sessionOf(finalAuthorize);
        String finalUrl = JsonPath.read(finalAuthorize.getResponse().getContentAsString(), "$.authorizeUrl");
        String finalState = finalUrl.substring(finalUrl.lastIndexOf("state=") + "state=".length());

        mockMvc.perform(get("/iam/web/auth/callback/fake-sso")
                        .cookie(finalSession)
                        .param("code", FakeExternalIdentitySupport.SSO_VALID_CODE)
                        .param("state", finalState)
                        .param("username", ssoUsername))
                .andExpect(redirectedUrl("/login?error=login-failed"));
    }

    @Test
    @DisplayName("已绑定的禁用账号外部登录应被拒绝")
    void testDisabledBoundAccountExternalLoginRejected() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(preBoundUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName("禁用预绑定账号");
        Long userId = userService.createUser(request).getId();
        IamUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AssertionError("禁用预绑定账号创建后必须可查到：" + userId));
        user.setIdentitySource(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE);
        user.setExternalId("uid=" + preBoundUsername + ",ou=people,dc=example,dc=org");
        user.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        userRepository.save(user);

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"provider\":\"ldap-password\",\"username\":\"" + preBoundUsername
                                + "\",\"password\":\"" + FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL + "\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JIT 账号解绑后本地密码登录仍应被拒绝（随机哈希不可匹配）")
    void testUnboundJitAccountLocalPasswordStillRejected() throws Exception {
        loginWithProvider(FakeExternalIdentitySupport.LDAP_PROVIDER_CODE,
                ldapUsername, FakeExternalIdentitySupport.LDAP_VALID_CREDENTIAL);
        Long userId = userRepository.findByUsername(ldapUsername)
                .map(IamUserEntity::getId)
                .orElseThrow(() -> new AssertionError("JIT 账号登录后必须可按用户名查到：" + ldapUsername));
        createAdminAndLogin();

        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/iam/admin/users/" + userId + "/external-identity")
                        .cookie(adminSession())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + ldapUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("同一外部身份重复绑定其他账号、绑定不存在用户、空参数均应被拒绝")
    void testAdminBindConflictsRejected() throws Exception {
        String externalId = "uid=conflict-" + suffix + ",ou=people,dc=example,dc=org";
        CreateUserRequest ownerRequest = new CreateUserRequest();
        ownerRequest.setUsername(preBoundUsername);
        ownerRequest.setPassword("Admin@1234");
        ownerRequest.setDisplayName("绑定属主");
        Long ownerUserId = userService.createUser(ownerRequest).getId();
        createAdminAndLogin();

        mockMvc.perform(post("/iam/admin/users/" + ownerUserId + "/external-identity")
                        .cookie(adminSession())
                        .contentType("application/json")
                        .content("{\"providerCode\":\"ldap-password\",\"externalId\":\"" + externalId + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        CreateUserRequest otherRequest = new CreateUserRequest();
        otherRequest.setUsername(conflictingUsername);
        otherRequest.setPassword("Admin@1234");
        otherRequest.setDisplayName("重复绑定账号");
        Long otherUserId = userService.createUser(otherRequest).getId();

        mockMvc.perform(post("/iam/admin/users/" + otherUserId + "/external-identity")
                        .cookie(adminSession())
                        .contentType("application/json")
                        .content("{\"providerCode\":\"ldap-password\",\"externalId\":\"" + externalId + "\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/iam/admin/users/999999999/external-identity")
                        .cookie(adminSession())
                        .contentType("application/json")
                        .content("{\"providerCode\":\"ldap-password\",\"externalId\":\"whatever\"}")
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/iam/admin/users/" + otherUserId + "/external-identity")
                        .cookie(adminSession())
                        .contentType("application/json")
                        .content("{\"providerCode\":\"ldap-password\",\"externalId\":\" \"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private MvcResult loginWithProvider(String provider, String username, String credential) throws Exception {
        return mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"provider\":\"" + provider + "\",\"username\":\"" + username
                                + "\",\"password\":\"" + credential + "\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Cookie sessionOf(MvcResult result) {
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }

    private Cookie adminSession() throws Exception {
        if (adminSessionCache == null) {
            throw new AssertionError("请先调用 createAdminAndLogin");
        }
        return adminSessionCache;
    }

    private Long createAdminAndLogin() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName("外部身份测试管理员");
        Long userId = userService.createUser(request).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(userId, adminRole.getId());
        MvcResult loginResult = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        adminSessionCache = sessionOf(loginResult);
        return userId;
    }

    // manual profile（手工验收真适配器模式）下 fake 退位：runIamServerLocal 的包扫描会
    // 把本嵌套类带进手工验收上下文（@TestConfiguration 的测试框架排除语义对 JavaExec
    // 主应用扫描无效），与 classpath 上的真适配器同码冲突（registry 快速失败）
    @TestConfiguration
    @Profile("!manual")
    static class FakeProviderConfiguration {

        @Bean
        ExternalCredentialAuthenticator fakeLdapAuthenticator() {
            return FakeExternalIdentitySupport.ldapAuthenticator();
        }

        @Bean
        ExternalBrowserLoginProvider fakeSsoProvider() {
            return FakeExternalIdentitySupport.ssoProvider();
        }
    }
}
