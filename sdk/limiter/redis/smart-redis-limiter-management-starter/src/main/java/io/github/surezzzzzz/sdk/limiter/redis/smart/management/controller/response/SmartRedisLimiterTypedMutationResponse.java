package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import lombok.Builder;
import lombok.Getter;

/**
 * v2 类型化规则变更响应：变更后的规则（删除时为 null）与服务 revision
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterTypedMutationResponse {

    private SmartRedisLimiterTypedRuleResponse rule;
    private Long revision;
}
