package io.github.surezzzzzz.sdk.limiter.redis.smart.generator;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * @author: Sure.
 * @description 路径级别Key生成器（独立限流）
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterPathKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        String path = context.getRequestPath();
        if (path == null) {
            throw new IllegalArgumentException(SmartRedisLimiterConstant.MSG_REQUEST_PATH_NULL);
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
