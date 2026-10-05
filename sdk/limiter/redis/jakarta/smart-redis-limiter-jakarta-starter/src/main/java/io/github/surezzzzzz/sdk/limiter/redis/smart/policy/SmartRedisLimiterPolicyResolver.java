package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;

import java.util.List;

/**
 * 请求级动态策略解析接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyResolver {

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
    SmartRedisLimiterPolicyResolution resolve(
            SmartRedisLimiterAcceptedPolicySnapshot acceptedSnapshot,
            String serviceCode,
            String resourceCode,
            String policySubject,
            List<SmartRedisLimiterProperties.SmartLimitRule> localLimits);
}
