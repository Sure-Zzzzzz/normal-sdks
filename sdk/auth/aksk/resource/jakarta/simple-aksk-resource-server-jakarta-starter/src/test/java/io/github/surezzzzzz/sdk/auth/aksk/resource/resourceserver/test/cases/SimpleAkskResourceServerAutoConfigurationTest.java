package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AKSK Resource Server 自动配置边界测试。
 *
 * @author surezzzzzz
 */
class SimpleAkskResourceServerAutoConfigurationTest {

    @Test
    void failsStartupWhenEndpointIsMissing() {
        assertFailsWithMissingProperty("client-id", "client-secret");
    }

    @Test
    void failsStartupWhenClientIdIsMissing() {
        assertFailsWithMissingProperty("endpoint", "client-secret");
    }

    @Test
    void failsStartupWhenClientSecretIsMissing() {
        assertFailsWithMissingProperty("endpoint", "client-id");
    }

    @Test
    void failsStartupWhenEndpointIsMalformed() {
        assertFailsWithInvalidEndpoint("http://[invalid");
    }

    @Test
    void failsStartupWhenEndpointIsRelative() {
        assertFailsWithInvalidEndpoint("/oauth2/introspect");
    }

    @Test
    void failsStartupWhenEndpointHasNoHost() {
        assertFailsWithInvalidEndpoint("http:/oauth2/introspect");
    }

    @Test
    void failsStartupWhenEndpointUsesUnsupportedScheme() {
        assertFailsWithInvalidEndpoint("ftp://aksk.test/introspect");
    }

    @Test
    void failsStartupWhenEndpointContainsUserInfo() {
        assertFailsWithInvalidEndpoint("https://client:secret@aksk.test/introspect");
    }

    @Test
    void failsStartupWhenEndpointContainsQuery() {
        assertFailsWithInvalidEndpoint("https://aksk.test/introspect?tenant=default");
    }

    @Test
    void failsStartupWhenEndpointContainsFragment() {
        assertFailsWithInvalidEndpoint("https://aksk.test/introspect#section");
    }

    @Test
    void failsStartupWhenEndpointContainsInvalidPort() {
        assertFailsWithInvalidEndpoint("https://aksk.test:99999/introspect");
    }

    @Test
    void doesNotFailWhenProviderIsDisabled() {
        contextRunner().withPropertyValues(
                        "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("akskOpaqueTokenIntrospector"),
                            "禁用时不得创建 AKSK 内省器");
                    assertFalse(context.getStartupFailure() != null,
                            "禁用时缺少内省配置不得启动失败");
                });
    }

    @Test
    void allowsNamedIntrospectorToOwnProviderConfiguration() {
        contextRunner().withUserConfiguration(CustomIntrospectorConfiguration.class)
                .run(context -> {
                    assertFalse(context.getStartupFailure() != null,
                            "自定义内省器存在时不得要求 AKSK HTTP 配置");
                    assertNotNull(context.getBean("akskOpaqueTokenIntrospector"));
                    assertEquals(1, context.getBeansOfType(ResourceAuthenticationAdapter.class).size(),
                            "必须注册一个 AKSK 认证适配器");
                });
    }

    private void assertFailsWithInvalidEndpoint(String endpoint) {
        contextRunner().withPropertyValues(
                        "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.endpoint=" + endpoint,
                        "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.client-id=test-client",
                        "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.client-secret=test-secret")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure, "非法 AKSK 内省端点必须启动失败");
                    assertTrue(containsConfigurationException(startupFailure),
                            "非法 AKSK 内省端点必须使用模块配置异常");
                    assertTrue(containsMessage(startupFailure,
                                    "AKSK introspect.endpoint必须是包含host的绝对HTTP(S) URI"),
                            "启动失败必须明确内省端点格式边界");
                });
    }

    private void assertFailsWithMissingProperty(String first, String second) {
        contextRunner().withPropertyValues(
                        property(first), property(second))
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure, "缺少 AKSK 内省配置时必须启动失败");
                    assertTrue(containsConfigurationException(startupFailure),
                            "启动失败必须使用 AKSK 模块配置异常");
                    assertTrue(containsMessage(startupFailure,
                                    "AKSK introspect.endpoint、client-id和client-secret必须配置"),
                            "启动失败必须明确必填配置边界");
                });
    }

    private String property(String name) {
        return "io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect."
                + name + "=test-value";
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleAkskResourceServerAutoConfiguration.class));
    }

    private boolean containsConfigurationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SimpleAkskResourceServerConfigurationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsMessage(Throwable throwable, String expectedMessage) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedMessage.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Configuration
    static class CustomIntrospectorConfiguration {

        @Bean(name = "akskOpaqueTokenIntrospector")
        OpaqueTokenIntrospector akskOpaqueTokenIntrospector() {
            return token -> (OAuth2AuthenticatedPrincipal) null;
        }
    }
}
