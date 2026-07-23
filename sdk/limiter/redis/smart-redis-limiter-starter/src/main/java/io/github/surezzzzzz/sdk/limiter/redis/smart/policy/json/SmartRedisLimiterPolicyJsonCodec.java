package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;

import java.io.InputStream;

/**
 * 远程策略 JSON 编解码接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyJsonCodec {

    /**
     * 从受限输入流解析完整策略快照
     *
     * @param inputStream JSON 输入流
     * @return 完整策略快照
     */
    SmartRedisLimiterPolicySnapshot decode(InputStream inputStream);
}
