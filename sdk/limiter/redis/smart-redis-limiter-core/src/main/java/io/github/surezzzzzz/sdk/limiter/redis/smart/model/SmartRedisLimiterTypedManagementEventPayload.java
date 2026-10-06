package io.github.surezzzzzz.sdk.limiter.redis.smart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterAttributeSnapshotHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * SmartRedisLimiter 类型化管理事件载荷
 * <p>表达维度、规则选择类型、规则标识、资源、代次内的 revision、结果与受控原因；
 * 计数对象只记录命名空间受控的摘要，不输出原始用户、客户、IP 或自定义 key。
 * 操作人完整身份事实由 attributes 的 {@code operatorIdentity} 保留键携带
 * （schema=resource-principal-v1，含 sourceId/subjectType/subjectId），
 * 不携带 username、Token、权限集合或整个验证上下文。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedManagementEventPayload {

    /**
     * 管理操作类型
     */
    private final SmartRedisLimiterManagementOperation operation;

    /**
     * 服务编码
     */
    private final String serviceCode;

    /**
     * 资源编码
     */
    private final String resourceCode;

    /**
     * 计数维度
     */
    private final SmartRedisLimiterDataDimension dimension;

    /**
     * 规则选择器
     */
    private final SmartRedisLimiterRuleSelector selector;

    /**
     * 计数对象的命名空间受控摘要
     */
    private final String objectDigest;

    /**
     * 管理端分配的规则标识
     */
    private final Long ruleId;

    /**
     * 服务策略版本
     */
    private final long revision;

    /**
     * 操作结果（SUCCESS / FAILURE）
     */
    private final String result;

    /**
     * 受控原因（失败或拒绝时携带，不含敏感数据）
     */
    private final String reason;

    /**
     * 事件发生时间
     */
    private final Instant occurredAt;

    /**
     * 扩展属性（含 operatorIdentity 身份事实）
     */
    private final Map<String, Object> attributes;

    /**
     * 构造类型化管理事件载荷
     *
     * @param operation    管理操作类型
     * @param serviceCode  服务编码
     * @param resourceCode 资源编码
     * @param dimension    计数维度
     * @param selector     规则选择器
     * @param objectDigest 计数对象摘要
     * @param ruleId       规则标识
     * @param revision     服务策略版本
     * @param result       操作结果
     * @param reason       受控原因
     * @param occurredAt   事件发生时间
     * @param attributes   扩展属性
     * @throws SmartRedisLimiterException 载荷字段非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterTypedManagementEventPayload(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OPERATION, required = true)
            SmartRedisLimiterManagementOperation operation,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SERVICE_CODE, required = true)
            String serviceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_RESOURCE_CODE, required = true)
            String resourceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_DIMENSION, required = true)
            SmartRedisLimiterDataDimension dimension,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SELECTOR, required = true)
            SmartRedisLimiterRuleSelector selector,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OBJECT_DIGEST, required = true)
            String objectDigest,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_RULE_ID)
            Long ruleId,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_REVISION, required = true)
            Long revision,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_RESULT, required = true)
            String result,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_REASON)
            String reason,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OCCURRED_AT, required = true)
            Instant occurredAt,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_ATTRIBUTES)
            Map<String, Object> attributes) {
        if (operation == null) {
            throw invalidPayload(ErrorMessage.REASON_MANAGEMENT_PAYLOAD_REQUIRED);
        }
        if (dimension == null) {
            throw invalidPayload(ErrorMessage.REASON_TYPED_DIMENSION_INVALID);
        }
        if (selector == null) {
            throw invalidPayload(ErrorMessage.REASON_TYPED_SELECTOR_INVALID);
        }
        if (objectDigest == null || objectDigest.trim().isEmpty()) {
            throw invalidPayload(ErrorMessage.REASON_MANAGEMENT_PAYLOAD_REQUIRED);
        }
        if (revision == null || revision < 0) {
            throw invalidPayload(ErrorMessage.REASON_REVISION_NEGATIVE);
        }
        if (!SmartRedisLimiterConstant.TYPED_EVENT_RESULT_SUCCESS.equals(result)
                && !SmartRedisLimiterConstant.TYPED_EVENT_RESULT_FAILURE.equals(result)) {
            throw invalidPayload(ErrorMessage.REASON_TYPED_EVENT_RESULT_INVALID);
        }
        if (ruleId != null && ruleId < 0) {
            throw invalidPayload(ErrorMessage.REASON_TYPED_EVENT_RULE_ID_INVALID);
        }
        if (occurredAt == null) {
            throw invalidPayload(ErrorMessage.REASON_MANAGEMENT_PAYLOAD_REQUIRED);
        }
        if (attributes == null) {
            throw invalidPayload(ErrorMessage.REASON_MANAGEMENT_PAYLOAD_REQUIRED);
        }
        this.operation = operation;
        this.serviceCode = SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode);
        this.resourceCode = SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(resourceCode);
        this.dimension = dimension;
        this.selector = selector;
        this.objectDigest = objectDigest;
        this.ruleId = ruleId;
        this.revision = revision;
        this.result = result;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.attributes = SmartRedisLimiterAttributeSnapshotHelper.snapshotStrict(attributes);
    }

    private static SmartRedisLimiterException invalidPayload(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.TYPED_EVENT_PAYLOAD_INVALID,
                String.format(ErrorMessage.TYPED_EVENT_PAYLOAD_INVALID, reason));
    }
}
