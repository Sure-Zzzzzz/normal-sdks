package io.github.surezzzzzz.sdk.limiter.redis.smart.typed;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已接受的类型化远程策略快照
 * <p>与 v1 已接受快照同构：携带原始 ETag、本地接受时间与按规则键构建的不可变索引；
 * v1 与 v2 的已接受状态、ETag 与 schema 相互独立，不混为一份快照。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterAcceptedTypedPolicySnapshot {

    /**
     * 已校验的类型化协议快照
     */
    private final SmartRedisLimiterTypedPolicySnapshot snapshot;

    /**
     * 服务端原始 ETag
     */
    private final String etag;

    /**
     * 本地接受时间
     */
    private final Instant acceptedAt;

    /**
     * 按规则键构建的不可变索引
     */
    private final Map<SmartRedisLimiterTypedPolicyKey, SmartRedisLimiterTypedPolicy> ruleIndex;

    /**
     * 构造已接受类型化快照
     *
     * @param snapshot   已校验的类型化协议快照
     * @param etag       服务端原始 ETag
     * @param acceptedAt 本地接受时间
     */
    public SmartRedisLimiterAcceptedTypedPolicySnapshot(SmartRedisLimiterTypedPolicySnapshot snapshot,
                                                        String etag,
                                                        Instant acceptedAt) {
        if (snapshot == null || etag == null || etag.trim().isEmpty() || acceptedAt == null) {
            throw new IllegalArgumentException("已接受类型化快照的快照、ETag 与接受时间均不能为空");
        }
        this.snapshot = snapshot;
        this.etag = etag;
        this.acceptedAt = acceptedAt;
        Map<SmartRedisLimiterTypedPolicyKey, SmartRedisLimiterTypedPolicy> index = new LinkedHashMap<>();
        for (SmartRedisLimiterTypedPolicy rule : snapshot.getRules()) {
            index.put(rule.getKey(), rule);
        }
        this.ruleIndex = Collections.unmodifiableMap(index);
    }
}
