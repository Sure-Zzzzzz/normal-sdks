package io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata;

import lombok.Builder;
import lombok.Getter;

/**
 * Redis 精确 key 元数据安全响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisKeyMetadataResponse {

    private final boolean exists;
    private final String dataType;
    private final String ttlState;
    private final Long ttlSeconds;
}
