package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Context path invalid configuration test
 *
 * @author surezzzzzz
 */
@Slf4j
class ContextPathInvalidConfigurationTest {

    @Test
    void testShouldFailFastWhenPermitAllOverridesProtected() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> startInvalidContext(
                "server.servlet.context-path=/api",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.context-path-aware=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths[0]=/api/admin/**",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths[0]=/api/**"),
                "permit-all-paths 归一化为 /** 且 protected 非空时应 fail fast");
        assertConfigurationException(exception);
    }

    @Test
    void testShouldFailFastWhenSecurityPathContainsQuery() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> startInvalidContext(
                "server.servlet.context-path=/api",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.context-path-aware=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths[0]=/api/user/**?debug=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths="),
                "security path 包含 query string 时应 fail fast");
        assertConfigurationException(exception);
    }

    private void startInvalidContext(String... properties) {
        ConfigurableApplicationContext context = null;
        try {
            log.info("启动非法配置上下文，properties={}", (Object) properties);
            context = new SpringApplicationBuilder(SimpleAkskResourceServerTestApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(toCommandLineArgs(properties));
            context.getBean(SecurityFilterChain.class);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private String[] toCommandLineArgs(String... properties) {
        String[] args = new String[properties.length + 1];
        args[0] = "--server.port=0";
        for (int i = 0; i < properties.length; i++) {
            args[i + 1] = "--" + properties[i];
        }
        return args;
    }

    private void assertConfigurationException(Throwable throwable) {
        SimpleAkskResourceServerConfigurationException cause = findConfigurationException(throwable);
        log.info("配置异常: {}", cause == null ? null : cause.getMessage());
        assertNotNull(cause, "异常链中应包含 SimpleAkskResourceServerConfigurationException");
    }

    private SimpleAkskResourceServerConfigurationException findConfigurationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SimpleAkskResourceServerConfigurationException) {
                return (SimpleAkskResourceServerConfigurationException) current;
            }
            current = current.getCause();
        }
        return null;
    }
}
