package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy;

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
 * SmartRedisLimiter 服务级动态策略快照
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterPolicySnapshot {

    /**
     * 策略协议版本
     */
    private final String schemaVersion;

    /**
     * 服务编码
     */
    private final String serviceCode;

    /**
     * 服务策略版本
     */
    private final long revision;

    /**
     * 策略版本发布时间
     */
    private final Instant publishedAt;

    /**
     * 服务完整动态策略列表
     */
    private final List<SmartRedisLimiterPolicy> policies;

    /**
     * 构造服务级动态策略快照
     *
     * @param schemaVersion 策略协议版本
     * @param serviceCode   服务编码
     * @param revision      服务策略版本
     * @param publishedAt   策略版本发布时间
     * @param policies      服务完整动态策略列表
     * @throws SmartRedisLimiterException 快照字段或策略内容非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterPolicySnapshot(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SCHEMA_VERSION, required = true)
            String schemaVersion,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SERVICE_CODE, required = true)
            String serviceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_REVISION, required = true)
            Long revision,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_PUBLISHED_AT, required = true)
            Instant publishedAt,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_POLICIES, required = true)
            List<SmartRedisLimiterPolicy> policies) {
        if (!SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION.equals(schemaVersion)) {
            throw invalidSnapshot(ErrorMessage.REASON_SNAPSHOT_SCHEMA_UNSUPPORTED);
        }
        String normalizedServiceCode = SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode);
        if (revision == null) {
            throw invalidSnapshot(ErrorMessage.REASON_REVISION_REQUIRED);
        }
        if (revision < 0) {
            throw invalidSnapshot(ErrorMessage.REASON_REVISION_NEGATIVE);
        }
        if (publishedAt == null) {
            throw invalidSnapshot(ErrorMessage.REASON_PUBLISHED_AT_REQUIRED);
        }
        if (policies == null) {
            throw invalidSnapshot(ErrorMessage.REASON_POLICIES_REQUIRED);
        }

        List<SmartRedisLimiterPolicy> copiedPolicies = new ArrayList<>(policies.size());
        Set<SmartRedisLimiterPolicyKey> keys = new HashSet<>();
        for (SmartRedisLimiterPolicy policy : policies) {
            if (policy == null) {
                throw invalidSnapshot(ErrorMessage.REASON_POLICY_ITEM_REQUIRED);
            }
            if (!normalizedServiceCode.equals(policy.getKey().getServiceCode())) {
                throw new SmartRedisLimiterException(
                        ErrorCode.POLICY_SNAPSHOT_SERVICE_MISMATCH,
                        ErrorMessage.POLICY_SNAPSHOT_SERVICE_MISMATCH);
            }
            if (!keys.add(policy.getKey())) {
                throw new SmartRedisLimiterException(
                        ErrorCode.POLICY_DUPLICATE_KEY,
                        ErrorMessage.POLICY_DUPLICATE_KEY);
            }
            copiedPolicies.add(policy);
        }

        this.schemaVersion = schemaVersion;
        this.serviceCode = normalizedServiceCode;
        this.revision = revision;
        this.publishedAt = publishedAt;
        this.policies = Collections.unmodifiableList(copiedPolicies);
    }

    private static SmartRedisLimiterException invalidSnapshot(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_SNAPSHOT_INVALID,
                String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID, reason));
    }
}
