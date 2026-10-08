package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * v2 类型化限流规则数据库实体
 *
 * <p>身份七字段与 smart-redis-limiter-core 2.2.0 的
 * {@code SmartRedisLimiterTypedPolicyKey} 一一对应；数据库层不适用字段以空串存储，
 * 保证唯一索引可判重（MySQL 唯一索引对 NULL 不判重）。
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterTypedRuleEntity {

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
    private List<SmartRedisLimiterTypedRuleLimitEntity> limits = new ArrayList<>();
}
