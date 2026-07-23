package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

/**
 * 远程策略刷新管理接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyRefreshManager
        extends SmartRedisLimiterPolicyRefreshStateProvider {

    /**
     * 手工触发一次刷新
     *
     * @return 本次是否执行；已有刷新在途或管理器已关闭时返回 false
     */
    boolean refresh();
}
