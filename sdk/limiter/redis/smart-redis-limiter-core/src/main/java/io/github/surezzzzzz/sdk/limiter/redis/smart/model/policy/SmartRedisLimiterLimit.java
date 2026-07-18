package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * SmartRedisLimiter 动态限额窗口
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class SmartRedisLimiterLimit {

    /**
     * 限流次数
     */
    @EqualsAndHashCode.Include
    private final long count;

    /**
     * 时间窗口
     */
    private final long window;

    /**
     * 时间单位
     */
    private final SmartRedisLimiterTimeUnit unit;

    /**
     * 标准化时间窗口秒数
     */
    @EqualsAndHashCode.Include
    private final long windowSeconds;

    /**
     * 构造动态限额窗口
     *
     * @param count  限流次数
     * @param window 时间窗口
     * @param unit   时间单位
     * @throws SmartRedisLimiterException 限额、窗口或时间单位非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterLimit(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_COUNT, required = true) Long count,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_WINDOW, required = true) Long window,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_UNIT, required = true)
            SmartRedisLimiterTimeUnit unit) {
        if (count == null) {
            throw invalidLimit(SmartRedisLimiterConstant.POLICY_FIELD_COUNT,
                    ErrorMessage.REASON_FIELD_REQUIRED);
        }
        if (window == null) {
            throw invalidLimit(SmartRedisLimiterConstant.POLICY_FIELD_WINDOW,
                    ErrorMessage.REASON_FIELD_REQUIRED);
        }
        if (count <= 0) {
            throw invalidLimit(SmartRedisLimiterConstant.POLICY_FIELD_COUNT, ErrorMessage.REASON_FIELD_MUST_BE_POSITIVE);
        }
        if (window <= 0) {
            throw invalidLimit(SmartRedisLimiterConstant.POLICY_FIELD_WINDOW, ErrorMessage.REASON_FIELD_MUST_BE_POSITIVE);
        }
        if (unit == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_TIME_UNIT_INVALID,
                    ErrorMessage.POLICY_TIME_UNIT_INVALID);
        }
        this.count = count;
        this.window = window;
        this.unit = unit;
        this.windowSeconds = unit.toSeconds(window);
    }

    /**
     * 获取标准化时间窗口秒数
     *
     * @return 时间窗口秒数
     */
    @JsonIgnore
    public long getWindowSeconds() {
        return windowSeconds;
    }

    private static SmartRedisLimiterException invalidLimit(String field, String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_LIMIT_INVALID,
                String.format(ErrorMessage.POLICY_LIMIT_INVALID, field, reason));
    }
}
