package io.github.surezzzzzz.sdk.cache.configuration;

import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

/**
 * Smart Cache Properties
 * <p>
 * 全局配置类
 * </p>
 *
 * @author Sure
 */
@Slf4j
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.cache")
public class SmartCacheProperties {

    /**
     * 是否启用缓存
     */
    private boolean enabled = true;

    /**
     * Redis Key 前缀（用于数据存储、分布式锁等）
     */
    private String keyPrefix = SmartCacheConstant.REDIS_KEY_PREFIX;

    /**
     * 应用实例标识（用于 Redis Key 构建）
     */
    private String me = SmartCacheConstant.DEFAULT_INSTANCE_ID;

    /**
     * L1 缓存配置
     */
    private L1Config l1 = new L1Config();

    /**
     * L2 缓存配置
     */
    private L2Config l2 = new L2Config();

    /**
     * 一致性配置
     */
    private ConsistencyConfig consistency = new ConsistencyConfig();

    /**
     * 统计配置
     */
    private StatsConfig stats = new StatsConfig();

    /**
     * 预热配置
     */
    private WarmUpConfig warmUp = new WarmUpConfig();

    /**
     * 分布式锁配置
     */
    private LockConfig lock = new LockConfig();

    /**
     * 配置校验
     */
    @PostConstruct
    public void validate() {
        // 校验 keyPrefix
        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            log.warn("keyPrefix is null or empty, using default: {}", SmartCacheConstant.REDIS_KEY_PREFIX);
            keyPrefix = SmartCacheConstant.REDIS_KEY_PREFIX;
        }

        // 校验 me
        if (me == null || me.trim().isEmpty()) {
            log.warn("me (instance id) is null or empty, using default: {}", SmartCacheConstant.DEFAULT_INSTANCE_ID);
            me = SmartCacheConstant.DEFAULT_INSTANCE_ID;
        }

        // 校验至少启用一个缓存层
        if (!l1.enabled && !l2.enabled) {
            log.error("Both L1 and L2 cache are disabled, enabling L1 cache by default");
            l1.enabled = true;
        }

        // 校验 L1 配置
        if (l1.maxSize < 1 || l1.maxSize > SmartCacheConstant.MAX_L1_MAX_SIZE) {
            log.warn("L1 maxSize {} is out of range [1, {}], using default {}",
                    l1.maxSize, SmartCacheConstant.MAX_L1_MAX_SIZE, SmartCacheConstant.DEFAULT_L1_MAX_SIZE);
            l1.maxSize = SmartCacheConstant.DEFAULT_L1_MAX_SIZE;
        }
        if (l1.expireSeconds < 1) {
            log.warn("L1 expireSeconds {} is invalid, using default {}", l1.expireSeconds, SmartCacheConstant.DEFAULT_L1_EXPIRE_SECONDS);
            l1.expireSeconds = SmartCacheConstant.DEFAULT_L1_EXPIRE_SECONDS;
        }
        if (l1.refreshSeconds < 1) {
            log.warn("L1 refreshSeconds {} is invalid, using default {}", l1.refreshSeconds, SmartCacheConstant.DEFAULT_L1_REFRESH_SECONDS);
            l1.refreshSeconds = SmartCacheConstant.DEFAULT_L1_REFRESH_SECONDS;
        }
        if (l1.refreshSeconds < SmartCacheConstant.MIN_L1_REFRESH_SECONDS) {
            log.warn("L1 refreshSeconds {} is too small (< {}s), adjusting to {}",
                    l1.refreshSeconds, SmartCacheConstant.MIN_L1_REFRESH_SECONDS, SmartCacheConstant.MIN_L1_REFRESH_SECONDS);
            l1.refreshSeconds = SmartCacheConstant.MIN_L1_REFRESH_SECONDS;
        }
        if (l1.refreshSeconds >= l1.expireSeconds) {
            log.warn("L1 refreshSeconds {} should be less than expireSeconds {}, adjusting",
                    l1.refreshSeconds, l1.expireSeconds);
            l1.refreshSeconds = Math.max(SmartCacheConstant.MIN_L1_REFRESH_SECONDS, l1.expireSeconds - SmartCacheConstant.L1_REFRESH_EXPIRE_BUFFER_SECONDS);
        }

        // 校验 L2 配置
        if (l2.expireSeconds < 1) {
            log.warn("L2 expireSeconds {} is invalid, using default {}", l2.expireSeconds, SmartCacheConstant.DEFAULT_L2_EXPIRE_SECONDS);
            l2.expireSeconds = SmartCacheConstant.DEFAULT_L2_EXPIRE_SECONDS;
        }
        if (l2.ttlRandomOffsetRatio < 0 || l2.ttlRandomOffsetRatio > 1) {
            log.warn("L2 ttlRandomOffsetRatio {} is out of range [0, 1], using default {}",
                    l2.ttlRandomOffsetRatio, SmartCacheConstant.DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO);
            l2.ttlRandomOffsetRatio = SmartCacheConstant.DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO;
        }

        // 校验 L2 preload 配置
        if (l2.preload.enabled) {
            if (l2.preload.beforeExpireSeconds <= 0) {
                log.warn("L2 preload.before-expire-seconds {} is invalid (must be > 0), disabling preload",
                        l2.preload.beforeExpireSeconds);
                l2.preload.enabled = false;
            } else if (l2.preload.beforeExpireSeconds >= l2.expireSeconds) {
                log.warn("L2 preload.before-expire-seconds {} must be < expire-seconds {}, disabling preload",
                        l2.preload.beforeExpireSeconds, l2.expireSeconds);
                l2.preload.enabled = false;
            }
        }

        // 校验一致性模式
        if (consistency.mode == null || consistency.mode.trim().isEmpty()) {
            log.warn("Consistency mode is null or empty, using default: {}", SmartCacheConstant.CONSISTENCY_MODE_STRONG);
            consistency.mode = SmartCacheConstant.CONSISTENCY_MODE_STRONG;
        } else if (!consistency.mode.equals(SmartCacheConstant.CONSISTENCY_MODE_EVENTUAL)
                && !consistency.mode.equals(SmartCacheConstant.CONSISTENCY_MODE_STRONG)) {
            log.warn("Invalid consistency mode: {}, must be '{}' or '{}', using default: {}",
                    consistency.mode,
                    SmartCacheConstant.CONSISTENCY_MODE_EVENTUAL,
                    SmartCacheConstant.CONSISTENCY_MODE_STRONG,
                    SmartCacheConstant.CONSISTENCY_MODE_STRONG);
            consistency.mode = SmartCacheConstant.CONSISTENCY_MODE_STRONG;
        }

        // 校验预热配置
        if (warmUp.completionMarkTtlSeconds < SmartCacheConstant.MIN_WARMUP_COMPLETION_MARK_TTL_SECONDS
                || warmUp.completionMarkTtlSeconds > SmartCacheConstant.MAX_WARMUP_COMPLETION_MARK_TTL_SECONDS) {
            log.warn("WarmUp completionMarkTtlSeconds {} is out of range [{}, {}], using default {}",
                    warmUp.completionMarkTtlSeconds,
                    SmartCacheConstant.MIN_WARMUP_COMPLETION_MARK_TTL_SECONDS,
                    SmartCacheConstant.MAX_WARMUP_COMPLETION_MARK_TTL_SECONDS,
                    SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS);
            warmUp.completionMarkTtlSeconds = SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;
        }

        // 校验分布式锁配置
        if (lock.timeoutSeconds < SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS
                || lock.timeoutSeconds > SmartCacheConstant.MAX_LOCK_TIMEOUT_SECONDS) {
            log.warn("Lock timeoutSeconds {} is out of range [{}, {}], using default {}",
                    lock.timeoutSeconds,
                    SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS,
                    SmartCacheConstant.MAX_LOCK_TIMEOUT_SECONDS,
                    SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS);
            lock.timeoutSeconds = SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
        }

        log.info("Smart Cache Properties validated successfully");
    }

    @Data
    @NoArgsConstructor
    public static class L1Config {
        /**
         * 是否启用 L1 缓存
         */
        private boolean enabled = true;

        /**
         * 最大容量
         */
        private int maxSize = SmartCacheConstant.DEFAULT_L1_MAX_SIZE;

        /**
         * 过期时间（秒）
         */
        private int expireSeconds = SmartCacheConstant.DEFAULT_L1_EXPIRE_SECONDS;

        /**
         * 刷新时间（秒），用于异步刷新
         */
        private int refreshSeconds = SmartCacheConstant.DEFAULT_L1_REFRESH_SECONDS;
    }

    @Data
    @NoArgsConstructor
    public static class L2Config {
        /**
         * 是否启用 L2 缓存
         */
        private boolean enabled = true;

        /**
         * 过期时间（秒）
         */
        private int expireSeconds = SmartCacheConstant.DEFAULT_L2_EXPIRE_SECONDS;

        /**
         * TTL 随机偏移比例（0-1），用于防止缓存雪崩
         */
        private double ttlRandomOffsetRatio = SmartCacheConstant.DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO;

        /**
         * Redis Key 前缀（已废弃，请使用 keyFormat）
         *
         * @deprecated 使用 keyFormat 替代
         */
        @Deprecated
        private String keyPrefix;

        /**
         * Redis Key 格式模板，支持占位符：
         * <ul>
         *   <li>{keyPrefix} - key 前缀（来自全局配置 io.github.surezzzzzz.sdk.cache.keyPrefix）</li>
         *   <li>{cacheName} - 缓存名称</li>
         *   <li>{me} - 实例标识（来自全局配置 io.github.surezzzzzz.sdk.cache.me）</li>
         *   <li>{key} - 缓存 key（带 hash tag）</li>
         * </ul>
         * 默认格式：{keyPrefix}:{cacheName}:{me}::{key}
         * <p>
         * 示例：
         * <ul>
         *   <li>{keyPrefix}:{cacheName}:{me}::{key} - 默认格式（SmartCache 标准格式）</li>
         *   <li>{keyPrefix}:{me}:{cacheName}::{key} - AKSK 老格式</li>
         *   <li>custom-prefix:{cacheName}:{me}::{key} - 自定义前缀</li>
         * </ul>
         */
        private String keyFormat = "{keyPrefix}:{cacheName}:{me}::{key}";

        /**
         * L2 异步续期配置
         * <p>
         * L2 条目剩余 TTL &lt; before-expire-seconds 时，异步触发 loader 提前续期，
         * 当前请求返回旧值，续期完成后写回 L2（同时更新 L1）。
         * 目的：在 L2 TTL 到期前提供容错窗口，避免 loader 失败时请求直接失败。
         */
        private PreloadConfig preload = new PreloadConfig();

        @Data
        @NoArgsConstructor
        public static class PreloadConfig {
            /**
             * 是否启用异步续期，默认关闭
             */
            private boolean enabled = false;

            /**
             * 提前多少秒触发续期，需 &lt; l2.expire-seconds
             */
            private int beforeExpireSeconds = SmartCacheConstant.DEFAULT_L2_PRELOAD_BEFORE_EXPIRE_SECONDS;
        }
    }

    /**
     * 获取 Pub/Sub 频道前缀
     * 如果未配置，则使用 keyPrefix + PUBSUB_CHANNEL_SUFFIX
     *
     * @return Pub/Sub 频道前缀
     */
    public String getPubsubChannelPrefix() {
        if (consistency != null) {
            String customPrefix = consistency.getPubsubChannelPrefix();
            if (customPrefix != null && !customPrefix.isEmpty()) {
                return customPrefix;
            }
        }
        return keyPrefix + SmartCacheConstant.PUBSUB_CHANNEL_SUFFIX;
    }

    @Data
    @NoArgsConstructor
    public static class ConsistencyConfig {
        /**
         * 一致性模式：eventual（最终一致性）、strong（强一致性）
         * 默认：strong（推荐，适合多实例部署，单实例也可用）
         */
        private String mode = SmartCacheConstant.CONSISTENCY_MODE_STRONG;

        /**
         * Pub/Sub 频道前缀
         * 如果不设置，默认使用 {keyPrefix}-cache-invalidation
         */
        private String pubsubChannelPrefix;
    }

    @Data
    @NoArgsConstructor
    public static class StatsConfig {
        /**
         * 是否启用统计
         */
        private boolean enabled = true;
    }

    @Data
    @NoArgsConstructor
    public static class WarmUpConfig {
        /**
         * 预热完成标记 TTL（秒）
         * 用于防止滚动发布时重复预热
         */
        private int completionMarkTtlSeconds = SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;
    }

    @Data
    @NoArgsConstructor
    public static class LockConfig {
        /**
         * 分布式锁超时时间（秒）
         * 用于防止缓存击穿时的并发加载
         */
        private int timeoutSeconds = SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
    }
}
