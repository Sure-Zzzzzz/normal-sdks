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
 * 路径级别Key生成器（独立限流）
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmartRedisLimiterPathKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        String path = context.getRequestPath();
        if (path == null) {
            throw new SmartRedisLimiterKeyException(
                    ErrorCode.KEY_PART_REQUEST_PATH_MISSING,
                    ErrorMessage.KEY_PART_REQUEST_PATH_MISSING);
        }

        String prefix = SmartRedisLimiterKeyStrategy.PATH.getCode();

        String httpMethod = context.getRequestMethod();
        if (httpMethod != null && !httpMethod.isEmpty()) {
            return String.format(SmartRedisLimiterConstant.TEMPLATE_KEY_PATH_WITH_METHOD, prefix, path, httpMethod);
        }

        return String.format(SmartRedisLimiterConstant.TEMPLATE_KEY_PATH, prefix, path);
    }
}
