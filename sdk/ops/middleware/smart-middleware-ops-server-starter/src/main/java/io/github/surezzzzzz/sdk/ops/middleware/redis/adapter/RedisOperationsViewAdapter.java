package io.github.surezzzzzz.sdk.ops.middleware.redis.adapter;

import io.github.surezzzzzz.sdk.ops.middleware.redis.datasource.RedisDatasourceListResponse;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata.RedisKeyMetadataRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata.RedisKeyMetadataResponse;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadResponse;
import io.github.surezzzzzz.sdk.ops.middleware.redis.summary.RedisDatasourceResponse;

/**
 * Redis 受控运维视图适配口。
 *
 * @author surezzzzzz
 */
public interface RedisOperationsViewAdapter {

    /**
     * 获取已注册 Redis 数据源的安全清单。
     *
     * @return 数据源清单
     */
    RedisDatasourceListResponse listDatasources();

    /**
     * 获取指定 Redis 数据源的安全摘要。
     *
     * @param datasourceKey 数据源标识
     * @return 数据源摘要
     */
    RedisDatasourceResponse getSummary(String datasourceKey);

    /**
     * 获取精确 key 的安全元数据。
     *
     * @param request 查询请求
     * @return key 元数据
     */
    RedisKeyMetadataResponse getKeyMetadata(RedisKeyMetadataRequest request);

    /**
     * 按字面量前缀发现受限 Redis key。
     *
     * @param request 查询请求
     * @return key 发现响应
     */
    RedisKeyDiscoveryResponse discoverKeys(RedisKeyDiscoveryRequest request);

    /**
     * 读取精确 key 的实际类型数据。
     *
     * @param request 查询请求
     * @return 类型化数据
     */
    RedisKeyReadResponse readKey(RedisKeyReadRequest request);
}
