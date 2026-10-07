package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterBucketIdentityHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 类型化门禁已解析规则
 * <p>一次请求的执行计划单元：命名空间/类型/实际对象来自声明与事实，
 * 限额来自远程精确、远程默认或本地声明的整体选择。桶身份摘要覆盖
 * 服务、资源、维度、命名空间、类型与实际对象，不含选择器、revision 与限额值，
 * 修改额度或切换默认/覆盖不换桶；RESOURCE 维度实际对象为固定共享标识。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class SmartRedisLimiterTypedGateRule {

    /**
     * 计数维度
     */
    private final SmartRedisLimiterDataDimension dimension;

    /**
     * 固定命名空间
     */
    private final String namespace;

    /**
     * 自定义类型（仅 CUSTOM）
     */
    private final String customType;

    /**
     * 实际计数对象（RESOURCE 为固定共享标识）
     */
    private final String objectId;

    /**
     * 计数桶身份摘要（64 位小写十六进制）
     */
    private final String bucketDigest;

    /**
     * 计数桶 Redis Key（含 Hash Tag）
     */
    private final String redisKey;

    /**
     * 完整限额窗口
     */
    private final List<SmartRedisLimiterProperties.SmartLimitRule> limits;

    /**
     * 规则来源：local / default / exact
     */
    private final String source;

    /**
     * 构造已解析门禁规则
     *
     * @param serviceCode  服务编码（宿主 me）
     * @param resourceCode 资源编码
     * @param dimension    计数维度
     * @param namespace    固定命名空间
     * @param customType   自定义类型
     * @param objectId     实际计数对象
     * @param limits       完整限额窗口
     * @param source       规则来源
     * @param useHashTag   是否以 Hash Tag 包裹摘要
     */
    public SmartRedisLimiterTypedGateRule(String serviceCode,
                                          String resourceCode,
                                          SmartRedisLimiterDataDimension dimension,
                                          String namespace,
                                          String customType,
                                          String objectId,
                                          List<SmartRedisLimiterProperties.SmartLimitRule> limits,
                                          String source,
                                          boolean useHashTag) {
        this.dimension = dimension;
        this.namespace = namespace;
        this.customType = customType;
        this.objectId = objectId;
        this.limits = Collections.unmodifiableList(limits);
        this.source = source;
        SmartRedisLimiterRuleSelector carrier = dimension == SmartRedisLimiterDataDimension.RESOURCE
                ? SmartRedisLimiterRuleSelector.DEFAULT
                : SmartRedisLimiterRuleSelector.EXACT;
        this.bucketDigest = SmartRedisLimiterBucketIdentityHelper.digest(new SmartRedisLimiterTypedPolicyKey(
                serviceCode, resourceCode, dimension, carrier, namespace, customType, objectId));
        this.redisKey = SmartRedisLimiterTypedKeyHelper.buildBucketKey(
                serviceCode, resourceCode, bucketDigest, useHashTag);
    }

    /**
     * 判断规则来源是否远程
     *
     * @return 远程精确或远程默认返回 true
     */
    public boolean isRemote() {
        return !SmartRedisLimiterStarterConstant.TYPED_SOURCE_LOCAL.equals(source);
    }
}
