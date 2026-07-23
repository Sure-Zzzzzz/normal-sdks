package io.github.surezzzzzz.sdk.limiter.redis.smart.executor;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SmartRedisLimiter 超时保护执行器
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterTimeoutExecutor {

    private final ThreadPoolExecutor executor;

    public SmartRedisLimiterTimeoutExecutor(SmartRedisLimiterProperties properties) {
        int threads = properties.getRedis().getTimeoutExecutorThreads();
        int queueCapacity = properties.getRedis().getTimeoutExecutorQueueCapacity();
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                SmartRedisLimiterStarterConstant.TIMEOUT_EXECUTOR_KEEP_ALIVE_MILLIS,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new TimeoutThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        log.info("SmartRedisLimiter 超时保护线程池初始化完成, threads={}, queueCapacity={}",
                threads, queueCapacity);
    }

    /**
     * 提交限流执行任务
     *
     * @param task 执行任务
     */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    /**
     * 关闭超时保护执行器并中断仍在执行的任务
     */
    @PreDestroy
    public void destroy() {
        executor.shutdownNow();
        log.info("SmartRedisLimiter 超时保护线程池已关闭");
    }

    private static class TimeoutThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger(SmartRedisLimiterStarterConstant.THREAD_INDEX_INITIAL_VALUE);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    SmartRedisLimiterStarterConstant.TIMEOUT_EXECUTOR_THREAD_NAME_PREFIX
                            + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
