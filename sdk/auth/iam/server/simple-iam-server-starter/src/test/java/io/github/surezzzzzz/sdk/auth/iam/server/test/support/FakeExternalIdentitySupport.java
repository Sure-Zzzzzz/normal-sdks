package io.github.surezzzzzz.sdk.auth.iam.server.test.support;

import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;

import java.util.Map;

/**
 * 外部身份源 SPI 测试替身
 *
 * <p>凭证型：ldap-password，正确凭证为 Ldap@1234；
 * 跳转型：fake-sso，回调 code=fake-code 且带 username 参数时认证成功。
 *
 * @author surezzzzzz
 */
public final class FakeExternalIdentitySupport {

    public static final String LDAP_PROVIDER_CODE = "ldap-password";
    public static final String LDAP_VALID_CREDENTIAL = "Ldap@1234";
    public static final String SSO_PROVIDER_CODE = "fake-sso";
    public static final String SSO_VALID_CODE = "fake-code";

    private FakeExternalIdentitySupport() {
    }

    public static ExternalCredentialAuthenticator ldapAuthenticator() {
        return new ExternalCredentialAuthenticator() {
            @Override
            public String providerCode() {
                return LDAP_PROVIDER_CODE;
            }

            @Override
            public ExternalIdentity authenticate(String username, String credential) {
                if (!LDAP_VALID_CREDENTIAL.equals(credential)) {
                    throw new IamProtocolException(ErrorCode.EXTERNAL_BAD_CREDENTIALS, "凭据错误");
                }
                return identity(LDAP_PROVIDER_CODE, "uid=" + username + ",ou=people,dc=example,dc=org", username);
            }
        };
    }

    public static ExternalBrowserLoginProvider ssoProvider() {
        return new ExternalBrowserLoginProvider() {
            @Override
            public String providerCode() {
                return SSO_PROVIDER_CODE;
            }

            @Override
            public String buildAuthorizeUrl(String callbackUrl, String state) {
                return "https://sso.example.org/authorize?callbackUrl=" + callbackUrl + "&state=" + state;
            }

            @Override
            public ExternalIdentity consumeCallback(Map<String, String> callbackParams) {
                if (!SSO_VALID_CODE.equals(callbackParams.get("code"))) {
                    throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "code 无效");
                }
                String username = callbackParams.get("username");
                return identity(SSO_PROVIDER_CODE, "sso-sub-" + username, username);
            }
        };
    }

    private static ExternalIdentity identity(String providerCode, String externalId, String username) {
        return ExternalIdentity.builder()
                .providerCode(providerCode)
                .externalId(externalId)
                .usernameSuggestion(username)
                .displayName(providerCode + "-" + username)
                .email(username + "@example.org")
                .build();
    }
}
