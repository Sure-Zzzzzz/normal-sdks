package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 原子类型化远程策略快照存储
 * <p>与 v1 原子存储同构：单引用原子替换，读侧无锁快照视图。</p>
 *
 * @author surezzzzzz
 */
public class AtomicSmartRedisLimiterTypedPolicySnapshotStore
        implements SmartRedisLimiterTypedPolicySnapshotStore {

    private final AtomicReference<SmartRedisLimiterAcceptedTypedPolicySnapshot> current =
            new AtomicReference<>();

    /**
     * 获取当前已接受类型化快照
     *
     * @return 当前类型化快照，从未成功接受时返回 null
     */
    @Override
    public SmartRedisLimiterAcceptedTypedPolicySnapshot getCurrent() {
        return current.get();
    }

    /**
     * 原子替换当前类型化快照
     *
     * @param snapshot 新类型化快照
     */
    @Override
    public void replace(SmartRedisLimiterAcceptedTypedPolicySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("类型化快照不能为空");
        }
        current.set(snapshot);
    }
}
