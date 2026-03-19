package io.github.surezzzzzz.sdk.cache.warmup;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.cache.L2Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
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

        log.info("开始执行缓存预热...");

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
            log.info("未找到需要预热的缓存方法");
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

                log.info("开始执行 order={} 的预热任务，共 {} 个", order, orderTasks.size());

                // 同order的任务并行执行
                List<CompletableFuture<Void>> futures = orderTasks.stream()
                        .map(task -> CompletableFuture.runAsync(() -> executeWarmUpTask(task), warmupExecutor))
                        .collect(Collectors.toList());

                // 等待当前order的所有任务完成后，再执行下一个order
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                log.info("order={} 的预热任务执行完成", order);
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

        log.info("缓存预热完成，共执行 {} 个预热任务", tasks.size());
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
            log.info("执行预热任务: {}.{}, cacheName={}, order={}",
                    task.bean.getClass().getSimpleName(),
                    task.method.getName(),
                    cacheName,
                    task.warmUp.order());

            // 尝试获取分布式锁（仅用于控制 L2 预热）
            if (redisLock != null && l2Cache != null && redisTemplate != null) {
                try {
                    locked = redisLock.tryLock(lockKey, requestId, SmartCacheConstant.WARMUP_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (locked) {
                        log.info("获取预热锁成功，开始执行预热方法");
                        // 执行预热方法并写入 L2
                        Map<String, Object> data = executeWarmUpMethod(task);
                        if (data != null && !data.isEmpty()) {
                            // 写入 L2 (Redis) 和 L1 (Caffeine)
                            cacheManager.putAll(cacheName, data);
                            log.info("预热成功: {} 个缓存项已写入 L2 (Redis) 和 L1 (Caffeine)", data.size());

                            // 保存预热的 key 列表，供其他实例使用
                            List<String> keys = new ArrayList<>(data.keySet());
                            int ttlSeconds = properties != null && properties.getWarmUp() != null
                                    ? properties.getWarmUp().getCompletionMarkTtlSeconds()
                                    : SmartCacheConstant.WARMUP_COMPLETE_MARK_TTL_SECONDS;
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
                            log.info("设置预热完成标记: {}", warmupCompleteKey);
                        }
                    } else {
                        log.info("未获取到预热锁，等待其他实例完成预热");
                        // 等待预热完成标记
                        if (waitForWarmupComplete(warmupCompleteKey)) {
                            log.info("检测到预热完成标记，开始从 L2 加载数据到 L1");
                            // 从 L2 加载预热数据到 L1
                            loadWarmupDataToL1(warmupKeysKey, cacheName);
                        } else {
                            log.warn("等待预热完成超时，L1 将在后续访问时自动从 L2 加载");
                        }
                    }
                } catch (Exception e) {
                    log.error("预热过程异常", e);
                }
            } else {
                // 没有分布式锁或 L2，直接执行
                log.info("无分布式锁或 L2，直接执行预热方法");
                Map<String, Object> data = executeWarmUpMethod(task);
                if (data != null && !data.isEmpty()) {
                    cacheManager.putAll(cacheName, data);
                    log.info("预热成功: {} 个缓存项已写入缓存", data.size());
                }
            }

        } catch (Exception e) {
            log.error("执行预热任务失败: {}.{}",
                    task.bean.getClass().getSimpleName(),
                    task.method.getName(), e);
        } finally {
            // 释放锁
            if (locked && redisLock != null) {
                try {
                    redisLock.unlock(lockKey, requestId);
                    log.debug("释放预热锁成功");
                } catch (Exception e) {
                    log.error("释放预热锁失败", e);
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
                log.error("检查预热完成标记失败", e);
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
                log.info("L1 预热完成: {} 个缓存项已从 L2 加载到 L1", keys.size());
            } else {
                log.warn("未找到预热 key 列表");
            }
        } catch (Exception e) {
            log.error("L1 预热失败", e);
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
            log.warn("预热方法返回null");
            return null;
        }

        log.info("预热方法返回结果类型: {}", result.getClass().getName());
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            log.info("准备写入 {} 个缓存项", map.size());
            return map;
        } else if (result instanceof List) {
            log.warn("预热方法返回List类型暂不支持，请返回Map<String, Object>类型");
            return null;
        } else {
            log.warn("预热方法返回类型不支持: {}", result.getClass().getName());
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
