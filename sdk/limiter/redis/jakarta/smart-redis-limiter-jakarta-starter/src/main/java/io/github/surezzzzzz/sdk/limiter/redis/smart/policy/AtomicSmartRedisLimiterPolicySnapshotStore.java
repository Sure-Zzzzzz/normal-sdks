package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于原子引用的远程策略快照存储
 *
 * @author surezzzzzz
 */
public class AtomicSmartRedisLimiterPolicySnapshotStore implements SmartRedisLimiterPolicySnapshotStore {

    /**
     * 当前完整快照
     */
    private final AtomicReference<SmartRedisLimiterAcceptedPolicySnapshot> current = new AtomicReference<>();

    /**
     * 获取当前已接受快照
     *
     * @return 当前快照，从未成功接受时返回 null
     */
    @Override
    public SmartRedisLimiterAcceptedPolicySnapshot getCurrent() {
        return current.get();
    }

    /**
     * 原子替换当前快照
     *
     * @param snapshot 新快照
     */
    @Override
    public void replace(SmartRedisLimiterAcceptedPolicySnapshot snapshot) {
        if (snapshot == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_SNAPSHOT_INVALID, ErrorMessage.POLICY_SNAPSHOT_REQUIRED);
        }
        current.set(snapshot);
    }
}
