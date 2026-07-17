package io.github.surezzzzzz.sdk.retry.redis.smart.listener;

import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;

/**
 * Smart Redis Retry 生命周期监听器
 *
 * @author surezzzzzz
 */
public interface SmartRedisRetryListener {

    /**
     * 重试决策完成后回调
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param decision 重试决策
     */
    void onDecision(String retryType, String retryKey, RetryDecision decision);

    /**
     * 失败记录写入后回调
     *
     * @param failure 失败信息
     * @param retryInfo 最新重试状态
     */
    void onRecord(RetryFailure failure, RetryInfo retryInfo);

    /**
     * 重试记录清理后回调
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param retryInfo 清理前的重试状态
     */
    void onClear(String retryType, String retryKey, RetryInfo retryInfo);

    /**
     * 失败记录首次达到最大重试次数后回调。
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param retryInfo 首次耗尽时的重试状态
     */
    void onExhausted(String retryType, String retryKey, RetryInfo retryInfo);
}
