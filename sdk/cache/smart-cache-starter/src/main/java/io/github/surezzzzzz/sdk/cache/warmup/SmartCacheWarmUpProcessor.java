package io.github.surezzzzzz.sdk.cache.warmup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheWarmUpException;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/**
 * Smart Cache 预热处理器
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
    @Qualifier(SmartCacheConstant.SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
    private ObjectMapper smartCacheObjectMapper;

    @Autowired
    @Qualifier(SmartCacheConstant.SMART_CACHE_WARMUP_EXECUTOR_BEAN_NAME)
    private Executor warmupExecutor;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        if (context.getParent() != null) {
            return;
        }

        log.info("开始执行缓存预热");
        List<WarmUpTask> tasks = findWarmUpTasks(context);
        if (tasks.isEmpty()) {
            log.info("未发现缓存预热方法，跳过预热");
            return;
        }

        Map<Integer, List<WarmUpTask>> tasksByOrder = groupTasksByOrder(tasks);
        for (Map.Entry<Integer, List<WarmUpTask>> entry : tasksByOrder.entrySet()) {
            int order = entry.getKey();
            List<WarmUpTask> orderTasks = entry.getValue();
            log.info("执行缓存预热任务，order：{}，数量：{}", order, orderTasks.size());

            List<CacheWarmUpException> failures = executeOrderTasks(orderTasks);
            if (!failures.isEmpty()) {
                if (isFailFast()) {
                    throw failures.get(0);
                }
                failures.forEach(failure -> log.error("缓存预热任务执行失败：{}", failure.getMessage(), failure));
            }
            log.info("缓存预热任务完成，order：{}", order);
        }

        log.info("缓存预热完成，执行任务数：{}", tasks.size());
    }

    private List<WarmUpTask> findWarmUpTasks(ApplicationContext context) {
        List<WarmUpTask> tasks = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(beanName);
            for (Method method : bean.getClass().getDeclaredMethods()) {
                SmartCacheWarmUp warmUp = method.getAnnotation(SmartCacheWarmUp.class);
                if (warmUp != null) {
                    tasks.add(new WarmUpTask(bean, method, warmUp));
                }
            }
        }
        return tasks;
    }

    private Map<Integer, List<WarmUpTask>> groupTasksByOrder(List<WarmUpTask> tasks) {
        tasks.sort(Comparator.comparingInt(task -> task.warmUp.order()));
        Map<Integer, List<WarmUpTask>> tasksByOrder = new LinkedHashMap<>();
        for (WarmUpTask task : tasks) {
            tasksByOrder.computeIfAbsent(task.warmUp.order(), ignored -> new ArrayList<>()).add(task);
        }
        return tasksByOrder;
    }

    private List<CacheWarmUpException> executeOrderTasks(List<WarmUpTask> tasks) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<WarmUpTask> submittedTasks = new ArrayList<>();
        List<CacheWarmUpException> failures = new ArrayList<>();
        for (WarmUpTask task : tasks) {
            try {
                futures.add(CompletableFuture.runAsync(() -> executeWarmUpTask(task), warmupExecutor));
                submittedTasks.add(task);
            } catch (RejectedExecutionException e) {
                failures.add(createWarmUpException(task, e));
            }
        }
        for (int i = 0; i < futures.size(); i++) {
            try {
                futures.get(i).join();
            } catch (CompletionException e) {
                failures.add(toWarmUpException(submittedTasks.get(i), e));
            }
        }
        return failures;
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
        RedisLockLease lease = null;

        try {
            log.info("执行缓存预热方法：{}.{}, cacheName：{}，order：{}",
                    task.bean.getClass().getSimpleName(), task.method.getName(), cacheName, task.warmUp.order());
            if (redisLock != null && l2Cache != null && redisRouteTemplate != null) {
                Optional<RedisLockLease> optionalLease = redisLock.tryLockWithLease(
                        lockKey, getLockTimeoutSeconds(), TimeUnit.SECONDS);
                if (optionalLease.isPresent()) {
                    lease = optionalLease.get();
                    writeWarmUpData(task, lease, cacheName, warmupKeysKey, warmupCompleteKey);
                } else if (waitForWarmupComplete(warmupCompleteKey)) {
                    loadWarmupDataToL1(warmupKeysKey, cacheName);
                } else {
                    throw createWarmUpException(task, ErrorMessage.SMART_CACHE_WARMUP_WAIT_TIMEOUT, null);
                }
            } else {
                writeWarmUpData(task, null, cacheName, null, null);
            }
        } catch (CacheWarmUpException e) {
            throw e;
        } catch (Exception e) {
            throw createWarmUpException(task, e);
        } finally {
            closeLease(lease);
        }
    }

    private void writeWarmUpData(WarmUpTask task, RedisLockLease lease, String cacheName,
                                 String warmupKeysKey, String warmupCompleteKey) throws Exception {
        Map<String, Object> data = executeWarmUpMethod(task);
        if (data == null || data.isEmpty()) {
            return;
        }
        if (lease != null && !renewLeaseBeforeWrite(lease, getLockTimeoutSeconds(), cacheName)) {
            throw createWarmUpException(task, ErrorMessage.SMART_CACHE_WARMUP_LEASE_RENEW_FAILED, null);
        }
        cacheManager.putAll(cacheName, data);
        log.info("缓存预热成功，写入缓存条目数：{}", data.size());
        if (warmupKeysKey == null || warmupCompleteKey == null) {
            return;
        }

        int ttlSeconds = getCompletionMarkTtlSeconds();
        String keysPayload = smartCacheObjectMapper.writeValueAsString(new ArrayList<>(data.keySet()));
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

    private void closeLease(RedisLockLease lease) {
        if (lease == null) {
            return;
        }
        try {
            lease.close();
            log.debug("预热租约已释放");
        } catch (Exception e) {
            log.error("释放预热租约失败", e);
        }
    }

    private int getLockTimeoutSeconds() {
        return properties != null && properties.getLock() != null
                ? properties.getLock().getTimeoutSeconds()
                : SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS;
    }

    private int getCompletionMarkTtlSeconds() {
        return properties != null && properties.getWarmUp() != null
                ? properties.getWarmUp().getCompletionMarkTtlSeconds()
                : SmartCacheConstant.DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS;
    }

    private boolean isFailFast() {
        return properties != null && properties.getWarmUp() != null
                && SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST.equals(
                properties.getWarmUp().getFailurePolicy());
    }

    private boolean renewLeaseBeforeWrite(RedisLockLease lease, int lockTimeout, String cacheName) throws Exception {
        try {
            if (lease.renew(lockTimeout, TimeUnit.SECONDS)) {
                return true;
            }
            log.warn("预热租约已失效，丢弃预热数据与完成标记，cacheName：{}", cacheName);
            return false;
        } catch (Exception e) {
            log.warn("预热租约续租失败，丢弃预热数据与完成标记，cacheName：{}，原因：{}",
                    cacheName, e.getMessage());
            throw e;
        }
    }

    private boolean waitForWarmupComplete(String warmupCompleteKey) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = SmartCacheConstant.WARMUP_WAIT_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            String value = redisRouteTemplate.execute(warmupCompleteKey,
                    template -> template.opsForValue().get(warmupCompleteKey));
            if (SmartCacheConstant.WARMUP_COMPLETE_MARK_VALUE.equals(value)) {
                return true;
            }
            try {
                Thread.sleep(SmartCacheConstant.WARMUP_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
        return false;
    }

    private void loadWarmupDataToL1(String warmupKeysKey, String cacheName) throws Exception {
        String keysPayload = redisRouteTemplate.execute(warmupKeysKey,
                template -> template.opsForValue().get(warmupKeysKey));
        List<String> keys = keysPayload == null || keysPayload.isEmpty()
                ? Collections.emptyList()
                : smartCacheObjectMapper.readValue(keysPayload, new TypeReference<List<String>>() {
        });
        if (keys == null || keys.isEmpty()) {
            log.warn("未找到预热 key 列表");
            return;
        }
        for (String key : keys) {
            Object value = l2Cache.get(cacheName, key);
            if (value != null && l1Cache != null) {
                l1Cache.put(cacheName, key, value);
            }
        }
        log.info("L1 预热完成，从 L2 加载条目数：{}", keys.size());
    }

    private Map<String, Object> executeWarmUpMethod(WarmUpTask task) throws Exception {
        task.method.setAccessible(true);
        Object result;
        try {
            result = task.method.invoke(task.bean);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
        if (result == null) {
            return null;
        }
        if (!(result instanceof Map)) {
            throw createWarmUpException(task, ErrorMessage.SMART_CACHE_WARMUP_RETURN_TYPE_INVALID, null);
        }
        Map<?, ?> source = (Map<?, ?>) result;
        Map<String, Object> data = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw createWarmUpException(task, ErrorMessage.SMART_CACHE_WARMUP_MAP_KEY_INVALID, null);
            }
            data.put((String) entry.getKey(), entry.getValue());
        }
        return data;
    }

    private CacheWarmUpException createWarmUpException(WarmUpTask task, Throwable cause) {
        String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return createWarmUpException(task, reason, cause);
    }

    private CacheWarmUpException createWarmUpException(WarmUpTask task, String reason, Throwable cause) {
        String taskName = String.format(ErrorMessage.SMART_CACHE_WARMUP_TASK_NAME,
                task.bean.getClass().getSimpleName(), task.method.getName());
        String detail = String.format(ErrorMessage.SMART_CACHE_WARMUP_FAILURE_DETAIL, taskName, reason);
        return new CacheWarmUpException(ErrorCode.SMART_CACHE_WARMUP_FAILED,
                String.format(ErrorMessage.SMART_CACHE_WARMUP_FAILED, detail), cause);
    }

    private CacheWarmUpException toWarmUpException(WarmUpTask task, CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof CacheWarmUpException) {
            return (CacheWarmUpException) cause;
        }
        return createWarmUpException(task, cause == null ? exception : cause);
    }

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
