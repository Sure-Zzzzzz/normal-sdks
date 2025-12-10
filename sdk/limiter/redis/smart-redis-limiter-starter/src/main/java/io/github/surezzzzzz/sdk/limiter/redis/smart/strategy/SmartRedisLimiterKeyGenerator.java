package io.github.surezzzzzz.sdk.limiter.redis.smart.strategy;

/**
 * @author: Sure.
 * @description 限流Key生成策略接口
 * @Date: 2024/12/XX XX:XX
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
