package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterTypedPolicyValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

/**
 * SmartRedisLimiter 类型化动态限流规则
 * <p>规则身份即类型化键；限额为 1 至 16 个有效窗口的完整集合，
 * 窗口单位规范化后不得重复，选中 limits 整体替换不做隐式合并。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedPolicy {

    /**
     * 类型化规则键
     */
    private final SmartRedisLimiterTypedPolicyKey key;

    /**
     * 是否启用
     */
    private final boolean enabled;

    /**
     * 完整限额窗口集合
     */
    private final List<SmartRedisLimiterLimit> limits;

    /**
     * 构造类型化动态限流规则
     *
     * @param key     类型化规则键
     * @param enabled 是否启用
     * @param limits  完整限额窗口集合
     * @throws SmartRedisLimiterException 键、启用状态或窗口集合非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterTypedPolicy(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_KEY, required = true)
            SmartRedisLimiterTypedPolicyKey key,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_ENABLED, required = true)
            Boolean enabled,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_LIMITS, required = true)
            List<SmartRedisLimiterLimit> limits) {
        if (key == null) {
            throw typedInvalid(ErrorMessage.REASON_TYPED_KEY_REQUIRED);
        }
        if (enabled == null) {
            throw typedInvalid(ErrorMessage.REASON_TYPED_ENABLED_REQUIRED);
        }
        this.key = key;
        this.enabled = enabled;
        this.limits = SmartRedisLimiterTypedPolicyValidationHelper.validateLimits(limits);
    }

    private static SmartRedisLimiterException typedInvalid(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.TYPED_POLICY_INVALID,
                String.format(ErrorMessage.TYPED_POLICY_INVALID, reason));
    }
}
