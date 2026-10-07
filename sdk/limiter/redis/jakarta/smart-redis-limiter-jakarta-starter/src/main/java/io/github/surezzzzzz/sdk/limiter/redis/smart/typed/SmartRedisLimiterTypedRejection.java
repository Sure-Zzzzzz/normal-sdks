package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 类型化执行计划扣减前拒绝
 * <p>事实明确拒绝（403 语义）、事实不可用（503 语义）或同桶重复（配置错误）
 * 在任何扣减前发生，先到的额度不会被取得。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedRejection {

    /**
     * 拒绝状态
     */
    private final SmartRedisLimiterTypedFactStatus status;

    /**
     * 触发拒绝的维度
     */
    private final SmartRedisLimiterDataDimension dimension;

    /**
     * 受控原因
     */
    private final String reason;

    /**
     * 构造扣减前拒绝
     *
     * @param status    拒绝状态（DENIED / UNAVAILABLE）
     * @param dimension 触发维度
     * @param reason    受控原因
     */
    public SmartRedisLimiterTypedRejection(SmartRedisLimiterTypedFactStatus status,
                                           SmartRedisLimiterDataDimension dimension,
                                           String reason) {
        this.status = status;
        this.dimension = dimension;
        this.reason = reason;
    }
}
