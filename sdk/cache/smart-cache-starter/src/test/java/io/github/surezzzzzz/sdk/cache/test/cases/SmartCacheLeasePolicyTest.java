package io.github.surezzzzzz.sdk.cache.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.cache.CachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheWarmUpException;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.cache.warmup.SmartCacheWarmUpProcessor;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Smart Cache 租约策略测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class SmartCacheLeasePolicyTest {

    private static final String CACHE_NAME = "lease-cache";
    private static final String CACHE_KEY = "lease-key";
    private static final String CACHE_VALUE = "lease-value";

    @Test
    @DisplayName("缓存击穿续租成功后写入 L1 和 L2，并关闭一次租约")
    void shouldWriteCacheAfterCacheMissLeaseRenews() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        SmartCacheManager manager = newManager(executor);
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);

        String result = manager.get(CACHE_NAME, CACHE_KEY, () -> CACHE_VALUE);

        log.info("缓存击穿续租成功结果：{}，续租次数：{}，释放次数：{}",
                result, executor.getRenewCount(), executor.getUnlockCount());
        assertEquals(CACHE_VALUE, result, "续租成功时应返回 loader 结果");
        assertEquals(SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS, executor.getLastAcquireLeaseSeconds(),
                "缓存击穿初始租约应使用 lock.timeout-seconds");
        assertEquals(SmartCacheConstant.DEFAULT_LOCK_TIMEOUT_SECONDS, executor.getLastRenewLeaseSeconds(),
                "缓存击穿续租应使用 lock.timeout-seconds");
        assertEquals(1, executor.getRenewCount(), "写缓存前应续租一次");
        assertEquals(1, executor.getUnlockCount(), "租约应只关闭一次");
        verify(l2Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        verify(l1Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
    }

    @Test
    @DisplayName("缓存击穿续租失效时返回结果但不写 L1、L2 或空值占位")
    void shouldDiscardCacheMissResultWhenLeaseRenewReturnsFalse() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setRenewResult(false);
        SmartCacheManager manager = newManager(executor);
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        AtomicInteger loaderCount = new AtomicInteger();

        String result = manager.get(CACHE_NAME, CACHE_KEY, () -> {
            loaderCount.incrementAndGet();
            return CACHE_VALUE;
        });

        log.info("缓存击穿失租结果：{}，loader 次数：{}，续租次数：{}，释放次数：{}",
                result, loaderCount.get(), executor.getRenewCount(), executor.getUnlockCount());
        assertEquals(CACHE_VALUE, result, "失租后仍应返回已计算的业务结果");
        assertEquals(1, loaderCount.get(), "失租后不得重新执行 loader");
        assertEquals(1, executor.getRenewCount(), "写缓存前应尝试续租一次");
        assertEquals(1, executor.getUnlockCount(), "失租后仍应关闭租约一次");
        verify(l2Cache, never()).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        verify(l1Cache, never()).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
    }

    @Test
    @DisplayName("缓存击穿续租异常时返回结果且不写空值占位")
    void shouldDiscardNullCacheMissResultWhenLeaseRenewThrows() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setRenewFailure(new IllegalStateException("测试续租异常"));
        SmartCacheManager manager = newManager(executor);
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        AtomicInteger loaderCount = new AtomicInteger();

        Object result = manager.get(CACHE_NAME, CACHE_KEY, () -> {
            loaderCount.incrementAndGet();
            return null;
        });

        log.info("缓存击穿续租异常结果：{}，loader 次数：{}，续租次数：{}，释放次数：{}",
                result, loaderCount.get(), executor.getRenewCount(), executor.getUnlockCount());
        assertNull(result, "续租异常时应返回 loader 的 null 结果");
        assertEquals(1, loaderCount.get(), "续租异常不得重新执行 loader");
        assertEquals(1, executor.getRenewCount(), "写入空值占位前应尝试续租一次");
        assertEquals(1, executor.getUnlockCount(), "续租异常后仍应关闭租约一次");
        verify(l1Cache, never()).put(CACHE_NAME, CACHE_KEY, SmartCacheConstant.NULL_PLACEHOLDER);
        verify(l2Cache, never()).put(eq(CACHE_NAME), eq(CACHE_KEY), any(Object.class));
    }

    @Test
    @DisplayName("租约关闭异常不覆盖缓存击穿的业务结果")
    void shouldKeepCacheMissResultWhenLeaseCloseThrows() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setUnlockFailure(new IllegalStateException("测试租约关闭异常"));
        SmartCacheManager manager = newManager(executor);
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);

        String result = manager.get(CACHE_NAME, CACHE_KEY, () -> CACHE_VALUE);

        log.info("租约关闭异常结果：{}，续租次数：{}，释放次数：{}",
                result, executor.getRenewCount(), executor.getUnlockCount());
        assertEquals(CACHE_VALUE, result, "关闭租约异常不得覆盖 loader 结果");
        assertEquals(1, executor.getRenewCount(), "关闭前应已完成一次续租");
        assertEquals(1, executor.getUnlockCount(), "关闭异常路径也只应调用一次释放");
        verify(l2Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        verify(l1Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
    }

    @Test
    @DisplayName("预刷新续租成功后写回缓存并关闭一次租约")
    void shouldWritePreloadResultAfterLeaseRenews() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        FixedPreloadHandler handler = new FixedPreloadHandler(CACHE_VALUE);
        SmartCacheManager manager = newPreloadManager(executor, handler);
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        CacheInvalidationListener invalidationListener = mock(CacheInvalidationListener.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        ReflectionTestUtils.setField(manager, "invalidationListener", invalidationListener);
        when(l2Cache.get(CACHE_NAME, CACHE_KEY, Object.class)).thenReturn("old-value");

        String result = manager.get(CACHE_NAME, CACHE_KEY, () -> "unexpected-value");

        log.info("预刷新续租成功结果：{}，续租次数：{}，释放次数：{}",
                result, executor.getRenewCount(), executor.getUnlockCount());
        assertEquals("old-value", result, "L2 命中时当前请求应返回旧值");
        assertEquals(1, handler.getReloadCount(), "预刷新任务应只执行一次 reload");
        assertEquals(1, executor.getRenewCount(), "预刷新写入前应续租一次");
        assertEquals(1, executor.getUnlockCount(), "预刷新完成后应关闭租约一次");
        verify(l2Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        verify(l1Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        verify(invalidationListener).publishInvalidation(CACHE_NAME, CACHE_KEY, SmartCacheConstant.OPERATION_EVICT);
    }

    @Test
    @DisplayName("预刷新续租失效或异常时丢弃 reload 结果")
    void shouldDiscardPreloadResultWhenLeaseCannotRenew() {
        assertPreloadWriteSuppressed(false, null);
        assertPreloadWriteSuppressed(true, new IllegalStateException("测试预刷新续租异常"));
    }

    @Test
    @DisplayName("预刷新任务被拒绝时关闭租约且不执行 reload")
    void shouldCloseLeaseWhenPreloadExecutorRejects() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        FixedPreloadHandler handler = new FixedPreloadHandler(CACHE_VALUE);
        SmartCacheManager manager = newPreloadManager(executor, handler);
        L2Cache l2Cache = mock(L2Cache.class);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        ReflectionTestUtils.setField(manager, "preloadExecutor", new RejectingExecutor());
        when(l2Cache.get(CACHE_NAME, CACHE_KEY, Object.class)).thenReturn("old-value");

        String result = manager.get(CACHE_NAME, CACHE_KEY, () -> "unexpected-value");

        log.info("预刷新拒绝结果：{}，reload 次数：{}，释放次数：{}",
                result, handler.getReloadCount(), executor.getUnlockCount());
        assertEquals("old-value", result, "任务被拒绝时当前请求应返回旧值");
        assertEquals(0, handler.getReloadCount(), "任务被拒绝后不得执行 reload");
        assertEquals(0, executor.getRenewCount(), "任务未执行时不得续租");
        assertEquals(1, executor.getUnlockCount(), "任务被拒绝后应关闭租约一次");
    }

    @Test
    @DisplayName("启动预热续租成功后写入数据与完成元数据")
    void shouldWriteWarmupDataAndMetadataAfterLeaseRenews() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheManager manager = mock(SmartCacheManager.class);
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        ReflectionTestUtils.setField(processor, "cacheManager", manager);
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", routeTemplate);

        processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext()));

        log.info("预热续租成功次数：{}，释放次数：{}", executor.getRenewCount(), executor.getUnlockCount());
        assertEquals(1, executor.getRenewCount(), "预热数据写入前应续租一次");
        assertEquals(1, executor.getUnlockCount(), "预热完成后应关闭租约一次");
        verify(manager).putAll(CACHE_NAME, Collections.<String, Object>singletonMap(CACHE_KEY, CACHE_VALUE));
        verify(routeTemplate, times(2)).execute(any(String.class), any());
    }

    @Test
    @DisplayName("启动预热续租失效或异常时不写数据和完成元数据")
    void shouldDiscardWarmupDataWhenLeaseCannotRenew() {
        assertWarmupWriteSuppressed(false, null);
        assertWarmupWriteSuppressed(true, new IllegalStateException("测试预热续租异常"));
    }

    @Test
    @DisplayName("fail-fast 在预热续租异常时抛出 CacheWarmUpException")
    void shouldFailFastWhenWarmupLeaseRenewThrows() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setRenewFailure(new IllegalStateException("测试预热续租异常"));
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getLock().setTimeoutSeconds(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS);
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        ReflectionTestUtils.setField(processor, "properties", properties);
        ReflectionTestUtils.setField(processor, "cacheManager", mock(SmartCacheManager.class));
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", mock(RedisRouteTemplate.class));

        CacheWarmUpException exception;
        try {
            processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext()));
            exception = null;
        } catch (CacheWarmUpException e) {
            exception = e;
        }

        log.info("严格预热续租异常错误码：{}，续租次数：{}，释放次数：{}",
                exception == null ? null : exception.getErrorCode(), executor.getRenewCount(), executor.getUnlockCount());
        assertNotNull(exception, "严格预热在续租异常时必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "应使用统一预热错误码");
        assertEquals(1, executor.getRenewCount(), "写入前应尝试续租一次");
        assertEquals(1, executor.getUnlockCount(), "失败后仍应释放租约");
    }

    @Test
    @DisplayName("fail-fast 在预热续租失效时抛出 CacheWarmUpException")
    void shouldFailFastWhenWarmupLeaseCannotRenew() {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setRenewResult(false);
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getLock().setTimeoutSeconds(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS);
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        ReflectionTestUtils.setField(processor, "properties", properties);
        ReflectionTestUtils.setField(processor, "cacheManager", mock(SmartCacheManager.class));
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", mock(RedisRouteTemplate.class));

        CacheWarmUpException exception;
        try {
            processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext()));
            exception = null;
        } catch (CacheWarmUpException e) {
            exception = e;
        }

        log.info("严格预热续租失败错误码：{}，续租次数：{}，释放次数：{}",
                exception == null ? null : exception.getErrorCode(), executor.getRenewCount(), executor.getUnlockCount());
        assertNotNull(exception, "严格预热在续租失败时必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "应使用统一预热错误码");
        assertEquals(1, executor.getRenewCount(), "写入前应尝试续租一次");
        assertEquals(1, executor.getUnlockCount(), "失败后仍应释放租约");
    }

    @Test
    @DisplayName("预热完成元数据写入失败按策略决定是否进入后续 order")
    void shouldApplyPolicyWhenWarmupMetadataWriteFails() {
        LeaseRecordingExecutor continueExecutor = new LeaseRecordingExecutor();
        SmartCacheWarmUpProcessor continueProcessor = newWarmupProcessor(continueExecutor);
        SmartCacheManager continueManager = mock(SmartCacheManager.class);
        RedisRouteTemplate continueRouteTemplate = mock(RedisRouteTemplate.class);
        doThrow(new IllegalStateException("测试预热完成元数据写入异常"))
                .when(continueRouteTemplate).execute(any(String.class), any());
        ReflectionTestUtils.setField(continueProcessor, "cacheManager", continueManager);
        ReflectionTestUtils.setField(continueProcessor, "redisRouteTemplate", continueRouteTemplate);
        PolicyLeaseWarmupFixture continueFixture = new PolicyLeaseWarmupFixture();

        continueProcessor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext(continueFixture)));

        log.info("continue 元数据写入失败后续 order 执行次数：{}，续租次数：{}，释放次数：{}",
                continueFixture.getLaterOrderCount(), continueExecutor.getRenewCount(), continueExecutor.getUnlockCount());
        assertEquals(1, continueFixture.getLaterOrderCount(), "默认 continue 应继续执行后续 order");
        assertEquals(1, continueExecutor.getRenewCount(), "失败任务写缓存前应完成一次续租");
        assertEquals(2, continueExecutor.getUnlockCount(), "continue 的失败任务和后续任务都应关闭各自租约");
        verify(continueManager).putAll(CACHE_NAME, Collections.<String, Object>singletonMap(CACHE_KEY, CACHE_VALUE));

        LeaseRecordingExecutor failFastExecutor = new LeaseRecordingExecutor();
        SmartCacheWarmUpProcessor failFastProcessor = newWarmupProcessor(failFastExecutor);
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getLock().setTimeoutSeconds(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS);
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        ReflectionTestUtils.setField(failFastProcessor, "properties", properties);
        SmartCacheManager failFastManager = mock(SmartCacheManager.class);
        RedisRouteTemplate failFastRouteTemplate = mock(RedisRouteTemplate.class);
        doThrow(new IllegalStateException("测试预热完成元数据写入异常"))
                .when(failFastRouteTemplate).execute(any(String.class), any());
        ReflectionTestUtils.setField(failFastProcessor, "cacheManager", failFastManager);
        ReflectionTestUtils.setField(failFastProcessor, "redisRouteTemplate", failFastRouteTemplate);
        PolicyLeaseWarmupFixture failFastFixture = new PolicyLeaseWarmupFixture();

        CacheWarmUpException exception;
        try {
            failFastProcessor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext(failFastFixture)));
            exception = null;
        } catch (CacheWarmUpException e) {
            exception = e;
        }

        log.info("fail-fast 元数据写入失败错误码：{}，错误消息：{}，后续 order 执行次数：{}，释放次数：{}",
                exception == null ? null : exception.getErrorCode(), exception == null ? null : exception.getMessage(),
                failFastFixture.getLaterOrderCount(), failFastExecutor.getUnlockCount());
        assertNotNull(exception, "严格模式在完成元数据写入失败后必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "应使用统一预热错误码");
        assertTrue(exception.getMessage().contains("PolicyLeaseWarmupFixture.first"), "错误消息必须包含失败预热任务名称");
        assertEquals(0, failFastFixture.getLaterOrderCount(), "严格模式不应进入后续 order");
        assertEquals(1, failFastExecutor.getUnlockCount(), "失败任务应关闭一次租约");
        verify(failFastManager).putAll(CACHE_NAME, Collections.<String, Object>singletonMap(CACHE_KEY, CACHE_VALUE));
    }

    @Test
    @DisplayName("竞争实例回填 L1 失败按策略决定是否进入后续 order")
    void shouldApplyPolicyWhenWarmupL1RefillFails() {
        assertWarmupL1RefillFailurePolicy(false);
        assertWarmupL1RefillFailurePolicy(true);
    }

    @Test
    @DisplayName("等待竞争实例完成标记超时按策略决定是否进入后续 order")
    void shouldApplyPolicyWhenWaitingForWarmupCompletionTimesOut() {
        assertWarmupCompletionTimeoutPolicy(false);
        assertWarmupCompletionTimeoutPolicy(true);
    }

    @Test
    @DisplayName("竞争实例读取完成元数据失败按策略决定是否进入后续 order")
    void shouldApplyPolicyWhenReadingWarmupMetadataFails() {
        assertWarmupMetadataReadFailurePolicy(false);
        assertWarmupMetadataReadFailurePolicy(true);
    }

    private void assertWarmupL1RefillFailurePolicy(boolean failFast) {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setLockResults(false, true);
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getLock().setTimeoutSeconds(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS);
        if (failFast) {
            properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        }
        ReflectionTestUtils.setField(processor, "properties", properties);
        RedisRouteTemplate routeTemplate = warmupCompletedRouteTemplate();
        L2Cache l2Cache = mock(L2Cache.class);
        L1Cache l1Cache = mock(L1Cache.class);
        doThrow(new IllegalStateException("测试预热 L1 回填异常"))
                .when(l1Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        when(l2Cache.get(CACHE_NAME, CACHE_KEY)).thenReturn(CACHE_VALUE);
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", routeTemplate);
        ReflectionTestUtils.setField(processor, "l2Cache", l2Cache);
        ReflectionTestUtils.setField(processor, "l1Cache", l1Cache);
        PolicyLeaseWarmupFixture fixture = new PolicyLeaseWarmupFixture();

        CacheWarmUpException exception;
        try {
            processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext(fixture)));
            exception = null;
        } catch (CacheWarmUpException e) {
            exception = e;
        }

        log.info("竞争实例 L1 回填失败策略：{}，错误码：{}，后续 order 执行次数：{}，锁获取次数：{}",
                failFast ? "fail-fast" : "continue", exception == null ? null : exception.getErrorCode(),
                fixture.getLaterOrderCount(), executor.getLockAttemptCount());
        verify(l2Cache).get(CACHE_NAME, CACHE_KEY);
        verify(l1Cache).put(CACHE_NAME, CACHE_KEY, CACHE_VALUE);
        assertEquals(failFast ? 1 : 2, executor.getLockAttemptCount(), "continue 仅在后续 order 额外尝试一次预热锁");
        if (failFast) {
            assertNotNull(exception, "严格模式在 L1 回填失败后必须阻断启动");
            assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "应使用统一预热错误码");
            assertTrue(exception.getMessage().contains("PolicyLeaseWarmupFixture.first"), "错误消息必须包含失败预热任务名称");
            assertEquals(0, fixture.getLaterOrderCount(), "严格模式不应进入后续 order");
        } else {
            assertNull(exception, "默认 continue 在 L1 回填失败后不应阻断启动");
            assertEquals(1, fixture.getLaterOrderCount(), "默认 continue 应继续执行后续 order");
        }
    }

    private void assertWarmupMetadataReadFailurePolicy(boolean failFast) {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setLockResults(false, true);
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheProperties properties = new SmartCacheProperties();
        if (failFast) {
            properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        }
        ReflectionTestUtils.setField(processor, "properties", properties);
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        doThrow(new IllegalStateException("测试预热完成元数据读取异常"))
                .when(routeTemplate).execute(any(String.class), any());
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", routeTemplate);
        PolicyLeaseWarmupFixture fixture = new PolicyLeaseWarmupFixture();

        CacheWarmUpException exception;
        try {
            processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext(fixture)));
            exception = null;
        } catch (CacheWarmUpException e) {
            exception = e;
        }

        log.info("竞争实例元数据读取失败策略：{}，错误码：{}，后续 order 执行次数：{}，锁获取次数：{}",
                failFast ? "fail-fast" : "continue", exception == null ? null : exception.getErrorCode(),
                fixture.getLaterOrderCount(), executor.getLockAttemptCount());
        assertEquals(failFast ? 1 : 2, executor.getLockAttemptCount(), "continue 仅在后续 order 额外尝试一次预热锁");
        if (failFast) {
            assertNotNull(exception, "严格模式在完成元数据读取失败后必须阻断启动");
            assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "应使用统一预热错误码");
            assertTrue(exception.getMessage().contains("PolicyLeaseWarmupFixture.first"), "错误消息必须包含失败预热任务名称");
            assertEquals(0, fixture.getLaterOrderCount(), "严格模式不应进入后续 order");
        } else {
            assertNull(exception, "默认 continue 在完成元数据读取失败后不应阻断启动");
            assertEquals(1, fixture.getLaterOrderCount(), "默认 continue 应继续执行后续 order");
        }
    }

    private void assertWarmupCompletionTimeoutPolicy(boolean failFast) {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setLockResults(false, true);
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheProperties properties = new SmartCacheProperties();
        if (failFast) {
            properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        }
        ReflectionTestUtils.setField(processor, "properties", properties);
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", warmupIncompleteRouteTemplate());
        PolicyLeaseWarmupFixture fixture = new PolicyLeaseWarmupFixture();

        long startTime = System.nanoTime();
        CacheWarmUpException exception;
        try {
            processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext(fixture)));
            exception = null;
        } catch (CacheWarmUpException e) {
            exception = e;
        }
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);

        log.info("竞争实例完成标记超时策略：{}，等待秒数：{}，错误码：{}，后续 order 执行次数：{}",
                failFast ? "fail-fast" : "continue", elapsedSeconds, exception == null ? null : exception.getErrorCode(),
                fixture.getLaterOrderCount());
        assertTrue(elapsedSeconds >= SmartCacheConstant.WARMUP_WAIT_TIMEOUT_SECONDS - 1,
                "竞争实例完成标记必须等待完整超时窗口");
        assertEquals(failFast ? 1 : 2, executor.getLockAttemptCount(), "continue 仅在后续 order 额外尝试一次预热锁");
        if (failFast) {
            assertNotNull(exception, "严格模式在等待完成标记超时后必须阻断启动");
            assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "应使用统一预热错误码");
            assertTrue(exception.getMessage().contains("PolicyLeaseWarmupFixture.first"), "错误消息必须包含失败预热任务名称");
            assertEquals(0, fixture.getLaterOrderCount(), "严格模式不应进入后续 order");
        } else {
            assertNull(exception, "默认 continue 在等待完成标记超时后不应阻断启动");
            assertEquals(1, fixture.getLaterOrderCount(), "默认 continue 应继续执行后续 order");
        }
    }

    @SuppressWarnings("unchecked")
    private RedisRouteTemplate warmupIncompleteRouteTemplate() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any(String.class))).thenReturn(null);
        when(routeTemplate.execute(any(String.class), any(Function.class))).thenAnswer(invocation -> {
            Function<StringRedisTemplate, Object> callback = invocation.getArgument(1);
            return callback.apply(stringRedisTemplate);
        });
        return routeTemplate;
    }

    @SuppressWarnings("unchecked")
    private RedisRouteTemplate warmupCompletedRouteTemplate() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any(String.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.endsWith(SmartCacheConstant.WARMUP_COMPLETE_KEY_SUFFIX)
                    ? SmartCacheConstant.WARMUP_COMPLETE_MARK_VALUE
                    : "[\"lease-key\"]";
        });
        when(routeTemplate.execute(any(String.class), any(Function.class))).thenAnswer(invocation -> {
            Function<StringRedisTemplate, Object> callback = invocation.getArgument(1);
            return callback.apply(stringRedisTemplate);
        });
        return routeTemplate;
    }

    private void assertPreloadWriteSuppressed(boolean renewThrows, RuntimeException renewFailure) {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setRenewResult(false);
        if (renewThrows) {
            executor.setRenewFailure(renewFailure);
        }
        FixedPreloadHandler handler = new FixedPreloadHandler(CACHE_VALUE);
        SmartCacheManager manager = newPreloadManager(executor, handler);
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        CacheInvalidationListener invalidationListener = mock(CacheInvalidationListener.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        ReflectionTestUtils.setField(manager, "invalidationListener", invalidationListener);
        when(l2Cache.get(CACHE_NAME, CACHE_KEY, Object.class)).thenReturn("old-value");

        String result = manager.get(CACHE_NAME, CACHE_KEY, () -> "unexpected-value");

        log.info("预刷新失租类型：{}，结果：{}，续租次数：{}，释放次数：{}",
                renewThrows ? "异常" : "返回false", result, executor.getRenewCount(), executor.getUnlockCount());
        assertEquals("old-value", result, "失租时当前请求仍应返回旧值");
        assertEquals(1, handler.getReloadCount(), "失租时 reload 仍只应执行一次");
        assertEquals(CACHE_VALUE, handler.getReloadValue(), "失租时应在 reload 完成后丢弃其结果");
        assertEquals(1, executor.getRenewCount(), "失租前应尝试续租一次");
        assertEquals(1, executor.getUnlockCount(), "失租后应关闭租约一次");
        verify(l2Cache, never()).put(eq(CACHE_NAME), eq(CACHE_KEY), any(Object.class));
        verify(l1Cache, never()).put(eq(CACHE_NAME), eq(CACHE_KEY), eq(CACHE_VALUE));
        verifyNoInteractions(invalidationListener);
    }

    private void assertWarmupWriteSuppressed(boolean renewThrows, RuntimeException renewFailure) {
        LeaseRecordingExecutor executor = new LeaseRecordingExecutor();
        executor.setRenewResult(false);
        if (renewThrows) {
            executor.setRenewFailure(renewFailure);
        }
        SmartCacheWarmUpProcessor processor = newWarmupProcessor(executor);
        SmartCacheManager manager = mock(SmartCacheManager.class);
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        ReflectionTestUtils.setField(processor, "cacheManager", manager);
        ReflectionTestUtils.setField(processor, "redisRouteTemplate", routeTemplate);

        processor.onApplicationEvent(new ContextRefreshedEvent(newWarmupContext()));

        log.info("预热失租类型：{}，续租次数：{}，释放次数：{}",
                renewThrows ? "异常" : "返回false", executor.getRenewCount(), executor.getUnlockCount());
        assertEquals(1, executor.getRenewCount(), "预热写入前应尝试续租一次");
        assertEquals(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS, executor.getLastAcquireLeaseSeconds(),
                "预热初始租约应复用 lock.timeout-seconds");
        assertEquals(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS, executor.getLastRenewLeaseSeconds(),
                "预热续租应复用 lock.timeout-seconds");
        assertEquals(1, executor.getUnlockCount(), "预热失租后应关闭租约一次");
        verifyNoInteractions(manager);
        verifyNoInteractions(routeTemplate);
    }

    private SmartCacheWarmUpProcessor newWarmupProcessor(LeaseRecordingExecutor executor) {
        SmartCacheWarmUpProcessor processor = new SmartCacheWarmUpProcessor();
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getLock().setTimeoutSeconds(SmartCacheConstant.MIN_LOCK_TIMEOUT_SECONDS);
        ReflectionTestUtils.setField(processor, "properties", properties);
        ReflectionTestUtils.setField(processor, "l2Cache", mock(L2Cache.class));
        ReflectionTestUtils.setField(processor, "redisLock", new SimpleRedisLock(executor));
        ReflectionTestUtils.setField(processor, "smartCacheObjectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(processor, "warmupExecutor", new DirectExecutor());
        return processor;
    }

    private ApplicationContext newWarmupContext() {
        return newWarmupContext(new LeaseWarmupFixture());
    }

    private ApplicationContext newWarmupContext(Object fixture) {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getParent()).thenReturn(null);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"leaseWarmupFixture"});
        when(context.getBean("leaseWarmupFixture")).thenReturn(fixture);
        return context;
    }

    private SmartCacheManager newManager(LeaseRecordingExecutor executor) {
        SmartCacheManager manager = new SmartCacheManager();
        ReflectionTestUtils.setField(manager, "redisLock", new SimpleRedisLock(executor));
        ReflectionTestUtils.setField(manager, "properties", new SmartCacheProperties());
        return manager;
    }

    private SmartCacheManager newPreloadManager(LeaseRecordingExecutor executor, CachePreloadHandler handler) {
        SmartCacheManager manager = newManager(executor);
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getL2().getPreload().setEnabled(true);
        ReflectionTestUtils.setField(manager, "properties", properties);
        ReflectionTestUtils.setField(manager, "preloadHandlers", Collections.singletonList(handler));
        ReflectionTestUtils.setField(manager, "preloadExecutor", new DirectExecutor());
        return manager;
    }

    static class PolicyLeaseWarmupFixture {

        private final AtomicInteger laterOrderCount = new AtomicInteger();

        @SmartCacheWarmUp(cacheName = CACHE_NAME, order = 1)
        public Map<String, Object> first() {
            return Collections.<String, Object>singletonMap(CACHE_KEY, CACHE_VALUE);
        }

        @SmartCacheWarmUp(cacheName = CACHE_NAME, order = 2)
        public Map<String, Object> second() {
            laterOrderCount.incrementAndGet();
            return Collections.emptyMap();
        }

        int getLaterOrderCount() {
            return laterOrderCount.get();
        }
    }

    static class LeaseRecordingExecutor implements RedisLockExecutor {

        private final AtomicInteger lockAttemptCount = new AtomicInteger();
        private final AtomicInteger renewCount = new AtomicInteger();
        private final AtomicInteger unlockCount = new AtomicInteger();
        private volatile RuntimeException unlockFailure;
        private volatile long lastAcquireLeaseSeconds;
        private volatile long lastRenewLeaseSeconds;
        private volatile boolean lockAcquired = true;
        private volatile boolean[] lockResults;
        private volatile boolean renewResult = true;
        private volatile RuntimeException renewFailure;

        @Override
        public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
            int attempt = lockAttemptCount.incrementAndGet();
            lastAcquireLeaseSeconds = timeUnit.toSeconds(expireTime);
            return lockResults != null && attempt <= lockResults.length ? lockResults[attempt - 1] : lockAcquired;
        }

        @Override
        public boolean unlock(String lockKey, String lockValue) {
            unlockCount.incrementAndGet();
            if (unlockFailure != null) {
                throw unlockFailure;
            }
            return true;
        }

        @Override
        public boolean renew(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
            renewCount.incrementAndGet();
            lastRenewLeaseSeconds = timeUnit.toSeconds(leaseTime);
            if (renewFailure != null) {
                throw renewFailure;
            }
            return renewResult;
        }

        int getLockAttemptCount() {
            return lockAttemptCount.get();
        }

        int getRenewCount() {
            return renewCount.get();
        }

        int getUnlockCount() {
            return unlockCount.get();
        }

        long getLastAcquireLeaseSeconds() {
            return lastAcquireLeaseSeconds;
        }

        long getLastRenewLeaseSeconds() {
            return lastRenewLeaseSeconds;
        }

        void setLockAcquired(boolean lockAcquired) {
            this.lockAcquired = lockAcquired;
            this.lockResults = null;
        }

        void setLockResults(boolean... lockResults) {
            this.lockResults = lockResults;
        }

        void setRenewResult(boolean renewResult) {
            this.renewResult = renewResult;
        }

        void setRenewFailure(RuntimeException renewFailure) {
            this.renewFailure = renewFailure;
        }

        void setUnlockFailure(RuntimeException unlockFailure) {
            this.unlockFailure = unlockFailure;
        }
    }

    static class FixedPreloadHandler implements CachePreloadHandler {

        private final AtomicInteger reloadCount = new AtomicInteger();
        private final Object reloadValue;

        FixedPreloadHandler(Object reloadValue) {
            this.reloadValue = reloadValue;
        }

        @Override
        public boolean support(String cacheName) {
            return CACHE_NAME.equals(cacheName);
        }

        @Override
        public Object reload(String cacheName, String key) {
            reloadCount.incrementAndGet();
            return reloadValue;
        }

        @Override
        public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
            return Optional.of(true);
        }

        int getReloadCount() {
            return reloadCount.get();
        }

        Object getReloadValue() {
            return reloadValue;
        }
    }

    static class LeaseWarmupFixture {

        @SmartCacheWarmUp(cacheName = CACHE_NAME, order = 1)
        public Map<String, Object> load() {
            return Collections.<String, Object>singletonMap(CACHE_KEY, CACHE_VALUE);
        }
    }

    static class DirectExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    static class RejectingExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            throw new java.util.concurrent.RejectedExecutionException("测试预刷新执行器拒绝任务");
        }
    }
}
