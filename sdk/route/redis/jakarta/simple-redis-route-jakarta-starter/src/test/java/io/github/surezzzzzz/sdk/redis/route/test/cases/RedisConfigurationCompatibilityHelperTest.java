package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.support.RedisConfigurationCompatibilityHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Redis 连接配置 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisConfigurationCompatibilityHelperTest {

    @Test
    public void testAbsentValueKeepsDefaults() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        RedisConfigurationCompatibilityHelper.applyUsername(configuration, null);
        RedisConfigurationCompatibilityHelper.applyPassword(configuration, null);
        assertNull(configuration.getUsername());
        assertFalse(configuration.getPassword().isPresent());
    }

    @Test
    public void testApplyAuthenticationToStandaloneAndCluster() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        RedisClusterConfiguration cluster = new RedisClusterConfiguration(Collections.singletonList("localhost:7000"));

        RedisConfigurationCompatibilityHelper.applyUsername(standalone, "route-user");
        RedisConfigurationCompatibilityHelper.applyPassword(standalone, "OPAQUE-CREDENTIAL-CONTENT");
        RedisConfigurationCompatibilityHelper.applyUsername(cluster, "route-user");
        RedisConfigurationCompatibilityHelper.applyPassword(cluster, "OPAQUE-CREDENTIAL-CONTENT");

        assertEquals("route-user", standalone.getUsername());
        assertArrayEquals("OPAQUE-CREDENTIAL-CONTENT".toCharArray(), standalone.getPassword().get());
        assertEquals("route-user", cluster.getUsername());
        assertArrayEquals("OPAQUE-CREDENTIAL-CONTENT".toCharArray(), cluster.getPassword().get());
    }

    @Test
    public void testApplyClientName() {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
        RedisConfigurationCompatibilityHelper.applyClientName(builder, "route-client");
        assertEquals("route-client", builder.build().getClientName().orElse(null));
    }

    @Test
    public void testExplicitBlankAndUnsupportedConfigurationFailsClosed() {
        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyUsername(
                new RedisStandaloneConfiguration(), " "));
        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyPassword(
                new RedisStandaloneConfiguration(), " "));
        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyClientName(
                LettuceClientConfiguration.builder(), " "));
        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyUsername(
                new Object(), "route-user"));
        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyPassword(
                new Object(), "OPAQUE-CREDENTIAL-CONTENT"));
        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyClientName(
                new Object(), "route-client"));
    }

    @Test
    public void testAuthenticationSetterFailureDoesNotExposeCredential() {
        RedisConfiguration.WithAuthentication configuration = mock(RedisConfiguration.WithAuthentication.class);
        doThrow(new RuntimeException("OPAQUE-CREDENTIAL-CONTENT"))
                .when(configuration).setPassword(any(RedisPassword.class));

        assertConfigurationRejected(() -> RedisConfigurationCompatibilityHelper.applyPassword(
                configuration, "OPAQUE-CREDENTIAL-CONTENT"));
    }

    private void assertConfigurationRejected(org.junit.jupiter.api.function.Executable action) {
        ConfigurationException exception = assertThrows(ConfigurationException.class, action);
        assertEquals(ErrorCode.REDIS_ROUTE_006, exception.getErrorCode());
        assertFalse(exception.getMessage().contains("OPAQUE-CREDENTIAL-CONTENT"));
        assertFalse(exception.getMessage().contains("route-user"));
        assertFalse(exception.getMessage().contains("route-client"));
        assertNull(exception.getCause());
    }
}
