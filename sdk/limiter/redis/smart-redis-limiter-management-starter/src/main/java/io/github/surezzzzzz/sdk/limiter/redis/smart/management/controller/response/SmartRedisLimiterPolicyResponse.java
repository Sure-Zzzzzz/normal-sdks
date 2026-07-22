package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 限流策略管理响应
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterPolicyResponse {

    private Long id;
    private SmartRedisLimiterPolicyKey key;
    private List<SmartRedisLimiterLimit> limits;
    private Boolean enabled;
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
