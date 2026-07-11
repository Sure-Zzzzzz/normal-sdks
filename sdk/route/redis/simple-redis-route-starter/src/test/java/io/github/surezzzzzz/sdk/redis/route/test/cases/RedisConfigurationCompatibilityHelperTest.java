package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.support.RedisConfigurationCompatibilityHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 配置兼容 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisConfigurationCompatibilityHelperTest {

    @Test
    public void testBlankValueSkipped() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        RedisConfigurationCompatibilityHelper.applyUsername(configuration, " ");
        RedisConfigurationCompatibilityHelper.applyPassword(configuration, " ");
        assertUsernameBlank(configuration);
        assertFalse(configuration.getPassword().isPresent());
    }

    @Test
    public void testApplyPassword() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        RedisConfigurationCompatibilityHelper.applyPassword(configuration, "mock-password");
        assertTrue(configuration.getPassword().isPresent());
    }

    @Test
    public void testMissingMethodDoesNotThrow() {
        Object target = new Object();
        assertDoesNotThrow(() -> RedisConfigurationCompatibilityHelper.applyClientName(target, "mock-client"));
    }

    private void assertUsernameBlank(RedisStandaloneConfiguration configuration) {
        Method method = ReflectionUtils.findMethod(configuration.getClass(), "getUsername");
        if (method == null) {
            return;
        }
        ReflectionUtils.makeAccessible(method);
        assertNull(ReflectionUtils.invokeMethod(method, configuration));
    }
}
