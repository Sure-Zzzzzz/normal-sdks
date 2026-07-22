package io.github.surezzzzzz.sdk.limiter.redis.smart.management.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyLimitEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Management 数据模型转换 Helper
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterManagementMapper {

    private SmartRedisLimiterManagementMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 数据库实体转换为 core Policy
     */
    public static SmartRedisLimiterPolicy toCorePolicy(SmartRedisLimiterPolicyEntity entity) {
        return new SmartRedisLimiterPolicy(
                new SmartRedisLimiterPolicyKey(
                        entity.getServiceCode(), entity.getResourceCode(), entity.getSubject()),
                toCoreLimits(entity.getLimits()));
    }

    /**
     * 数据库窗口转换为 core limits
     */
    public static List<SmartRedisLimiterLimit> toCoreLimits(
            List<SmartRedisLimiterPolicyLimitEntity> entities) {
        List<SmartRedisLimiterLimit> limits = new ArrayList<>();
        for (SmartRedisLimiterPolicyLimitEntity entity : entities) {
            limits.add(new SmartRedisLimiterLimit(
                    entity.getCount(), entity.getWindow(),
                    SmartRedisLimiterTimeUnit.fromCode(entity.getUnit())));
        }
        return limits;
    }

    /**
     * 数据库实体转换为管理响应
     */
    public static SmartRedisLimiterPolicyResponse toResponse(SmartRedisLimiterPolicyEntity entity) {
        SmartRedisLimiterPolicy policy = toCorePolicy(entity);
        return SmartRedisLimiterPolicyResponse.builder()
                .id(entity.getId())
                .key(policy.getKey())
                .limits(policy.getLimits())
                .enabled(entity.getEnabled())
                .rowVersion(entity.getRowVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
