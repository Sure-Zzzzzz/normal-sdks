package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.model.OAuth2TokenResponse;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.StaticSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.SimpleAkskClientCoreTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 敏感值暴露防回归测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientCoreTestApplication.class)
class SensitiveValueExposureTest {

    private static final String FIXTURE_CLIENT_SECRET = "fixture-client-secret";
    private static final String FIXTURE_ACCESS_TOKEN = "fixture-access-token";
    private static final String FIXTURE_SECURITY_CONTEXT = "fixture-security-context";

    @Autowired
    private SimpleAkskClientCoreProperties properties;

    @Test
    @DisplayName("字符串表示不得暴露密钥 Token 或安全上下文")
    void shouldNotExposeSensitiveValuesThroughToString() {
        properties.setClientSecret(FIXTURE_CLIENT_SECRET);
        OAuth2TokenResponse response = new OAuth2TokenResponse(FIXTURE_ACCESS_TOKEN, null, null, null);
        StaticSecurityContextProvider provider = new StaticSecurityContextProvider(FIXTURE_SECURITY_CONTEXT);

        String propertiesText = properties.toString();
        String responseText = response.toString();
        String providerText = provider.toString();

        log.info("敏感模型字符串表示已完成脱敏验证");
        assertFalse(propertiesText.contains(FIXTURE_CLIENT_SECRET), "配置字符串不得包含 Client Secret");
        assertFalse(responseText.contains(FIXTURE_ACCESS_TOKEN), "Token 响应字符串不得包含 Access Token");
        assertFalse(providerText.contains(FIXTURE_SECURITY_CONTEXT), "安全上下文提供者字符串不得包含安全上下文");
    }
}
