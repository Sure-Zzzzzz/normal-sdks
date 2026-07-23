package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterPolicyRefreshState;

/**
 * 远程策略刷新状态提供接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyRefreshStateProvider {

    /**
     * 获取当前刷新状态
     *
     * @return 当前刷新状态
     */
    SmartRedisLimiterPolicyRefreshState getRefreshState();
}
