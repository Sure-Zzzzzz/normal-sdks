package io.github.surezzzzzz.sdk.redis.route.factory;

import io.github.surezzzzzz.sdk.redis.route.support.RedisClusterPoolShutdownHelper;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 连接池 Redis Cluster 连接工厂
 *
 * @author surezzzzzz
 */
final class PooledClusterLettuceConnectionFactory extends LettuceConnectionFactory {

    PooledClusterLettuceConnectionFactory(RedisClusterConfiguration clusterConfiguration,
                                          LettuceClientConfiguration clientConfiguration) {
        super(clusterConfiguration, clientConfiguration);
    }

    @Override
    public void destroy() {
        RedisClusterPoolShutdownHelper.destroyClusterCommandExecutor(this);
        super.destroy();
    }
}
