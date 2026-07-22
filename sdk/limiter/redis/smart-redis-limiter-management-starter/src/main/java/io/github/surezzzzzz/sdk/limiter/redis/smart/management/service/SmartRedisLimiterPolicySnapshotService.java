package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicySnapshotView;

/**
 * 服务级限流策略快照接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicySnapshotService {

    /**
     * 构建服务完整已启用策略快照
     *
     * @param serviceCode 服务编码
     * @return 快照与 ETag
     */
    SmartRedisLimiterPolicySnapshotView getSnapshot(String serviceCode);
}
