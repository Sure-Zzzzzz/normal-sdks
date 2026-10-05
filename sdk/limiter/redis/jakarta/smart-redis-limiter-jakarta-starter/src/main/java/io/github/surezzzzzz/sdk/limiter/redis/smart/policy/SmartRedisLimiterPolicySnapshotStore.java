package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;

/**
 * 远程策略快照存储接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicySnapshotStore {

    /**
     * 获取当前已接受快照
     *
     * @return 当前快照，从未成功接受时返回 null
     */
    SmartRedisLimiterAcceptedPolicySnapshot getCurrent();

    /**
     * 原子替换当前快照
     *
     * @param snapshot 新快照
     */
    void replace(SmartRedisLimiterAcceptedPolicySnapshot snapshot);
}
