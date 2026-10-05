package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已接受的远程策略快照
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterAcceptedPolicySnapshot {

    /**
     * 已校验的协议快照
     */
    private final SmartRedisLimiterPolicySnapshot snapshot;

    /**
     * 服务端原始 ETag
     */
    private final String etag;

    /**
     * canonical SHA-256 摘要
     */
    private final String canonicalDigest;

    /**
     * 本地接受时间
     */
    private final Instant acceptedAt;

    /**
     * 按策略键构建的不可变索引
     */
    private final Map<SmartRedisLimiterPolicyKey, SmartRedisLimiterPolicy> policyIndex;

    /**
     * 构造已接受快照
     *
     * @param snapshot        已校验的协议快照
     * @param etag            服务端原始 ETag
     * @param canonicalDigest canonical SHA-256 摘要
     * @param acceptedAt      本地接受时间
     */
    public SmartRedisLimiterAcceptedPolicySnapshot(SmartRedisLimiterPolicySnapshot snapshot,
                                                   String etag,
                                                   String canonicalDigest,
                                                   Instant acceptedAt) {
        this.snapshot = snapshot;
        this.etag = etag;
        this.canonicalDigest = canonicalDigest;
        this.acceptedAt = acceptedAt;
        Map<SmartRedisLimiterPolicyKey, SmartRedisLimiterPolicy> copiedIndex = new LinkedHashMap<>();
        for (SmartRedisLimiterPolicy policy : snapshot.getPolicies()) {
            copiedIndex.put(policy.getKey(), policy);
        }
        this.policyIndex = Collections.unmodifiableMap(copiedIndex);
    }

    /**
     * 获取快照版本
     *
     * @return 快照版本
     */
    public long getRevision() {
        return snapshot.getRevision();
    }

    /**
     * 精确查找策略
     *
     * @param key 策略键
     * @return 命中的策略，未命中返回 null
     */
    public SmartRedisLimiterPolicy findPolicy(SmartRedisLimiterPolicyKey key) {
        return policyIndex.get(key);
    }
}
