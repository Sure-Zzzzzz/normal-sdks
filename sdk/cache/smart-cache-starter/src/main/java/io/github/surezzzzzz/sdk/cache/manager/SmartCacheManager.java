package io.github.surezzzzzz.sdk.cache.manager;

import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheLoadException;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.stats.CacheStatsCollector;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.*;
import java.util.concurrent.*;

/**
 * Smart Cache 管理器
 * <p>
 * 缓存管理器，提供编程式 API
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
public class SmartCacheManager {

    @Autowired(required = false)
    private SmartCacheProperties properties;

    @Autowired(required = false)
    private L1Cache l1Cache;

    @Autowired(required = false)
    private L2Cache l2Cache;

    @Autowired(required = false)
    private CacheStatsCollector statsCollector;

    @Autowired(required = false)
    private CacheInvalidationListener invalidationListener;

    @Autowired(required = false)
    private SimpleRedisLock redisLock;

    @Autowired(required = false)
    private TaskRetryExecutor retryExecutor;

    @Autowired(required = false)
    private List<CachePreloadHandler> preloadHandlers;

    @Autowired(required = false)
    @Qualifier(SmartCacheConstant.SMART_CACHE_PRELOAD_EXECUTOR_BEAN_NAME)
    private Executor preloadExecutor;

    /**
     * 使用 ThreadLocal 记录当前线程正在加载的 key，用于循环依赖检测
     */
    private static final ThreadLocal<Set<String>> LOADING_KEYS =
            ThreadLocal.withInitial(LinkedHashSet::new);

    /**
     * 本地锁持有者，用于引用计数
     */
    private static class LockHolder {
        private final Object lock = new Object();
        private int refCount = 0;

        public synchronized void acquire() {
            refCount++;
        }

        public synchronized boolean release() {
            refCount--;
            return refCount == 0;
        }

        public Object getLock() {
            return lock;
        }
    }

    /**
     * 本地锁 Map，用于同一实例内的并发控制（兜底机制）
     */
    private final ConcurrentHashMap<String, LockHolder> localLocks = new ConcurrentHashMap<>();

    /**
     * 获取缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        return (T) get(cacheName, key, Object.class);
    }

    /**
     * 获取缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<T> valueType) {
        if (l1Cache != null) {
            T value = l1Cache.get(cacheName, key);
            if (value != null) {
                if (statsCollector != null) {
                    statsCollector.recordL1Hit(cacheName);
                }
                if (isNullPlaceholder(value)) {
                    return null;
                }
                return value;
            }
        }

        if (l2Cache != null) {
            T value = l2Cache.get(cacheName, key, valueType);
            if (value != null) {
                return handleL2Hit(cacheName, key, value);
            }
        }

        if (statsCollector != null) {
            statsCollector.recordMiss(cacheName);
        }
        return null;
    }

    /**
     * 获取缓存值，如果不存在则加载
     */
    public <T> T get(String cacheName, String key, Callable<T> loader) {
        return get(cacheName, key, loader, 0, Object.class);
    }

    /**
     * 获取缓存值，如果不存在则加载
     */
    public <T> T get(String cacheName, String key, Callable<T> loader, Class<?> valueType) {
        return get(cacheName, key, loader, 0, valueType);
    }

    /**
     * 获取缓存值，如果不存在则加载，并使用指定 TTL 写入 L2
     *
     * <p>{@code ttlSeconds <= 0} 时 fallback 到全局配置，行为与 {@link #get(String, String, Callable)} 完全一致。
     *
     * @param cacheName  缓存名称
     * @param key        缓存 key
     * @param loader     数据加载器，cache miss 时调用
     * @param ttlSeconds L2 TTL（秒），&lt;= 0 时使用全局配置
     * @return 缓存值或 loader 返回值
     */
    public <T> T get(String cacheName, String key, Callable<T> loader, int ttlSeconds) {
        return get(cacheName, key, loader, ttlSeconds, Object.class);
    }

    /**
     * 获取缓存值，如果不存在则加载，并使用指定 TTL 写入 L2
     */
    public <T> T get(String cacheName, String key, Callable<T> loader, int ttlSeconds, Class<?> valueType) {
        String fullKey = cacheName + SmartCacheConstant.KEY_SEPARATOR + key;
        Set<String> loadingKeys = LOADING_KEYS.get();

        if (loadingKeys.contains(fullKey)) {
            throw new SmartCacheException(
                    ErrorCode.SMART_CACHE_CIRCULAR_DEPENDENCY,
                    String.format(ErrorMessage.SMART_CACHE_CIRCULAR_DEPENDENCY,
                            fullKey + SmartCacheConstant.KEY_SEPARATOR + loadingKeys)
            );
        }

        if (loadingKeys.size() > SmartCacheConstant.CACHE_DEPENDENCY_WARN_DEPTH) {
            log.error("检测到异常的缓存依赖深度: {}, 加载链: {}",
                    loadingKeys.size(), loadingKeys);
        }

        try {
            loadingKeys.add(fullKey);
            return doGet(cacheName, key, loader, ttlSeconds, valueType);
        } finally {
            loadingKeys.remove(fullKey);
            if (loadingKeys.isEmpty()) {
                LOADING_KEYS.remove();
            }
        }
    }

    /**
     * 实际获取缓存值的逻辑
     */
    private <T> T doGet(String cacheName, String key, Callable<T> loader) {
        return doGet(cacheName, key, loader, 0, Object.class);
    }

    /**
     * 实际获取缓存值的逻辑（支持自定义 L2 TTL）
     */
    private <T> T doGet(String cacheName, String key, Callable<T> loader, int ttlSeconds, Class<?> valueType) {
        // 先查 L1
        if (l1Cache != null) {
            T value = l1Cache.get(cacheName, key);
            if (value != null) {
                if (statsCollector != null) {
                    statsCollector.recordL1Hit(cacheName);
                }
                if (isNullPlaceholder(value)) {
                    return null;
                }
                return value;
            }
        }

        // 再查 L2
        if (l2Cache != null) {
            T value = l2Cache.get(cacheName, key, valueType);
            if (value != null) {
                return handleL2Hit(cacheName, key, value);
            }
        }

        // L1 和 L2 都未命中，使用分布式锁加载数据（防止缓存击穿）
        return loadWithLock(cacheName, key, loader, ttlSeconds, valueType);
    }

    /**
     * 使用分布式锁加载数据（防止缓存击穿）
     */
    private <T> T loadWithLock(String cacheName, String key, Callable<T> loader) {
        return loadWithLock(cacheName, key, loader, 0, Object.class);
    }

    /**
     * 使用分布式锁加载数据（防止缓存击穿，支持自定义 L2 TTL）
     */
    private <T> T loadWithLock(String cacheName, String key, Callable<T> loader, int ttlSeconds, Class<?> valueType) {
        String keyPrefix = properties != null ? properties.getKeyPrefix() : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null ? properties.getMe() : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String lockKey = KeyHelper.buildLockKey(
                keyPrefix,
                cacheName,
                me,
                key
        );

        // 如果没有分布式锁，使用本地锁兜底
        if (redisLock == null) {
            return loadWithLocalLock(cacheName, key, loader, ttlSeconds, valueType);
        }

        int lockTimeout = getLockTimeoutSeconds();
        Optional<RedisLockLease> optionalLease;
        try {
            optionalLease = redisLock.tryLockWithLease(lockKey, lockTimeout, TimeUnit.SECONDS);
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            log.warn("Redis 连接失败，降级使用本地锁兜底。原因：{}", e.getMessage());
            return loadWithLocalLock(cacheName, key, loader, ttlSeconds, valueType);
        } catch (Exception e) {
            log.warn("获取分布式锁租约失败，降级使用本地锁兜底。原因：{}", e.getMessage());
            return loadWithLocalLock(cacheName, key, loader, ttlSeconds, valueType);
        }

        if (!optionalLease.isPresent()) {
            return loadAfterLeaseContention(cacheName, key, loader, ttlSeconds, valueType, lockTimeout);
        }

        RedisLockLease lease = optionalLease.get();
        try {
            if (l2Cache != null) {
                T value = l2Cache.get(cacheName, key, valueType);
                if (value != null) {
                    return handleL2Hit(cacheName, key, value);
                }
            }

            T value = loadValue(cacheName, key, loader);
            if (!renewLeaseBeforeWrite(lease, lockTimeout, "缓存击穿", cacheName, key)) {
                return value;
            }
            cacheLoadedValue(cacheName, key, value, ttlSeconds);
            return value;
        } finally {
            closeLease(lease, "缓存击穿", cacheName, key);
        }
    }

    private <T> T loadAfterLeaseContention(String cacheName, String key, Callable<T> loader, int ttlSeconds,
                                           Class<?> valueType, int lockTimeout) {
        if (retryExecutor != null && l2Cache != null) {
            try {
                int retryTimes = Math.max(1, lockTimeout / 5);
                long retryDelayMillis = SmartCacheConstant.MIN_RETRY_DELAY_MILLIS;
                T value = retryExecutor.executeWithFixedDelay(() -> {
                    T cachedValue = l2Cache.get(cacheName, key, valueType);
                    if (cachedValue != null) {
                        return cachedValue;
                    }
                    throw new SmartCacheException(
                            ErrorCode.SMART_CACHE_L2_OPERATION_FAILED,
                            String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED,
                                    cacheName + SmartCacheConstant.KEY_SEPARATOR + key)
                    );
                }, retryTimes, retryDelayMillis);
                return handleL2Hit(cacheName, key, value);
            } catch (Exception e) {
                log.warn("重试读取缓存仍未命中，降级使用本地锁兜底");
            }
        }
        return loadWithLocalLock(cacheName, key, loader, ttlSeconds, valueType);
    }

    /**
     * 使用本地锁加载数据（兜底机制，防止同一实例内缓存击穿）
     */
    private <T> T loadWithLocalLock(String cacheName, String key, Callable<T> loader) {
        return loadWithLocalLock(cacheName, key, loader, 0, Object.class);
    }

    /**
     * 使用本地锁加载数据（兜底机制，防止同一实例内缓存击穿，支持自定义 L2 TTL）
     */
    private <T> T loadWithLocalLock(String cacheName, String key, Callable<T> loader, int ttlSeconds, Class<?> valueType) {
        String localLockKey = cacheName + SmartCacheConstant.KEY_SEPARATOR + key;
        LockHolder holder = localLocks.computeIfAbsent(localLockKey, k -> new LockHolder());
        holder.acquire();

        try {
            synchronized (holder.getLock()) {
                // 双重检查缓存
                T value = (T) get(cacheName, key, valueType);
                if (value != null || (l2Cache != null && isNullPlaceholder(l2Cache.get(cacheName, key, valueType)))) {
                    return value;
                }
                // 加载数据
                return loadAndCache(cacheName, key, loader, ttlSeconds);
            }
        } finally {
            // 引用计数减1，如果为0则清理
            if (holder.release()) {
                localLocks.remove(localLockKey, holder);
            }
        }
    }

    /**
     * 加载数据并缓存
     */
    private <T> T loadAndCache(String cacheName, String key, Callable<T> loader) {
        return loadAndCache(cacheName, key, loader, 0);
    }

    /**
     * 加载数据并缓存（支持自定义 L2 TTL）
     *
     * <p>{@code ttlSeconds <= 0} 时使用全局配置。
     */
    private <T> T loadAndCache(String cacheName, String key, Callable<T> loader, int ttlSeconds) {
        T value = loadValue(cacheName, key, loader);
        cacheLoadedValue(cacheName, key, value, ttlSeconds);
        return value;
    }

    private <T> T loadValue(String cacheName, String key, Callable<T> loader) {
        try {
            T value = loader.call();
            if (statsCollector != null) {
                statsCollector.recordMiss(cacheName);
            }
            return value;
        } catch (SmartCacheException e) {
            throw e;
        } catch (Exception e) {
            throw new CacheLoadException(
                    ErrorCode.SMART_CACHE_LOAD_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_LOAD_FAILED,
                            cacheName + SmartCacheConstant.KEY_SEPARATOR + key),
                    e
            );
        }
    }

    private void cacheLoadedValue(String cacheName, String key, Object value, int ttlSeconds) {
        if (value != null) {
            if (l2Cache != null) {
                if (ttlSeconds > 0) {
                    l2Cache.put(cacheName, key, value, ttlSeconds);
                } else {
                    l2Cache.put(cacheName, key, value);
                }
            }
            if (l1Cache != null) {
                l1Cache.put(cacheName, key, value);
            }
        } else if (l1Cache != null) {
            l1Cache.put(cacheName, key, SmartCacheConstant.NULL_PLACEHOLDER);
        }
    }

    private int getLockTimeoutSeconds() {
        return properties != null && properties.getLock() != null
                ? properties.getLock().getTimeoutSeconds()
                : SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
    }

    private boolean renewLeaseBeforeWrite(RedisLockLease lease, int lockTimeout, String operation,
                                          String cacheName, String key) {
        try {
            if (lease.renew(lockTimeout, TimeUnit.SECONDS)) {
                return true;
            }
            log.warn("{}租约已失效，丢弃共享缓存写入，cacheName：{}，key：{}", operation, cacheName, key);
        } catch (Exception e) {
            log.warn("{}租约续租失败，丢弃共享缓存写入，cacheName：{}，key：{}，原因：{}",
                    operation, cacheName, key, e.getMessage());
        }
        return false;
    }

    private void closeLease(RedisLockLease lease, String operation, String cacheName, String key) {
        try {
            lease.close();
        } catch (Exception e) {
            log.warn("{}租约释放失败，cacheName：{}，key：{}，原因：{}", operation, cacheName, key, e.getMessage());
        }
    }

    /**
     * 设置缓存值
     */
    public void put(String cacheName, String key, Object value) {
        put(cacheName, key, value, 0);
    }

    /**
     * 设置缓存值，并使用指定 TTL 写入 L2
     *
     * <p>{@code ttlSeconds <= 0} 时 fallback 到全局配置，行为与 {@link #put(String, String, Object)} 完全一致。
     *
     * @param cacheName  缓存名称
     * @param key        缓存 key
     * @param value      缓存值，null 时直接返回
     * @param ttlSeconds L2 TTL（秒），&lt;= 0 时使用全局配置
     */
    public void put(String cacheName, String key, Object value, int ttlSeconds) {
        if (value == null) {
            return;
        }

        // 写入 L2，ttlSeconds > 0 时使用指定 TTL，否则使用全局配置
        if (l2Cache != null) {
            if (ttlSeconds > 0) {
                l2Cache.put(cacheName, key, value, ttlSeconds);
            } else {
                l2Cache.put(cacheName, key, value);
            }
        }

        // 写入 L1
        if (l1Cache != null) {
            l1Cache.put(cacheName, key, value);
        }

        // 通知其他实例删除旧 L1，后续读取从已更新的 L2 获取新值
        if (invalidationListener != null) {
            invalidationListener.publishInvalidation(cacheName, key, SmartCacheConstant.OPERATION_EVICT);
        }
    }

    /**
     * 删除缓存值
     */
    public void evict(String cacheName, String key) {
        // 删除 L1
        if (l1Cache != null) {
            l1Cache.evict(cacheName, key);
        }

        // 删除 L2
        if (l2Cache != null) {
            l2Cache.evict(cacheName, key);
        }

        // 发布失效消息（强一致性模式）
        if (invalidationListener != null) {
            invalidationListener.publishInvalidation(cacheName, key, SmartCacheConstant.OPERATION_EVICT);
        }
    }

    /**
     * 清空缓存
     */
    public void clear(String cacheName) {
        // 清空 L1
        if (l1Cache != null) {
            l1Cache.clear(cacheName);
        }

        // 清空 L2
        if (l2Cache != null) {
            l2Cache.clear(cacheName);
        }

        // 发布失效消息（强一致性模式）
        if (invalidationListener != null) {
            invalidationListener.publishInvalidation(cacheName, null, SmartCacheConstant.OPERATION_CLEAR);
        }
    }

    /**
     * 获取缓存大小
     */
    public long size(String cacheName) {
        long l1Size = l1Cache != null ? l1Cache.size(cacheName) : 0;
        long l2Size = l2Cache != null ? l2Cache.size(cacheName) : 0;
        return l1Size + l2Size;
    }

    /**
     * 批量获取缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getAll(String cacheName, List<String> keys) {
        return (Map<String, T>) getAll(cacheName, keys, Object.class);
    }

    /**
     * 批量获取缓存值
     */
    public <T> Map<String, T> getAll(String cacheName, List<String> keys, Class<T> valueType) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, T> result = new HashMap<>();
        Set<String> l1HitKeys = new HashSet<>();

        // 先从 L1 批量获取
        if (l1Cache != null) {
            Map<String, T> l1Result = l1Cache.getAll(cacheName, keys);
            for (Map.Entry<String, T> entry : l1Result.entrySet()) {
                l1HitKeys.add(entry.getKey());
                if (!isNullPlaceholder(entry.getValue())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // 找出 L1 未命中的 key
        List<String> missedKeys = new ArrayList<>();
        for (String key : keys) {
            if (!l1HitKeys.contains(key)) {
                missedKeys.add(key);
            }
        }

        // 如果有未命中的 key，从 L2 批量获取
        if (!missedKeys.isEmpty() && l2Cache != null) {
            Map<String, T> l2Result = l2Cache.getAll(cacheName, missedKeys, valueType);
            Map<String, T> actualL2Result = new HashMap<>();
            for (Map.Entry<String, T> entry : l2Result.entrySet()) {
                if (!isNullPlaceholder(entry.getValue())) {
                    actualL2Result.put(entry.getKey(), entry.getValue());
                }
            }
            result.putAll(actualL2Result);

            // 回写 L1
            if (l1Cache != null && !actualL2Result.isEmpty()) {
                l1Cache.putAll(cacheName, (Map<String, Object>) (Map<?, ?>) actualL2Result);
            }

            // 记录统计
            if (statsCollector != null) {
                int l2HitCount = actualL2Result.size();
                int missCount = missedKeys.size() - l2HitCount;
                for (int i = 0; i < l2HitCount; i++) {
                    statsCollector.recordL2Hit(cacheName);
                }
                for (int i = 0; i < missCount; i++) {
                    statsCollector.recordMiss(cacheName);
                }
            }
        }

        // 记录 L1 命中统计
        if (statsCollector != null && l1Cache != null) {
            for (int i = 0; i < l1HitKeys.size(); i++) {
                statsCollector.recordL1Hit(cacheName);
            }
        }

        return result;
    }

    /**
     * 批量设置缓存值
     */
    public void putAll(String cacheName, Map<String, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        // 写入 L2
        if (l2Cache != null) {
            l2Cache.putAll(cacheName, entries);
        }

        // 写入 L1
        if (l1Cache != null) {
            l1Cache.putAll(cacheName, entries);
        }

        // 通知其他实例删除每个已更新 key 的旧 L1
        if (invalidationListener != null) {
            entries.forEach((key, value) -> {
                if (value != null) {
                    invalidationListener.publishInvalidation(cacheName, key, SmartCacheConstant.OPERATION_EVICT);
                }
            });
        }
    }

    /**
     * 获取统计信息
     */
    public CacheStats getStats(String cacheName) {
        if (statsCollector != null) {
            return statsCollector.getStats(cacheName);
        }
        return null;
    }

    /**
     * 检查是否为空值占位符
     *
     * @param value 待检查的值
     * @return 如果是空值占位符返回true，否则返回false
     */
    private boolean isNullPlaceholder(Object value) {
        return value == SmartCacheConstant.NULL_PLACEHOLDER;
    }

    /**
     * L2 命中后的统一处理：回写 L1、记录统计、触发 preload
     *
     * <p>空值占位符也回写 L1，避免下次请求再穿透到 L2。
     */
    @SuppressWarnings("unchecked")
    private <T> T handleL2Hit(String cacheName, String key, Object rawValue) {
        if (isNullPlaceholder(rawValue)) {
            if (l1Cache != null) {
                l1Cache.put(cacheName, key, rawValue);
            }
            return null;
        }
        if (statsCollector != null) {
            statsCollector.recordL2Hit(cacheName);
        }
        if (l1Cache != null) {
            l1Cache.put(cacheName, key, rawValue);
        }
        // 关闭预刷新时不触发 handler、TTL 查询或异步任务
        if (properties == null || !properties.getL2().getPreload().isEnabled()) {
            return (T) rawValue;
        }

        // 检查是否需要异步续期
        CachePreloadHandler handler = findHandler(cacheName);
        if (handler != null) {
            boolean doPreload = handler.needPreload(cacheName, key, rawValue)
                    .orElseGet(() -> shouldPreload(cacheName, key));
            if (doPreload) {
                asyncPreload(cacheName, key, handler);
            }
        }
        return (T) rawValue;
    }

    /**
     * 异步触发 L2 续期
     *
     * <p>使用分布式锁防止多实例重复触发同一 key 的 preload。
     * 抢到锁的实例执行 reload（带指数退避重试），其他实例跳过。
     *
     * <p>重试策略：固定 {@link SmartCacheConstant#PRELOAD_MAX_RETRIES} 次，
     * 初始间隔由 before-expire-seconds 反推，保证总等待时间不超过预刷新窗口：
     * <pre>
     *   initialInterval = beforeExpireSeconds * (ratio - 1) / (ratio^n - 1)
     * </pre>
     */
    private void asyncPreload(String cacheName, String key, CachePreloadHandler handler) {
        if (redisLock == null) {
            log.debug("跳过 L2 预刷新，未配置分布式锁，key：{}", key);
            return;
        }
        String keyPrefix = properties != null ? properties.getKeyPrefix() : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null ? properties.getMe() : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String lockKey = KeyHelper.buildPreloadLockKey(keyPrefix, cacheName, me, key);
        int lockTimeout = getLockTimeoutSeconds();
        Optional<RedisLockLease> optionalLease;
        try {
            optionalLease = redisLock.tryLockWithLease(lockKey, lockTimeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("跳过 L2 预刷新，获取租约失败，cacheName：{}，key：{}，原因：{}", cacheName, key, e.getMessage());
            return;
        }
        if (!optionalLease.isPresent()) {
            log.debug("跳过 L2 预刷新，其他实例正在刷新，key：{}", key);
            return;
        }

        RedisLockLease lease = optionalLease.get();
        int beforeExpireSeconds = properties != null
                ? properties.getL2().getPreload().getBeforeExpireSeconds()
                : SmartCacheConstant.DEFAULT_L2_PRELOAD_BEFORE_EXPIRE_SECONDS;
        double ratio = SmartCacheConstant.PRELOAD_RETRY_BACKOFF_RATIO;
        int maxRetries = SmartCacheConstant.PRELOAD_MAX_RETRIES;
        int initialIntervalSeconds = (int) Math.max(1L,
                (long) (beforeExpireSeconds * (ratio - 1) / (Math.pow(ratio, maxRetries) - 1)));
        long initialIntervalMillis = TimeUnit.SECONDS.toMillis(initialIntervalSeconds);
        long maxDelayMillis = TimeUnit.SECONDS.toMillis(beforeExpireSeconds);
        if (preloadExecutor == null) {
            closeLease(lease, "L2 预刷新", cacheName, key);
            log.warn("跳过 L2 预刷新，未配置预刷新线程池，cacheName：{}，key：{}", cacheName, key);
            return;
        }

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    log.debug("触发 L2 预刷新，cacheName：{}，key：{}，maxRetries：{}，initialIntervalMillis：{}",
                            cacheName, key, maxRetries, initialIntervalMillis);
                    Object newValue = retryExecutor != null
                            ? retryExecutor.executeWithRetry(
                            () -> handler.reload(cacheName, key),
                            maxRetries,
                            initialIntervalMillis,
                            ratio,
                            maxDelayMillis)
                            : handler.reload(cacheName, key);
                    if (newValue != null && renewLeaseBeforeWrite(lease, lockTimeout, "L2 预刷新", cacheName, key)) {
                        int reloadTtl = handler.getReloadTtlSeconds(cacheName, key);
                        put(cacheName, key, newValue, reloadTtl);
                        log.debug("L2 预刷新完成，cacheName：{}，key：{}，reloadTtl：{}", cacheName, key, reloadTtl);
                    }
                } catch (Exception e) {
                    log.warn("L2 预刷新重试后仍失败，maxRetries：{}，cacheName：{}，key：{}", maxRetries, cacheName, key, e);
                } finally {
                    closeLease(lease, "L2 预刷新", cacheName, key);
                }
            }, preloadExecutor);
        } catch (RejectedExecutionException e) {
            closeLease(lease, "L2 预刷新", cacheName, key);
            log.warn("跳过 L2 预刷新，线程池已饱和，cacheName：{}，key：{}", cacheName, key);
        }
    }

    private CachePreloadHandler findHandler(String cacheName) {
        if (preloadHandlers == null || preloadHandlers.isEmpty()) {
            return null;
        }
        return preloadHandlers.stream()
                .filter(h -> h.support(cacheName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查 L2 TTL 判断是否进入预刷新窗口
     *
     * <p>TTL ≤ 0 时不触发——key 不存在（如被淘汰）或无 TTL 时，
     * 大不了下次 miss 重新加载，这是 smart-cache 的默认行为。
     */
    private boolean shouldPreload(String cacheName, String key) {
        if (properties == null || l2Cache == null) {
            return false;
        }
        SmartCacheProperties.L2Config.PreloadConfig preload = properties.getL2().getPreload();
        if (!preload.isEnabled()) {
            return false;
        }
        long ttl = l2Cache.getTtl(cacheName, key);
        return ttl > 0 && ttl <= preload.getBeforeExpireSeconds();
    }
}
