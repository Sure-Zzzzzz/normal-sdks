package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterTypedPolicyFetchResult;

/**
 * 运行端策略客户端契约
 * <p>聚形态（集中策略管理）宿主经认证传输 starter 装配实现；散形态不引任何 client 制品。
 * fetch 返回的是单次拉取结果；last-known-good 保留、ETag 条件请求与刷新节奏由刷新管理器负责。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterManagementClient {

    /**
     * 拉取服务完整 v1 三元组策略快照
     *
     * @param serviceCode 服务编码
     * @param currentEtag 当前已接受 ETag，无快照时为 null
     * @return 拉取结果（200 完整快照或 304 未修改）
     */
    SmartRedisLimiterPolicyFetchResult fetchPolicy(String serviceCode, String currentEtag);

    /**
     * 拉取服务完整 v2 类型化策略快照
     *
     * @param serviceCode 服务编码
     * @param currentEtag 当前已接受 ETag，无快照时为 null
     * @return 拉取结果（200 完整快照或 304 未修改）
     */
    SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(String serviceCode, String currentEtag);
}
