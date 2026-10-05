package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterTimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SmartRedisLimiter 超时保护执行器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterTimeoutExecutorTest {

    private SmartRedisLimiterTimeoutExecutor executor;

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    public void tearDown() {
        if (executor != null) {
            executor.destroy();
            executor = null;
        }
    }

    @Test
    public void testBoundedQueueRejectsExcessTask() throws Exception {
        executor = createExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS), "首个任务应进入工作线程");
            executor.execute(() -> await(releaseWorker));

            log.info("线程池工作线程和有界队列均已占满，验证第三个任务被拒绝");
            assertThrows(RejectedExecutionException.class,
                    () -> executor.execute(() -> {
                    }),
                    "队列满时必须立即拒绝，不能阻塞业务线程或使用无界队列");
        } finally {
            releaseWorker.countDown();
        }
    }

    @Test
    public void testDestroyRejectsNewTask() {
        executor = createExecutor();
        executor.destroy();

        log.info("超时保护执行器关闭后验证新任务被拒绝");
        assertThrows(RejectedExecutionException.class,
                () -> executor.execute(() -> {
                }),
                "执行器销毁后不应再接受任务");
    }

    private SmartRedisLimiterTimeoutExecutor createExecutor() {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.getRedis().setTimeoutExecutorThreads(1);
        properties.getRedis().setTimeoutExecutorQueueCapacity(1);
        executor = new SmartRedisLimiterTimeoutExecutor(properties);
        return executor;
    }
}
