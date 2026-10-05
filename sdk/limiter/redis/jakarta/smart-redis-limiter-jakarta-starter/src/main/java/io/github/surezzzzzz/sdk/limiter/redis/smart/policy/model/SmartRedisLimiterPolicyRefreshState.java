package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 远程策略刷新状态
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class SmartRedisLimiterPolicyRefreshState {

    /**
     * 最近一次刷新开始时间
     */
    private final Instant lastAttemptAt;

    /**
     * 最近一次刷新成功时间
     */
    private final Instant lastSuccessAt;

    /**
     * 当前已接受快照版本
     */
    private final Long acceptedRevision;

    /**
     * 当前已接受快照 ETag
     */
    private final String acceptedEtag;

    /**
     * 最近一次刷新是否成功
     */
    private final boolean lastAttemptSuccessful;

    /**
     * 最近一次失败分类
     */
    private final String lastFailureReason;
}
