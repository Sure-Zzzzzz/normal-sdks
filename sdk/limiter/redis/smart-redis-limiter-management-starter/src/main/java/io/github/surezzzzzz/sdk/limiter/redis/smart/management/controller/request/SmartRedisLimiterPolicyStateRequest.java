package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request;

import lombok.Data;

/**
 * 限流策略状态变更请求
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterPolicyStateRequest {

    private Long expectedRowVersion;
    private Boolean enabled;
}
