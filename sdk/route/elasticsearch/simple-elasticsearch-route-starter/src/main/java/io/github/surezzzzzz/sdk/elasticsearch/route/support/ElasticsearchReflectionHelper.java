package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ElasticsearchReflectionException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Elasticsearch 反射兼容 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchReflectionHelper {

    private ElasticsearchReflectionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isClassPresent(String className) {
        return findClass(className) != null;
    }

    public static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Class<?> loadClass(String className) {
        Class<?> targetClass = findClass(className);
        if (targetClass == null) {
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_CLASS_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_CLASS_NOT_FOUND, className));
        }
        return targetClass;
    }

    public static Class<?> loadFirstPresentClass(String... classNames) {
        if (classNames != null) {
            for (String className : classNames) {
                Class<?> targetClass = findClass(className);
                if (targetClass != null) {
                    return targetClass;
                }
            }
        }
        throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_CLASS_NOT_FOUND,
                String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_CLASS_NOT_FOUND, joinClassNames(classNames)));
    }

    public static Method findMethod(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        if (targetClass == null) {
            return null;
        }
        try {
            return targetClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Method loadMethod(Class<?> targetClass, String methodName, Class<?>... parameterTypes) {
        Method method = findMethod(targetClass, methodName, parameterTypes);
        if (method == null) {
            String className = targetClass == null ? "null" : targetClass.getName();
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_METHOD_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_METHOD_NOT_FOUND, className, methodName));
        }
        return method;
    }

    public static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Exception e) {
            String methodName = method == null ? "null" : method.getName();
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_INVOKE_FAILED,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_INVOKE_FAILED, methodName), e);
        }
    }

    public static Object getStaticField(Class<?> targetClass, String fieldName) {
        return getFieldValue(targetClass, null, fieldName);
    }

    public static Object getField(Object target, String fieldName) {
        if (target == null) {
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_FIELD_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_FIELD_NOT_FOUND, "null", fieldName));
        }
        return getFieldValue(target.getClass(), target, fieldName);
    }

    public static Constructor<?> findConstructor(Class<?> targetClass, Class<?>... parameterTypes) {
        if (targetClass == null) {
            return null;
        }
        try {
            return targetClass.getConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Constructor<?> loadConstructor(Class<?> targetClass, Class<?>... parameterTypes) {
        Constructor<?> constructor = findConstructor(targetClass, parameterTypes);
        if (constructor == null) {
            String className = targetClass == null ? "null" : targetClass.getName();
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_CONSTRUCTOR_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_CONSTRUCTOR_NOT_FOUND, className));
        }
        return constructor;
    }

    public static Object newInstance(Constructor<?> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (Exception e) {
            String className = constructor == null ? "null" : constructor.getDeclaringClass().getName();
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_INVOKE_FAILED,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_INVOKE_FAILED, className), e);
        }
    }

    private static Object getFieldValue(Class<?> targetClass, Object target, String fieldName) {
        try {
            Field field = targetClass.getField(fieldName);
            return field.get(target);
        } catch (Exception e) {
            String className = targetClass == null ? "null" : targetClass.getName();
            throw new ElasticsearchReflectionException(ErrorCode.ROUTE_COMPAT_REFLECT_FIELD_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_COMPAT_REFLECT_FIELD_NOT_FOUND, className, fieldName), e);
        }
    }

    private static String joinClassNames(String... classNames) {
        if (classNames == null || classNames.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String className : classNames) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(className);
        }
        return builder.toString();
    }
}
