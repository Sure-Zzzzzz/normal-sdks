package io.github.surezzzzzz.sdk.auth.aksk.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.core.test.SimpleAkskServerCoreTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple AKSK Server Properties Test
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerCoreTestApplication.class)
class SimpleAkskServerPropertiesTest {

    @Test
    void shouldInitializeDefaultLimiterConfig() {
        SimpleAkskServerProperties properties = new SimpleAkskServerProperties();

        SimpleAkskServerProperties.LimiterConfig.OAuth2Config oauth2 = properties.getLimiter().getOauth2();

        assertTrue(oauth2.getEnable());
        assertEndpointConfig(oauth2.getToken(),
                SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_FALLBACK,
                SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_COUNT);
        assertEndpointConfig(oauth2.getIntrospect(),
                SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_FALLBACK,
                SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_COUNT);
        assertEndpointConfig(oauth2.getRevoke(),
                SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_FALLBACK,
                SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_COUNT);

        log.info("Default OAuth2 limiter config initialized: {}", oauth2);
    }

    @Test
    void redisConfigShouldNotExposeEnabledProperty() throws Exception {
        assertFalse(hasMethod(SimpleAkskServerProperties.RedisConfig.class, "getEnabled"));
        assertFalse(hasMethod(SimpleAkskServerProperties.RedisConfig.class, "setEnabled", Boolean.class));
    }

    private void assertEndpointConfig(SimpleAkskServerProperties.LimiterConfig.EndpointLimitConfig config,
                                      String fallback, Integer count) {
        assertNotNull(config);
        assertEquals(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM, config.getAlgorithm());
        assertEquals(fallback, config.getFallback());
        assertEquals(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY, config.getKeyStrategy());
        assertEquals(1, config.getLimits().size());
        assertEquals(count, config.getLimits().get(0).getCount());
        assertEquals(SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW, config.getLimits().get(0).getWindow());
        assertEquals(TimeUnit.MINUTES, config.getLimits().get(0).getUnit());
    }

    private boolean hasMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        try {
            type.getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}
