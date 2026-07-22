package io.github.surezzzzzz.sdk.limiter.redis.smart.management.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Management ETag 构建工具
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterEtagHelper {

    private SmartRedisLimiterEtagHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建服务 revision 强 ETag
     *
     * @param serviceCode 服务编码
     * @param revision    服务 revision
     * @return 带引号的 ETag
     */
    public static String build(String serviceCode, long revision) {
        String canonical = String.format(
                SmartRedisLimiterManagementConstant.TEMPLATE_ETAG_CANONICAL,
                serviceCode.getBytes(StandardCharsets.UTF_8).length,
                serviceCode,
                String.valueOf(revision).length(),
                revision);
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    SmartRedisLimiterManagementConstant.DIGEST_ALGORITHM_SHA_256);
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(
                        SmartRedisLimiterManagementConstant.FORMAT_HEX_BYTE,
                        value & 0xff));
            }
            return SmartRedisLimiterManagementConstant.ETAG_QUOTE
                    + SmartRedisLimiterManagementConstant.ETAG_PREFIX
                    + builder
                    + SmartRedisLimiterManagementConstant.ETAG_QUOTE;
        } catch (NoSuchAlgorithmException ex) {
            throw new SmartRedisLimiterManagementException(
                    ErrorCode.SNAPSHOT_FAILED, ErrorMessage.SNAPSHOT_FAILED, ex);
        }
    }

    /**
     * 判断 If-None-Match 是否命中当前 ETag
     *
     * @param ifNoneMatch 请求 Header
     * @param currentEtag 当前 ETag
     * @return 是否命中
     */
    public static boolean matches(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.trim().isEmpty()) {
            return false;
        }
        String[] values = ifNoneMatch.split(
                SmartRedisLimiterManagementConstant.ETAG_SEPARATOR);
        for (String value : values) {
            String normalized = value.trim();
            if (normalized.startsWith(SmartRedisLimiterManagementConstant.ETAG_WEAK_PREFIX)) {
                normalized = normalized.substring(
                        SmartRedisLimiterManagementConstant.ETAG_WEAK_PREFIX.length());
            }
            if (currentEtag.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
