package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import lombok.Value;

/**
 * 已验证主体的页面权限与目标服务写能力，不暴露授权集合。
 */
@Value
public class SmartRedisLimiterPolicyCapabilitiesResponse {
    boolean pageAllowed;
    boolean canWrite;
}
