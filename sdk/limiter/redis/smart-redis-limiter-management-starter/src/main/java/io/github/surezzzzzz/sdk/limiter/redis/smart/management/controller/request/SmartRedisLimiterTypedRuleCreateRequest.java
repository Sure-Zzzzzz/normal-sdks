package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import lombok.Data;

import java.util.List;

/**
 * v2 类型化规则创建请求：身份七字段平铺 + 启停 + 窗口
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterTypedRuleCreateRequest {

    private String serviceCode;
    private String resourceCode;
    private String dimension;
    private String selector;
    private String namespace;
    private String customType;
    private String objectId;
    private Boolean enabled;
    private List<SmartRedisLimiterLimit> limits;
}
