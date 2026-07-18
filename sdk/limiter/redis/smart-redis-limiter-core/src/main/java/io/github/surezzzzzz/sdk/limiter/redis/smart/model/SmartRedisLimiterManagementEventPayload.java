package io.github.surezzzzzz.sdk.limiter.redis.smart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterAttributeSnapshotHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * SmartRedisLimiter 动态策略管理事件载荷
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterManagementEventPayload {

    /**
     * 管理操作
     */
    private final SmartRedisLimiterManagementOperation operation;

    /**
     * 策略键
     */
    private final SmartRedisLimiterPolicyKey policyKey;

    /**
     * 变更前策略
     */
    private final SmartRedisLimiterPolicy beforePolicy;

    /**
     * 变更后策略
     */
    private final SmartRedisLimiterPolicy afterPolicy;

    /**
     * 变更前启用状态
     */
    private final Boolean beforeEnabled;

    /**
     * 变更后启用状态
     */
    private final Boolean afterEnabled;

    /**
     * 服务策略版本
     */
    private final long revision;

    /**
     * 操作人标识
     */
    private final String operator;

    /**
     * 事件发生时间
     */
    private final Instant occurredAt;

    /**
     * 扩展属性
     */
    private final Map<String, Object> attributes;

    /**
     * 构造动态策略管理事件载荷
     *
     * @param operation     管理操作
     * @param policyKey     策略键
     * @param beforePolicy  变更前策略
     * @param afterPolicy   变更后策略
     * @param beforeEnabled 变更前启用状态
     * @param afterEnabled  变更后启用状态
     * @param revision      服务策略版本
     * @param operator      操作人标识
     * @param occurredAt    事件发生时间
     * @param attributes    扩展属性
     * @throws SmartRedisLimiterException 载荷字段或操作状态非法时抛出
     */
    @Builder
    @JsonCreator
    public SmartRedisLimiterManagementEventPayload(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OPERATION, required = true)
            SmartRedisLimiterManagementOperation operation,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_POLICY_KEY, required = true)
            SmartRedisLimiterPolicyKey policyKey,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_BEFORE_POLICY)
            SmartRedisLimiterPolicy beforePolicy,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_AFTER_POLICY)
            SmartRedisLimiterPolicy afterPolicy,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_BEFORE_ENABLED)
            Boolean beforeEnabled,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_AFTER_ENABLED)
            Boolean afterEnabled,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_REVISION, required = true)
            Long revision,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OPERATOR, required = true)
            String operator,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OCCURRED_AT, required = true)
            Instant occurredAt,
            @JsonProperty(SmartRedisLimiterConstant.JSON_FIELD_ATTRIBUTES)
            Map<String, Object> attributes) {
        if (operation == null) {
            throw invalidPayload(ErrorMessage.REASON_OPERATION_REQUIRED);
        }
        if (policyKey == null) {
            throw invalidPayload(ErrorMessage.REASON_MANAGEMENT_POLICY_KEY_REQUIRED);
        }
        if (revision == null) {
            throw invalidPayload(ErrorMessage.REASON_REVISION_REQUIRED);
        }
        if (revision < 0) {
            throw invalidPayload(ErrorMessage.REASON_REVISION_NEGATIVE);
        }
        if (occurredAt == null) {
            throw invalidPayload(ErrorMessage.REASON_OCCURRED_AT_REQUIRED);
        }
        validatePolicyKey(policyKey, beforePolicy, ErrorMessage.REASON_BEFORE_POLICY_KEY_MISMATCH);
        validatePolicyKey(policyKey, afterPolicy, ErrorMessage.REASON_AFTER_POLICY_KEY_MISMATCH);
        validateOperation(operation, beforePolicy, afterPolicy, beforeEnabled, afterEnabled);

        this.operation = operation;
        this.policyKey = policyKey;
        this.beforePolicy = beforePolicy;
        this.afterPolicy = afterPolicy;
        this.beforeEnabled = beforeEnabled;
        this.afterEnabled = afterEnabled;
        this.revision = revision;
        this.operator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        this.occurredAt = occurredAt;
        this.attributes = SmartRedisLimiterAttributeSnapshotHelper.snapshotStrict(attributes);
    }

    private static void validatePolicyKey(SmartRedisLimiterPolicyKey policyKey,
                                          SmartRedisLimiterPolicy policy,
                                          String reason) {
        if (policy != null && !policyKey.equals(policy.getKey())) {
            throw invalidPayload(reason);
        }
    }

    private static void validateOperation(SmartRedisLimiterManagementOperation operation,
                                          SmartRedisLimiterPolicy beforePolicy,
                                          SmartRedisLimiterPolicy afterPolicy,
                                          Boolean beforeEnabled,
                                          Boolean afterEnabled) {
        switch (operation) {
            case CREATE:
                require(beforePolicy == null && beforeEnabled == null
                                && afterPolicy != null && afterEnabled != null,
                        ErrorMessage.REASON_CREATE_PAYLOAD_INVALID);
                break;
            case UPDATE:
                require(beforePolicy != null && afterPolicy != null
                                && beforeEnabled != null && beforeEnabled.equals(afterEnabled),
                        ErrorMessage.REASON_UPDATE_PAYLOAD_INVALID);
                break;
            case ENABLE:
                require(beforePolicy != null && beforePolicy.equals(afterPolicy)
                                && Boolean.FALSE.equals(beforeEnabled) && Boolean.TRUE.equals(afterEnabled),
                        ErrorMessage.REASON_ENABLE_PAYLOAD_INVALID);
                break;
            case DISABLE:
                require(beforePolicy != null && beforePolicy.equals(afterPolicy)
                                && Boolean.TRUE.equals(beforeEnabled) && Boolean.FALSE.equals(afterEnabled),
                        ErrorMessage.REASON_DISABLE_PAYLOAD_INVALID);
                break;
            case DELETE:
                require(beforePolicy != null && beforeEnabled != null
                                && afterPolicy == null && afterEnabled == null,
                        ErrorMessage.REASON_DELETE_PAYLOAD_INVALID);
                break;
            default:
                throw invalidPayload(ErrorMessage.REASON_MANAGEMENT_OPERATION_UNSUPPORTED);
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw invalidPayload(reason);
        }
    }

    private static SmartRedisLimiterException invalidPayload(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, reason));
    }
}
