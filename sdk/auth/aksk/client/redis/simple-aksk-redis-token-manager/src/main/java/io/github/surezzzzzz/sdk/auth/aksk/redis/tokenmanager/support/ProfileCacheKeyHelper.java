package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.exception.CacheKeyGenerationException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant.CACHE_KEY_HASH_ALGORITHM;
import static io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant.CACHE_KEY_HASH_TRUNCATE_BYTES;

/**
 * Profile 缓存 Key 生成工具
 *
 * <p>default 保持旧 key；命名 Profile 使用 Profile、凭据身份哈希和安全上下文三层隔离。
 *
 * @author surezzzzzz
 */
public final class ProfileCacheKeyHelper {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProfileCacheKeyHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 Profile 缓存 Key
     *
     * @param profile         Profile 名称
     * @param credentialHash  凭据身份哈希
     * @param securityContext 安全上下文
     * @return 缓存 Key
     */
    public static String generate(String profile, String credentialHash, String securityContext) {
        if (profile == null || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(profile)) {
            return CacheKeyHelper.generate(securityContext);
        }
        return profile + ':' + requireHash(credentialHash) + ':' + contextKey(securityContext);
    }

    private static String contextKey(String securityContext) {
        if (!StringUtils.hasText(securityContext)) {
            return SimpleAkskRedisTokenManagerConstant.DEFAULT_CACHE_KEY;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(CACHE_KEY_HASH_ALGORITHM);
            byte[] full = digest.digest(securityContext.getBytes(StandardCharsets.UTF_8));
            return toHex(full, CACHE_KEY_HASH_TRUNCATE_BYTES);
        } catch (NoSuchAlgorithmException exception) {
            throw new CacheKeyGenerationException(
                    ErrorCode.CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE,
                    String.format(ErrorMessage.CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE, CACHE_KEY_HASH_ALGORITHM),
                    exception);
        }
    }

    private static String requireHash(String credentialHash) {
        if (!StringUtils.hasText(credentialHash)) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP credentialHash"));
        }
        return credentialHash;
    }

    private static String toHex(byte[] bytes, int length) {
        char[] result = new char[length * 2];
        for (int i = 0; i < length; i++) {
            result[i * 2] = HEX[(bytes[i] >>> 4) & 0x0f];
            result[i * 2 + 1] = HEX[bytes[i] & 0x0f];
        }
        return new String(result);
    }
}
