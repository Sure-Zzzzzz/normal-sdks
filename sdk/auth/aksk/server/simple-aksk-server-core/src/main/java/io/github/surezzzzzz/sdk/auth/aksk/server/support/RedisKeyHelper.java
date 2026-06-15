package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Redis Key Helper
 *
 * <p>Key格式说明：
 * <ul>
 *   <li>Authorization by ID: sure-auth-aksk:{me}:oauth2:authorization::{id}</li>
 *   <li>Authorization by Token: sure-auth-aksk:{me}:oauth2:authorization:token::{token}:{tokenType}</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Component
@RequiredArgsConstructor
public class RedisKeyHelper {

    private final SimpleAkskServerProperties properties;

    // ==================== 公共常量 ====================

    /**
     * Redis Key前缀模板: sure-auth-aksk:{me}:
     */
    public static final String REDIS_KEY_PREFIX_TEMPLATE = "sure-auth-aksk:%s:";

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

    // ==================== 私有常量 ====================

    private static final String SEPARATOR_DOUBLE_COLON = "::";
    private static final String SEPARATOR_COLON = ":";
    private static final String WILDCARD = "*";
    private static final String BRACE_PREFIX = "{";
    private static final String BRACE_SUFFIX = "}";
    private static final String STRING_NULL = "null";

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
        return BRACE_PREFIX + token + BRACE_SUFFIX
                + SEPARATOR_COLON
                + (tokenType != null ? tokenType : STRING_NULL);
    }
}
