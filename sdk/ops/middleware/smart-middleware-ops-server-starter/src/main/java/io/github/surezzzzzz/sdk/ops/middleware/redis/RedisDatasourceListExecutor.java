package io.github.surezzzzzz.sdk.ops.middleware.redis;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Redis 数据源清单执行器。
 *
 * @author surezzzzzz
 */
public class RedisDatasourceListExecutor
        extends AbstractMiddlewareOpsExecutor<RedisDatasourceListRequest, RedisDatasourceListResponse> {

    private final RedisOperationsViewAdapter adapter;

    /**
     * 创建执行器。
     *
     * @param adapter Redis Route 安全适配器
     */
    public RedisDatasourceListExecutor(RedisOperationsViewAdapter adapter) {
        super(RedisDatasourceListRequest.class);
        this.adapter = adapter;
    }

    @Override
    public RedisDatasourceListResponse execute(RedisDatasourceListRequest request) {
        return adapter.listDatasources();
    }
}
