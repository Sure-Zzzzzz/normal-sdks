package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import lombok.Getter;

/**
 * 类型化执行结果
 * <p>状态语义与 HTTP 严格对应：LIMIT_EXCEEDED 即 429；ACCESS_DENIED 即 403；
 * UNAVAILABLE 与 DEGRADE_DENIED 即 503；DUPLICATE_BUCKET 是宿主配置错误，同样拒绝整次执行。
 * 首个实际拒绝门禁的窗口信息由首个拒绝结果携带，用于 Retry-After 与额度头。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterTypedExecutionOutcome {

    /**
     * 执行状态
     */
    private final SmartRedisLimiterTypedExecutionStatus status;

    /**
     * 首个拒绝门禁维度
     */
    private final SmartRedisLimiterDataDimension rejectedDimension;

    /**
     * 首个拒绝门禁的算法结果（额度耗尽时含窗口信息）
     */
    private final SmartRedisLimiterResult rejectedResult;

    /**
     * 已成功取得额度的门禁数
     */
    private final int acquiredCount;

    /**
     * 构造执行结果
     *
     * @param status            执行状态
     * @param rejectedDimension 首个拒绝门禁维度
     * @param rejectedResult    首个拒绝门禁算法结果
     * @param acquiredCount     已成功取得额度的门禁数
     */
    public SmartRedisLimiterTypedExecutionOutcome(SmartRedisLimiterTypedExecutionStatus status,
                                                  SmartRedisLimiterDataDimension rejectedDimension,
                                                  SmartRedisLimiterResult rejectedResult,
                                                  int acquiredCount) {
        this.status = status;
        this.rejectedDimension = rejectedDimension;
        this.rejectedResult = rejectedResult;
        this.acquiredCount = acquiredCount;
    }

    /**
     * 判断是否放行
     *
     * @return 全部门禁取得额度返回 true
     */
    public boolean isPassed() {
        return status == SmartRedisLimiterTypedExecutionStatus.PASSED;
    }
}
