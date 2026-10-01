package io.github.surezzzzzz.sdk.redis.route.factory;

import io.github.surezzzzzz.sdk.redis.route.support.RedisClusterPoolShutdownHelper;
import io.lettuce.core.resource.ClientResources;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 持有自建 ClientResources 的 Redis Cluster 连接工厂
 *
 * @author surezzzzzz
 */
final class NodesTopologyLettuceConnectionFactory extends LettuceConnectionFactory {

    private final ClientResources clientResources;
    private final long shutdownTimeoutMs;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    NodesTopologyLettuceConnectionFactory(RedisClusterConfiguration clusterConfiguration,
                                          LettuceClientConfiguration clientConfiguration,
                                          ClientResources clientResources,
                                          long shutdownTimeoutMs) {
        super(clusterConfiguration, clientConfiguration);
        this.clientResources = clientResources;
        this.shutdownTimeoutMs = shutdownTimeoutMs;
    }

    /**
     * 幂等关闭连接工厂及其专属 ClientResources。
     */
    @Override
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        try {
            RedisClusterPoolShutdownHelper.destroyClusterCommandExecutor(this);
            super.destroy();
        } finally {
            clientResources.shutdown(shutdownTimeoutMs, shutdownTimeoutMs, TimeUnit.MILLISECONDS)
                    .awaitUninterruptibly();
        }
    }
}
