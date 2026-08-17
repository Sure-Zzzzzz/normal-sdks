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
     * 在指定类上按方法名查找第一个匹配的 public 方法。
     *
     * @param clazz      待查找的类型
     * @param methodName 方法名
     * @return 首个同名 public 方法；参数为空或未找到时返回 null
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
     * 在指定类上按精确参数类型查找 public 方法。
     *
     * @param clazz          待查找的类型
     * @param methodName     方法名
     * @param parameterTypes 参数类型
     * @return 匹配的 public 方法；参数为空或未找到时返回 null
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
     * 在指定实例上调用方法。
     * 调用失败时返回 null 并记录 warn，日志只记录异常类型，避免 Redis 客户端异常可能携带的连接信息泄露。
     *
     * @param target 调用目标
     * @param method 待调用的方法
     * @param args   调用参数
     * @return 方法返回值；目标、方法为空或调用失败时返回 null
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
