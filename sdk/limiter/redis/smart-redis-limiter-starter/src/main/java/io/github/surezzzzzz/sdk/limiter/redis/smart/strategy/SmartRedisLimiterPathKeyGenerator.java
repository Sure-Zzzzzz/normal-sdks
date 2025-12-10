package io.github.surezzzzzz.sdk.limiter.redis.smart.strategy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @author: Sure.
 * @description 路径级别Key生成器（独立限流）
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterPathKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        String path = context.getRequestPath();
        if (path == null) {
            throw new IllegalArgumentException("RequestPath不能为null");
        }

        // 从枚举获取前缀
        String prefix = SmartRedisLimiterKeyStrategy.PATH.getCode();

        // 支持包含HTTP方法
        String method = context.getRequestMethod();
        if (method != null && !method.isEmpty()) {
            return prefix + ":" + path + ":" + method;
        }

        return prefix + ":" + path;
    }
}
