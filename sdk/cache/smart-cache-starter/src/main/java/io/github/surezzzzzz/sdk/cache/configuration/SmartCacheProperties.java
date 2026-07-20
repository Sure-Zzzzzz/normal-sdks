package io.github.surezzzzzz.sdk.cache.configuration;

import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheConfigurationException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Smart Cache 配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Data
@NoArgsConstructor
@ConfigurationProperties(SmartCacheConstant.CONFIG_PREFIX)
public class SmartCacheProperties {

    /**
     * 是否启用缓存
     */
    private boolean enabled = true;

    /**
     * Redis Key 前缀
     */
    private String keyPrefix = SmartCacheConstant.REDIS_KEY_PREFIX;

    /**
     * 应用实例标识
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
     * Pub/Sub 配置
     */
    private PubSubConfig pubsub = new PubSubConfig();

    /**
     * route 配置
     */
    private RouteConfig route = new RouteConfig();

    /**
     * 序列化配置
     */
    private SerializerConfig serializer = new SerializerConfig();

    @PostConstruct
    public void validate() {
        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            log.warn("keyPrefix 为空，使用默认值：{}", SmartCacheConstant.REDIS_KEY_PREFIX);
            keyPrefix = SmartCacheConstant.REDIS_KEY_PREFIX;
        }
        if (me == null || me.trim().isEmpty()) {
            log.warn("me 为空，使用默认值：{}", SmartCacheConstant.DEFAULT_INSTANCE_ID);
            me = SmartCacheConstant.DEFAULT_INSTANCE_ID;
        }
        if (!l1.enabled && !l2.enabled) {
            log.warn("L1 和 L2 都已关闭，自动启用 L1");
            l1.enabled = true;
        }
        validateL1();
        validateL2();
        validateConsistency();
        validatePubSub();
        validateWarmUp();
        validateLock();
        validateRoute();
        validateSerializer();
        log.info("Smart Cache 配置校验完成");
    }

    private void validateL1() {
        if (l1.maxSize < 1 || l1.maxSize > SmartCacheConstant.MAX_L1_MAX_SIZE) {
            log.warn("L1 maxSize {} 超出范围，使用默认值 {}", l1.maxSize, SmartCacheConstant.DEFAULT_L1_MAX_SIZE);
            l1.maxSize = SmartCacheConstant.DEFAULT_L1_MAX_SIZE;
        }
        if (l1.expireSeconds < 1) {
            log.warn("L1 expireSeconds {} 无效，使用默认值 {}", l1.expireSeconds,
                    SmartCacheConstant.DEFAULT_L1_EXPIRE_SECONDS);
            l1.expireSeconds = SmartCacheConstant.DEFAULT_L1_EXPIRE_SECONDS;
        }
        if (l1.refreshSeconds < SmartCacheConstant.MIN_L1_REFRESH_SECONDS) {
            log.warn("L1 refreshSeconds {} 过小，调整为 {}", l1.refreshSeconds,
                    SmartCacheConstant.MIN_L1_REFRESH_SECONDS);
            l1.refreshSeconds = SmartCacheConstant.MIN_L1_REFRESH_SECONDS;
        }
        if (l1.refreshSeconds >= l1.expireSeconds) {
            log.warn("L1 refreshSeconds {} 不应大于等于 expireSeconds {}，自动调整", l1.refreshSeconds,
                    l1.expireSeconds);
            l1.refreshSeconds = Math.max(SmartCacheConstant.MIN_L1_REFRESH_SECONDS,
                    l1.expireSeconds - SmartCacheConstant.L1_REFRESH_EXPIRE_BUFFER_SECONDS);
        }
    }

    private void validateL2() {
        if (l2.expireSeconds < 1) {
            log.warn("L2 expireSeconds {} 无效，使用默认值 {}", l2.expireSeconds,
                    SmartCacheConstant.DEFAULT_L2_EXPIRE_SECONDS);
            l2.expireSeconds = SmartCacheConstant.DEFAULT_L2_EXPIRE_SECONDS;
        }
        if (l2.keyFormat == null || l2.keyFormat.trim().isEmpty()) {
            log.warn("L2 keyFormat 为空，使用默认值 {}", SmartCacheConstant.DEFAULT_L2_KEY_FORMAT);
            l2.keyFormat = SmartCacheConstant.DEFAULT_L2_KEY_FORMAT;
        }
        if (l2.ttlRandomOffsetRatio < 0 || l2.ttlRandomOffsetRatio > 1) {
            log.warn("L2 ttlRandomOffsetRatio {} 超出范围，使用默认值 {}", l2.ttlRandomOffsetRatio,
                    SmartCacheConstant.DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO);
            l2.ttlRandomOffsetRatio = SmartCacheConstant.DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO;
        }
        if (l2.preload.enabled) {
            if (l2.preload.beforeExpireSeconds <= 0 || l2.preload.beforeExpireSeconds >= l2.expireSeconds) {
                log.warn("L2 preload.beforeExpireSeconds {} 无效，关闭异步续期", l2.preload.beforeExpireSeconds);
                l2.preload.enabled = false;
            }
        }
        if (l2.preload.executorThreads < 1) {
            log.warn("L2 preload.executorThreads {} 无效，使用默认值 {}", l2.preload.executorThreads,
                    SmartCacheConstant.DEFAULT_PRELOAD_EXECUTOR_THREADS);
            l2.preload.executorThreads = SmartCacheConstant.DEFAULT_PRELOAD_EXECUTOR_THREADS;
        }
        if (l2.preload.executorQueueCapacity < 1) {
            log.warn("L2 preload.executorQueueCapacity {} 无效，使用默认值 {}", l2.preload.executorQueueCapacity,
                    SmartCacheConstant.DEFAULT_PRELOAD_EXECUTOR_QUEUE_CAPACITY);
            l2.preload.executorQueueCapacity = SmartCacheConstant.DEFAULT_PRELOAD_EXECUTOR_QUEUE_CAPACITY;
        }
    }

    private void validateConsistency() {
        if (consistency.mode == null || consistency.mode.trim().isEmpty()) {
            log.warn("一致性模式为空，使用默认值 {}", SmartCacheConstant.CONSISTENCY_MODE_STRONG);
            consistency.mode = SmartCacheConstant.CONSISTENCY_MODE_STRONG;
        } else if (!SmartCacheConstant.CONSISTENCY_MODE_EVENTUAL.equals(consistency.mode)
                && !SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(consistency.mode)) {
            log.warn("一致性模式 {} 无效，使用默认值 {}", consistency.mode,
                    SmartCacheConstant.CONSISTENCY_MODE_STRONG);
            consistency.mode = SmartCacheConstant.CONSISTENCY_MODE_STRONG;
        }
    }

    private void validatePubSub() {
        if (pubsub.mode == null || pubsub.mode.trim().isEmpty()) {
            pubsub.mode = SmartCacheConstant.PUBSUB_MODE_ROUTED;
        }
        if (!SmartCacheConstant.PUBSUB_MODE_ROUTED.equals(pubsub.mode)
                && !SmartCacheConstant.PUBSUB_MODE_DISABLED.equals(pubsub.mode)) {
            log.warn("Pub/Sub 模式 {} 无效，使用默认值 {}", pubsub.mode, SmartCacheConstant.PUBSUB_MODE_ROUTED);
            pubsub.mode = SmartCacheConstant.PUBSUB_MODE_ROUTED;
        }
        if (pubsub.channelPrefix == null || pubsub.channelPrefix.trim().isEmpty()) {
            pubsub.channelPrefix = SmartCacheConstant.DEFAULT_PUBSUB_CHANNEL_PREFIX;
        }
        if (SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(consistency.mode)
                && SmartCacheConstant.PUBSUB_MODE_DISABLED.equals(pubsub.mode)) {
            throw new CacheConfigurationException(
                    ErrorCode.SMART_CACHE_CONFIG_ERROR,
                    String.format(ErrorMessage.SMART_CACHE_CONFIG_ERROR, "强一致性模式不能关闭 Pub/Sub")
            );
        }
    }

    private void validateWarmUp() {
        if (warmUp.completionMarkTtlSeconds < SmartCacheConstant.MIN_WARMUP_COMPLETION_MARK_TTL_SECONDS
                || warmUp.completionMarkTtlSeconds > SmartCacheConstant.MAX_WARMUP_COMPLETION_MARK_TTL_SECONDS) {
            log.warn("预热完成标记 TTL {} 超出范围，使用默认值 {}", warmUp.completionMarkTtlSeconds,
                    SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS);
            warmUp.completionMarkTtlSeconds = SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;
        }
        if (warmUp.executorThreads < 1) {
            log.warn("预热线程数 {} 无效，使用默认值 {}", warmUp.executorThreads,
                    SmartCacheConstant.DEFAULT_WARMUP_EXECUTOR_THREADS);
            warmUp.executorThreads = SmartCacheConstant.DEFAULT_WARMUP_EXECUTOR_THREADS;
        }
        if (warmUp.executorQueueCapacity < 1) {
            log.warn("预热队列容量 {} 无效，使用默认值 {}", warmUp.executorQueueCapacity,
                    SmartCacheConstant.DEFAULT_WARMUP_EXECUTOR_QUEUE_CAPACITY);
            warmUp.executorQueueCapacity = SmartCacheConstant.DEFAULT_WARMUP_EXECUTOR_QUEUE_CAPACITY;
        }
        if (warmUp.failurePolicy == null || warmUp.failurePolicy.trim().isEmpty()) {
            log.warn("预热失败策略为空，使用默认值 {}", SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY);
            warmUp.failurePolicy = SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY;
        } else if (!SmartCacheConstant.WARMUP_FAILURE_POLICY_CONTINUE.equals(warmUp.failurePolicy)
                && !SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST.equals(warmUp.failurePolicy)) {
            log.warn("预热失败策略 {} 无效，使用默认值 {}", warmUp.failurePolicy,
                    SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY);
            warmUp.failurePolicy = SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY;
        }
    }

    private void validateLock() {
        if (lock.timeoutSeconds < SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS
                || lock.timeoutSeconds > SmartCacheConstant.MAX_LOCK_TIMEOUT_SECONDS) {
            log.warn("分布式锁超时时间 {} 超出范围，使用默认值 {}", lock.timeoutSeconds,
                    SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS);
            lock.timeoutSeconds = SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
        }
    }

    private void validateRoute() {
        if (route.scanCount == null || route.scanCount < 1) {
            route.scanCount = SmartCacheConstant.DEFAULT_SCAN_COUNT;
        }
    }

    private void validateSerializer() {
        if (serializer.trustedPackages == null || serializer.trustedPackages.isEmpty()) {
            serializer.trustedPackages = new ArrayList<>(Arrays.asList(
                    SmartCacheConstant.TRUSTED_PACKAGE_JAVA_LANG,
                    SmartCacheConstant.TRUSTED_PACKAGE_JAVA_TIME,
                    SmartCacheConstant.TRUSTED_PACKAGE_JAVA_UTIL
            ));
        }
    }

    public String getPubsubChannelPrefix() {
        return pubsub.channelPrefix;
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
         * 刷新时间（秒）
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
         * TTL 随机偏移比例
         */
        private double ttlRandomOffsetRatio = SmartCacheConstant.DEFAULT_L2_TTL_RANDOM_OFFSET_RATIO;

        /**
         * Redis Key 格式模板
         */
        private String keyFormat = SmartCacheConstant.DEFAULT_L2_KEY_FORMAT;

        /**
         * L2 异步续期配置
         */
        private PreloadConfig preload = new PreloadConfig();

        @Data
        @NoArgsConstructor
        public static class PreloadConfig {
            /**
             * 异步预刷新总开关
             */
            private boolean enabled = false;

            /**
             * 提前多少秒触发续期
             */
            private int beforeExpireSeconds = SmartCacheConstant.DEFAULT_L2_PRELOAD_BEFORE_EXPIRE_SECONDS;

            /**
             * 线程数
             */
            private int executorThreads = SmartCacheConstant.DEFAULT_PRELOAD_EXECUTOR_THREADS;

            /**
             * 队列容量
             */
            private int executorQueueCapacity = SmartCacheConstant.DEFAULT_PRELOAD_EXECUTOR_QUEUE_CAPACITY;
        }
    }

    @Data
    @NoArgsConstructor
    public static class ConsistencyConfig {
        /**
         * 一致性模式
         */
        private String mode = SmartCacheConstant.CONSISTENCY_MODE_STRONG;

    }

    @Data
    @NoArgsConstructor
    public static class PubSubConfig {
        /**
         * Pub/Sub 模式
         */
        private String mode = SmartCacheConstant.PUBSUB_MODE_ROUTED;

        /**
         * channel 前缀
         */
        private String channelPrefix;
    }

    @Data
    @NoArgsConstructor
    public static class RouteConfig {
        /**
         * clear / size 是否允许扫描 Redis
         */
        private Boolean scanEnabled = SmartCacheConstant.DEFAULT_ROUTE_SCAN_ENABLED;

        /**
         * SCAN count
         */
        private Integer scanCount = SmartCacheConstant.DEFAULT_SCAN_COUNT;
    }

    @Data
    @NoArgsConstructor
    public static class SerializerConfig {
        /**
         * 可信包名前缀
         */
        private List<String> trustedPackages = new ArrayList<>(Arrays.asList(
                SmartCacheConstant.TRUSTED_PACKAGE_JAVA_LANG,
                SmartCacheConstant.TRUSTED_PACKAGE_JAVA_TIME,
                SmartCacheConstant.TRUSTED_PACKAGE_JAVA_UTIL
        ));
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
         */
        private int completionMarkTtlSeconds = SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;

        /**
         * 线程数
         */
        private int executorThreads = SmartCacheConstant.DEFAULT_WARMUP_EXECUTOR_THREADS;

        /**
         * 队列容量
         */
        private int executorQueueCapacity = SmartCacheConstant.DEFAULT_WARMUP_EXECUTOR_QUEUE_CAPACITY;

        /**
         * 预热失败策略
         */
        private String failurePolicy = SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY;
    }

    @Data
    @NoArgsConstructor
    public static class LockConfig {
        /**
         * 分布式锁超时时间（秒）
         */
        private int timeoutSeconds = SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
    }
}
