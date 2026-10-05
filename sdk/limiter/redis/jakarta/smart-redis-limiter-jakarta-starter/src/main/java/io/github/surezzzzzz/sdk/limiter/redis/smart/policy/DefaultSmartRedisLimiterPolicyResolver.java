package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认请求级动态策略解析器
 *
 * @author surezzzzzz
 */
public class DefaultSmartRedisLimiterPolicyResolver implements SmartRedisLimiterPolicyResolver {

    /**
     * 使用请求开始时读取的单份快照解析最终策略
     *
     * @param acceptedSnapshot 本次请求读取的已接受快照
     * @param serviceCode      服务编码
     * @param resourceCode     资源编码
     * @param policySubject    原始限流对象标识
     * @param localLimits      本地完整限额列表
     * @return 策略解析结果
     */
    @Override
    public SmartRedisLimiterPolicyResolution resolve(
            SmartRedisLimiterAcceptedPolicySnapshot acceptedSnapshot,
            String serviceCode,
            String resourceCode,
            String policySubject,
            List<SmartRedisLimiterProperties.SmartLimitRule> localLimits) {
        if (acceptedSnapshot == null
                || resourceCode == null
                || SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE.equals(resourceCode)) {
            return localResolution(localLimits);
        }

        SmartRedisLimiterPolicyKey key = new SmartRedisLimiterPolicyKey(
                serviceCode, resourceCode, policySubject);
        SmartRedisLimiterPolicy policy = acceptedSnapshot.findPolicy(key);
        if (policy == null) {
            return localResolution(localLimits);
        }

        List<SmartRedisLimiterProperties.SmartLimitRule> remoteLimits = new ArrayList<>();
        for (SmartRedisLimiterLimit limit : policy.getLimits()) {
            SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
            rule.setCount(limit.getCount());
            rule.setWindow(limit.getWindow());
            rule.setUnit(limit.getUnit());
            remoteLimits.add(rule);
        }
        return new SmartRedisLimiterPolicyResolution(
                remoteLimits,
                SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE,
                acceptedSnapshot.getRevision());
    }

    private SmartRedisLimiterPolicyResolution localResolution(
            List<SmartRedisLimiterProperties.SmartLimitRule> localLimits) {
        return new SmartRedisLimiterPolicyResolution(
                localLimits,
                SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL,
                null);
    }
}
