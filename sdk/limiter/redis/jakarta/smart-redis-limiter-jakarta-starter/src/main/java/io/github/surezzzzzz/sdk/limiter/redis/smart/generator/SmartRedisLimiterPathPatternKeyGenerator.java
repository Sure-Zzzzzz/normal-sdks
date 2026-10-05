package io.github.surezzzzzz.sdk.limiter.redis.smart.generator;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 路径模式级别Key生成器（共享限流）
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmartRedisLimiterPathPatternKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        String pattern = context.getMatchedPathPattern();
        if (pattern == null) {
            String path = context.getRequestPath();
            if (path == null) {
                throw new SmartRedisLimiterKeyException(
                        ErrorCode.KEY_PART_PATH_PATTERN_MISSING,
                        ErrorMessage.KEY_PART_PATH_PATTERN_MISSING);
            }
            pattern = path;
        }

        String prefix = SmartRedisLimiterKeyStrategy.PATH_PATTERN.getCode();

        String httpMethod = context.getRequestMethod();
        if (httpMethod != null && !httpMethod.isEmpty()) {
            return String.format(SmartRedisLimiterConstant.TEMPLATE_KEY_PATH_PATTERN_WITH_METHOD, prefix, pattern, httpMethod);
        }

        return String.format(SmartRedisLimiterConstant.TEMPLATE_KEY_PATH_PATTERN, prefix, pattern);
    }
}
