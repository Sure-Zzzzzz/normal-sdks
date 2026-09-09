package io.github.surezzzzzz.sdk.auth.iam.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.support.HttpIamResourceTokenVerificationClient;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM资源服务自动配置装配测试。
 *
 * @author surezzzzzz
 */
class SimpleIamResourceServerAutoConfigurationTest {

    @Test
    void doesNotAssembleWhenVerificationEndpointIsMissing() {
        contextRunner().run(context -> {
            assertFalse(context.containsBean("iamResourceTokenVerificationClient"),
                    "未配置验证端点时不得装配IAM验证客户端");
            assertFalse(context.containsBean("iamResourceAuthenticationAdapter"),
                    "未配置验证端点时不得注册IAM资源认证适配器");
            assertFalse(context.getStartupFailure() != null, "未启用IAM Provider不得启动失败");
        });
    }

    @Test
    void failsStartupWhenClientIdIsMissing() {
        contextRunner().withPropertyValues(verificationProperties("client-secret", "test-secret"))
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure, "缺少client-id时验证客户端创建必须失败");
                    assertTrue(containsConfigurationException(startupFailure),
                            "启动失败必须使用IAM模块配置异常");
                });
    }

    @Test
    void failsStartupWhenClientSecretIsMissing() {
        contextRunner().withPropertyValues(verificationProperties("client-id", "test-client"))
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure, "缺少client-secret时验证客户端创建必须失败");
                    assertTrue(containsConfigurationException(startupFailure),
                            "启动失败必须使用IAM模块配置异常");
                });
    }

    @Test
    void failsStartupWhenTimeoutIsNotPositive() {
        contextRunner().withPropertyValues(verificationProperties("client-id", "test-client",
                        "client-secret", "test-secret", "connect-timeout-millis", "0"))
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure, "非正数连接超时必须启动失败");
                    assertTrue(containsConfigurationException(startupFailure),
                            "启动失败必须使用IAM模块配置异常");
                });
    }

    @Test
    void assemblesIamProviderWithCompleteConfiguration() {
        contextRunner().withPropertyValues(verificationProperties("client-id", "test-client",
                        "client-secret", "test-secret"))
                .run(context -> {
                    assertFalse(context.getStartupFailure() != null, "完整配置必须成功启动");
                    assertNotNull(context.getBean(HttpIamResourceTokenVerificationClient.class),
                            "必须装配IAM受控验证客户端");
                    assertEquals(1, context.getBeansOfType(ResourceAuthenticationAdapter.class).size(),
                            "必须注册一个IAM资源认证适配器");
                });
    }

    @Test
    void allowsCustomVerificationClientToReplaceDefault() {
        contextRunner().withPropertyValues(verificationProperties("client-id", "test-client",
                        "client-secret", "test-secret"))
                .withUserConfiguration(CustomClientConfiguration.class)
                .run(context -> {
                    assertFalse(context.getStartupFailure() != null, "自定义验证客户端不得启动失败");
                    assertEquals(1, context.getBeansOfType(ResourceAuthenticationAdapter.class).size(),
                            "适配器必须改用自定义验证客户端");
                    assertEquals("customIamResourceTokenVerificationClient",
                            context.getBeanNamesForType(HttpIamResourceTokenVerificationClient.class)[0],
                            "@ConditionalOnMissingBean必须允许替换默认验证客户端");
                });
    }

    private String[] verificationProperties(String... nameValuePairs) {
        String[] properties = new String[nameValuePairs.length / 2 + 1];
        properties[0] = "io.github.surezzzzzz.sdk.auth.iam.resource.server.verification-endpoint="
                + "http://iam.example/iam/resource/tokens/verify";
        for (int index = 0; index < nameValuePairs.length; index += 2) {
            properties[index / 2 + 1] = "io.github.surezzzzzz.sdk.auth.iam.resource.server."
                    + nameValuePairs[index] + "=" + nameValuePairs[index + 1];
        }
        return properties;
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleIamResourceServerAutoConfiguration.class));
    }

    private boolean containsConfigurationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConfigurationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Configuration
    static class CustomClientConfiguration {

        @Bean
        HttpIamResourceTokenVerificationClient customIamResourceTokenVerificationClient() {
            return new HttpIamResourceTokenVerificationClient(validProperties());
        }

        private io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties validProperties() {
            io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties properties =
                    new io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties();
            properties.setVerificationEndpoint("http://iam.example/iam/resource/tokens/verify");
            properties.setClientId("test-client");
            properties.setClientSecret("test-secret");
            return properties;
        }
    }
}
