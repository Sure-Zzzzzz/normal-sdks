package io.github.surezzzzzz.sdk.retry.redis.smart.listener;

import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;

/**
 * 空 Smart Redis Retry 生命周期监听器
 *
 * @author surezzzzzz
 */
public class NoopSmartRedisRetryListener implements SmartRedisRetryListener {

    /**
     * 忽略重试决策事件。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @param decision  重试决策
     */
    @Override
    public void onDecision(String retryType, String retryKey, RetryDecision decision) {
    }

    /**
     * 忽略失败记录事件。
     *
     * @param failure   失败信息
     * @param retryInfo 最新重试状态
     */
    @Override
    public void onRecord(RetryFailure failure, RetryInfo retryInfo) {
    }

    /**
     * 忽略记录清理事件。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @param retryInfo 清理前的重试状态
     */
    @Override
    public void onClear(String retryType, String retryKey, RetryInfo retryInfo) {
    }

    /**
     * 忽略首次重试耗尽事件。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @param retryInfo 首次耗尽时的重试状态
     */
    @Override
    public void onExhausted(String retryType, String retryKey, RetryInfo retryInfo) {
    }
}
