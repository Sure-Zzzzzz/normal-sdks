package io.github.surezzzzzz.sdk.retry.task.predicate;

import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;

/**
 * 重试判断器
 *
 * @author surezzzzzz
 */
public interface RetryPredicate {

    boolean shouldRetry(Exception exception, int attempt, RetryRequest request);
}
