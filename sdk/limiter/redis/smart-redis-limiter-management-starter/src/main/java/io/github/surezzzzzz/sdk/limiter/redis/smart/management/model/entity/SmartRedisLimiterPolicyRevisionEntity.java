package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 服务级策略 revision 数据库实体
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterPolicyRevisionEntity {

    private String serviceCode;
    private Long revision;
    private Instant publishedAt;
}
