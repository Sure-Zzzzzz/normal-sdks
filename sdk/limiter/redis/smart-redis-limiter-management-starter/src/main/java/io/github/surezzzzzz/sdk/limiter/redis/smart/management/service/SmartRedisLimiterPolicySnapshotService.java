package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicySnapshotView;

/**
 * 服务级限流策略快照接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicySnapshotService {
    /**
     * 先校验完整 DATA 服务范围，再读取 revision 与快照。
     */
    SmartRedisLimiterPolicySnapshotView getSnapshot(String serviceCode,
                                                    io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope scope);

    /**
     * 构建服务完整已启用策略快照
     *
     * @param serviceCode 服务编码
     * @return 快照与 ETag
     */
    SmartRedisLimiterPolicySnapshotView getSnapshot(String serviceCode);
}
