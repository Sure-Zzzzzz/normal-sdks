package io.github.surezzzzzz.sdk.retry.redis.smart.policy;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import lombok.RequiredArgsConstructor;

/**
 * 默认重试策略解析器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultRetryPolicyResolver implements RetryPolicyResolver {

    /**
     * Smart Redis Retry 配置
     */
    private final SmartRedisRetryProperties properties;

    /**
     * 按请求策略、场景策略、默认策略的优先级解析重试策略。
     *
     * @param retryType 重试类型
     * @param failure   失败信息
     * @return 补全默认值后的重试策略
     */
    @Override
    public RetryPolicy resolve(String retryType, RetryFailure failure) {
        RetryPolicy resolved = RetryPolicy.defaultPolicy();
        resolved = merge(resolved, properties.getPolicy().getDefaultPolicy());
        resolved = merge(resolved, properties.getPolicy().getScene().get(retryType));
        return merge(resolved, failure == null ? null : failure.getPolicy());
    }

    private RetryPolicy merge(RetryPolicy base, RetryPolicy override) {
        if (override == null) {
            return base;
        }
        return RetryPolicy.builder()
                .maxRetryTimes(override.getMaxRetryTimes() == null
                        ? base.getMaxRetryTimes() : override.getMaxRetryTimes())
                .retryIntervalMillis(override.getRetryIntervalMillis() == null
                        ? base.getRetryIntervalMillis() : override.getRetryIntervalMillis())
                .maxIntervalMillis(override.getMaxIntervalMillis() == null
                        ? base.getMaxIntervalMillis() : override.getMaxIntervalMillis())
                .backoffMultiplier(override.getBackoffMultiplier() == null
                        ? base.getBackoffMultiplier() : override.getBackoffMultiplier())
                .jitterRatio(override.getJitterRatio() == null
                        ? base.getJitterRatio() : override.getJitterRatio())
                .build();
    }
}
