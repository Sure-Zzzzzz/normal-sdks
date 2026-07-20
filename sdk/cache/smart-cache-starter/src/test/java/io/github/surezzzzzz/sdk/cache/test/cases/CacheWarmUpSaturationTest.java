package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheWarmUp;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheWarmUpException;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.cache.warmup.SmartCacheWarmUpProcessor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动预热失败策略测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class CacheWarmUpSaturationTest {

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("无法注入字段 " + fieldName + "：" + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("默认 continue 在预热线程池拒绝后继续后续 order")
    void shouldContinueWhenExecutorRejectsByDefault() {
        AtomicInteger completed = new AtomicInteger();
        SmartCacheWarmUpProcessor processor = newProcessor(new RejectFirstExecutor());
        ApplicationContext context = newContext(new WarmupFixture(completed));

        Throwable failure = executeWarmup(processor, context);

        log.info("默认 continue 线程池拒绝结果：{}，后续 order 执行次数：{}",
                failure == null ? "未抛出异常" : failure.getClass().getSimpleName(), completed.get());
        assertNull(failure, "默认 continue 在预热线程池拒绝后不应阻断启动");
        assertEquals(1, completed.get(), "默认 continue 应继续执行后续 order 的预热任务");
    }

    @Test
    @DisplayName("默认 continue 在预热返回类型错误后继续后续 order")
    void shouldContinueWhenWarmUpReturnTypeIsInvalid() {
        AtomicInteger completed = new AtomicInteger();
        SmartCacheWarmUpProcessor processor = newProcessor(new DirectExecutor());
        ApplicationContext context = newContext(new InvalidReturnFixture(completed));

        Throwable failure = executeWarmup(processor, context);

        log.info("默认 continue 返回类型错误结果：{}，后续 order 执行次数：{}",
                failure == null ? "未抛出异常" : failure.getClass().getSimpleName(), completed.get());
        assertNull(failure, "默认 continue 在预热返回类型错误后不应阻断启动");
        assertEquals(1, completed.get(), "默认 continue 应继续执行后续 order");
    }

    @Test
    @DisplayName("fail-fast 在预热返回类型错误后抛出 CacheWarmUpException")
    void shouldFailFastWhenWarmUpReturnTypeIsInvalid() {
        AtomicInteger completed = new AtomicInteger();
        SmartCacheWarmUpProcessor processor = newProcessor(new DirectExecutor());
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        injectField(processor, "properties", properties);
        ApplicationContext context = newContext(new InvalidReturnFixture(completed));

        Throwable failure = executeWarmup(processor, context);
        CacheWarmUpException exception = failure instanceof CacheWarmUpException ? (CacheWarmUpException) failure : null;

        log.info("严格返回类型错误结果：{}，错误码：{}，后续 order 执行次数：{}",
                failure == null ? "未抛出异常" : failure.getClass().getSimpleName(),
                exception == null ? null : exception.getErrorCode(), completed.get());
        assertNotNull(exception, "fail-fast 在预热返回类型错误后必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "严格模式应使用统一预热错误码");
        assertEquals(0, completed.get(), "严格模式不应进入后续 order");
    }

    @Test
    @DisplayName("fail-fast 在预热线程池拒绝后抛出 CacheWarmUpException")
    void shouldFailFastWhenExecutorRejects() {
        AtomicInteger completed = new AtomicInteger();
        SmartCacheWarmUpProcessor processor = newProcessor(new RejectFirstExecutor());
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        injectField(processor, "properties", properties);
        ApplicationContext context = newContext(new WarmupFixture(completed));

        Throwable failure = executeWarmup(processor, context);
        CacheWarmUpException exception = failure instanceof CacheWarmUpException ? (CacheWarmUpException) failure : null;

        log.info("fail-fast 线程池拒绝结果：{}，错误码：{}，根因：{}，后续 order 执行次数：{}",
                failure == null ? "未抛出异常" : failure.getClass().getSimpleName(),
                exception == null ? null : exception.getErrorCode(),
                exception == null || exception.getCause() == null ? null : exception.getCause().getClass().getSimpleName(),
                completed.get());
        assertNotNull(exception, "fail-fast 在预热线程池拒绝后必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(),
                "严格模式应使用统一预热错误码");
        assertEquals(0, completed.get(), "严格模式不应进入后续 order");
    }

    @Test
    @DisplayName("默认 continue 在调用契约失败后继续后续 order")
    void shouldContinueWhenWarmUpContractFails() {
        assertContinueAfterWarmUpFailure(new BusinessExceptionFixture(), "BusinessExceptionFixture.first");
        assertContinueAfterWarmUpFailure(new ErrorFixture(), "ErrorFixture.first");
        assertContinueAfterWarmUpFailure(new InvalidKeyFixture(), "InvalidKeyFixture.first");
    }

    @Test
    @DisplayName("fail-fast 在调用契约失败后携带任务上下文并阻断后续 order")
    void shouldFailFastWhenWarmUpContractFails() {
        assertFailFastAfterWarmUpFailure(new BusinessExceptionFixture(), "BusinessExceptionFixture.first",
                IllegalStateException.class);
        assertFailFastAfterWarmUpFailure(new ErrorFixture(), "ErrorFixture.first", AssertionError.class);
        assertFailFastAfterWarmUpFailure(new InvalidKeyFixture(), "InvalidKeyFixture.first", null);
    }

    private void assertContinueAfterWarmUpFailure(FailureFixture fixture, String taskName) {
        SmartCacheWarmUpProcessor processor = newProcessor(new DirectExecutor());
        ApplicationContext context = newContext(fixture);

        Throwable failure = executeWarmup(processor, context);

        log.info("continue 调用契约失败任务：{}，执行结果：{}，后续 order 执行次数：{}", taskName,
                failure == null ? "未抛出异常" : failure.getClass().getSimpleName(), fixture.completed.get());
        assertNull(failure, "默认 continue 在预热失败后不应阻断启动");
        assertEquals(1, fixture.completed.get(), "默认 continue 应继续执行后续 order");
    }

    private void assertFailFastAfterWarmUpFailure(FailureFixture fixture, String taskName,
                                                  Class<? extends Throwable> causeType) {
        SmartCacheWarmUpProcessor processor = newProcessor(new DirectExecutor());
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        injectField(processor, "properties", properties);
        ApplicationContext context = newContext(fixture);

        Throwable failure = executeWarmup(processor, context);
        CacheWarmUpException exception = failure instanceof CacheWarmUpException ? (CacheWarmUpException) failure : null;

        log.info("fail-fast 调用契约失败任务：{}，执行结果：{}，错误码：{}，错误消息：{}，后续 order 执行次数：{}", taskName,
                failure == null ? "未抛出异常" : failure.getClass().getSimpleName(),
                exception == null ? null : exception.getErrorCode(), exception == null ? null : exception.getMessage(),
                fixture.completed.get());
        assertNotNull(exception, "fail-fast 在预热失败后必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "严格模式应使用统一预热错误码");
        assertTrue(exception.getMessage().contains(taskName), "错误消息必须包含失败预热任务名称");
        if (causeType != null) {
            assertNotNull(exception.getCause(), "调用失败必须保留原始根因");
            assertEquals(causeType, exception.getCause().getClass(), "调用失败根因类型必须保持不变");
        }
        assertEquals(0, fixture.completed.get(), "严格模式不应进入后续 order");
    }

    @Test
    @DisplayName("预热写缓存失败按策略决定是否进入后续 order")
    void shouldApplyPolicyWhenCacheWriteFails() {
        AtomicInteger continueCompleted = new AtomicInteger();
        SmartCacheWarmUpProcessor continueProcessor = newProcessor(new DirectExecutor());
        SmartCacheManager continueManager = mock(SmartCacheManager.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("测试预热缓存写入异常"))
                .when(continueManager).putAll(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
        injectField(continueProcessor, "cacheManager", continueManager);
        Throwable continueFailure = executeWarmup(continueProcessor,
                newContext(new CacheWriteFailureFixture(continueCompleted)));
        log.info("continue 缓存写入失败执行结果：{}，后续 order 执行次数：{}",
                continueFailure == null ? "未抛出异常" : continueFailure.getClass().getSimpleName(), continueCompleted.get());
        assertNull(continueFailure, "默认 continue 在缓存写入失败后不应阻断启动");
        assertEquals(1, continueCompleted.get(), "默认 continue 应继续执行后续 order");

        AtomicInteger failFastCompleted = new AtomicInteger();
        SmartCacheWarmUpProcessor failFastProcessor = newProcessor(new DirectExecutor());
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
        injectField(failFastProcessor, "properties", properties);
        SmartCacheManager failFastManager = mock(SmartCacheManager.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("测试预热缓存写入异常"))
                .when(failFastManager).putAll(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap());
        injectField(failFastProcessor, "cacheManager", failFastManager);
        Throwable failFastFailure = executeWarmup(failFastProcessor,
                newContext(new CacheWriteFailureFixture(failFastCompleted)));
        CacheWarmUpException exception = failFastFailure instanceof CacheWarmUpException
                ? (CacheWarmUpException) failFastFailure : null;
        log.info("fail-fast 缓存写入失败执行结果：{}，错误码：{}，错误消息：{}，后续 order 执行次数：{}",
                failFastFailure == null ? "未抛出异常" : failFastFailure.getClass().getSimpleName(),
                exception == null ? null : exception.getErrorCode(), exception == null ? null : exception.getMessage(),
                failFastCompleted.get());
        assertNotNull(exception, "fail-fast 在缓存写入失败后必须阻断启动");
        assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "严格模式应使用统一预热错误码");
        assertTrue(exception.getMessage().contains("CacheWriteFailureFixture.first"), "错误消息必须包含失败预热任务名称");
        assertEquals(0, failFastCompleted.get(), "严格模式不应进入后续 order");
    }

    @Test
    @DisplayName("fail-fast 等待同 order 已启动任务完成后阻断后续 order")
    void shouldWaitForStartedTasksInSameOrderBeforeFailFast() throws Exception {
        CountDownLatch peerStarted = new CountDownLatch(1);
        CountDownLatch releasePeer = new CountDownLatch(1);
        SameOrderFixture fixture = new SameOrderFixture(peerStarted, releasePeer);
        ExecutorService warmupExecutor = Executors.newFixedThreadPool(2);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        try {
            SmartCacheWarmUpProcessor processor = newProcessor(warmupExecutor);
            SmartCacheProperties properties = new SmartCacheProperties();
            properties.getWarmUp().setFailurePolicy(SmartCacheConstant.WARMUP_FAILURE_POLICY_FAIL_FAST);
            injectField(processor, "properties", properties);
            Future<CacheWarmUpException> failure = callerExecutor.submit(() -> {
                try {
                    processor.onApplicationEvent(new ContextRefreshedEvent(newContext(fixture)));
                    return null;
                } catch (CacheWarmUpException e) {
                    return e;
                }
            });

            boolean peerHasStarted = peerStarted.await(5, TimeUnit.SECONDS);
            boolean returnedBeforePeerRelease = failure.isDone();
            log.info("同 order 回调已启动：{}，释放前 fail-fast 是否返回：{}，后续 order 执行次数：{}",
                    peerHasStarted, returnedBeforePeerRelease, fixture.laterOrderCount.get());
            assertTrue(peerHasStarted, "同 order 的正常任务必须已启动");
            assertFalse(returnedBeforePeerRelease, "fail-fast 必须等待同 order 已启动任务结束");
            assertEquals(0, fixture.laterOrderCount.get(), "等待同 order 任务期间不得进入后续 order");

            releasePeer.countDown();
            CacheWarmUpException exception = failure.get(5, TimeUnit.SECONDS);
            log.info("同 order 回调完成次数：{}，严格失败错误码：{}，后续 order 执行次数：{}",
                    fixture.peerCompleted.get(), exception == null ? null : exception.getErrorCode(),
                    fixture.laterOrderCount.get());
            assertNotNull(exception, "同 order 存在失败时严格模式必须阻断启动");
            assertEquals(ErrorCode.SMART_CACHE_WARMUP_FAILED, exception.getErrorCode(), "严格模式应使用统一预热错误码");
            assertEquals(1, fixture.peerCompleted.get(), "已启动的同 order 回调必须完成");
            assertEquals(0, fixture.laterOrderCount.get(), "严格模式不得进入后续 order");
        } finally {
            releasePeer.countDown();
            callerExecutor.shutdownNow();
            warmupExecutor.shutdownNow();
        }
    }

    private Throwable executeWarmup(SmartCacheWarmUpProcessor processor, ApplicationContext context) {
        try {
            processor.onApplicationEvent(new ContextRefreshedEvent(context));
            return null;
        } catch (Throwable e) {
            return e;
        }
    }

    private SmartCacheWarmUpProcessor newProcessor(Executor executor) {
        SmartCacheWarmUpProcessor processor = new SmartCacheWarmUpProcessor();
        injectField(processor, "warmupExecutor", executor);
        injectField(processor, "properties", new SmartCacheProperties());
        return processor;
    }

    private ApplicationContext newContext(Object bean) {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getParent()).thenReturn(null);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"warmupFixture"});
        when(context.getBean("warmupFixture")).thenReturn(bean);
        return context;
    }

    static class WarmupFixture {

        private final AtomicInteger completed;

        WarmupFixture(AtomicInteger completed) {
            this.completed = completed;
        }

        @SmartCacheWarmUp(cacheName = "saturation-warmup", order = 1)
        public Map<String, Object> first() {
            return Collections.emptyMap();
        }

        @SmartCacheWarmUp(cacheName = "saturation-warmup", order = 2)
        public Map<String, Object> second() {
            completed.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static class InvalidReturnFixture {

        private final AtomicInteger completed;

        InvalidReturnFixture(AtomicInteger completed) {
            this.completed = completed;
        }

        @SmartCacheWarmUp(cacheName = "invalid-return-warmup", order = 1)
        public String first() {
            return "invalid";
        }

        @SmartCacheWarmUp(cacheName = "invalid-return-warmup", order = 2)
        public Map<String, Object> second() {
            completed.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static class CacheWriteFailureFixture {

        private final AtomicInteger completed;

        CacheWriteFailureFixture(AtomicInteger completed) {
            this.completed = completed;
        }

        @SmartCacheWarmUp(cacheName = "cache-write-failure-warmup", order = 1)
        public Map<String, Object> first() {
            return Collections.<String, Object>singletonMap("key", "value");
        }

        @SmartCacheWarmUp(cacheName = "cache-write-failure-warmup", order = 2)
        public Map<String, Object> second() {
            completed.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static abstract class FailureFixture {

        protected final AtomicInteger completed = new AtomicInteger();
    }

    static class BusinessExceptionFixture extends FailureFixture {

        @SmartCacheWarmUp(cacheName = "failure-contract-warmup", order = 1)
        public Map<String, Object> first() {
            throw new IllegalStateException("测试预热业务异常");
        }

        @SmartCacheWarmUp(cacheName = "failure-contract-warmup", order = 2)
        public Map<String, Object> second() {
            completed.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static class ErrorFixture extends FailureFixture {

        @SmartCacheWarmUp(cacheName = "failure-contract-warmup", order = 1)
        public Map<String, Object> first() {
            throw new AssertionError("测试预热 Error");
        }

        @SmartCacheWarmUp(cacheName = "failure-contract-warmup", order = 2)
        public Map<String, Object> second() {
            completed.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static class InvalidKeyFixture extends FailureFixture {

        @SmartCacheWarmUp(cacheName = "failure-contract-warmup", order = 1)
        public Map<Object, Object> first() {
            return Collections.<Object, Object>singletonMap(1, "invalid-key");
        }

        @SmartCacheWarmUp(cacheName = "failure-contract-warmup", order = 2)
        public Map<String, Object> second() {
            completed.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static class SameOrderFixture {

        private final CountDownLatch peerStarted;
        private final CountDownLatch releasePeer;
        private final AtomicInteger peerCompleted = new AtomicInteger();
        private final AtomicInteger laterOrderCount = new AtomicInteger();

        SameOrderFixture(CountDownLatch peerStarted, CountDownLatch releasePeer) {
            this.peerStarted = peerStarted;
            this.releasePeer = releasePeer;
        }

        @SmartCacheWarmUp(cacheName = "same-order-warmup", order = 1)
        public Map<String, Object> failingTask() {
            throw new IllegalStateException("测试同 order 预热异常");
        }

        @SmartCacheWarmUp(cacheName = "same-order-warmup", order = 1)
        public Map<String, Object> peerTask() {
            peerStarted.countDown();
            try {
                releasePeer.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("测试同 order 回调被中断", e);
            }
            peerCompleted.incrementAndGet();
            return Collections.emptyMap();
        }

        @SmartCacheWarmUp(cacheName = "same-order-warmup", order = 2)
        public Map<String, Object> laterTask() {
            laterOrderCount.incrementAndGet();
            return Collections.emptyMap();
        }
    }

    static class DirectExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    static class RejectFirstExecutor implements Executor {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            if (calls.incrementAndGet() == 1) {
                throw new RejectedExecutionException("测试预热线程池饱和");
            }
            command.run();
        }
    }
}
