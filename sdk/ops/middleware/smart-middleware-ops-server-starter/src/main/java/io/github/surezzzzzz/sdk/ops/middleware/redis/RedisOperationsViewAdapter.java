package io.github.surezzzzzz.sdk.ops.middleware.redis;

/**
 * Redis Route 只读运维视图适配口。
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
     * 读取精确 key 的实际类型数据。
     *
     * @param request 查询请求
     * @return 类型化数据
     */
    RedisKeyReadResponse readKey(RedisKeyReadRequest request);
}
