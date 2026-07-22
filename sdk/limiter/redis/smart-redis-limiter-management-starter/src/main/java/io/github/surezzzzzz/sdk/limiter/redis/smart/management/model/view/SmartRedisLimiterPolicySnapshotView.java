package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 限流策略快照与 ETag 视图
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class SmartRedisLimiterPolicySnapshotView {

    private final SmartRedisLimiterPolicySnapshot snapshot;
    private final String etag;
}
