package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;

import java.time.Instant;

/**
 * 远程策略快照校验接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicySnapshotValidator {

    /**
     * 校验完整快照并构造可原子激活的不可变快照
     *
     * @param snapshot   待校验协议快照
     * @param etag       服务端原始 ETag
     * @param current    当前已接受快照
     * @param acceptedAt 本地接受时间
     * @return 已校验的不可变快照
     */
    SmartRedisLimiterAcceptedPolicySnapshot validate(
            SmartRedisLimiterPolicySnapshot snapshot,
            String etag,
            SmartRedisLimiterAcceptedPolicySnapshot current,
            Instant acceptedAt);
}
