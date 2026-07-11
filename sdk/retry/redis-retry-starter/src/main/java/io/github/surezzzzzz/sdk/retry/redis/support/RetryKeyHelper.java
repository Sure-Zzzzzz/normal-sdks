package io.github.surezzzzzz.sdk.retry.redis.support;

import io.github.surezzzzzz.sdk.retry.redis.annotation.RedisRetryComponent;
import io.github.surezzzzzz.sdk.retry.redis.configuration.RedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.constant.RedisRetryConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 重试 Redis Key Helper
 *
 * @author surezzzzzz
 */
@Slf4j
@RedisRetryComponent
@RequiredArgsConstructor
public class RetryKeyHelper {

    private final RedisRetryProperties properties;

    /**
     * 构建标准重试 Key
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param redisTemplate Redis 模板
     * @return 标准重试 Key
     */
    public String buildStandardKey(String retryType, String retryKey, RedisTemplate<String, String> redisTemplate) {
        String identifierHash = buildIdentifierHash(retryKey);
        if (shouldUseHashTag(redisTemplate)) {
            return String.format(RedisRetryConstant.TEMPLATE_STANDARD_HASH_TAG_KEY,
                    properties.getKeyPrefix(), retryType, properties.getMe(), identifierHash);
        }
        return String.format(RedisRetryConstant.TEMPLATE_STANDARD_KEY,
                properties.getKeyPrefix(), retryType, properties.getMe(), identifierHash);
    }

    /**
     * 构建 legacy 重试 Key
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param redisTemplate Redis 模板
     * @return legacy 重试 Key
     */
    public String buildLegacyKey(String retryType, String retryKey, RedisTemplate<String, String> redisTemplate) {
        String identifierHash = buildIdentifierHash(retryKey);
        if (shouldUseHashTag(redisTemplate)) {
            return String.format(RedisRetryConstant.TEMPLATE_LEGACY_HASH_TAG_KEY, retryType, identifierHash);
        }
        return String.format(RedisRetryConstant.TEMPLATE_LEGACY_KEY, retryType, identifierHash);
    }

    /**
     * 构建标准扫描 Pattern
     *
     * @param retryType 重试类型
     * @return 标准扫描 Pattern
     */
    public String buildStandardKeysPattern(String retryType) {
        return String.format(RedisRetryConstant.TEMPLATE_STANDARD_KEYS_PATTERN,
                properties.getKeyPrefix(), retryType, properties.getMe());
    }

    /**
     * 构建 legacy 扫描 Pattern
     *
     * @param retryType 重试类型
     * @param redisTemplate Redis 模板
     * @return legacy 扫描 Pattern
     */
    public String buildLegacyKeysPattern(String retryType, RedisTemplate<String, String> redisTemplate) {
        if (shouldUseHashTag(redisTemplate)) {
            return String.format(RedisRetryConstant.TEMPLATE_LEGACY_HASH_TAG_KEYS_PATTERN, retryType);
        }
        return String.format(RedisRetryConstant.TEMPLATE_LEGACY_KEYS_PATTERN, retryType);
    }

    /**
     * 构建标识摘要
     *
     * @param retryKey 重试标识
     * @return 标识摘要
     */
    public String buildIdentifierHash(String retryKey) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(RedisRetryConstant.HASH_ALGORITHM_SHA1);
            byte[] digest = messageDigest.digest(retryKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString().toUpperCase();
        } catch (Exception e) {
            log.warn("生成重试标识摘要失败，使用 hashCode 降级: retryKey={}", retryKey, e);
            return String.valueOf(Math.abs(retryKey.hashCode()));
        }
    }

    private boolean shouldUseHashTag(RedisTemplate<String, String> redisTemplate) {
        if (properties.getForceHashTag() != null) {
            return properties.getForceHashTag();
        }
        return detectRedisCluster(redisTemplate);
    }

    private boolean detectRedisCluster(RedisTemplate<String, String> redisTemplate) {
        if (redisTemplate.getConnectionFactory() == null) {
            return false;
        }
        String connectionFactoryClass = redisTemplate.getConnectionFactory().getClass().getName();
        return connectionFactoryClass.contains("Cluster") || connectionFactoryClass.contains("cluster");
    }
}
