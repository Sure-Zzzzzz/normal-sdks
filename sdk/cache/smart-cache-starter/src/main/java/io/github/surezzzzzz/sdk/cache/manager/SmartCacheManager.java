package io.github.surezzzzzz.sdk.cache.manager;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.cache.L2Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheLoadException;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.stats.CacheStats;
import io.github.surezzzzzz.sdk.cache.stats.CacheStatsCollector;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Smart Cache Manager
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
    public <T> T get(String cacheName, String key) {
        // 先查 L1
        if (l1Cache != null) {
            T value = l1Cache.get(cacheName, key);
            if (value != null) {
                // 检查是否为空值占位符
                if (isNullPlaceholder(value)) {
                    return null;
                }
                // 记录 L1 命中
                if (statsCollector != null) {
                    statsCollector.recordL1Hit(cacheName);
                }
                return value;
            }
        }

        // 再查 L2
        if (l2Cache != null) {
            T value = l2Cache.get(cacheName, key);
            if (value != null) {
                // 检查是否为空值占位符
                if (isNullPlaceholder(value)) {
                    // 回写 L1（空值也要回写）
                    if (l1Cache != null) {
                        l1Cache.put(cacheName, key, value);
                    }
                    return null;
                }
                // 记录 L2 命中
                if (statsCollector != null) {
                    statsCollector.recordL2Hit(cacheName);
                }
                // 回写 L1
                if (l1Cache != null) {
                    l1Cache.put(cacheName, key, value);
                }
                return value;
            }
        }

        // 记录未命中
        if (statsCollector != null) {
            statsCollector.recordMiss(cacheName);
        }
        return null;
    }

    /**
     * 获取缓存值，如果不存在则加载
     */
    public <T> T get(String cacheName, String key, Callable<T> loader) {
        String fullKey = cacheName + SmartCacheConstant.KEY_SEPARATOR + key;
        Set<String> loadingKeys = LOADING_KEYS.get();

        // 检测循环依赖
        if (loadingKeys.contains(fullKey)) {
            throw new SmartCacheException("检测到循环依赖: " + fullKey +
                    ", 当前加载链: " + loadingKeys);
        }

        // 监控：检测异常的依赖深度
        if (loadingKeys.size() > 10) {
            log.error("检测到异常的缓存依赖深度: {}, 加载链: {}",
                    loadingKeys.size(), loadingKeys);
        }

        try {
            loadingKeys.add(fullKey);
            return doGet(cacheName, key, loader);
        } finally {
            // 关键：无论成功、异常、还是循环依赖，都必须清理
            loadingKeys.remove(fullKey);

            // 如果 Set 为空，彻底移除 ThreadLocal（防止内存泄漏）
            if (loadingKeys.isEmpty()) {
                LOADING_KEYS.remove();
            }
        }
    }

    /**
     * 实际获取缓存值的逻辑
     */
    private <T> T doGet(String cacheName, String key, Callable<T> loader) {
        // 先查 L1
        if (l1Cache != null) {
            T value = l1Cache.get(cacheName, key);
            if (value != null) {
                // 检查是否为空值占位符
                if (isNullPlaceholder(value)) {
                    return null;
                }
                // 记录 L1 命中
                if (statsCollector != null) {
                    statsCollector.recordL1Hit(cacheName);
                }
                return value;
            }
        }

        // 再查 L2
        if (l2Cache != null) {
            T value = l2Cache.get(cacheName, key);
            if (value != null) {
                // 检查是否为空值占位符
                if (isNullPlaceholder(value)) {
                    // 回写 L1（空值也要回写）
                    if (l1Cache != null) {
                        l1Cache.put(cacheName, key, value);
                    }
                    return null;
                }
                // 记录 L2 命中
                if (statsCollector != null) {
                    statsCollector.recordL2Hit(cacheName);
                }
                // 回写 L1
                if (l1Cache != null) {
                    l1Cache.put(cacheName, key, value);
                }
                return value;
            }
        }

        // L1 和 L2 都未命中，使用分布式锁加载数据（防止缓存击穿）
        return loadWithLock(cacheName, key, loader);
    }

    /**
     * 使用分布式锁加载数据（防止缓存击穿）
     */
    private <T> T loadWithLock(String cacheName, String key, Callable<T> loader) {
        String keyPrefix = properties != null ? properties.getKeyPrefix() : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null ? properties.getMe() : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String lockKey = KeyHelper.buildLockKey(
                keyPrefix,
                cacheName,
                me,
                key
        );
        String requestId = UUID.randomUUID().toString();

        // 如果没有分布式锁，使用本地锁兜底
        if (redisLock == null) {
            return loadWithLocalLock(cacheName, key, loader);
        }

        // 尝试获取分布式锁
        boolean locked = false;
        try {
            int lockTimeout = properties != null && properties.getLock() != null
                    ? properties.getLock().getTimeoutSeconds()
                    : 30;
            locked = redisLock.tryLock(lockKey, requestId, lockTimeout, TimeUnit.SECONDS);
            if (locked) {
                // 获取锁成功，双重检查
                if (l2Cache != null) {
                    T value = l2Cache.get(cacheName, key);
                    if (value != null) {
                        // 检查是否为空值占位符
                        if (isNullPlaceholder(value)) {
                            // 回写 L1
                            if (l1Cache != null) {
                                l1Cache.put(cacheName, key, value);
                            }
                            return null;
                        }
                        // 回写 L1
                        if (l1Cache != null) {
                            l1Cache.put(cacheName, key, value);
                        }
                        return value;
                    }
                }

                // 加载数据
                return loadAndCache(cacheName, key, loader);
            } else {
                // 获取锁失败，使用重试机制循环查询缓存
                if (retryExecutor != null && l2Cache != null) {
                    try {
                        T value = retryExecutor.executeWithFixedDelay(() -> {
                            T v = l2Cache.get(cacheName, key);
                            if (v != null) {
                                return v;
                            }
                            // 如果还没有值，抛出异常继续重试
                            throw new RuntimeException("Cache not ready yet");
                        }, 5, 1); // 重试 5 次，每次间隔 1 秒

                        // 检查是否为空值占位符
                        if (isNullPlaceholder(value)) {
                            // 回写 L1
                            if (l1Cache != null) {
                                l1Cache.put(cacheName, key, value);
                            }
                            // 记录 L2 命中
                            if (statsCollector != null) {
                                statsCollector.recordL2Hit(cacheName);
                            }
                            return null;
                        }
                        // 回写 L1
                        if (l1Cache != null) {
                            l1Cache.put(cacheName, key, value);
                        }
                        // 记录 L2 命中
                        if (statsCollector != null) {
                            statsCollector.recordL2Hit(cacheName);
                        }
                        return value;
                    } catch (Exception e) {
                        // 重试多次后还是没有，使用本地锁兜底（避免同一实例内缓存击穿）
                        log.warn("Failed to get cache after retries, using local lock as fallback");
                        return loadWithLocalLock(cacheName, key, loader);
                    }
                } else {
                    // 如果没有重试器或 L2 缓存，使用本地锁兜底
                    return loadWithLocalLock(cacheName, key, loader);
                }
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis连接失败，降级到本地锁
            log.warn("Redis connection failed, fallback to local lock. Error: {}", e.getMessage());
            return loadWithLocalLock(cacheName, key, loader);
        } catch (Exception e) {
            // 其他异常也降级到本地锁
            log.warn("Failed to acquire distributed lock, fallback to local lock. Error: {}", e.getMessage());
            return loadWithLocalLock(cacheName, key, loader);
        } finally {
            if (locked) {
                try {
                    redisLock.unlock(lockKey, requestId);
                } catch (Exception e) {
                    log.warn("Failed to unlock distributed lock: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 使用本地锁加载数据（兜底机制，防止同一实例内缓存击穿）
     */
    private <T> T loadWithLocalLock(String cacheName, String key, Callable<T> loader) {
        String localLockKey = cacheName + SmartCacheConstant.KEY_SEPARATOR + key;
        LockHolder holder = localLocks.computeIfAbsent(localLockKey, k -> new LockHolder());
        holder.acquire();

        try {
            synchronized (holder.getLock()) {
                // 双重检查缓存
                T value = get(cacheName, key);
                if (value != null || (l2Cache != null && isNullPlaceholder(l2Cache.get(cacheName, key)))) {
                    return value;
                }
                // 加载数据
                return loadAndCache(cacheName, key, loader);
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
        try {
            T value = loader.call();
            // 记录未命中
            if (statsCollector != null) {
                statsCollector.recordMiss(cacheName);
            }
            if (value != null) {
                // 写入 L2
                if (l2Cache != null) {
                    l2Cache.put(cacheName, key, value);
                }
                // 写入 L1
                if (l1Cache != null) {
                    l1Cache.put(cacheName, key, value);
                }
            } else {
                // 缓存空值，防止缓存穿透
                Object placeholder = SmartCacheConstant.NULL_PLACEHOLDER;
                if (l2Cache != null) {
                    l2Cache.put(cacheName, key, placeholder, SmartCacheConstant.NULL_CACHE_TTL_SECONDS);
                }
                if (l1Cache != null) {
                    l1Cache.put(cacheName, key, placeholder);
                }
            }
            return value;
        } catch (SmartCacheException e) {
            // 循环依赖等业务异常直接抛出，不包装
            throw e;
        } catch (Exception e) {
            throw new CacheLoadException("缓存加载失败" + SmartCacheConstant.KEY_SEPARATOR + " " + cacheName + SmartCacheConstant.KEY_SEPARATOR + key, e);
        }
    }

    /**
     * 设置缓存值
     */
    public void put(String cacheName, String key, Object value) {
        if (value == null) {
            return;
        }

        // 写入 L2
        if (l2Cache != null) {
            l2Cache.put(cacheName, key, value);
        }

        // 写入 L1
        if (l1Cache != null) {
            l1Cache.put(cacheName, key, value);
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
    public <T> Map<String, T> getAll(String cacheName, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, T> result = new HashMap<>();

        // 先从 L1 批量获取
        if (l1Cache != null) {
            Map<String, T> l1Result = l1Cache.getAll(cacheName, keys);
            result.putAll(l1Result);
        }

        // 找出 L1 未命中的 key
        List<String> missedKeys = new ArrayList<>();
        for (String key : keys) {
            if (!result.containsKey(key)) {
                missedKeys.add(key);
            }
        }

        // 如果有未命中的 key，从 L2 批量获取
        if (!missedKeys.isEmpty() && l2Cache != null) {
            Map<String, T> l2Result = l2Cache.getAll(cacheName, missedKeys);
            result.putAll(l2Result);

            // 回写 L1
            if (l1Cache != null && !l2Result.isEmpty()) {
                l1Cache.putAll(cacheName, (Map<String, Object>) (Map<?, ?>) l2Result);
            }

            // 记录统计
            if (statsCollector != null) {
                int l2HitCount = l2Result.size();
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
            int l1HitCount = keys.size() - missedKeys.size();
            for (int i = 0; i < l1HitCount; i++) {
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
        // 使用对象引用比较，性能优于字符串比较
        return value == SmartCacheConstant.NULL_PLACEHOLDER;
    }
}
