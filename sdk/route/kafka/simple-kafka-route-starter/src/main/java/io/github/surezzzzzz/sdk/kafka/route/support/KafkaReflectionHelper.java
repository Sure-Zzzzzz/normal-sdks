package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Kafka route 反射兼容 Helper
 *
 * @author surezzzzzz
 */
@Slf4j
public final class KafkaReflectionHelper {

    private KafkaReflectionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断 class 是否存在
     *
     * @param className class 名称
     * @return true 表示存在
     */
    public static boolean isClassPresent(String className) {
        return findClass(className) != null;
    }

    /**
     * 查找 class，不存在时返回 null
     *
     * @param className class 名称
     * @return class 对象
     */
    public static Class<?> findClass(String className) {
        if (!KafkaRouteStringHelper.hasText(className)) {
            return null;
        }
        try {
            return Class.forName(className.trim());
        } catch (ClassNotFoundException e) {
            log.debug("Kafka route 兼容层未找到 class: {}", className);
            return null;
        }
    }

    /**
     * 加载 class，不存在时抛出兼容层异常
     *
     * @param className class 名称
     * @return class 对象
     */
    public static Class<?> loadClass(String className) {
        Class<?> clazz = findClass(className);
        if (clazz == null) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_014,
                    String.format(ErrorMessage.COMPAT_REFLECT_CLASS_NOT_FOUND, className));
        }
        return clazz;
    }

    /**
     * 查找方法，不存在时返回 null
     *
     * @param clazz      class 对象
     * @param name       方法名
     * @param paramTypes 参数类型
     * @return 方法对象
     */
    public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null || !KafkaRouteStringHelper.hasText(name)) {
            return null;
        }
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, paramTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        try {
            Method method = clazz.getMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            log.debug("Kafka route 兼容层未找到 method: {}#{}", clazz.getName(), name);
            return null;
        }
    }

    /**
     * 加载方法，不存在时抛出兼容层异常
     *
     * @param clazz      class 对象
     * @param name       方法名
     * @param paramTypes 参数类型
     * @return 方法对象
     */
    public static Method loadMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        Method method = findMethod(clazz, name, paramTypes);
        if (method == null) {
            String className = clazz == null ? null : clazz.getName();
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_014,
                    String.format(ErrorMessage.COMPAT_REFLECT_METHOD_NOT_FOUND, className, name));
        }
        return method;
    }

    /**
     * 调用反射方法
     *
     * @param method 方法对象
     * @param target 目标对象
     * @param args   参数
     * @return 调用结果
     */
    public static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_014,
                    String.format(ErrorMessage.COMPAT_REFLECT_METHOD_NOT_FOUND,
                            method.getDeclaringClass().getName(), method.getName()), e);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            }
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_014,
                    String.format(ErrorMessage.COMPAT_REFLECT_METHOD_NOT_FOUND,
                            method.getDeclaringClass().getName(), method.getName()), targetException);
        }
    }

    /**
     * 方法存在时调用，不存在时返回 null
     *
     * @param target     目标对象
     * @param methodName 方法名
     * @param args       参数
     * @return 调用结果
     */
    public static Object invokeIfPresent(Object target, String methodName, Object... args) {
        if (target == null || !KafkaRouteStringHelper.hasText(methodName)) {
            return null;
        }
        Method method = findCompatibleMethod(target.getClass(), methodName, args);
        if (method == null) {
            log.debug("Kafka route 兼容层跳过不存在的方法: {}#{}", target.getClass().getName(), methodName);
            return null;
        }
        return invoke(method, target, args);
    }

    private static Method findCompatibleMethod(Class<?> clazz, String methodName, Object[] args) {
        Class<?> current = clazz;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterTypes().length != args.length) {
                    continue;
                }
                if (isParameterCompatible(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            for (Class<?> iface : current.getInterfaces()) {
                for (Method method : iface.getMethods()) {
                    if (!method.getName().equals(methodName) || method.getParameterTypes().length != args.length) {
                        continue;
                    }
                    if (isParameterCompatible(method.getParameterTypes(), args)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isParameterCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) {
                continue;
            }
            if (!wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
