package io.github.surezzzzzz.sdk.ops.middleware.redis;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Redis 精确 key 类型化读取请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisKeyReadRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String key;
    private final String field;
    private final long offset;
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.REDIS_KEY_READ;
    }

    @Override
    public String getResourceScope() {
        return "key-read:" + key;
    }
}
