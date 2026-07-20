package io.github.surezzzzzz.sdk.cache.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheConfigurationException;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationMessage;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.Message;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * CacheInvalidationListener 配置验证与消息处理测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class CacheInvalidationListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("无法注入字段 " + fieldName + "：" + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("SmartCacheProperties.validatePubSub() 在 strong + disabled 时抛出 CacheConfigurationException")
    void shouldFailValidationWhenStrongWithDisabledPubsub() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.setMe("test");
        properties.getConsistency().setMode(SmartCacheConstant.CONSISTENCY_MODE_STRONG);
        properties.getPubsub().setMode(SmartCacheConstant.PUBSUB_MODE_DISABLED);

        CacheConfigurationException exception = org.junit.jupiter.api.Assertions.assertThrows(CacheConfigurationException.class,
                properties::validate,
                "strong + disabled 必须抛出配置异常");

        assertEquals("SMART_CACHE_001", exception.getErrorCode(), "错误码应为 SMART_CACHE_001");
        assertTrue(exception.getMessage().contains("强一致性模式不能关闭 Pub/Sub"),
                "错误消息应包含配置语义，实际：" + exception.getMessage());
    }

    @Test
    @DisplayName("消息队列饱和时由监听线程背压处理所有已接收失效消息")
    void shouldProcessAllDeliveredMessagesWithCallerRunsBackpressure() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1),
                runnable -> {
                    Thread thread = new Thread(runnable, "saturation-test-");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            CountDownLatch blocker = new CountDownLatch(1);
            CountDownLatch workerStarted = new CountDownLatch(1);
            executor.submit(() -> {
                workerStarted.countDown();
                await(blocker);
            });
            boolean workerOccupied = workerStarted.await(5, TimeUnit.SECONDS);
            log.info("失效监听器背压测试工作线程是否已占用：{}", workerOccupied);
            assertTrue(workerOccupied, "阻塞任务应先占用唯一工作线程");

            CacheInvalidationListener listener = new CacheInvalidationListener();
            L1Cache l1Cache = mock(L1Cache.class);
            AtomicInteger processed = new AtomicInteger();
            AtomicReference<String> overflowThread = new AtomicReference<>();
            String callerThread = Thread.currentThread().getName();
            doAnswer(invocation -> {
                int count = processed.incrementAndGet();
                if (count == 1) {
                    overflowThread.set(Thread.currentThread().getName());
                }
                return null;
            }).when(l1Cache).evict(anyString(), anyString());
            injectField(listener, "properties", strongProperties());
            injectField(listener, "smartCacheObjectMapper", objectMapper);
            injectField(listener, "messageExecutor", executor);
            injectField(listener, "l1Cache", l1Cache);

            Message message = evictionMessage();
            listener.onMessage(message, new byte[0]);
            listener.onMessage(message, new byte[0]);

            log.info("队列饱和后同步处理数：{}，同步处理线程：{}", processed.get(), overflowThread.get());
            assertEquals(1, processed.get(), "队列饱和后的消息应由当前监听线程同步处理");
            assertEquals(callerThread, overflowThread.get(), "溢出消息应在 onMessage 调用线程执行");

            blocker.countDown();
            assertTrue(awaitProcessed(processed, 2), "队列中的消息释放后也应完成处理");
            assertEquals(2, processed.get(), "所有已接收消息均应触发 L1 失效");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("监听器关闭后跳过新消息且不访问 L1")
    void shouldSkipMessageWhenExecutorIsShutdown() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1));
        executor.shutdown();
        CacheInvalidationListener listener = new CacheInvalidationListener();
        L1Cache l1Cache = mock(L1Cache.class);
        injectField(listener, "properties", strongProperties());
        injectField(listener, "smartCacheObjectMapper", objectMapper);
        injectField(listener, "messageExecutor", executor);
        injectField(listener, "l1Cache", l1Cache);

        assertDoesNotThrow(() -> listener.onMessage(evictionMessage(), new byte[0]),
                "关闭阶段收到消息不应向监听线程传播异常");
        org.mockito.Mockito.verifyNoInteractions(l1Cache);
    }

    private SmartCacheProperties strongProperties() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.setMe("test");
        properties.getConsistency().setMode(SmartCacheConstant.CONSISTENCY_MODE_STRONG);
        properties.getPubsub().setMode(SmartCacheConstant.PUBSUB_MODE_ROUTED);
        return properties;
    }

    private Message evictionMessage() {
        try {
            String payload = objectMapper.writeValueAsString(new CacheInvalidationMessage("cache", "key",
                    SmartCacheConstant.OPERATION_EVICT, "other-instance"));
            return new Message() {
                @Override
                public byte[] getBody() {
                    return payload.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public byte[] getChannel() {
                    return "channel".getBytes(StandardCharsets.UTF_8);
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("无法构造缓存失效消息：" + e.getMessage(), e);
        }
    }

    private void await(CountDownLatch blocker) {
        try {
            blocker.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean awaitProcessed(AtomicInteger processed, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (processed.get() >= expected) {
                return true;
            }
            Thread.sleep(10L);
        }
        return false;
    }
}
