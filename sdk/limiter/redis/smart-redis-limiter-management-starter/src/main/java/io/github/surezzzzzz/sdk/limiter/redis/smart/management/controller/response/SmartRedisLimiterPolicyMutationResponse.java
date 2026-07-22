package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.Builder;
import lombok.Getter;

/**
 * 限流策略变更响应
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterPolicyMutationResponse {

    private SmartRedisLimiterPolicyResponse policy;
    private SmartRedisLimiterPolicyKey deletedPolicyKey;
    private Long revision;
    private Boolean changed;
}
