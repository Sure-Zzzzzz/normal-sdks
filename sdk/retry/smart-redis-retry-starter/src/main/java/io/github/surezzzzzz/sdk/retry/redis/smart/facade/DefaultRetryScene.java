package io.github.surezzzzzz.sdk.retry.redis.smart.facade;

import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanResult;
import lombok.RequiredArgsConstructor;

/**
 * 默认重试场景门面
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultRetryScene implements RetryScene {

    /**
     * 重试类型
     */
    private final String retryType;
    /**
     * Smart Redis Retry 引擎
     */
    private final SmartRedisRetryEngine engine;

    /**
     * 快速判断当前是否可以重试。
     *
     * @param retryKey 重试标识
     * @return true 表示允许重试，false 表示不允许
     */
    @Override
    public boolean canRetry(String retryKey) {
        return engine.canRetry(retryType, retryKey);
    }

    /**
     * 获取当前重试决策。
     *
     * @param retryKey 重试标识
     * @return 重试决策
     */
    @Override
    public RetryDecision decide(String retryKey) {
        return engine.decide(retryType, retryKey);
    }

    /**
     * 使用 Key 记录一次失败。
     *
     * @param retryKey 重试标识
     * @return 更新后的重试信息
     */
    @Override
    public RetryInfo recordFailure(String retryKey) {
        return engine.recordFailure(retryType, retryKey);
    }

    /**
     * 使用完整失败请求记录一次失败。
     *
     * @param failure 失败请求（retryType 将被覆盖为场景绑定类型）
     * @return 更新后的重试信息
     */
    @Override
    public RetryInfo recordFailure(RetryFailure failure) {
        failure.setRetryType(retryType);
        return engine.recordFailure(failure);
    }

    /**
     * 原子清理指定重试记录并返回清理前的状态。
     *
     * @param retryKey 重试标识
     * @return 清理前的重试信息，记录不存在时返回 null
     */
    @Override
    public RetryInfo clear(String retryKey) {
        return engine.clear(retryType, retryKey);
    }

    /**
     * 查询指定重试记录的当前状态。
     *
     * @param retryKey 重试标识
     * @return 重试信息，记录不存在时返回 null
     */
    @Override
    public RetryInfo getInfo(String retryKey) {
        return engine.getInfo(retryType, retryKey);
    }

    /**
     * 分页扫描指定场景的重试 Key。
     *
     * @param routeKey 路由 Key
     * @param cursor   分页游标，传 null 或空字符串从头开始
     * @return 扫描结果
     */
    @Override
    public RetryScanResult scan(String routeKey, String cursor) {
        return engine.scan(routeKey, retryType, cursor);
    }
}
