package io.github.surezzzzzz.sdk.cache.warmup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Smart Cache 预热处理器
 * <p>
 * 缓存预热处理器，在应用启动后自动执行预热方法
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
@ConditionalOnProperty(prefix = SmartCacheConstant.CONFIG_PREFIX, name = SmartCacheConstant.PROPERTY_ENABLED,
        havingValue = SmartCacheConstant.PROPERTY_VALUE_TRUE, matchIfMissing = true)
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
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier(SmartCacheConstant.SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
    private ObjectMapper smartCacheObjectMapper;

    @Autowired
    @Qualifier(SmartCacheConstant.SMART_CACHE_WARMUP_EXECUTOR_BEAN_NAME)
    private Executor warmupExecutor;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();

        // 只在根容器启动时执行一次
        if (context.getParent() != null) {
            return;
        }

        log.info("开始执行缓存预热");

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
            log.info("未发现缓存预热方法，跳过预热");
            return;
        }

        // 按order排序
        tasks.sort(Comparator.comparingInt(t -> t.warmUp.order()));

        // 按order分组
        Map<Integer, List<WarmUpTask>> tasksByOrder = new LinkedHashMap<>();
        for (WarmUpTask task : tasks) {
            tasksByOrder.computeIfAbsent(task.warmUp.order(), k -> new ArrayList<>()).add(task);
        }

        // 按order顺序执行，同order的任务并行执行
        for (Map.Entry<Integer, List<WarmUpTask>> entry : tasksByOrder.entrySet()) {
            int order = entry.getKey();
            List<WarmUpTask> orderTasks = entry.getValue();

            log.info("执行缓存预热任务，order：{}，数量：{}", order, orderTasks.size());

            try {
                List<CompletableFuture<Void>> futures = orderTasks.stream()
                        .map(task -> CompletableFuture.runAsync(() -> executeWarmUpTask(task), warmupExecutor))
                        .collect(Collectors.toList());
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (RejectedExecutionException e) {
                throw new IllegalStateException("缓存预热线程池已饱和，order：" + order, e);
            }

            log.info("缓存预热任务完成，order：{}", order);
        }

        log.info("缓存预热完成，执行任务数：{}", tasks.size());
    }

    private void executeWarmUpTask(WarmUpTask task) {
        String cacheName = task.warmUp.cacheName();
        String keyPrefix = properties != null ? properties.getKeyPrefix() : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null ? properties.getMe() : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String lockKey = KeyHelper.buildLockKey(keyPrefix, cacheName, me, SmartCacheConstant.WARMUP_LOCK_KEY);
        String warmupCompleteKey = KeyHelper.buildWarmUpMetadataKey(keyPrefix, cacheName, me,
                SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX);
        String warmupKeysKey = KeyHelper.buildWarmUpMetadataKey(keyPrefix, cacheName, me,
                SmartCacheConstant.WARMUP_KEYS_KEY_SUFFIX);
        int lockTimeout = getLockTimeoutSeconds();
        RedisLockLease lease = null;

        try {
            log.info("执行缓存预热方法：{}.{}, cacheName：{}，order：{}",
                    task.bean.getClass().getSimpleName(),
                    task.method.getName(),
                    cacheName,
                    task.warmUp.order());

            // 尝试获取分布式锁（仅用于控制 L2 预热）
            if (redisLock != null && l2Cache != null && redisRouteTemplate != null) {
                try {
                    Optional<RedisLockLease> optionalLease = redisLock.tryLockWithLease(
                            lockKey, lockTimeout, TimeUnit.SECONDS);
                    if (optionalLease.isPresent()) {
                        lease = optionalLease.get();
                        log.info("已获取预热租约，开始执行预热方法");
                        Map<String, Object> data = executeWarmUpMethod(task);
                        if (data != null && !data.isEmpty()) {
                            if (!renewLeaseBeforeWrite(lease, lockTimeout, cacheName)) {
                                return;
                            }
                            cacheManager.putAll(cacheName, data);
                            log.info("缓存预热成功，写入 L2 和 L1 的条目数：{}", data.size());

                            List<String> keys = new ArrayList<>(data.keySet());
                            int ttlSeconds = properties != null && properties.getWarmUp() != null
                                    ? properties.getWarmUp().getCompletionMarkTtlSeconds()
                                    : SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;
                            String keysPayload = smartCacheObjectMapper.writeValueAsString(keys);
                            redisRouteTemplate.execute(warmupKeysKey, template -> {
                                template.opsForValue().set(warmupKeysKey, keysPayload, ttlSeconds, TimeUnit.SECONDS);
                                return null;
                            });

                            redisRouteTemplate.execute(warmupCompleteKey, template -> {
                                template.opsForValue().set(warmupCompleteKey,
                                        SmartCacheConstant.WARMUP_COMPLETE_MARK_VALUE, ttlSeconds, TimeUnit.SECONDS);
                                return null;
                            });
                            log.info("已设置缓存预热完成标记：{}", warmupCompleteKey);
                        }
                    } else {
                        log.info("未获取预热租约，等待其他实例完成预热");
                        if (waitForWarmupComplete(warmupCompleteKey)) {
                            log.info("检测到预热完成标记，开始从 L2 加载数据到 L1");
                            loadWarmupDataToL1(warmupKeysKey, cacheName);
                        } else {
                            log.warn("等待预热完成超时，L1 将在首次访问时填充");
                        }
                    }
                } catch (Exception e) {
                    log.error("缓存预热过程异常", e);
                }
            } else {
                // 没有分布式锁或 L2，直接执行
                log.info("未配置分布式锁或 L2，直接执行缓存预热");
                Map<String, Object> data = executeWarmUpMethod(task);
                if (data != null && !data.isEmpty()) {
                    cacheManager.putAll(cacheName, data);
                    log.info("缓存预热成功，写入缓存条目数：{}", data.size());
                }
            }

        } catch (Exception e) {
            log.error("缓存预热任务执行失败：{}.{}",
                    task.bean.getClass().getSimpleName(),
                    task.method.getName(), e);
        } finally {
            if (lease != null) {
                try {
                    lease.close();
                    log.debug("预热租约已释放");
                } catch (Exception e) {
                    log.error("释放预热租约失败", e);
                }
            }
        }
    }

    private int getLockTimeoutSeconds() {
        return properties != null && properties.getLock() != null
                ? properties.getLock().getTimeoutSeconds()
                : SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
    }

    private boolean renewLeaseBeforeWrite(RedisLockLease lease, int lockTimeout, String cacheName) {
        try {
            if (lease.renew(lockTimeout, TimeUnit.SECONDS)) {
                return true;
            }
            log.warn("预热租约已失效，丢弃预热数据与完成标记，cacheName：{}", cacheName);
        } catch (Exception e) {
            log.warn("预热租约续租失败，丢弃预热数据与完成标记，cacheName：{}，原因：{}",
                    cacheName, e.getMessage());
        }
        return false;
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
                String value = redisRouteTemplate.execute(warmupCompleteKey,
                        template -> template.opsForValue().get(warmupCompleteKey));
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
            String keysPayload = redisRouteTemplate.execute(warmupKeysKey,
                    template -> template.opsForValue().get(warmupKeysKey));
            List<String> keys = keysPayload == null || keysPayload.isEmpty()
                    ? Collections.emptyList()
                    : smartCacheObjectMapper.readValue(keysPayload, new TypeReference<List<String>>() {
            });
            if (keys != null && !keys.isEmpty()) {
                // 从 L2 读取并写入 L1
                for (String key : keys) {
                    Object value = l2Cache.get(cacheName, key);
                    if (value != null && l1Cache != null) {
                        l1Cache.put(cacheName, key, value);
                    }
                }
                log.info("L1 预热完成，从 L2 加载条目数：{}", keys.size());
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
            log.warn("预热方法返回 null");
            return null;
        }

        log.info("预热方法返回类型：{}", result.getClass().getName());
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            log.info("准备写入缓存条目数：{}", map.size());
            return map;
        } else if (result instanceof List) {
            log.warn("预热方法返回 List，当前不支持，请返回 Map<String, Object>");
            return null;
        } else {
            log.warn("不支持的预热方法返回类型：{}", result.getClass().getName());
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
