package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.support;

import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import lombok.RequiredArgsConstructor;

/**
 * Redis Key Helper
 * <p>
 * 统一管理所有 Redis Key 的生成，作为 Redis Key 的唯一出口
 * <p>
 * Key 格式说明：
 * <ul>
 *   <li>Token by Hash: sure-auth-aksk-client:{me}:token::{hash}</li>
 *   <li>Token Default: sure-auth-aksk-client:{me}:token::{default}</li>
 *   <li>Lock by Hash: sure-auth-aksk-client:{me}:lock:token::{hash}</li>
 *   <li>Lock Default: sure-auth-aksk-client:{me}:lock:token::{default}</li>
 * </ul>
 * <p>
 * 示例：
 * <ul>
 *   <li>sure-auth-aksk-client:my-app:token::{123456}</li>
 *   <li>sure-auth-aksk-client:my-app:token::{default}</li>
 *   <li>sure-auth-aksk-client:my-app:lock:token::{123456}</li>
 *   <li>sure-auth-aksk-client:my-app:lock:token::{default}</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@SimpleAkskRedisTokenManagerComponent
@RequiredArgsConstructor
public class RedisKeyHelper {

    private final SimpleAkskRedisTokenManagerProperties properties;

    // ==================== 公共常量 ====================

    /**
     * Redis Key 前缀模板: sure-auth-aksk-client:{me}:
     * <p>
     * 参数: application name (me)
     */
    public static final String REDIS_KEY_PREFIX_TEMPLATE = "sure-auth-aksk-client:%s:";

    /**
     * Token 缓存名称
     */
    public static final String CACHE_TOKEN = "token";

    /**
     * 分布式锁名称
     */
    public static final String CACHE_LOCK = "lock";

    // ==================== 私有常量 ====================

    /**
     * Key 分隔符（双冒号）
     */
    private static final String SEPARATOR_DOUBLE_COLON = "::";

    /**
     * 大括号前缀
     */
    private static final String BRACE_PREFIX = "{";

    /**
     * 大括号后缀
     */
    private static final String BRACE_SUFFIX = "}";

    /**
     * 默认 Key 标识
     */
    private static final String DEFAULT_KEY = "default";

    // ==================== 公共方法 ====================

    /**
     * 构建 Token 缓存的完整 Redis Key
     * <p>
     * 格式: sure-auth-aksk-client:{me}:token::{hash}
     * <p>
     * 大括号的作用：确保相同应用的所有 Token Key 使用相同的 Redis Slot（Redis Cluster）
     *
     * @param hash security_context 的 hashCode
     * @return 完整 Redis Key
     */
    public String buildTokenKey(int hash) {
        String me = properties.getRedis().getToken().getMe();
        return String.format(REDIS_KEY_PREFIX_TEMPLATE, me)
                + CACHE_TOKEN
                + SEPARATOR_DOUBLE_COLON
                + BRACE_PREFIX
                + hash
                + BRACE_SUFFIX;
    }

    /**
     * 构建默认的 Token Key（无 security_context 场景）
     * <p>
     * 格式: sure-auth-aksk-client:{me}:token::{default}
     *
     * @return 完整 Redis Key
     */
    public String buildDefaultTokenKey() {
        String me = properties.getRedis().getToken().getMe();
        return String.format(REDIS_KEY_PREFIX_TEMPLATE, me)
                + CACHE_TOKEN
                + SEPARATOR_DOUBLE_COLON
                + BRACE_PREFIX
                + DEFAULT_KEY
                + BRACE_SUFFIX;
    }

    /**
     * 构建分布式锁的 Redis Key（基于 Token Key）
     * <p>
     * 格式: sure-auth-aksk-client:{me}:lock:token::{hash}
     * <p>
     * 说明：锁 Key 与 Token Key 对应，确保相同 Token 的锁和缓存在同一应用隔离范围内
     *
     * @param tokenCacheKey Token 的缓存 Key
     * @return 分布式锁 Key
     */
    public String buildTokenLockKey(String tokenCacheKey) {
        String me = properties.getRedis().getToken().getMe();
        String prefix = String.format(REDIS_KEY_PREFIX_TEMPLATE, me);

        // 从 tokenCacheKey 中提取 hash 部分
        // tokenCacheKey 格式: sure-auth-aksk-client:{me}:token::{hash}
        // 提取 {hash} 部分
        String hashPart = tokenCacheKey.substring(prefix.length() + CACHE_TOKEN.length() + SEPARATOR_DOUBLE_COLON.length());

        return prefix
                + CACHE_LOCK
                + ":"
                + CACHE_TOKEN
                + SEPARATOR_DOUBLE_COLON
                + hashPart;
    }
}
