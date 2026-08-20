package io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata;

import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.RedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;

/**
 * Redis 精确 key 元数据执行器。
 *
 * @author surezzzzzz
 */
public class RedisKeyMetadataExecutor
        extends AbstractMiddlewareOpsExecutor<RedisKeyMetadataRequest, RedisKeyMetadataResponse> {

    private final RedisOperationsViewAdapter adapter;

    public RedisKeyMetadataExecutor(RedisOperationsViewAdapter adapter) {
        super(RedisKeyMetadataRequest.class);
        this.adapter = adapter;
    }

    @Override
    public RedisKeyMetadataResponse execute(RedisKeyMetadataRequest request) {
        return adapter.getKeyMetadata(request);
    }
}
