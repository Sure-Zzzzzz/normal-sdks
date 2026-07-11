package io.github.surezzzzzz.sdk.redis.route.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Spring Data Redis 配置兼容 Helper
 *
 * @author surezzzzzz
 */
@Slf4j
public final class RedisConfigurationCompatibilityHelper {

    private RedisConfigurationCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void applyUsername(Object redisConfiguration, String username) {
        if (!RedisRouteStringHelper.hasText(username)) {
            return;
        }
        invokeOptional(redisConfiguration, "setUsername", new Class<?>[]{String.class}, new Object[]{username});
    }

    public static void applyPassword(Object redisConfiguration, String password) {
        if (!RedisRouteStringHelper.hasText(password)) {
            return;
        }
        if (invokeIfPresent(redisConfiguration, "setPassword", new Class<?>[]{RedisPassword.class},
                new Object[]{RedisPassword.of(password)})) {
            return;
        }
        if (!invokeIfPresent(redisConfiguration, "setPassword", new Class<?>[]{String.class}, new Object[]{password})) {
            log.warn("当前 Spring Data Redis 版本不支持方法 [{}#{}]，跳过配置", redisConfiguration.getClass().getName(), "setPassword");
        }
    }

    public static void applyClientName(Object clientConfigurationBuilder, String clientName) {
        if (!RedisRouteStringHelper.hasText(clientName)) {
            return;
        }
        invokeOptional(clientConfigurationBuilder, "clientName", new Class<?>[]{String.class}, new Object[]{clientName});
    }

    private static boolean invokeOptional(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (invokeIfPresent(target, methodName, parameterTypes, args)) {
            return true;
        }
        if (target != null) {
            log.warn("当前 Spring Data Redis 版本不支持方法 [{}#{}]，跳过配置", target.getClass().getName(), methodName);
        }
        return false;
    }

    private static boolean invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (target == null) {
            return false;
        }
        Method method = ReflectionUtils.findMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return false;
        }
        try {
            ReflectionUtils.makeAccessible(method);
            ReflectionUtils.invokeMethod(method, target, args);
            return true;
        } catch (Exception e) {
            log.warn("调用 Spring Data Redis 兼容方法失败 [{}#{}]，跳过配置", target.getClass().getName(), methodName, e);
            return false;
        }
    }
}
