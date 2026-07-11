package io.github.surezzzzzz.sdk.retry.redis.service;

import io.github.surezzzzzz.sdk.retry.redis.model.RetryInfo;

/**
 * 重试上下文接口
 *
 * @author surezzzzzz
 */
public interface RetryContext {

    /**
     * 检查是否可以重试
     *
     * @param identifier 重试标识
     * @return true 可以重试，false 不可重试
     */
    boolean canRetry(String identifier);

    /**
     * 记录失败并增加重试次数
     *
     * @param identifier 重试标识
     * @param error 异常
     */
    void recordFailure(String identifier, Exception error);

    /**
     * 获取当前重试信息
     *
     * @param identifier 重试标识
     * @return 当前重试信息
     */
    RetryInfo getCurrentRetryInfo(String identifier);

    /**
     * 计算重试延迟时间
     *
     * @param identifier 重试标识
     * @return 延迟时间，单位毫秒
     */
    long calculateRetryDelay(String identifier);

    /**
     * 清除重试记录
     *
     * @param identifier 重试标识
     */
    void clearRetryRecord(String identifier);

    /**
     * 构建完整重试 Key
     *
     * @param identifier 重试标识
     * @return 完整重试 Key
     */
    String buildRetryKey(String identifier);
}
