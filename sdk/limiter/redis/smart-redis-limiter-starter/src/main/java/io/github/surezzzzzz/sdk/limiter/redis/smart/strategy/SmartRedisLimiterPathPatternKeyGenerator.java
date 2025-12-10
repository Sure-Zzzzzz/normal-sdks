package io.github.surezzzzzz.sdk.limiter.redis.smart.strategy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @author: Sure.
 * @description 路径模式级别Key生成器（共享限流）
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterPathPatternKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        // 优先使用匹配到的路径模式
        String pattern = context.getMatchedPathPattern();
        if (pattern == null) {
            // 如果没有匹配到路径模式，降级使用实际路径
            String path = context.getRequestPath();
            if (path == null) {
                throw new IllegalArgumentException("RequestPath和MatchedPathPattern都为null");
            }
            pattern = path;
        }

        // 从枚举获取前缀
        String prefix = SmartRedisLimiterKeyStrategy.PATH_PATTERN.getCode();

        // 支持包含HTTP方法
        String method = context.getRequestMethod();
        if (method != null && !method.isEmpty()) {
            return prefix + ":" + pattern + ":" + method;
        }

        return prefix + ":" + pattern;
    }
}
