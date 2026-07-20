package io.github.surezzzzzz.sdk.cache.support;

import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;

/**
 * Key 辅助类
 * <p>
 * 统一管理 Redis Key 生成逻辑
 * </p>
 *
 * @author Sure
 */
public class KeyHelper {

    /**
     * 构建缓存数据 Key（使用自定义格式模板）
     * <p>
     * 支持的占位符：
     * <ul>
     *   <li>{keyPrefix} - key 前缀</li>
     *   <li>{cacheName} - 缓存名称</li>
     *   <li>{me} - 实例标识</li>
     *   <li>{key} - 缓存 key（自动添加缓存命名空间 hash tag）</li>
     * </ul>
     * <p>
     * 注意：{key} 占位符会自动添加由 keyPrefix、cacheName、me 组成的 hash tag，确保 Redis Cluster 模式下同一缓存命名空间的 key 在同一个 slot
     *
     * @param keyFormat 格式模板，如 "{keyPrefix}:{cacheName}:{me}::{key}"
     * @param keyPrefix key 前缀
     * @param cacheName 缓存名称
     * @param me        实例标识
     * @param key       缓存 key
     * @return 完整的 Redis key
     */
    public static String buildCacheKey(String keyFormat, String keyPrefix, String cacheName, String me, String key) {
        // 缓存命名空间必须共享 hash tag，确保 Redis Cluster 模式下批量操作的 key 在同一个 slot
        String cacheNamespaceHashTag = SmartCacheConstant.HASH_TAG_PREFIX + keyPrefix
                + SmartCacheConstant.KEY_SEPARATOR + cacheName
                + SmartCacheConstant.KEY_SEPARATOR + me
                + SmartCacheConstant.HASH_TAG_SUFFIX;
        String keyWithHashTag = cacheNamespaceHashTag + key;

        return keyFormat
                .replace("{keyPrefix}", keyPrefix)
                .replace("{cacheName}", cacheName)
                .replace("{me}", me)
                .replace("{key}", keyWithHashTag);
    }

    /**
     * 构建缓存数据 Key（使用默认格式）
     * 格式：{keyPrefix}:{cacheName}:{me}::{key}
     *
     * @deprecated 使用 buildCacheKey(String keyFormat, ...) 替代
     */
    @Deprecated
    public static String buildCacheKey(String keyPrefix, String cacheName, String me, String key) {
        return buildCacheKey(SmartCacheConstant.DEFAULT_L2_KEY_FORMAT, keyPrefix, cacheName, me, key);
    }

    /**
     * 构建缓存数据 Key 的匹配模式（使用自定义格式模板）
     * <p>
     * 将 {key} 占位符替换为 *，用于 Redis SCAN 命令
     *
     * @param keyFormat 格式模板
     * @param keyPrefix key 前缀
     * @param cacheName 缓存名称
     * @param me        实例标识
     * @return key 匹配模式
     */
    public static String buildCacheKeyPattern(String keyFormat, String keyPrefix, String cacheName, String me) {
        return keyFormat
                .replace("{keyPrefix}", keyPrefix)
                .replace("{cacheName}", cacheName)
                .replace("{me}", me)
                .replace("{key}", "*");
    }

    /**
     * 构建缓存数据 Key 的匹配模式（使用默认格式）
     * 格式：{keyPrefix}:{cacheName}:{me}::*
     *
     * @deprecated 使用 buildCacheKeyPattern(String keyFormat, ...) 替代
     */
    @Deprecated
    public static String buildCacheKeyPattern(String keyPrefix, String cacheName, String me) {
        return keyPrefix + SmartCacheConstant.KEY_SEPARATOR +
                cacheName + SmartCacheConstant.KEY_SEPARATOR +
                me + SmartCacheConstant.KEY_SEPARATOR +
                SmartCacheConstant.KEY_SEPARATOR + "*";
    }

    /**
     * 构建分布式锁 Key
     * 格式：{keyPrefix}-lock:{cacheName}:{me}:{key}
     * 注意：锁 key 必须包含 me（实例标识），避免多应用/多实例共用 Redis 时锁冲突
     */
    public static String buildLockKey(String keyPrefix, String cacheName, String me, String key) {
        return keyPrefix + SmartCacheConstant.LOCK_KEY_SUFFIX + SmartCacheConstant.KEY_SEPARATOR +
                cacheName + SmartCacheConstant.KEY_SEPARATOR +
                me + SmartCacheConstant.KEY_SEPARATOR + key;
    }

    /**
     * 构建预刷新分布式锁 Key
     * 格式：{keyPrefix}:preload-lock:{cacheName}:{me}:{key}
     * 注意：锁 key 必须包含 me，避免不同缓存应用组共用 Redis 时互相抑制预刷新
     */
    public static String buildPreloadLockKey(String keyPrefix, String cacheName, String me, String key) {
        return keyPrefix + SmartCacheConstant.PRELOAD_LOCK_KEY_SUFFIX + cacheName
                + SmartCacheConstant.KEY_SEPARATOR + me + SmartCacheConstant.KEY_SEPARATOR + key;
    }

    /**
     * 构建预热元数据 Key
     * 格式：{keyPrefix}:{cacheName}:{me}:{metadataSuffix}
     * 注意：元数据 key 必须包含 me，避免不同缓存应用组共享预热状态
     */
    public static String buildWarmUpMetadataKey(String keyPrefix, String cacheName, String me, String metadataSuffix) {
        return keyPrefix + SmartCacheConstant.KEY_SEPARATOR + cacheName + SmartCacheConstant.KEY_SEPARATOR
                + me + SmartCacheConstant.KEY_SEPARATOR + metadataSuffix;
    }

    /**
     * 构建 Pub/Sub 频道名称
     * 格式：{pubsubChannelPrefix}:{me}:{cacheName}
     * 注意：频道必须包含 me（应用标识），避免多应用共用 Redis 时消息串扰
     */
    public static String buildPubSubChannel(String pubsubChannelPrefix, String me, String cacheName) {
        return pubsubChannelPrefix + SmartCacheConstant.KEY_SEPARATOR +
                me + SmartCacheConstant.KEY_SEPARATOR + cacheName;
    }

    private KeyHelper() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }
}
