package io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Redis 字面量前缀 key 发现安全响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisKeyDiscoveryResponse {

    private final List<String> items;
    private final Integer limit;
    private final Integer returned;
    private final Boolean truncated;
    private final Boolean traversalComplete;
    private final String stopReason;
}
