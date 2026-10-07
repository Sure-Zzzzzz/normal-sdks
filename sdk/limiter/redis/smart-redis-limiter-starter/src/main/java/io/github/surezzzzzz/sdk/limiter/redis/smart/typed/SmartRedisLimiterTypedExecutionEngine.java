package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionPlan;

/**
 * 类型化唯一执行引擎
 * <p>类型化模式的唯一建计划与扣减入口：门禁按固定顺序串行执行，任一拒绝立即停止，
 * 先前门禁已取得的额度不退还；同桶多窗口原子性由算法在同一桶 Key 上保证。
 * Redis 故障按宿主显式配置执行：allow 只放过当前门禁并继续后续门禁、不绕过资格检查，
 * deny 返回 503 语义、不冒充额度耗尽；Redis 超时是否已扣减未知，不自动重试或承诺退还。</p>
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterTypedExecutionEngine {

    private final SmartRedisLimiterProperties properties;
    private final SmartRedisLimiterAlgorithmFactory algorithmFactory;

    /**
     * 构造类型化执行引擎
     *
     * @param properties       限流器配置
     * @param algorithmFactory 算法工厂
     */
    public SmartRedisLimiterTypedExecutionEngine(SmartRedisLimiterProperties properties,
                                                 SmartRedisLimiterAlgorithmFactory algorithmFactory) {
        this.properties = properties;
        this.algorithmFactory = algorithmFactory;
    }

    /**
     * 执行一次类型化计划
     *
     * @param plan        不可变执行计划
     * @param context     限流上下文
     * @param resourceCode 稳定资源编码
     * @param keyStrategy Key 生成策略
     * @return 执行结果
     */
    public SmartRedisLimiterTypedExecutionOutcome execute(SmartRedisLimiterTypedExecutionPlan plan,
                                                          SmartRedisLimiterContext context,
                                                          String resourceCode,
                                                          String keyStrategy) {
        if (plan.getRejection() != null) {
            return new SmartRedisLimiterTypedExecutionOutcome(
                    outcomeOf(plan.getRejection()), plan.getRejection().getDimension(), null, 0);
        }

        String algorithm = properties.getTyped().getAlgorithm();
        String fallback = properties.getTyped().getRedisDegradation();
        int acquired = 0;
        for (SmartRedisLimiterTypedGateRule gate : plan.getGates()) {
            SmartRedisLimiterExecutionPlan executionPlan = new SmartRedisLimiterExecutionPlan(
                    gate.getLimits(), gate.getRedisKey(), gate.getRedisKey(), algorithm, fallback,
                    resourceCode, gate.isRemote()
                    ? SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE
                    : SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, null);
            SmartRedisLimiterResult result = algorithmFactory.getAlgorithm(algorithm)
                    .tryAcquireWithResult(context, executionPlan, keyStrategy);
            if (result.isPassed()) {
                acquired++;
                continue;
            }
            if (result.isFallback()) {
                return new SmartRedisLimiterTypedExecutionOutcome(
                        SmartRedisLimiterTypedExecutionStatus.DEGRADE_DENIED,
                        gate.getDimension(), result, acquired);
            }
            return new SmartRedisLimiterTypedExecutionOutcome(
                    SmartRedisLimiterTypedExecutionStatus.LIMIT_EXCEEDED,
                    gate.getDimension(), result, acquired);
        }
        return new SmartRedisLimiterTypedExecutionOutcome(
                SmartRedisLimiterTypedExecutionStatus.PASSED, null, null, acquired);
    }

    private static SmartRedisLimiterTypedExecutionStatus outcomeOf(SmartRedisLimiterTypedRejection rejection) {
        if (rejection.getStatus() == SmartRedisLimiterTypedFactStatus.DENIED) {
            return SmartRedisLimiterTypedExecutionStatus.ACCESS_DENIED;
        }
        if ("duplicate-bucket".equals(rejection.getReason())) {
            return SmartRedisLimiterTypedExecutionStatus.DUPLICATE_BUCKET;
        }
        return SmartRedisLimiterTypedExecutionStatus.UNAVAILABLE;
    }
}
