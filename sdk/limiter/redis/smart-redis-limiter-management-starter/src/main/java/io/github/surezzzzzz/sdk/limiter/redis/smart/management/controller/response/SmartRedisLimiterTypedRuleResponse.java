package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * v2 类型化规则视图响应
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterTypedRuleResponse {

    private Long id;
    private String serviceCode;
    private String resourceCode;
    private String dimension;
    private String selector;
    private String namespace;
    private String customType;
    private String objectId;
    private Boolean enabled;
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private List<SmartRedisLimiterLimit> limits;
}
