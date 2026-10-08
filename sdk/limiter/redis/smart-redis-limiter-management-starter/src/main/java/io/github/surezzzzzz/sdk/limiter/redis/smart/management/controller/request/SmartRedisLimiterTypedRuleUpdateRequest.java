package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import lombok.Data;

import java.util.List;

/**
 * v2 类型化规则整体替换窗口请求（身份不可原地改写）
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterTypedRuleUpdateRequest {

    private Long expectedRowVersion;
    private List<SmartRedisLimiterLimit> limits;
}
