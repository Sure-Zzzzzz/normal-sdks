package io.github.surezzzzzz.sdk.limiter.redis.smart.strategy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @author: Sure.
 * @description IP级别Key生成器
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterIpKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        String clientIp = context.getClientIp();
        if (clientIp == null) {
            throw new IllegalArgumentException("ClientIp不能为null");
        }

        // 从枚举获取前缀
        String prefix = SmartRedisLimiterKeyStrategy.IP.getCode();
        return prefix + ":" + clientIp;
    }
}
