package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterTypedPolicyValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * SmartRedisLimiter 类型化动态策略键
 * <p>由服务、资源、计数维度、选择器、命名空间、自定义类型与计数对象组成；
 * DEFAULT 的计数对象为 null，CUSTOM 必须携带自定义类型，其他维度必须为 null。
 * 对象原值分字段保存，不把类型、来源与对象拼接进旧 subject。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SmartRedisLimiterTypedPolicyKey {

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
     * 命名空间（宿主声明的稳定身份域）
     */
    private final String namespace;

    /**
     * 自定义类型（仅 CUSTOM 维度非空）
     */
    private final String customType;

    /**
     * 计数对象（仅 EXACT 非空，原值保留不 trim）
     */
    private final String objectId;

    /**
     * 构造类型化动态策略键
     * <p>JSON 协议中可空字段（customType/objectId）序列化时整体省略（NON_NULL）、
     * 反序列化时缺省或显式 null 均归一为 null；字段组合合法性（CUSTOM 必带 customType、
     * DEFAULT 不带 objectId 等）由构造器校验承担，与传输层严格开关无关。</p>
     *
     * @param serviceCode  服务编码
     * @param resourceCode 资源编码
     * @param dimension    计数维度
     * @param selector     规则选择器
     * @param namespace    命名空间
     * @param customType   自定义类型
     * @param objectId     计数对象
     * @throws io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException 字段或组合非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterTypedPolicyKey(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SERVICE_CODE, required = true)
            String serviceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_RESOURCE_CODE, required = true)
            String resourceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_DIMENSION, required = true)
            SmartRedisLimiterDataDimension dimension,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SELECTOR, required = true)
            SmartRedisLimiterRuleSelector selector,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_NAMESPACE, required = true)
            String namespace,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_CUSTOM_TYPE)
            String customType,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_OBJECT_ID)
            String objectId) {
        SmartRedisLimiterTypedPolicyValidationHelper.validateDimension(dimension);
        SmartRedisLimiterTypedPolicyValidationHelper.validateSelector(dimension, selector);
        this.serviceCode = SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode);
        this.resourceCode = SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(resourceCode);
        this.dimension = dimension;
        this.selector = selector;
        this.namespace = SmartRedisLimiterTypedPolicyValidationHelper.normalizeNamespace(namespace);
        this.customType = SmartRedisLimiterTypedPolicyValidationHelper.normalizeCustomType(dimension, customType);
        this.objectId = SmartRedisLimiterTypedPolicyValidationHelper.validateObjectId(objectId, selector);
    }
}
