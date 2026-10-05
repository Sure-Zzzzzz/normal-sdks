package io.github.surezzzzzz.sdk.limiter.redis.smart.generator;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;

/**
 * @author: Sure.
 * @description 限流Key生成策略接口
 * @Date: 2026-05-08
 */
public interface SmartRedisLimiterKeyGenerator {

    /**
     * 生成限流Key的一部分（不包含前缀和时间窗口后缀）
     *
     * @param context 限流上下文
     * @return key的一部分，如 "method:UserController.login"
     */
    String generate(SmartRedisLimiterContext context);
}
