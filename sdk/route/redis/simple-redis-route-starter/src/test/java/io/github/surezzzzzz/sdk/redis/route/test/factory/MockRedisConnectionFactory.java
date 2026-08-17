package io.github.surezzzzzz.sdk.redis.route.test.factory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;

/**
 * 测试用 RedisConnectionFactory
 *
 * @author surezzzzzz
 */
public class MockRedisConnectionFactory implements RedisConnectionFactory, DisposableBean {

    private final String datasourceKey;
    private int destroyCount;

    public MockRedisConnectionFactory(String datasourceKey) {
        this.datasourceKey = datasourceKey;
    }

    @Override
    public RedisConnection getConnection() {
        throw new UnsupportedOperationException("测试工厂不创建真实 Redis 连接: " + datasourceKey);
    }

    @Override
    public RedisClusterConnection getClusterConnection() {
        throw new UnsupportedOperationException("测试工厂不创建真实 Redis Cluster 连接: " + datasourceKey);
    }

    @Override
    public boolean getConvertPipelineAndTxResults() {
        return true;
    }

    @Override
    public RedisSentinelConnection getSentinelConnection() {
        throw new UnsupportedOperationException("测试工厂不创建接口占位连接: " + datasourceKey);
    }

    @Override
    public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
        return null;
    }

    @Override
    public void destroy() {
        destroyCount++;
    }

    public boolean isDestroyed() {
        return destroyCount > 0;
    }

    public int getDestroyCount() {
        return destroyCount;
    }

    public String getDatasourceKey() {
        return datasourceKey;
    }
}
