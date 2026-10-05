package io.github.surezzzzzz.sdk.limiter.redis.smart.execution;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 请求级限流执行计划
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterExecutionPlan {

    private final List<SmartRedisLimiterProperties.SmartLimitRule> limits;
    private final String baseKey;
    private final String routeKey;
    private final String algorithm;
    private final String fallback;
    private final String resourceCode;
    private final String policySource;
    private final Long policyRevision;

    /**
     * 构造请求级限流执行计划
     *
     * @param limits         最终完整限额列表
     * @param baseKey        Redis 基础 Key
     * @param routeKey       Redis Route Key
     * @param algorithm      限流算法
     * @param fallback       降级策略
     * @param resourceCode   稳定资源编码
     * @param policySource   策略来源
     * @param policyRevision 远程策略版本
     */
    public SmartRedisLimiterExecutionPlan(
            List<SmartRedisLimiterProperties.SmartLimitRule> limits,
            String baseKey,
            String routeKey,
            String algorithm,
            String fallback,
            String resourceCode,
            String policySource,
            Long policyRevision) {
        this.limits = Collections.unmodifiableList(new ArrayList<>(limits));
        this.baseKey = baseKey;
        this.routeKey = routeKey;
        this.algorithm = algorithm;
        this.fallback = fallback;
        this.resourceCode = resourceCode;
        this.policySource = policySource;
        this.policyRevision = policyRevision;
    }
}
