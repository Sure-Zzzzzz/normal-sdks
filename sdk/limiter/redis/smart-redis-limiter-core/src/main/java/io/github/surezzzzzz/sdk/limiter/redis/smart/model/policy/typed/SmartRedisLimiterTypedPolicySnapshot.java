package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.*;

/**
 * SmartRedisLimiter 类型化服务级动态策略快照
 * <p>协议版本固定为 "2"，携带策略代次（policyEpoch）与单调 revision；
 * 代次是一次明确切换/恢复的版本标识，运行端只接受部署时明确预期的代次。
 * 快照必须完整、唯一：规则服务不匹配或键重复时整份拒绝。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedPolicySnapshot {

    /**
     * 类型化策略协议版本
     */
    private final String schemaVersion;

    /**
     * 服务编码
     */
    private final String serviceCode;

    /**
     * 策略代次
     */
    private final long policyEpoch;

    /**
     * 服务策略版本
     */
    private final long revision;

    /**
     * 策略版本发布时间
     */
    private final Instant publishedAt;

    /**
     * 服务完整类型化规则列表
     */
    private final List<SmartRedisLimiterTypedPolicy> rules;

    /**
     * 构造类型化服务级动态策略快照
     *
     * @param schemaVersion 类型化策略协议版本
     * @param serviceCode   服务编码
     * @param policyEpoch   策略代次
     * @param revision      服务策略版本
     * @param publishedAt   策略版本发布时间
     * @param rules         服务完整类型化规则列表
     * @throws SmartRedisLimiterException 快照字段或规则内容非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterTypedPolicySnapshot(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SCHEMA_VERSION, required = true)
            String schemaVersion,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SERVICE_CODE, required = true)
            String serviceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_POLICY_EPOCH, required = true)
            Long policyEpoch,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_REVISION, required = true)
            Long revision,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_PUBLISHED_AT, required = true)
            Instant publishedAt,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_RULES, required = true)
            List<SmartRedisLimiterTypedPolicy> rules) {
        if (!SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION.equals(schemaVersion)) {
            throw invalidSnapshot(ErrorMessage.REASON_SNAPSHOT_SCHEMA_UNSUPPORTED);
        }
        String normalizedServiceCode = SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode);
        if (policyEpoch == null || policyEpoch < 1L) {
            throw invalidSnapshot(ErrorMessage.REASON_TYPED_POLICY_EPOCH_INVALID);
        }
        if (revision == null) {
            throw invalidSnapshot(ErrorMessage.REASON_REVISION_REQUIRED);
        }
        if (revision < 0) {
            throw invalidSnapshot(ErrorMessage.REASON_REVISION_NEGATIVE);
        }
        if (publishedAt == null) {
            throw invalidSnapshot(ErrorMessage.REASON_PUBLISHED_AT_REQUIRED);
        }
        if (rules == null) {
            throw invalidSnapshot(ErrorMessage.REASON_POLICIES_REQUIRED);
        }

        List<SmartRedisLimiterTypedPolicy> copiedRules = new ArrayList<>(rules.size());
        Set<SmartRedisLimiterTypedPolicyKey> keys = new HashSet<>();
        for (SmartRedisLimiterTypedPolicy rule : rules) {
            if (rule == null) {
                throw invalidSnapshot(ErrorMessage.REASON_POLICY_ITEM_REQUIRED);
            }
            if (!normalizedServiceCode.equals(rule.getKey().getServiceCode())) {
                throw new SmartRedisLimiterException(
                        ErrorCode.TYPED_POLICY_SNAPSHOT_SERVICE_MISMATCH,
                        ErrorMessage.TYPED_POLICY_SNAPSHOT_SERVICE_MISMATCH);
            }
            if (!keys.add(rule.getKey())) {
                throw new SmartRedisLimiterException(
                        ErrorCode.TYPED_POLICY_DUPLICATE_KEY,
                        ErrorMessage.TYPED_POLICY_DUPLICATE_KEY);
            }
            copiedRules.add(rule);
        }

        this.schemaVersion = schemaVersion;
        this.serviceCode = normalizedServiceCode;
        this.policyEpoch = policyEpoch;
        this.revision = revision;
        this.publishedAt = publishedAt;
        this.rules = Collections.unmodifiableList(copiedRules);
    }

    private static SmartRedisLimiterException invalidSnapshot(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.TYPED_POLICY_SNAPSHOT_INVALID,
                String.format(ErrorMessage.TYPED_POLICY_SNAPSHOT_INVALID, reason));
    }
}
