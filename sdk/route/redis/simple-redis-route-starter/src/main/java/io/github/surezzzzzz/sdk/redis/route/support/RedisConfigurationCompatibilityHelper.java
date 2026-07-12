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

    private static final String METHOD_SET_USERNAME = "setUsername";
    private static final String METHOD_SET_PASSWORD = "setPassword";
    private static final String METHOD_CLIENT_NAME = "clientName";

    private static final Class<?>[] STRING_PARAMETER_TYPES = new Class<?>[]{String.class};
    private static final Class<?>[] REDIS_PASSWORD_PARAMETER_TYPES = new Class<?>[]{RedisPassword.class};

    private RedisConfigurationCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 兼容设置 Redis username。
     *
     * @param redisConfiguration Redis 配置对象
     * @param username           用户名
     */
    public static void applyUsername(Object redisConfiguration, String username) {
        if (!RedisRouteStringHelper.hasText(username)) {
            return;
        }
        invokeOptional(redisConfiguration, METHOD_SET_USERNAME, STRING_PARAMETER_TYPES, new Object[]{username});
    }

    /**
     * 兼容设置 Redis password。
     *
     * @param redisConfiguration Redis 配置对象
     * @param password           密码
     */
    public static void applyPassword(Object redisConfiguration, String password) {
        if (!RedisRouteStringHelper.hasText(password)) {
            return;
        }
        // Spring Data Redis 2.x 同时存在 RedisPassword 与 String 两类签名，优先使用不会泄露 toString 的 RedisPassword。
        if (invokeIfPresent(redisConfiguration, METHOD_SET_PASSWORD, REDIS_PASSWORD_PARAMETER_TYPES,
                new Object[]{RedisPassword.of(password)})) {
            return;
        }
        if (!invokeIfPresent(redisConfiguration, METHOD_SET_PASSWORD, STRING_PARAMETER_TYPES, new Object[]{password})) {
            log.warn("当前 Spring Data Redis 版本不支持方法 [{}#{}]，跳过配置", redisConfiguration.getClass().getName(), METHOD_SET_PASSWORD);
        }
    }

    /**
     * 兼容设置 Redis clientName。
     *
     * @param clientConfigurationBuilder 客户端配置 builder
     * @param clientName                 客户端名称
     */
    public static void applyClientName(Object clientConfigurationBuilder, String clientName) {
        if (!RedisRouteStringHelper.hasText(clientName)) {
            return;
        }
        invokeOptional(clientConfigurationBuilder, METHOD_CLIENT_NAME, STRING_PARAMETER_TYPES, new Object[]{clientName});
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
            log.warn("调用 Spring Data Redis 兼容方法失败 [{}#{}]，异常类型=[{}]，跳过配置",
                    target.getClass().getName(), methodName, e.getClass().getSimpleName());
            return false;
        }
    }
}
