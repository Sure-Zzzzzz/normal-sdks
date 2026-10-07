package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 类型化维度事实
 * <p>仅 PRESENT 状态携带稳定计数对象；对象原值保留，不做 trim 或归一化
 * （数字 IP 由提供方按数字地址规范化后给出）。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedDimensionFact {

    /**
     * 事实状态
     */
    private final SmartRedisLimiterTypedFactStatus status;

    /**
     * 稳定计数对象（仅 PRESENT 非空）
     */
    private final String objectId;

    private SmartRedisLimiterTypedDimensionFact(SmartRedisLimiterTypedFactStatus status, String objectId) {
        this.status = status;
        this.objectId = objectId;
    }

    /**
     * 构造有效事实
     *
     * @param objectId 稳定计数对象
     * @return PRESENT 事实
     */
    public static SmartRedisLimiterTypedDimensionFact present(String objectId) {
        if (objectId == null || objectId.trim().isEmpty()) {
            return new SmartRedisLimiterTypedDimensionFact(SmartRedisLimiterTypedFactStatus.UNAVAILABLE, null);
        }
        return new SmartRedisLimiterTypedDimensionFact(SmartRedisLimiterTypedFactStatus.PRESENT, objectId);
    }

    /**
     * 构造非 PRESENT 状态事实
     *
     * @param status 不适用、明确拒绝或不可用
     * @return 对应状态事实
     */
    public static SmartRedisLimiterTypedDimensionFact of(SmartRedisLimiterTypedFactStatus status) {
        if (status == null || status == SmartRedisLimiterTypedFactStatus.PRESENT) {
            return new SmartRedisLimiterTypedDimensionFact(SmartRedisLimiterTypedFactStatus.UNAVAILABLE, null);
        }
        return new SmartRedisLimiterTypedDimensionFact(status, null);
    }
}
