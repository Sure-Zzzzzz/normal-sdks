package io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Redis 精确 key 元数据请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisKeyMetadataRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String key;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.REDIS_KEY_METADATA;
    }

    @Override
    public String getResourceScope() {
        return "key-metadata:" + key;
    }
}
