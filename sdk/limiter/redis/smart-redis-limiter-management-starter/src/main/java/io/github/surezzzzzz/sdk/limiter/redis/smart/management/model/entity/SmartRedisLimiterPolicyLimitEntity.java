package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 限流策略窗口数据库实体
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterPolicyLimitEntity {

    private Long id;
    private Long policyId;
    private Integer sortOrder;
    private Long count;
    private Long window;
    private String unit;
    private Long windowSeconds;
    private Instant createdAt;
    private Instant updatedAt;
}
