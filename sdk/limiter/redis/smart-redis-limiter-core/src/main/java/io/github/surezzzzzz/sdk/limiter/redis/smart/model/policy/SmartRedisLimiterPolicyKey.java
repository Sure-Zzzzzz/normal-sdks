package io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * SmartRedisLimiter 动态策略键
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterPolicyKey {

    /**
     * 服务编码
     */
    private final String serviceCode;

    /**
     * 资源编码
     */
    private final String resourceCode;

    /**
     * 限流对象标识
     */
    private final String subject;

    /**
     * 构造动态策略键
     *
     * @param serviceCode  服务编码
     * @param resourceCode 资源编码
     * @param subject      限流对象标识
     * @throws io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException 字段非法时抛出
     */
    @JsonCreator
    public SmartRedisLimiterPolicyKey(
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SERVICE_CODE, required = true)
            String serviceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_RESOURCE_CODE, required = true)
            String resourceCode,
            @JsonProperty(value = SmartRedisLimiterConstant.JSON_FIELD_SUBJECT, required = true)
            String subject) {
        this.serviceCode = SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(serviceCode);
        this.resourceCode = SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(resourceCode);
        this.subject = SmartRedisLimiterPolicyValidationHelper.normalizeSubject(subject);
    }
}
