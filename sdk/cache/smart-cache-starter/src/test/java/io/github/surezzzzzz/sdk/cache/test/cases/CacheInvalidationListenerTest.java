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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("无法注入字段 " + fieldName + "：" + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("SmartCacheProperties.validatePubSub() 在 strong + disabled 时抛出 CacheConfigurationException")
    void shouldFailValidationWhenStrongWithDisabledPubsub() {
        SmartCacheProperties p = new SmartCacheProperties();
        p.setMe("test");
        p.getConsistency().setMode(SmartCacheConstant.CONSISTENCY_MODE_STRONG);
        p.getPubsub().setMode(SmartCacheConstant.PUBSUB_MODE_DISABLED);

        CacheConfigurationException ex = assertThrows(CacheConfigurationException.class,
                p::validate,
                "strong + disabled 必须抛出配置异常");

        assertEquals("SMART_CACHE_001", ex.getErrorCode(),
                "错误码应为 SMART_CACHE_001");
        assertTrue(ex.getMessage().contains("强一致性模式不能关闭 Pub/Sub"),
                "错误消息应包含配置语义，实际：" + ex.getMessage());
        log.info("验证通过：strong + disabled 在配置校验阶段正确抛出 CacheConfigurationException，错误码：{}，消息：{}",
                ex.getErrorCode(), ex.getMessage());
    }

    @Test
    @DisplayName("消息线程池使用 AbortPolicy，队列饱和时 onMessage 捕获 RejectedExecutionException 不传播且丢弃溢出消息")
    void shouldNotPropagateRejectionFromListenerThread() throws Exception {
        // 核心数=1、最大=1、队列=1，配合 AbortPolicy：1 个线程跑阻塞任务，1 个任务入队，再提交必然被拒绝
        ThreadPoolExecutor saturatedExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> {
                    Thread t = new Thread(r, "saturation-test-");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            CountDownLatch blocker = new CountDownLatch(1);
            saturatedExecutor.submit(() -> {
                try {
                    blocker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            CacheInvalidationListener l = new CacheInvalidationListener();
            SmartCacheProperties p = new SmartCacheProperties();
            p.setMe("test");
            p.getConsistency().setMode(SmartCacheConstant.CONSISTENCY_MODE_STRONG);
            p.getPubsub().setMode(SmartCacheConstant.PUBSUB_MODE_ROUTED);

            // 用 mock L1Cache 观察实际被处理的消息数量，区分“入队后被处理”与“被拒绝丢弃”
            CountDownLatch oneProcessed = new CountDownLatch(1);
            AtomicInteger processedCount = new AtomicInteger(0);
            L1Cache mockL1 = mock(L1Cache.class);
            doAnswer(inv -> {
                processedCount.incrementAndGet();
                oneProcessed.countDown();
                return null;
            }).when(mockL1).evict(anyString(), anyString());

            injectField(l, "properties", p);
            injectField(l, "smartCacheObjectMapper", objectMapper);
            injectField(l, "messageExecutor", saturatedExecutor);
            injectField(l, "l1Cache", mockL1);

            byte[] pattern = "test".getBytes(StandardCharsets.UTF_8);
            CacheInvalidationMessage msg = new CacheInvalidationMessage("c", "k",
                    SmartCacheConstant.OPERATION_EVICT, "sender-1");
            String payload = objectMapper.writeValueAsString(msg);
            Message redisMessage = new Message() {
                @Override
                public byte[] getBody() {
                    return payload.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public byte[] getChannel() {
                    return "ch".getBytes(StandardCharsets.UTF_8);
                }
            };

            // 5 条消息：1 条入队、4 条被 AbortPolicy 拒绝；onMessage 必须捕获拒绝异常，不能传播到 Redis listener 线程
            int propagated = 0;
            for (int i = 0; i < 5; i++) {
                try {
                    l.onMessage(redisMessage, pattern);
                } catch (RejectedExecutionException e) {
                    propagated++;
                }
            }
            assertEquals(0, propagated,
                    "onMessage 必须捕获 RejectedExecutionException，不能传播到 Redis listener 线程");

            // 释放阻塞任务，让唯一入队的消息得以处理
            blocker.countDown();
            assertTrue(oneProcessed.await(5, TimeUnit.SECONDS),
                    "入队的消息应在线程释放后被处理");

            // 5 条消息只有 1 条被处理，其余 4 条被拒绝并丢弃（非 CallerRuns，未在调用线程执行）
            assertEquals(1, processedCount.get(),
                    "5 条消息中仅 1 条入队并处理，4 条应被 AbortPolicy 拒绝并丢弃，而不是 CallerRuns");

            log.info("验证通过：5 条消息 1 入队 4 拒绝，onMessage 未传播异常，溢出消息被丢弃而非 CallerRuns");
        } finally {
            saturatedExecutor.shutdownNow();
        }
    }

}
