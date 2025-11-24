package io.github.surezzzzzz.sdk.retry.task.executor;

import io.github.surezzzzzz.sdk.retry.task.configuration.RetryComponent;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@RetryComponent
@Slf4j
public class TaskRetryExecutor {
    /**
     * 完整参数的重试执行方法
     */
    public <T> T executeWithRetry(Callable<T> task,
                                  Integer retryTimes,
                                  Integer retryInterval,
                                  Double backoffMultiplier,
                                  Long maxDelaySeconds) throws Exception {
        if (retryTimes < 0) {
            throw new IllegalArgumentException("retryTimes不能为负数");
        }

        Exception lastException = null;
        int totalAttempts = retryTimes + 1; // 1次初始执行 + retryTimes次重试

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return task.call();
            } catch (Exception e) {
                lastException = e;

                if (attempt < retryTimes) {
                    long delay = (long) (retryInterval * Math.pow(backoffMultiplier, attempt - 1));
                    long actualDelay = Math.min(delay, maxDelaySeconds);

                    log.warn("Attempt {}/{} failed, retrying in {} seconds...",
                            attempt, retryTimes, actualDelay);
                    log.debug("Exception details: ", e);

                    TimeUnit.SECONDS.sleep(actualDelay);
                } else {
                    log.warn("Attempt {}/{} failed, no more retries", attempt, retryTimes);
                    log.debug("Final exception details: ", e);
                }
            }
        }
        throw lastException;
    }

    /**
     * 使用默认退避策略的重试方法
     * 默认：1.5倍指数退避，最大延迟30秒
     */
    public <T> T executeWithRetry(Callable<T> task, Integer retryTimes, Integer retryInterval) throws Exception {
        return executeWithRetry(task, retryTimes, retryInterval, 1.5, 30L);
    }

    /**
     * 固定间隔重试（不使用指数退避）
     */
    public <T> T executeWithFixedDelay(Callable<T> task, Integer retryTimes, Integer retryInterval) throws Exception {
        return executeWithRetry(task, retryTimes, retryInterval, 1.0, (long) retryInterval);
    }

    /**
     * 使用默认配置：5次重试，5秒间隔，1.5倍退避，最大30秒
     */
    public <T> T executeWithRetry(Callable<T> task) throws Exception {
        return executeWithRetry(task, 5, 5, 1.5, 30L);
    }

    /**
     * 快速重试：适用于轻量级操作
     * 5次重试，2秒间隔，1.2倍退避，最大10秒
     */
    public <T> T executeWithFastRetry(Callable<T> task) throws Exception {
        return executeWithRetry(task, 5, 2, 1.2, 10L);
    }

    /**
     * 慢重试：适用于重量级操作
     * 5次重试，10秒间隔，2.0倍退避，最大60秒
     */
    public <T> T executeWithSlowRetry(Callable<T> task) throws Exception {
        return executeWithRetry(task, 5, 10, 2.0, 60L);
    }
}
