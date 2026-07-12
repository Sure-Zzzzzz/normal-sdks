package io.github.surezzzzzz.sdk.redis.route.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Spring Data Redis 跨版本命令兼容 Helper（Spring Boot 2.2.x ~ 2.7.x）
 *
 * <p>封装因 Spring Data Redis API 差异需要的反射适配，集中在此类处理，
 * 不散落在各 probe/helper 实现中。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class RedisSpringDataCompatibilityHelper {

    private RedisSpringDataCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 执行 INFO server 命令，返回原始多行文本。
     *
     * <p>通过 serverCommands().info("server") 获取 Properties 再转文本，
     * 兼容 Spring Data Redis 2.x 各版本（info(String) 返回类型一致为 Properties）。
     * 连接获取异常向上抛出，由调用方统一 sanitized 记录，避免凭证/主机信息泄露。
     *
     * @return INFO server 原始多行文本；连接为 null 或 INFO 命令不可用时返回 null
     */
    public static String infoServer(RedisConnectionFactory connectionFactory) {
        Object connection = connectionFactory.getConnection();
        if (connection == null) {
            log.warn("获取 Redis 连接失败，connectionFactory=[{}]", connectionFactory.getClass().getSimpleName());
            return null;
        }
        try {
            return invokeInfoServer(connection);
        } finally {
            closeConnection(connection);
        }
    }

    /**
     * 在 RedisConnection 上调用 INFO server：
     * 优先 serverCommands().info("server")，回退到 connection.info("server")
     */
    private static String invokeInfoServer(Object connection) {
        Method serverCommandsMethod = RedisReflectionHelper.findMethod(connection.getClass(), "serverCommands");
        if (serverCommandsMethod != null) {
            Object serverCommands = RedisReflectionHelper.invoke(connection, serverCommandsMethod);
            if (serverCommands != null) {
                String result = invokeInfoOnTarget(serverCommands, "server");
                if (result != null) {
                    return result;
                }
            }
        }
        return invokeInfoOnTarget(connection, "server");
    }

    /**
     * 在目标对象上调用 info(String)，把返回的 Properties 转为多行文本
     */
    private static String invokeInfoOnTarget(Object target, String section) {
        Method infoMethod = RedisReflectionHelper.findMethod(target.getClass(), "info", String.class);
        if (infoMethod == null) {
            return null;
        }
        Object result = RedisReflectionHelper.invoke(target, infoMethod, section);
        if (result == null) {
            return null;
        }
        return propertiesToString(result);
    }

    /**
     * 把 java.util.Properties 转为多行文本
     */
    private static String propertiesToString(Object result) {
        if (result instanceof Properties) {
            Properties props = (Properties) result;
            StringBuilder sb = new StringBuilder();
            for (String name : props.stringPropertyNames()) {
                sb.append(name).append(":").append(props.getProperty(name)).append("\n");
            }
            return sb.toString();
        }
        log.warn("INFO server 返回值类型未知: [{}]", result.getClass().getName());
        return null;
    }

    /**
     * 关闭 Redis 连接（兼容 Closeable / 反射 close()）
     */
    private static void closeConnection(Object connection) {
        if (connection instanceof java.io.Closeable) {
            try {
                ((java.io.Closeable) connection).close();
            } catch (Exception e) {
                log.warn("关闭 Redis 连接失败，异常类型=[{}]", e.getClass().getSimpleName());
            }
            return;
        }
        Method closeMethod = RedisReflectionHelper.findMethod(connection.getClass(), "close");
        if (closeMethod != null) {
            RedisReflectionHelper.invoke(connection, closeMethod);
        }
    }
}
