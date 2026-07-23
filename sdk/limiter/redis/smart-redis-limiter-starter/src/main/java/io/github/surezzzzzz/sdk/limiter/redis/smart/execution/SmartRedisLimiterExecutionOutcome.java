package io.github.surezzzzzz.sdk.limiter.redis.smart.execution;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import lombok.Getter;

/**
 * 统一限流执行结果
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterExecutionOutcome {

    private final SmartRedisLimiterExecutionPlan plan;
    private final SmartRedisLimiterResult result;

    /**
     * 构造统一限流执行结果
     *
     * @param plan   请求执行计划
     * @param result 算法执行结果
     */
    public SmartRedisLimiterExecutionOutcome(SmartRedisLimiterExecutionPlan plan,
                                             SmartRedisLimiterResult result) {
        this.plan = plan;
        this.result = result;
    }
}
