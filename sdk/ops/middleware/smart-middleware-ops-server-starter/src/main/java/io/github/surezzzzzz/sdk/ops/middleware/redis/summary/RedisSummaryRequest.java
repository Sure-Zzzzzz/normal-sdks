package io.github.surezzzzzz.sdk.ops.middleware.redis.summary;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import lombok.Builder;
import lombok.Getter;

/**
 * Redis 数据源安全摘要请求。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisSummaryRequest implements MiddlewareOpsRequest {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.REDIS_SUMMARY;
    }

    @Override
    public String getResourceScope() {
        return "datasource-summary";
    }
}
