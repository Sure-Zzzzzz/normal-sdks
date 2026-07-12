package io.github.surezzzzzz.sdk.redis.route.support;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 反射工具类，集中 Spring Data Redis 跨版本兼容所需的反射操作
 *
 * @author surezzzzzz
 */
@Slf4j
public final class RedisReflectionHelper {

    private RedisReflectionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 在指定类上按方法名查找第一个匹配的 public 方法，找不到返回 null
     */
    public static Method findMethod(Class<?> clazz, String methodName) {
        if (clazz == null || methodName == null) {
            return null;
        }
        for (Method method : clazz.getMethods()) {
            if (methodName.equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    /**
     * 在指定类上查找带精确参数类型的 public 方法，找不到返回 null
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null || methodName == null) {
            return null;
        }
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 在指定实例上调用方法，调用失败时返回 null 并记录 warn。
     * 只记录异常类型，不记录消息，避免 Redis 客户端异常中可能携带的凭证/主机信息泄露。
     */
    public static Object invoke(Object target, Method method, Object... args) {
        if (target == null || method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            log.warn("反射调用失败 [{}#{}]，异常类型=[{}]",
                    target.getClass().getName(), method.getName(), e.getClass().getSimpleName());
            return null;
        }
    }
}
