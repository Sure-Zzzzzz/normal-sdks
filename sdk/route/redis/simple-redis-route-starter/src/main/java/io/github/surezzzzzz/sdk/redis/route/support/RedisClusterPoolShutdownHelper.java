package io.github.surezzzzzz.sdk.redis.route.support;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 旧版 Spring Data Redis Cluster 连接池关闭兼容处理
 *
 * @author surezzzzzz
 */
public final class RedisClusterPoolShutdownHelper {

    private static final String CLUSTER_COMMAND_EXECUTOR_FIELD = "clusterCommandExecutor";
    private static final String DESTROY_METHOD = "destroy";

    private RedisClusterPoolShutdownHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 在连接池关闭前释放 Cluster 命令执行器，避免旧版 Spring Data Redis 归还已关闭的连接池。
     *
     * @param connectionFactory 使用连接池的 Cluster 连接工厂
     */
    public static void destroyClusterCommandExecutor(LettuceConnectionFactory connectionFactory) {
        if (!(connectionFactory.getClientConfiguration() instanceof LettucePoolingClientConfiguration)) {
            return;
        }
        Field field = ReflectionUtils.findField(LettuceConnectionFactory.class, CLUSTER_COMMAND_EXECUTOR_FIELD);
        if (field == null) {
            return;
        }
        ReflectionUtils.makeAccessible(field);
        Object executor = ReflectionUtils.getField(field, connectionFactory);
        if (executor == null) {
            return;
        }
        Method destroyMethod = ReflectionUtils.findMethod(executor.getClass(), DESTROY_METHOD);
        if (destroyMethod == null) {
            return;
        }
        ReflectionUtils.makeAccessible(destroyMethod);
        ReflectionUtils.setField(field, connectionFactory, null);
        ReflectionUtils.invokeMethod(destroyMethod, executor);
    }
}
