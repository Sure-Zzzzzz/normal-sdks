package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.ExternalProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部登录方式注册表测试
 *
 * @author surezzzzzz
 */
class ExternalProviderRegistryTest {

    @Test
    @DisplayName("空注册表不报错且查询返回空")
    void testEmptyRegistry() {
        ExternalProviderRegistry registry = ExternalProviderRegistry.create(null, null);
        assertTrue(registry.getCredentialAuthenticators().isEmpty());
        assertTrue(registry.getBrowserLoginProviders().isEmpty());
        assertNull(registry.getCredentialAuthenticator("ldap-password"));
        assertFalse(registry.contains("ldap-password"));
    }

    @Test
    @DisplayName("两个凭证型认证器注册同一编码应启动失败")
    void testDuplicateCredentialCodeFailsFast() {
        assertThrows(ConfigurationException.class, () -> ExternalProviderRegistry.create(
                Arrays.asList(authenticator("ldap-password"), authenticator("ldap-password")),
                Collections.emptyList()));
    }

    @Test
    @DisplayName("同一编码同时注册为凭证型与跳转型应启动失败")
    void testCrossKindCodeConflictFailsFast() {
        assertThrows(ConfigurationException.class, () -> ExternalProviderRegistry.create(
                Collections.singletonList(authenticator("shared-code")),
                Collections.singletonList(browserProvider("shared-code"))));
    }

    @Test
    @DisplayName("空编码应启动失败")
    void testBlankCodeFailsFast() {
        assertThrows(ConfigurationException.class, () -> ExternalProviderRegistry.create(
                Collections.singletonList(authenticator(" ")), Collections.emptyList()));
    }

    @Test
    @DisplayName("正常注册应可按编码查询且互不干扰")
    void testLookupByCode() {
        ExternalProviderRegistry registry = ExternalProviderRegistry.create(
                Collections.singletonList(authenticator("ldap-password")),
                Collections.singletonList(browserProvider("enterprise-sso")));
        assertNotNull(registry.getCredentialAuthenticator("ldap-password"));
        assertNotNull(registry.getBrowserLoginProvider("enterprise-sso"));
        assertNull(registry.getCredentialAuthenticator("enterprise-sso"));
        assertTrue(registry.contains("ldap-password"));
        assertTrue(registry.contains("enterprise-sso"));
        assertFalse(registry.contains("local-password"));
    }

    private ExternalCredentialAuthenticator authenticator(String code) {
        return new ExternalCredentialAuthenticator() {
            @Override
            public String providerCode() {
                return code;
            }

            @Override
            public ExternalIdentity authenticate(String username, String credential) {
                throw new IamProtocolException("BIZ_002", "not used");
            }
        };
    }

    private ExternalBrowserLoginProvider browserProvider(String code) {
        return new ExternalBrowserLoginProvider() {
            @Override
            public String providerCode() {
                return code;
            }

            @Override
            public String buildAuthorizeUrl(String callbackUrl, String state) {
                return "https://idp.example.org/authorize?state=" + state;
            }

            @Override
            public ExternalIdentity consumeCallback(Map<String, String> callbackParams) {
                throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "not used");
            }
        };
    }
}
