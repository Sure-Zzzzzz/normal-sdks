package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.manager.HttpSessionTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.test.SimpleAkskHttpSessionTokenManagerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpSessionTokenManager 并发测试
 * <p>
 * 测试 HttpSessionTokenManager 在并发场景下的表现
 * <p>
 * 注意：由于没有 HttpSession，每次获取都会从服务器重新获取 Token，
 * 但本地锁（synchronized）仍然会保证同一时刻只有一个线程在获取 Token。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskHttpSessionTokenManagerTestApplication.class)
class HttpSessionTokenManagerConcurrencyTest {

    @Autowired
    private HttpSessionTokenManager tokenManager;

    @Test
    @DisplayName("测试 10 个线程并发获取 Token")
    void testConcurrentGetToken10Threads() throws InterruptedException {
        log.info("======================================");
        log.info("测试 10 个线程并发获取 Token");
        log.info("======================================");

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<String> tokens = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    log.info("Thread-{} 开始获取 Token", threadId);
                    String token = tokenManager.getToken();
                    tokens.add(token);
                    successCount.incrementAndGet();
                    log.info("Thread-{} 获取 Token 成功", threadId);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("Thread-{} 获取 Token 失败", threadId, e);
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 启动所有线程
        boolean finished = endLatch.await(60, TimeUnit.SECONDS);

        log.info("======================================");
        log.info("并发测试结果：");
        log.info("成功: {}, 失败: {}", successCount.get(), failCount.get());
        log.info("获取的 Token 数量: {}", tokens.size());
        log.info("======================================");

        assertTrue(finished, "所有线程应在 60 秒内完成");
        assertEquals(threadCount, successCount.get(), "所有线程都应成功获取 Token");
        assertEquals(0, failCount.get(), "不应有失败的线程");
        assertEquals(threadCount, tokens.size(), "应获取到 10 个 Token");

        // 验证所有 Token 都不为 null
        for (String token : tokens) {
            assertNotNull(token, "Token 不应为 null");
            assertTrue(token.startsWith("eyJ"), "Token 应该是 JWT 格式");
        }
    }

    @Test
    @DisplayName("测试 50 个线程高并发获取 Token")
    void testConcurrentGetToken50Threads() throws InterruptedException {
        log.info("======================================");
        log.info("测试 50 个线程高并发获取 Token");
        log.info("======================================");

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String token = tokenManager.getToken();
                    assertNotNull(token);
                    successCount.incrementAndGet();
                    if (threadId % 10 == 0) {
                        log.info("Thread-{} 获取 Token 成功", threadId);
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("Thread-{} 获取 Token 失败", threadId, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("======================================");
        log.info("高并发测试结果：");
        log.info("成功: {}, 失败: {}", successCount.get(), failCount.get());
        log.info("======================================");

        assertTrue(finished, "所有线程应在 120 秒内完成");
        assertEquals(threadCount, successCount.get(), "所有线程都应成功");
        assertEquals(0, failCount.get(), "不应有失败");
    }

    @Test
    @DisplayName("测试并发获取和清除 Token")
    void testConcurrentGetAndClearToken() throws InterruptedException {
        log.info("======================================");
        log.info("测试并发获取和清除 Token");
        log.info("======================================");

        int getThreadCount = 20;
        int clearThreadCount = 5;
        int totalThreads = getThreadCount + clearThreadCount;

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(totalThreads);
        AtomicInteger getSuccessCount = new AtomicInteger(0);
        AtomicInteger clearCount = new AtomicInteger(0);

        // 启动获取 Token 的线程
        for (int i = 0; i < getThreadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String token = tokenManager.getToken();
                    assertNotNull(token);
                    getSuccessCount.incrementAndGet();
                    log.debug("Get Thread-{} 获取 Token 成功", threadId);
                } catch (Exception e) {
                    log.error("Get Thread-{} 失败", threadId, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动清除 Token 的线程
        for (int i = 0; i < clearThreadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Thread.sleep(50); // 稍微延迟，让获取操作先执行
                    tokenManager.clearToken();
                    clearCount.incrementAndGet();
                    log.debug("Clear Thread-{} 清除 Token 成功", threadId);
                } catch (Exception e) {
                    log.error("Clear Thread-{} 失败", threadId, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(90, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("======================================");
        log.info("并发获取和清除测试结果：");
        log.info("成功获取: {}, 清除次数: {}", getSuccessCount.get(), clearCount.get());
        log.info("======================================");

        assertTrue(finished, "所有线程应在 90 秒内完成");
        assertTrue(getSuccessCount.get() > 0, "应有成功获取 Token 的线程");
    }
}
