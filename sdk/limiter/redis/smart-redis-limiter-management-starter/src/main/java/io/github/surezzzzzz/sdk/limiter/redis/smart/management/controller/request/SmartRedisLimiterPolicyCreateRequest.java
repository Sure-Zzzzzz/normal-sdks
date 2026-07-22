package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建限流策略请求
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterPolicyCreateRequest {

    private SmartRedisLimiterPolicyKey key;
    private List<SmartRedisLimiterLimit> limits = new ArrayList<>();
    private Boolean enabled;
}
