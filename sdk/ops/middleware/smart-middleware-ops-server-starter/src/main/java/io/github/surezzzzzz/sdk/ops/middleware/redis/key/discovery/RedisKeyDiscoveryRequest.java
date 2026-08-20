package io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Redis 字面量前缀 key 发现请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisKeyDiscoveryRequest implements MiddlewareOpsRequest {

    private final String datasourceKey;
    private final String prefix;
    private final int size;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.REDIS_KEY_DISCOVERY;
    }

    @Override
    public String getResourceScope() {
        return "key-discovery";
    }
}
