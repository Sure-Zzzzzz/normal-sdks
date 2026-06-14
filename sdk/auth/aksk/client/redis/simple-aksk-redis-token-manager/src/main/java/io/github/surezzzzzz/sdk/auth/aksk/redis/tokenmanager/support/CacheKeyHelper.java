package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support;

import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.exception.CacheKeyGenerationException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant.*;

/**
 * Cache Key Helper
 *
 * <p>缓存 Key 生成工具：基于 SHA-256 截断哈希，避免 hashCode 32-bit 碰撞导致跨用户串号。
 *
 * @author surezzzzzz
 */
public final class CacheKeyHelper {

    private CacheKeyHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 根据 securityContext 生成缓存 Key
     *
     * <p>算法：SHA-256(securityContext) 取前 16 字节 hex 编码（128-bit）。
     * <p>null / 空字符串 → 返回固定值 {@link io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant#DEFAULT_CACHE_KEY}。
     *
     * @param securityContext 安全上下文（JSON 字符串或 null）
     * @return 缓存 Key
     * @throws CacheKeyGenerationException 哈希算法不可用时抛出（理论不会发生）
     */
    public static String generate(String securityContext) {
        if (!StringUtils.hasText(securityContext)) {
            return DEFAULT_CACHE_KEY;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(CACHE_KEY_HASH_ALGORITHM);
            byte[] full = digest.digest(securityContext.getBytes(StandardCharsets.UTF_8));
            return toHex(full, CACHE_KEY_HASH_TRUNCATE_BYTES);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 1.4+ 必备算法，理论不可能进入此分支
            throw new CacheKeyGenerationException(
                    ErrorCode.CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE,
                    String.format(ErrorMessage.CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE, CACHE_KEY_HASH_ALGORITHM),
                    e);
        }
    }

    private static String toHex(byte[] bytes, int len) {
        char[] hex = new char[len * 2];
        for (int i = 0; i < len; i++) {
            int b = bytes[i] & 0xff;
            hex[i * 2] = HEX[b >>> 4];
            hex[i * 2 + 1] = HEX[b & 0x0f];
        }
        return new String(hex);
    }
}
