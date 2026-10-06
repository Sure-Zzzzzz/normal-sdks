package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;

import java.io.InputStream;

/**
 * 策略快照 JSON 编解码契约
 * <p>codec（编解码器）只做对象与传输格式互转；严格解析（拒绝未知字段）、
 * 构造即校验的快照模型与字节上限由实现与调用方共同保证。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterManagementJsonCodec {

    /**
     * 从受限输入流解析 v1 三元组策略快照
     *
     * @param inputStream JSON 输入流
     * @return 完整策略快照
     */
    SmartRedisLimiterPolicySnapshot decodePolicy(InputStream inputStream);

    /**
     * 从受限输入流解析 v2 类型化策略快照
     *
     * @param inputStream JSON 输入流
     * @return 完整类型化策略快照
     */
    SmartRedisLimiterTypedPolicySnapshot decodeTypedPolicy(InputStream inputStream);
}
