package io.github.surezzzzzz.sdk.retry.task.executor;

import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;

import java.util.concurrent.Callable;

/**
 * Task Retry 执行器
 *
 * @author surezzzzzz
 */
public interface TaskRetryExecutor {

    <T> T execute(Callable<T> task) throws Exception;

    <T> T execute(Callable<T> task, RetryRequest request) throws Exception;

    <T> T executeWithRetry(Callable<T> task, int retryTimes, long initialDelayMillis) throws Exception;

    <T> T executeWithRetry(Callable<T> task,
                           int retryTimes,
                           long initialDelayMillis,
                           double backoffMultiplier,
                           long maxDelayMillis) throws Exception;

    <T> T executeWithFixedDelay(Callable<T> task, int retryTimes, long delayMillis) throws Exception;

    <T> T executeWithFastRetry(Callable<T> task) throws Exception;

    <T> T executeWithSlowRetry(Callable<T> task) throws Exception;
}
