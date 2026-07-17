package io.github.surezzzzzz.sdk.retry.redis.smart.support;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryOperationException;
import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 重试 Key Helper
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RetryKeyHelper {

    /**
     * Smart Redis Retry 配置
     */
    private final SmartRedisRetryProperties properties;

    /**
     * 构建 Redis 记录 Key
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @return Redis 记录 Key
     */
    public String buildRedisKey(String retryType, String retryKey) {
        String identifierHash = sha1Hex(retryKey);
        SmartRedisRetryProperties.RedisConfig redis = properties.getRedis();
        if (redis.isUseHashTag()) {
            return String.format(SmartRedisRetryConstant.KEY_HASH_TAG_TEMPLATE,
                    redis.getKeyPrefix(), SmartRedisRetryConstant.BUSINESS_TYPE_RETRY,
                    retryType, redis.getMe(), identifierHash);
        }
        return String.format(SmartRedisRetryConstant.KEY_TEMPLATE,
                redis.getKeyPrefix(), SmartRedisRetryConstant.BUSINESS_TYPE_RETRY,
                retryType, redis.getMe(), identifierHash);
    }

    /**
     * 构建扫描匹配表达式
     *
     * @param retryType 重试类型
     * @return 扫描匹配表达式
     */
    public String buildScanPattern(String retryType) {
        SmartRedisRetryProperties.RedisConfig redis = properties.getRedis();
        return String.format(SmartRedisRetryConstant.SCAN_PATTERN_TEMPLATE,
                redis.getKeyPrefix(), SmartRedisRetryConstant.BUSINESS_TYPE_RETRY,
                retryType, redis.getMe());
    }

    /**
     * 计算 SHA-1 十六进制摘要
     *
     * @param value 原始值
     * @return 十六进制摘要
     */
    public String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SmartRedisRetryConstant.HASH_ALGORITHM_SHA1);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * SmartRedisRetryConstant.HASH_HEX_CAPACITY_MULTIPLIER);
            for (byte b : bytes) {
                builder.append(String.format(SmartRedisRetryConstant.HASH_HEX_FORMAT,
                        b & SmartRedisRetryConstant.HASH_BYTE_MASK));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.RETRY_KEY_DIGEST_FAILED,
                    ErrorMessage.RETRY_KEY_DIGEST_FAILED, e);
        }
    }
}
