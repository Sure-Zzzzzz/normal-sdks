package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant.TOKEN_CACHE_KEY_HASH_ALGORITHM;
import static io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant.TOKEN_CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE;

/**
 * Redis Key Helper
 *
 * <p>Key格式说明：
 * <ul>
 *   <li>Authorization by ID: sure-auth-aksk:{me}:oauth2:authorization::{id}</li>
 *   <li>Authorization by Token: sure-auth-aksk:{me}:oauth2:authorization:token::{tokenSha256}:{tokenType}</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Component
@RequiredArgsConstructor
public class RedisKeyHelper {

    /**
     * Redis Key前缀模板: sure-auth-aksk:{me}:
     */
    public static final String REDIS_KEY_PREFIX_TEMPLATE = "sure-auth-aksk:%s:";

    // ==================== 公共常量 ====================
    /**
     * OAuth2 Authorization缓存名称（按ID索引）
     */
    public static final String CACHE_OAUTH2_AUTHORIZATION = "oauth2:authorization";
    /**
     * OAuth2 Authorization缓存名称（按Token索引）
     */
    public static final String CACHE_OAUTH2_AUTHORIZATION_TOKEN = "oauth2:authorization:token";
    /**
     * OAuth2 Registered Client Entity 缓存名称（按 clientId 索引）
     */
    public static final String CACHE_OAUTH2_CLIENT_ENTITY = "oauth2:client:entity";
    private static final String SEPARATOR_DOUBLE_COLON = "::";

    // ==================== 私有常量 ====================
    private static final String SEPARATOR_COLON = ":";
    private static final String WILDCARD = "*";
    private static final String BRACE_PREFIX = "{";
    private static final String BRACE_SUFFIX = "}";
    private static final String STRING_NULL = "null";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final SimpleAkskServerProperties properties;

    // ==================== 公共方法 ====================

    public String buildAuthorizationScanPattern() {
        String me = properties.getRedis().getToken().getMe();
        return String.format(REDIS_KEY_PREFIX_TEMPLATE, me)
                + CACHE_OAUTH2_AUTHORIZATION
                + SEPARATOR_DOUBLE_COLON
                + WILDCARD;
    }

    public String buildAuthorizationKeyById(String id) {
        String me = properties.getRedis().getToken().getMe();
        return String.format(REDIS_KEY_PREFIX_TEMPLATE, me)
                + CACHE_OAUTH2_AUTHORIZATION
                + SEPARATOR_DOUBLE_COLON
                + BRACE_PREFIX + id + BRACE_SUFFIX;
    }

    public String buildCacheKeyById(String id) {
        return BRACE_PREFIX + id + BRACE_SUFFIX;
    }

    public String buildCacheKeyByToken(String token, String tokenType) {
        return BRACE_PREFIX + sha256Hex(token) + BRACE_SUFFIX
                + SEPARATOR_COLON
                + (tokenType != null ? tokenType : STRING_NULL);
    }

    /**
     * 令牌缓存 key 只能保存不可逆摘要，避免 SmartCache 的锁、失效消息或 DEBUG 日志暴露 bearer 原文。
     */
    private String sha256Hex(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance(TOKEN_CACHE_KEY_HASH_ALGORITHM)
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            for (int index = 0; index < bytes.length; index++) {
                int current = bytes[index] & 0xff;
                hex[index * 2] = HEX[current >>> 4];
                hex[index * 2 + 1] = HEX[current & 0x0f];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 是 JDK 必备算法；不可用时不能退化为明文 key。
            throw new AkskException(TOKEN_CACHE_KEY_HASH_ALGORITHM_UNAVAILABLE, exception);
        }
    }
}
