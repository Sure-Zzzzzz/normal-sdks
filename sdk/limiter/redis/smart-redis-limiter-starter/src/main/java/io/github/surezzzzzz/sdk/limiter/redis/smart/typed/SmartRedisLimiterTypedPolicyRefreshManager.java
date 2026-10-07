package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

/**
 * 类型化远程策略刷新管理器接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTypedPolicyRefreshManager {

    /**
     * 手工触发一次刷新
     *
     * @return 本次是否执行
     */
    boolean refresh();

    /**
     * 获取最近一次刷新失败原因
     *
     * @return 最近失败原因；无失败为 null
     */
    Throwable getLastFailure();
}
