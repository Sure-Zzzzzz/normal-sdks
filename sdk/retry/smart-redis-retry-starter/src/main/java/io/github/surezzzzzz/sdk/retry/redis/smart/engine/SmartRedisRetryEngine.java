package io.github.surezzzzzz.sdk.retry.redis.smart.engine;

import io.github.surezzzzzz.sdk.retry.redis.smart.facade.RetryScene;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.*;

/**
 * Smart Redis Retry 引擎
 *
 * @author surezzzzzz
 */
public interface SmartRedisRetryEngine {

    /**
     * 判断当前是否允许重试
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return 是否允许重试
     */
    boolean canRetry(String retryType, String retryKey);

    /**
     * 获取重试决策
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return 重试决策
     */
    RetryDecision decide(String retryType, String retryKey);

    /**
     * 记录一次失败
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return 最新重试状态
     */
    RetryInfo recordFailure(String retryType, String retryKey);

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
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return 清理前的重试状态
     */
    RetryInfo clear(String retryType, String retryKey);

    /**
     * 查询重试状态
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return 重试状态
     */
    RetryInfo getInfo(String retryType, String retryKey);

    /**
     * 分页扫描重试记录。
     * <p>Standalone 模式返回 Redis 原生 SCAN 游标；Cluster 模式返回不透明的
     * master 节点和节点内 SCAN 游标组合，调用方必须原样传回 nextCursor。</p>
     *
     * @param routeKey 路由 Key
     * @param retryType 重试类型
     * @param cursor 扫描游标，传 null 或空字符串从头开始
     * @return 单页扫描结果
     */
    RetryScanResult scan(String routeKey, String retryType, String cursor);

    /**
     * 使用完整请求分页扫描重试记录。
     * <p>Cluster 模式的 nextCursor 为不透明值，调用方不得自行解析或修改。</p>
     *
     * @param request 扫描请求
     * @return 单页扫描结果
     */
    RetryScanResult scan(RetryScanRequest request);

    /**
     * 获取绑定重试类型的场景门面
     *
     * @param retryType 重试类型
     * @return 场景门面
     */
    RetryScene scene(String retryType);
}
