package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

/**
 * 类型化远程策略快照存储接口
 * <p>与 v1 快照存储分离：类型化模式只读本存储；两协议的 last-known-good 互不覆盖。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTypedPolicySnapshotStore {

    /**
     * 获取当前已接受类型化快照
     *
     * @return 当前类型化快照，从未成功接受时返回 null
     */
    SmartRedisLimiterAcceptedTypedPolicySnapshot getCurrent();

    /**
     * 原子替换当前类型化快照
     *
     * @param snapshot 新类型化快照
     */
    void replace(SmartRedisLimiterAcceptedTypedPolicySnapshot snapshot);
}
