package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view;

import lombok.Builder;
import lombok.Getter;

/**
 * 限流策略查询条件
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterPolicyQuery {

    private String serviceCode;
    private String resourceCode;
    private String subject;
    private Boolean enabled;
    private Integer page;
    private Integer size;
}
