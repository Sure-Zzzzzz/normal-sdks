package io.github.surezzzzzz.sdk.ops.middleware.redis;

import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Redis 数据源摘要执行器。
 *
 * @author surezzzzzz
 */
public class RedisSummaryExecutor
        extends AbstractMiddlewareOpsExecutor<RedisSummaryRequest, RedisDatasourceResponse> {

    private final RedisOperationsViewAdapter adapter;

    /**
     * 创建执行器。
     *
     * @param adapter Redis Route 安全适配器
     */
    public RedisSummaryExecutor(RedisOperationsViewAdapter adapter) {
        super(RedisSummaryRequest.class);
        this.adapter = adapter;
    }

    @Override
    public RedisDatasourceResponse execute(RedisSummaryRequest request) {
        return adapter.getSummary(request.getDatasourceKey());
    }
}
