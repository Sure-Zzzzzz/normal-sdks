package io.github.surezzzzzz.sdk.retry.redis.service;

import io.github.surezzzzzz.sdk.retry.redis.model.RetryInfo;

/**
 * 重试上下文接口，提供带前缀的便捷操作
 */
public interface RetryContext {

    /**
     * 检查是否可以重�?
     */
    boolean canRetry(String identifier);

    /**
     * 记录失败并增加重试次�?
     */
    void recordFailure(String identifier, Exception error);

    /**
     * 获取当前重试信息
     */
    RetryInfo getCurrentRetryInfo(String identifier);

    /**
     * 计算重试延迟时间
     */
    long calculateRetryDelay(String identifier);

    /**
     * 清除重试记录
     */
    void clearRetryRecord(String identifier);

    /**
     * 构建完整的重试key
     */
    String buildRetryKey(String identifier);
}
