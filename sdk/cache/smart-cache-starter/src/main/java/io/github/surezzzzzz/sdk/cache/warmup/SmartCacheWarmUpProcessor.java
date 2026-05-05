package io.github.surezzzzzz.sdk.cache.warmup;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Smart Cache WarmUp Processor
 * <p>
 * 缓存预热处理器，在应用启动后自动执行预热方法
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmartCacheWarmUpProcessor implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired(required = false)
    private SmartCacheProperties properties;

    @Autowired(required = false)
    private L1Cache l1Cache;

    @Autowired(required = false)
    private L2Cache l2Cache;

    @Autowired(required = false)
    private SimpleRedisLock redisLock;

    @Autowired(required = false)
    @Qualifier("smartCacheRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    private ExecutorService warmupExecutor;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();

        // 只在根容器启动时执行一次
        if (context.getParent() != null) {
            return;
        }

        log.info("Starting cache warm-up...");

        List<WarmUpTask> tasks = new ArrayList<>();

        // 扫描所有Bean，查找带@SmartCacheWarmUp注解的方法
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> clazz = bean.getClass();

            // 获取所有方法
            for (Method method : clazz.getDeclaredMethods()) {
                SmartCacheWarmUp warmUp = method.getAnnotation(SmartCacheWarmUp.class);
                if (warmUp != null) {
                    tasks.add(new WarmUpTask(bean, method, warmUp));
                }
            }
        }

        if (tasks.isEmpty()) {
            log.info("No warm-up methods found, skipping");
            return;
        }

        // 按order排序
        tasks.sort(Comparator.comparingInt(t -> t.warmUp.order()));

        // 按order分组
        Map<Integer, List<WarmUpTask>> tasksByOrder = new LinkedHashMap<>();
        for (WarmUpTask task : tasks) {
            tasksByOrder.computeIfAbsent(task.warmUp.order(), k -> new ArrayList<>()).add(task);
        }

        // 初始化线程池（使用可用处理器数量）
        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors());
        warmupExecutor = Executors.newFixedThreadPool(threadCount);

        try {
            // 按order顺序执行，同order的任务并行执行
            for (Map.Entry<Integer, List<WarmUpTask>> entry : tasksByOrder.entrySet()) {
                int order = entry.getKey();
                List<WarmUpTask> orderTasks = entry.getValue();

                log.info("Executing warm-up tasks with order={}, count={}", order, orderTasks.size());

                // 同order的任务并行执行
                List<CompletableFuture<Void>> futures = orderTasks.stream()
                        .map(task -> CompletableFuture.runAsync(() -> executeWarmUpTask(task), warmupExecutor))
                        .collect(Collectors.toList());

                // 等待当前order的所有任务完成后，再执行下一个order
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                log.info("Warm-up tasks with order={} completed", order);
            }
        } finally {
            // 关闭线程池
            warmupExecutor.shutdown();
            try {
                if (!warmupExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    warmupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                warmupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Cache warm-up completed, {} tasks executed", tasks.size());
    }

    private void executeWarmUpTask(WarmUpTask task) {
        String cacheName = task.warmUp.cacheName();
        String keyPrefix = properties != null ? properties.getKeyPrefix() : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null ? properties.getMe() : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String lockKey = KeyHelper.buildLockKey(keyPrefix, cacheName, me, SmartCacheConstant.WARMUP_LOCK_KEY);
        String warmupCompleteKey = keyPrefix + SmartCacheConstant.KEY_SEPARATOR + cacheName + SmartCacheConstant.KEY_SEPARATOR + SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX;
        String warmupKeysKey = keyPrefix + SmartCacheConstant.KEY_SEPARATOR + cacheName + SmartCacheConstant.KEY_SEPARATOR + SmartCacheConstant.WARMUP_KEYS_KEY_SUFFIX;
        String requestId = UUID.randomUUID().toString();
        boolean locked = false;

        try {
            log.info("Executing warm-up task: {}.{}, cacheName={}, order={}",
                    task.bean.getClass().getSimpleName(),
                    task.method.getName(),
                    cacheName,
                    task.warmUp.order());

            // 尝试获取分布式锁（仅用于控制 L2 预热）
            if (redisLock != null && l2Cache != null && redisTemplate != null) {
                try {
                    locked = redisLock.tryLock(lockKey, requestId, SmartCacheConstant.WARMUP_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (locked) {
                        log.info("Acquired warm-up lock, executing warm-up method");
                        // 执行预热方法并写入 L2
                        Map<String, Object> data = executeWarmUpMethod(task);
                        if (data != null && !data.isEmpty()) {
                            // 写入 L2 (Redis) 和 L1 (Caffeine)
                            cacheManager.putAll(cacheName, data);
                            log.info("Warm-up succeeded: {} entries written to L2 (Redis) and L1 (Caffeine)", data.size());

                            // 保存预热的 key 列表，供其他实例使用
                            List<String> keys = new ArrayList<>(data.keySet());
                            int ttlSeconds = properties != null && properties.getWarmUp() != null
                                    ? properties.getWarmUp().getCompletionMarkTtlSeconds()
                                    : SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;
                            redisTemplate.opsForValue().set(
                                    warmupKeysKey,
                                    keys,
                                    ttlSeconds,
                                    TimeUnit.SECONDS
                            );

                            // 设置预热完成标记，TTL 必须大于等待超时时间
                            redisTemplate.opsForValue().set(
                                    warmupCompleteKey,
                                    SmartCacheConstant.WARMUP_COMPLETE_MARK_VALUE,
                                    ttlSeconds,
                                    TimeUnit.SECONDS
                            );
                            log.info("Warm-up completion mark set: {}", warmupCompleteKey);
                        }
                    } else {
                        log.info("Warm-up lock not acquired, waiting for another instance to complete warm-up");
                        // 等待预热完成标记
                        if (waitForWarmupComplete(warmupCompleteKey)) {
                            log.info("Warm-up completion mark detected, loading data from L2 to L1");
                            // 从 L2 加载预热数据到 L1
                            loadWarmupDataToL1(warmupKeysKey, cacheName);
                        } else {
                            log.warn("Timed out waiting for warm-up completion, L1 will be populated on first access");
                        }
                    }
                } catch (Exception e) {
                    log.error("Warm-up process error", e);
                }
            } else {
                // 没有分布式锁或 L2，直接执行
                log.info("No distributed lock or L2, executing warm-up directly");
                Map<String, Object> data = executeWarmUpMethod(task);
                if (data != null && !data.isEmpty()) {
                    cacheManager.putAll(cacheName, data);
                    log.info("Warm-up succeeded: {} entries written to cache", data.size());
                }
            }

        } catch (Exception e) {
            log.error("Warm-up task failed: {}.{}",
                    task.bean.getClass().getSimpleName(),
                    task.method.getName(), e);
        } finally {
            // 释放锁
            if (locked && redisLock != null) {
                try {
                    redisLock.unlock(lockKey, requestId);
                    log.debug("Warm-up lock released");
                } catch (Exception e) {
                    log.error("Failed to release warm-up lock", e);
                }
            }
        }
    }

    /**
     * 等待预热完成标记
     * 轮询检查 Redis 中的预热完成标记，直到检测到标记或超时
     *
     * @param warmupCompleteKey 预热完成标记的 Redis key
     * @return 如果检测到预热完成标记返回 true，超时或异常返回 false
     */
    private boolean waitForWarmupComplete(String warmupCompleteKey) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = SmartCacheConstant.WARMUP_WAIT_TIMEOUT_SECONDS * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                String value = (String) redisTemplate.opsForValue().get(warmupCompleteKey);
                if (SmartCacheConstant.WARMUP_COMPLETE_MARK_VALUE.equals(value)) {
                    return true;
                }
                Thread.sleep(SmartCacheConstant.WARMUP_POLL_INTERVAL_MILLIS);
            } catch (Exception e) {
                log.error("Failed to check warm-up completion mark", e);
                return false;
            }
        }
        return false;
    }

    /**
     * 从 L2 加载预热数据到 L1
     * 读取 Redis 中保存的预热 key 列表，从 L2 加载对应的值并写入 L1
     *
     * @param warmupKeysKey 预热 key 列表的 Redis key
     * @param cacheName     缓存名称
     */
    private void loadWarmupDataToL1(String warmupKeysKey, String cacheName) {
        try {
            // 从 Redis 读取预热的 key 列表
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) redisTemplate.opsForValue().get(warmupKeysKey);
            if (keys != null && !keys.isEmpty()) {
                // 从 L2 读取并写入 L1
                for (String key : keys) {
                    Object value = l2Cache.get(cacheName, key);
                    if (value != null && l1Cache != null) {
                        l1Cache.put(cacheName, key, value);
                    }
                }
                log.info("L1 warm-up completed: {} entries loaded from L2 to L1", keys.size());
            } else {
                log.warn("Warm-up key list not found");
            }
        } catch (Exception e) {
            log.error("L1 warm-up failed", e);
        }
    }

    /**
     * 执行预热方法
     * 通过反射调用预热方法，返回预热数据
     *
     * @param task 预热任务
     * @return 预热数据 Map，key 为缓存键，value 为缓存值
     * @throws Exception 执行异常
     */
    private Map<String, Object> executeWarmUpMethod(WarmUpTask task) throws Exception {
        task.method.setAccessible(true);
        Object result = task.method.invoke(task.bean);

        if (result == null) {
            log.warn("Warm-up method returned null");
            return null;
        }

        log.info("Warm-up method result type: {}", result.getClass().getName());
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            log.info("Preparing to write {} entries to cache", map.size());
            return map;
        } else if (result instanceof List) {
            log.warn("Warm-up method returned List, which is not supported. Please return Map<String, Object>");
            return null;
        } else {
            log.warn("Unsupported warm-up method return type: {}", result.getClass().getName());
            return null;
        }
    }

    /**
     * 预热任务包装类
     * 封装预热方法的 Bean、Method 和注解信息
     */
    private static class WarmUpTask {
        final Object bean;
        final Method method;
        final SmartCacheWarmUp warmUp;

        WarmUpTask(Object bean, Method method, SmartCacheWarmUp warmUp) {
            this.bean = bean;
            this.method = method;
            this.warmUp = warmUp;
        }
    }
}
