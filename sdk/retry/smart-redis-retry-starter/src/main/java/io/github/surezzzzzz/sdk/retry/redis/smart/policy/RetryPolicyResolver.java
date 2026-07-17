package io.github.surezzzzzz.sdk.retry.redis.smart.policy;

import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;

/**
 * 重试策略解析器
 *
 * @author surezzzzzz
 */
public interface RetryPolicyResolver {

    /**
     * 解析当前失败记录的重试策略
     *
     * @param retryType 重试类型
     * @param failure 失败信息
     * @return 重试策略
     */
    RetryPolicy resolve(String retryType, RetryFailure failure);
}
