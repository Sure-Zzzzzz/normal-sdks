package io.github.surezzzzzz.sdk.ops.middleware.redis.key.read;

import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.RedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Redis 精确 key 类型化读取执行器。
 *
 * @author surezzzzzz
 */
public class RedisKeyReadExecutor extends AbstractMiddlewareOpsExecutor<RedisKeyReadRequest, RedisKeyReadResponse> {

    private final RedisOperationsViewAdapter adapter;

    /**
     * 创建 Redis key 读取执行器。
     *
     * @param adapter Redis Route 安全适配器
     */
    public RedisKeyReadExecutor(RedisOperationsViewAdapter adapter) {
        super(RedisKeyReadRequest.class);
        this.adapter = adapter;
    }

    @Override
    public RedisKeyReadResponse execute(RedisKeyReadRequest request) {
        return adapter.readKey(request);
    }
}
