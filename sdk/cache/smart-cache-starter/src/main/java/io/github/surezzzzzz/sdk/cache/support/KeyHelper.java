package io.github.surezzzzzz.sdk.cache.support;

import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;

/**
 * Key Helper
 * <p>
 * 统一管理 Redis Key 生成逻辑
 * </p>
 *
 * @author Sure
 */
public class KeyHelper {

    /**
     * 构建缓存数据 Key
     * 格式：{keyPrefix}:{cacheName}:{me}::{key}
     * Hash Tag 用于 Redis Cluster 确保相同 key 在同一个 slot
     */
    public static String buildCacheKey(String keyPrefix, String cacheName, String me, String key) {
        return keyPrefix + SmartCacheConstant.KEY_SEPARATOR +
                cacheName + SmartCacheConstant.KEY_SEPARATOR +
                me + SmartCacheConstant.KEY_SEPARATOR +
                SmartCacheConstant.KEY_SEPARATOR +
                SmartCacheConstant.HASH_TAG_PREFIX + key + SmartCacheConstant.HASH_TAG_SUFFIX;
    }

    /**
     * 构建缓存数据 Key 的匹配模式
     * 格式：{keyPrefix}:{cacheName}:{me}::*
     */
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
     * 构建 Pub/Sub 频道名称
     * 格式：{pubsubChannelPrefix}:{me}:{cacheName}
     * 注意：频道必须包含 me（应用标识），避免多应用共用 Redis 时消息串扰
     */
    public static String buildPubSubChannel(String pubsubChannelPrefix, String me, String cacheName) {
        return pubsubChannelPrefix + SmartCacheConstant.KEY_SEPARATOR +
                me + SmartCacheConstant.KEY_SEPARATOR + cacheName;
    }

    private KeyHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
