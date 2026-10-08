package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity;

import lombok.Data;

import java.time.Instant;

/**
 * v2 类型化规则窗口数据库实体
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterTypedRuleLimitEntity {

    private Long id;
    private Long ruleId;
    private Integer sortOrder;
    private Long count;
    private Long window;
    private String unit;
    private Long windowSeconds;
    private Instant createdAt;
    private Instant updatedAt;
}
