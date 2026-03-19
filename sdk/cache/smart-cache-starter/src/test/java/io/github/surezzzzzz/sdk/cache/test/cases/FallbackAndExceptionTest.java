package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheable;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fallback and Exception Test
 * <p>
 * 测试本地锁兜底机制和异常处理
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class FallbackAndExceptionTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private TestService testService;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("testCache");
        TestService.loadCount.set(0);
        log.info("测试环境初始化完成");
    }

    @Test
    public void testLocalLockFallbackMechanism() throws InterruptedException {
        log.info("========== 测试：本地锁兜底机制 ==========");

        // Given
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When - 多线程并发访问同一个 key
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String result = cacheManager.get("testCache", "key1", () -> {
                        log.info("线程 {} 正在加载数据", Thread.currentThread().getName());
                        Thread.sleep(100); // 模拟耗时操作
                        TestService.loadCount.incrementAndGet();
                        return "value1";
                    });
                    log.info("线程 {} 获取结果: {}", Thread.currentThread().getName(), result);
                } catch (Exception e) {
                    log.error("线程执行失败", e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 同时启动所有线程
        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(finished, "所有线程应该在超时前完成");
        int loadCount = TestService.loadCount.get();
        log.info("实际加载次数: {}", loadCount);
        // 本地锁应该保证只有一个线程加载数据
        assertEquals(1, loadCount, "本地锁应该防止同一实例内的缓存击穿，加载次数应该正好是1");
        log.info("验证通过：本地锁兜底机制生效");
        log.info("测试通过");
    }

    @Test
    public void testExceptionWrappingWrapsRuntimeException() {
        log.info("========== 测试：异常包装统一包装 RuntimeException ==========");

        // When & Then - RuntimeException 应该被包装成 SmartCacheException
        SmartCacheException exception = assertThrows(SmartCacheException.class, () -> {
            testService.methodThrowsRuntimeException();
        });

        log.info("捕获到异常: {} - {}", exception.getClass().getName(), exception.getMessage());
        assertTrue(exception.getMessage().contains("Cache operation failed"),
                "异常消息应该包含 'Cache operation failed'");
        assertTrue(exception.getMessage().contains("RuntimeException"),
                "异常消息应该包含原始异常类型 'RuntimeException'");
        assertNotNull(exception.getCause(), "应该保留原始异常");
        assertTrue(exception.getCause() instanceof RuntimeException, "原始异常应该是 RuntimeException");
        assertEquals("Runtime exception", exception.getCause().getMessage());
        log.info("验证通过：RuntimeException 被统一包装并保留原始异常");
        log.info("测试通过");
    }

    @Test
    public void testExceptionWrappingWrapsError() {
        log.info("========== 测试：异常包装统一包装 Error ==========");

        // When & Then - Error 应该被包装成 SmartCacheException
        SmartCacheException exception = assertThrows(SmartCacheException.class, () -> {
            testService.methodThrowsError();
        });

        log.info("捕获到异常: {} - {}", exception.getClass().getName(), exception.getMessage());
        assertTrue(exception.getMessage().contains("Cache operation failed"),
                "异常消息应该包含 'Cache operation failed'");
        assertTrue(exception.getMessage().contains("Error"),
                "异常消息应该包含原始异常类型 'Error'");
        assertNotNull(exception.getCause(), "应该保留原始异常");
        assertTrue(exception.getCause() instanceof Error, "原始异常应该是 Error");
        assertEquals("Error occurred", exception.getCause().getMessage());
        log.info("验证通过：Error 被统一包装并保留原始异常");
        log.info("测试通过");
    }

    @Test
    public void testExceptionWrappingWrapsCheckedException() {
        log.info("========== 测试：异常包装统一包装受检异常 ==========");

        // When & Then - 受检异常应该被包装成 SmartCacheException
        SmartCacheException exception = assertThrows(SmartCacheException.class, () -> {
            testService.methodThrowsCheckedException();
        });

        log.info("捕获到异常: {} - {}", exception.getClass().getName(), exception.getMessage());
        assertTrue(exception.getMessage().contains("Cache operation failed"),
                "异常消息应该包含 'Cache operation failed'");
        assertTrue(exception.getMessage().contains("IOException"),
                "异常消息应该包含原始异常类型 'IOException'");
        assertNotNull(exception.getCause(), "应该保留原始异常");
        assertTrue(exception.getCause() instanceof IOException, "原始异常应该是 IOException");
        assertEquals("Checked exception", exception.getCause().getMessage());
        log.info("验证通过：受检异常被统一包装并保留原始异常");
        log.info("测试通过");
    }

    /**
     * 测试服务
     */
    @Service
    public static class TestService {

        public static AtomicInteger loadCount = new AtomicInteger(0);

        @SmartCacheable(cacheName = "testCache", key = "'runtime'")
        public String methodThrowsRuntimeException() {
            throw new RuntimeException("Runtime exception");
        }

        @SmartCacheable(cacheName = "testCache", key = "'error'")
        public String methodThrowsError() {
            throw new Error("Error occurred");
        }

        @SmartCacheable(cacheName = "testCache", key = "'checked'")
        public String methodThrowsCheckedException() throws IOException {
            throw new IOException("Checked exception");
        }
    }
}
