package io.github.surezzzzzz.sdk.retry.redis.smart.facade;

import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryDecision;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanResult;

/**
 * 重试场景门面
 *
 * @author surezzzzzz
 */
public interface RetryScene {

    /**
     * 判断当前是否允许重试
     *
     * @param retryKey 重试标识
     * @return 是否允许重试
     */
    boolean canRetry(String retryKey);

    /**
     * 获取重试决策
     *
     * @param retryKey 重试标识
     * @return 重试决策
     */
    RetryDecision decide(String retryKey);

    /**
     * 记录一次失败
     *
     * @param retryKey 重试标识
     * @return 最新重试状态
     */
    RetryInfo recordFailure(String retryKey);

    /**
     * 记录一次失败
     *
     * @param failure 失败信息
     * @return 最新重试状态
     */
    RetryInfo recordFailure(RetryFailure failure);

    /**
     * 清理重试记录
     *
     * @param retryKey 重试标识
     * @return 清理前的重试状态
     */
    RetryInfo clear(String retryKey);

    /**
     * 查询重试状态
     *
     * @param retryKey 重试标识
     * @return 重试状态
     */
    RetryInfo getInfo(String retryKey);

    /**
     * 分页扫描当前场景的重试记录。
     * <p>Cluster 模式的 nextCursor 为不透明值，调用方必须原样传回。</p>
     *
     * @param routeKey 路由 Key
     * @param cursor   扫描游标，传 null 或空字符串从头开始
     * @return 单页扫描结果
     */
    RetryScanResult scan(String routeKey, String cursor);
}
