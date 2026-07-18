package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.*;

/**
 * SmartRedisLimiter 动态策略
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterPolicy {

    /**
     * 策略键
     */
    private final SmartRedisLimiterPolicyKey key;

    /**
     * 完整限额窗口列表
     */
    private final List<SmartRedisLimiterLimit> limits;

    /**
     * 构造动态策略
     *
     * @param key    策略键
     * @param limits 完整限额窗口列表
     * @throws SmartRedisLimiterException 策略键或限额窗口非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterPolicy(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_KEY, required = true)
            SmartRedisLimiterPolicyKey key,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_LIMITS, required = true)
            List<SmartRedisLimiterLimit> limits) {
        if (key == null) {
            throw invalidPolicy(ErrorMessage.REASON_POLICY_KEY_REQUIRED);
        }
        if (limits == null || limits.isEmpty()) {
            throw invalidPolicy(ErrorMessage.REASON_POLICY_LIMIT_REQUIRED);
        }
        if (limits.size() > SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY) {
            throw invalidPolicy(String.format(ErrorMessage.REASON_POLICY_LIMIT_MAX_EXCEEDED,
                    SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY));
        }

        List<SmartRedisLimiterLimit> copiedLimits = new ArrayList<>(limits.size());
        Set<Long> windows = new HashSet<>();
        for (SmartRedisLimiterLimit limit : limits) {
            if (limit == null) {
                throw invalidPolicy(ErrorMessage.REASON_POLICY_LIMIT_ITEM_REQUIRED);
            }
            if (!windows.add(limit.getWindowSeconds())) {
                throw new SmartRedisLimiterException(
                        ErrorCode.POLICY_DUPLICATE_WINDOW,
                        String.format(ErrorMessage.POLICY_DUPLICATE_WINDOW, limit.getWindowSeconds()));
            }
            copiedLimits.add(limit);
        }
        copiedLimits.sort(Comparator.comparingLong(SmartRedisLimiterLimit::getWindowSeconds));

        this.key = key;
        this.limits = Collections.unmodifiableList(copiedLimits);
    }

    private static SmartRedisLimiterException invalidPolicy(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_INVALID,
                String.format(ErrorMessage.POLICY_INVALID, reason));
    }
}
