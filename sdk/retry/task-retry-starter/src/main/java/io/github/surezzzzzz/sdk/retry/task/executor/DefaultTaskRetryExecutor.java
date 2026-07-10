package io.github.surezzzzzz.sdk.retry.task.executor;

import io.github.surezzzzzz.sdk.retry.task.annotation.TaskRetryComponent;
import io.github.surezzzzzz.sdk.retry.task.configuration.TaskRetryProperties;
import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.listener.RetryListener;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;
import io.github.surezzzzzz.sdk.retry.task.support.RetryBackoffHelper;
import io.github.surezzzzzz.sdk.retry.task.support.RetryValidationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.concurrent.Callable;

/**
 * 默认 Task Retry 执行器
 *
 * @author surezzzzzz
 */
@Slf4j
@TaskRetryComponent
@RequiredArgsConstructor
@ConditionalOnMissingBean(TaskRetryExecutor.class)
public class DefaultTaskRetryExecutor implements TaskRetryExecutor {

    private final TaskRetryProperties properties;
    private final RetrySleeper sleeper;
    private final RetryPredicate retryPredicate;
    private final RetryListener listener;

    @Override
    public <T> T execute(Callable<T> task) throws Exception {
        return execute(task, toRequest(properties.getDefaultPolicy(), RetryStrategyType.EXPONENTIAL));
    }

    @Override
    public <T> T execute(Callable<T> task, RetryRequest request) throws Exception {
        RetryValidationHelper.validate(task, request);
        int totalAttempts = request.getRetryTimes() + 1;
        Exception lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                onBeforeAttempt(attempt, totalAttempts);
                T result = task.call();
                onSuccess(attempt, totalAttempts);
                return result;
            } catch (Exception e) {
                lastException = e;
                onFailure(attempt, totalAttempts, e);
                if (attempt >= totalAttempts || !retryPredicate.shouldRetry(e, attempt, request)) {
                    throw e;
                }
                long delayMillis = RetryBackoffHelper.calculateDelayMillis(request, attempt);
                log.warn("第 {}/{} 次执行失败，{} 毫秒后重试", attempt, totalAttempts, delayMillis);
                log.debug("重试异常详情", e);
                sleep(delayMillis);
            }
        }
        throw lastException;
    }

    @Override
    public <T> T executeWithRetry(Callable<T> task, int retryTimes, long initialDelayMillis) throws Exception {
        return executeWithRetry(task, retryTimes, initialDelayMillis,
                properties.getDefaultPolicy().getBackoffMultiplier(), properties.getDefaultPolicy().getMaxDelayMillis());
    }

    @Override
    public <T> T executeWithRetry(Callable<T> task,
                                  int retryTimes,
                                  long initialDelayMillis,
                                  double backoffMultiplier,
                                  long maxDelayMillis) throws Exception {
        RetryRequest request = RetryRequest.builder()
                .retryTimes(retryTimes)
                .initialDelayMillis(initialDelayMillis)
                .backoffMultiplier(backoffMultiplier)
                .maxDelayMillis(maxDelayMillis)
                .strategyType(RetryStrategyType.EXPONENTIAL)
                .build();
        return execute(task, request);
    }

    @Override
    public <T> T executeWithFixedDelay(Callable<T> task, int retryTimes, long delayMillis) throws Exception {
        RetryRequest request = RetryRequest.builder()
                .retryTimes(retryTimes)
                .initialDelayMillis(delayMillis)
                .backoffMultiplier(1D)
                .maxDelayMillis(delayMillis)
                .strategyType(RetryStrategyType.FIXED)
                .build();
        return execute(task, request);
    }

    @Override
    public <T> T executeWithFastRetry(Callable<T> task) throws Exception {
        return execute(task, toRequest(properties.getFastPolicy(), RetryStrategyType.EXPONENTIAL));
    }

    @Override
    public <T> T executeWithSlowRetry(Callable<T> task) throws Exception {
        return execute(task, toRequest(properties.getSlowPolicy(), RetryStrategyType.EXPONENTIAL));
    }

    private RetryRequest toRequest(TaskRetryProperties.Policy policy, RetryStrategyType strategyType) {
        return RetryRequest.builder()
                .retryTimes(policy.getRetryTimes())
                .initialDelayMillis(policy.getInitialDelayMillis())
                .backoffMultiplier(policy.getBackoffMultiplier())
                .maxDelayMillis(policy.getMaxDelayMillis())
                .strategyType(strategyType)
                .build();
    }

    private void sleep(long delayMillis) throws InterruptedException {
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private void onBeforeAttempt(int attempt, int totalAttempts) {
        try {
            listener.onBeforeAttempt(attempt, totalAttempts);
        } catch (Exception e) {
            log.debug("重试监听器执行前回调异常", e);
        }
    }

    private void onFailure(int attempt, int totalAttempts, Exception exception) {
        try {
            listener.onFailure(attempt, totalAttempts, exception);
        } catch (Exception e) {
            log.debug("重试监听器失败回调异常", e);
        }
    }

    private void onSuccess(int attempt, int totalAttempts) {
        try {
            listener.onSuccess(attempt, totalAttempts);
        } catch (Exception e) {
            log.debug("重试监听器成功回调异常", e);
        }
    }
}
