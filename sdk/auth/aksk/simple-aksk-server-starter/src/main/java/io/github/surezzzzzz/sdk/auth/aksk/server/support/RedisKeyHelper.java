package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import lombok.RequiredArgsConstructor;

/**
 * Redis Key Helper
 * 作为所有Redis Key的唯一出口，统一管理Redis Key的生成
 *
 * <p>Key格式说明：
 * <ul>
 *   <li>Authorization by ID: sure-auth-aksk:{me}:oauth2:authorization::{id}</li>
 *   <li>Authorization by Token: sure-auth-aksk:{me}:oauth2:authorization:token::{token}:{tokenType}</li>
 *   <li>Scan Pattern: sure-auth-aksk:{me}:oauth2:authorization::*</li>
 *   <li>Cache Key by ID: {id}</li>
 *   <li>Cache Key by Token: {token}:{tokenType}</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class RedisKeyHelper {

    private final SimpleAkskServerProperties properties;

    // ==================== 公共常量 ====================

    /**
     * Redis Key前缀模板: sure-auth-aksk:{me}:
     * 参数: application name (me)
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

    // ==================== 私有常量 ====================

    /**
     * Key分隔符（双冒号）
     */
    private static final String SEPARATOR_DOUBLE_COLON = "::";

    /**
     * Key分隔符（单冒号）
     */
    private static final String SEPARATOR_COLON = ":";

    /**
     * 通配符
     */
    private static final String WILDCARD = "*";

    /**
     * 大括号前缀
     */
    private static final String BRACE_PREFIX = "{";

    /**
     * 大括号后缀
     */
    private static final String BRACE_SUFFIX = "}";

    /**
     * null字符串
     */
    private static final String STRING_NULL = "null";

    // ==================== 公共方法 ====================

    /**
     * 构建用于扫描所有授权记录的Key Pattern
     * 格式: sure-auth-aksk:{me}:oauth2:authorization::*
     *
     * @return Key Pattern
     */
    public String buildAuthorizationScanPattern() {
        String me = properties.getRedis().getToken().getMe();
        return String.format(REDIS_KEY_PREFIX_TEMPLATE, me)
                + CACHE_OAUTH2_AUTHORIZATION
                + SEPARATOR_DOUBLE_COLON
                + WILDCARD;
    }

    /**
     * 构建单个授权记录的完整Redis Key（用于Repository层）
     * 格式: sure-auth-aksk:{me}:oauth2:authorization::{id}
     *
     * @param id 授权ID
     * @return 完整Redis Key
     */
    public String buildAuthorizationKeyById(String id) {
        String me = properties.getRedis().getToken().getMe();
        return String.format(REDIS_KEY_PREFIX_TEMPLATE, me)
                + CACHE_OAUTH2_AUTHORIZATION
                + SEPARATOR_DOUBLE_COLON
                + BRACE_PREFIX
                + id
                + BRACE_SUFFIX;
    }

    /**
     * 构建缓存层使用的Key（用于CachedOAuth2AuthorizationService）
     * 格式: {id}
     *
     * <p>注意：Spring Cache会自动添加cacheName前缀，所以这里只需要返回{id}部分
     *
     * @param id 授权ID
     * @return 缓存Key
     */
    public String buildCacheKeyById(String id) {
        return BRACE_PREFIX + id + BRACE_SUFFIX;
    }

    /**
     * 构建缓存层使用的Token Key（用于CachedOAuth2AuthorizationService）
     * 格式: {token}:tokenType 或 {token}:null（当tokenType为空时）
     *
     * <p>注意：Spring Cache会自动添加cacheName前缀，所以这里只需要返回{token}:type部分
     *
     * @param token     Token值
     * @param tokenType Token类型（可能为null）
     * @return 缓存Key
     */
    public String buildCacheKeyByToken(String token, String tokenType) {
        return BRACE_PREFIX
                + token
                + BRACE_SUFFIX
                + SEPARATOR_COLON
                + (tokenType != null ? tokenType : STRING_NULL);
    }
}
