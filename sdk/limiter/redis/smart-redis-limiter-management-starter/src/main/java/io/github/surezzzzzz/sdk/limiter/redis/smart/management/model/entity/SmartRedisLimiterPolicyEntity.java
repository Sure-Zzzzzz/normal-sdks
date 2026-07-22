package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 限流策略数据库实体
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterPolicyEntity {

    private Long id;
    private String serviceCode;
    private String resourceCode;
    private String subject;
    private Boolean enabled;
    private Long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private List<SmartRedisLimiterPolicyLimitEntity> limits = new ArrayList<>();
}
