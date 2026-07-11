package io.github.surezzzzzz.sdk.retry.redis.support;

import io.github.surezzzzzz.sdk.retry.redis.configuration.RedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.exception.RedisRetryException;
import org.springframework.util.StringUtils;

/**
 * 重试参数校验 Helper
 *
 * @author surezzzzzz
 */
public final class RetryValidationHelper {

    private RetryValidationHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验配置
     *
     * @param properties 配置属性
     */
    public static void validateProperties(RedisRetryProperties properties) {
        if (properties.getBaseDelayMs() >= properties.getMaxDelayMs()) {
            throw new RedisRetryException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    ErrorMessage.CONFIG_BASE_DELAY_MUST_LESS_THAN_MAX);
        }
    }

    /**
     * 判断文本是否有内容
     *
     * @param value 文本
     * @return true 有内容，false 无内容
     */
    public static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
