package io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery;

import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.RedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Redis 字面量前缀 key 发现执行器。
 *
 * @author surezzzzzz
 */
public class RedisKeyDiscoveryExecutor extends AbstractMiddlewareOpsExecutor<RedisKeyDiscoveryRequest,
        RedisKeyDiscoveryResponse> {

    private final RedisOperationsViewAdapter adapter;

    /**
     * 创建 Redis key 发现执行器。
     *
     * @param adapter Redis 受控运维视图适配器
     */
    public RedisKeyDiscoveryExecutor(RedisOperationsViewAdapter adapter) {
        super(RedisKeyDiscoveryRequest.class);
        this.adapter = adapter;
    }

    @Override
    public RedisKeyDiscoveryResponse execute(RedisKeyDiscoveryRequest request) {
        return adapter.discoverKeys(request);
    }
}
