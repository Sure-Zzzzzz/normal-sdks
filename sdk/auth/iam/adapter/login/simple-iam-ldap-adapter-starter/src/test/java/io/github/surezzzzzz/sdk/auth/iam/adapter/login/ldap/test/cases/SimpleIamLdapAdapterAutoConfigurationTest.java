package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.configuration.SimpleIamLdapAdapterAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.configuration.SimpleIamLdapAdapterProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.service.LdapCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * LDAP 适配器自动装配测试（不依赖 LDAP 基础设施）
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleIamLdapAdapterAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleIamLdapAdapterAutoConfiguration.class));

    @Test
    @DisplayName("配置完整时应装配认证器（无 enable 开关，引入即装配）")
    void testCompleteConfigurationRegistersAuthenticator() {
        runner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.url=ldap://localhost:1389/dc=middleware-ops,dc=test")
                .run(context -> {
                    log.info("配置 url 完整：认证器装配数={}",
                            context.getBeanNamesForType(LdapCredentialAuthenticator.class).length);
                    assertThat(context).hasSingleBean(LdapCredentialAuthenticator.class);
                });
    }

    @Test
    @DisplayName("目录服务不可达应抛 PROVIDER_UNAVAILABLE 而非凭据错误")
    void testUnreachableDirectoryMapsToProviderUnavailable() {
        SimpleIamLdapAdapterProperties properties = new SimpleIamLdapAdapterProperties();
        properties.setUrl("ldap://127.0.0.1:1/dc=middleware-ops,dc=test");
        properties.setUserSearchBase("ou=people");
        LdapCredentialAuthenticator authenticator = new LdapCredentialAuthenticator(properties);

        IamProtocolException exception = assertThrows(IamProtocolException.class,
                () -> authenticator.authenticate("anyone", "any-password"));
        log.info("目录不可达：errorCode={}, message={}",
                exception.getErrorCode(), exception.getMessage());
        assertEquals("BIZ_003", exception.getErrorCode());
    }

    @Test
    @DisplayName("providerCode 应为 ldap-password")
    void testProviderCode() {
        SimpleIamLdapAdapterProperties properties = new SimpleIamLdapAdapterProperties();
        properties.setUrl("ldap://127.0.0.1:1/dc=example,dc=org");
        LdapCredentialAuthenticator authenticator = new LdapCredentialAuthenticator(properties);
        log.info("providerCode={}", authenticator.providerCode());
        assertEquals("ldap-password", authenticator.providerCode());
    }

    @Test
    @DisplayName("url 缺失应启动失败并指明配置前缀")
    void testIncompleteConfigurationFailsFast() {
        runner.run(context -> {
            log.info("url 缺失启动失败：{}",
                    context.getStartupFailure().getMessage());
            assertTrue(context.getStartupFailure() != null,
                    "url 缺失时必须快速失败，实际正常启动");
            assertTrue(context.getStartupFailure().getMessage()
                    .contains("adapter.login.ldap 已引入但配置不完整"));
        });
    }
}
