package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client;

/**
 * 远程策略客户端接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyClient {

    /**
     * 拉取服务完整策略快照
     *
     * @param serviceCode 服务编码
     * @param currentEtag 当前已接受 ETag，无快照时为 null
     * @return 拉取结果
     */
    SmartRedisLimiterPolicyFetchResult fetch(String serviceCode, String currentEtag);
}
