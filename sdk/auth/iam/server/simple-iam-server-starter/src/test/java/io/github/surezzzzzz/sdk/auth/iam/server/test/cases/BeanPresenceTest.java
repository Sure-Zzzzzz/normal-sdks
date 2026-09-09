package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.support.JwtKeyProvider;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * IAM Server 核心 Bean 存在性验证
 *
 * <p>骨架阶段验证：确保所有关键 Bean 都已正确注册，未被误删或遗漏。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class BeanPresenceTest {

    @Autowired(required = false)
    private List<SecurityFilterChain> securityFilterChains;

    @Autowired(required = false)
    private RegisteredClientRepository registeredClientRepository;

    @Autowired(required = false)
    private OAuth2AuthorizationService authorizationService;

    @Autowired(required = false)
    private OAuth2TokenGenerator<?> tokenGenerator;

    @Autowired(required = false)
    private AuthorizationServerSettings authorizationServerSettings;

    @Autowired(required = false)
    private JwtKeyProvider jwtKeyProvider;

    @Test
    @DisplayName("SecurityFilterChain 应注册 7 条（Order 0-6）")
    void securityFilterChains_shouldBeSeven() {
        assertNotNull(securityFilterChains, "SecurityFilterChain 列表不应为空");
        assertEquals(7, securityFilterChains.size(),
                "应有 7 条 SecurityFilterChain（Error Dispatch + AuthorizationServer + Resource Verification + WebAPI + AdminAPI + IAM App + Fallback）");
    }

    @Test
    @DisplayName("RegisteredClientRepository 应已注册")
    void registeredClientRepository_shouldBePresent() {
        assertNotNull(registeredClientRepository, "RegisteredClientRepository 未注册");
    }

    @Test
    @DisplayName("OAuth2AuthorizationService 应已注册")
    void authorizationService_shouldBePresent() {
        assertNotNull(authorizationService, "OAuth2AuthorizationService 未注册");
    }

    @Test
    @DisplayName("OAuth2TokenGenerator 应已注册")
    void tokenGenerator_shouldBePresent() {
        assertNotNull(tokenGenerator, "OAuth2TokenGenerator 未注册");
    }

    @Test
    @DisplayName("AuthorizationServerSettings 应已注册")
    void authorizationServerSettings_shouldBePresent() {
        assertNotNull(authorizationServerSettings, "AuthorizationServerSettings 未注册");
        assertNotNull(authorizationServerSettings.getIssuer(), "Issuer 不应为空");
    }

    @Test
    @DisplayName("JwtKeyProvider 应已注册且密钥非空")
    void jwtKeyProvider_shouldBePresentAndKeysLoaded() {
        assertNotNull(jwtKeyProvider, "JwtKeyProvider 未注册");
        assertNotNull(jwtKeyProvider.getPublicKey(), "RSA 公钥未加载");
        assertNotNull(jwtKeyProvider.getPrivateKey(), "RSA 私钥未加载");
    }
}
