package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SmartRedisLimiter Key 辅助工具
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterKeyHelper {

    private SmartRedisLimiterKeyHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建限流基础 Key
     *
     * @param me      服务标识
     * @param keyPart key 片段
     * @return 基础 Key
     */
    public static String buildBaseKey(String me, String keyPart) {
        if (keyPart == null || keyPart.trim().isEmpty()) {
            throw new SmartRedisLimiterKeyException(
                    ErrorCode.KEY_PART_INVALID,
                    ErrorMessage.KEY_PART_INVALID);
        }
        return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX
                + me
                + SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR
                + normalizeKeyPart(keyPart);
    }

    /**
     * 构建动态策略基础 Key，原始 subject 仅用于本方法内计算摘要
     *
     * @param serviceCode  服务编码
     * @param resourceCode 稳定资源编码
     * @param subject      原始限流对象标识
     * @return 不包含原始 subject 的基础 Key
     */
    public static String buildPolicyBaseKey(String serviceCode, String resourceCode, String subject) {
        return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX
                + serviceCode
                + SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR
                + resourceCode
                + SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR
                + sha256(subject);
    }

    /**
     * 计算字符串 SHA-256 小写十六进制摘要
     *
     * @param value 待摘要字符串
     * @return SHA-256 摘要
     */
    public static String sha256(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new SmartRedisLimiterKeyException(
                    ErrorCode.KEY_PART_INVALID,
                    ErrorMessage.KEY_PART_INVALID);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    SmartRedisLimiterStarterConstant.DIGEST_ALGORITHM_SHA_256);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format(
                        SmartRedisLimiterStarterConstant.FORMAT_HEX_BYTE,
                        current & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new SmartRedisLimiterKeyException(
                    ErrorCode.KEY_PART_INVALID,
                    ErrorMessage.KEY_PART_INVALID,
                    ex);
        }
    }

    /**
     * 构建 2.0 fixed used counter 物理 Key
     *
     * @param baseKey       基础 Key
     * @param windowSeconds 窗口秒数
     * @param useHashTag    是否使用 Hash Tag
     * @return fixed used counter 物理 Key
     */
    public static String buildFixedUsedWindowKey(String baseKey,
                                                 long windowSeconds,
                                                 boolean useHashTag) {
        String suffix = SmartRedisLimiterStarterConstant.SUFFIX_FIXED_WINDOW_USED_V2
                + windowSeconds
                + SmartRedisLimiterRedisKeyConstant.SUFFIX_SECONDS;
        if (!useHashTag) {
            return baseKey + suffix;
        }
        String hashTag = baseKey.startsWith(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX)
                ? baseKey.substring(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX.length())
                : baseKey;
        return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX
                + SmartRedisLimiterRedisKeyConstant.HASH_TAG_LEFT
                + normalizeKeyPart(hashTag)
                + SmartRedisLimiterRedisKeyConstant.HASH_TAG_RIGHT
                + suffix;
    }

    /**
     * 构建窗口 Key
     *
     * @param baseKey       基础 Key
     * @param windowSeconds 窗口秒数
     * @param windowSuffix  窗口后缀
     * @param useHashTag    是否使用 Hash Tag
     * @return 窗口 Key
     */
    public static String buildWindowKey(String baseKey, long windowSeconds, String windowSuffix, boolean useHashTag) {
        String suffix = SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR + windowSeconds + windowSuffix;
        if (!useHashTag) {
            return baseKey + suffix;
        }
        String hashTag = baseKey.startsWith(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX)
                ? baseKey.substring(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX.length())
                : baseKey;
        return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX
                + SmartRedisLimiterRedisKeyConstant.HASH_TAG_LEFT
                + normalizeKeyPart(hashTag)
                + SmartRedisLimiterRedisKeyConstant.HASH_TAG_RIGHT
                + suffix;
    }

    /**
     * 规范化 Hash Tag 内容，避免调用方传入大括号破坏 Redis Cluster slot 计算
     *
     * @param keyPart key 片段
     * @return 规范化后的 key 片段
     */
    public static String normalizeKeyPart(String keyPart) {
        if (keyPart == null) {
            return null;
        }
        return keyPart
                .replace(SmartRedisLimiterRedisKeyConstant.HASH_TAG_LEFT, "_")
                .replace(SmartRedisLimiterRedisKeyConstant.HASH_TAG_RIGHT, "_");
    }
}
