package io.github.surezzzzzz.sdk.auth.aksk.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.core.test.SimpleAkskServerCoreTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple AKSK Server 配置测试
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

    @Test
    void shouldExposeManagementAuthorizationContracts() {
        assertEquals(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                SimpleAkskServerConstant.JWT_CLAIM_APPLICATION_AUTHORIZATION);
        assertEquals("aksk-server", SimpleAkskServerConstant.MANAGEMENT_APPLICATION_CODE);

        assertEquals("akskClient", SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT);
        assertEquals("akskToken", SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN);
        assertEquals("akskApplicationAuthorization",
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION);

        assertEquals("create", SimpleAkskServerConstant.MANAGEMENT_ACTION_CREATE);
        assertEquals("read", SimpleAkskServerConstant.MANAGEMENT_ACTION_READ);
        assertEquals("update", SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE);
        assertEquals("delete", SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE);
        assertEquals("revoke", SimpleAkskServerConstant.MANAGEMENT_ACTION_REVOKE);

        assertEquals("akskApplicationAuthorization:create",
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_CREATE);
        assertEquals("akskApplicationAuthorization:read",
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ);
        assertEquals("akskApplicationAuthorization:update",
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_UPDATE);
        assertEquals("akskApplicationAuthorization:revoke",
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_REVOKE);
        assertEquals("akskClient:create", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_CREATE);
        assertEquals("akskClient:read", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ);
        assertEquals("akskClient:update", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_UPDATE);
        assertEquals("akskClient:delete", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_DELETE);
        assertEquals("akskToken:read", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ);
        assertEquals("akskToken:update", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
        assertEquals("akskToken:delete", SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_DELETE);

        assertEquals("applicationCode", SimpleAkskServerConstant.MANAGEMENT_DIMENSION_APPLICATION_CODE);
        assertEquals("clientId", SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_ID);
        assertEquals("clientType", SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE);
        assertEquals("ownerUserId", SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID);
        assertEquals("tokenId", SimpleAkskServerConstant.MANAGEMENT_DIMENSION_TOKEN_ID);
    }

    @Test
    void shouldNotExposeAnonymousIntrospectConfiguration() {
        assertFalse(hasMethod(SimpleAkskServerProperties.class, "getIntrospect"));
        assertFalse(hasNestedClass(SimpleAkskServerProperties.class, "IntrospectConfig"));
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

    private boolean hasNestedClass(Class<?> type, String simpleName) {
        for (Class<?> nestedClass : type.getDeclaredClasses()) {
            if (simpleName.equals(nestedClass.getSimpleName())) {
                return true;
            }
        }
        return false;
    }
}
