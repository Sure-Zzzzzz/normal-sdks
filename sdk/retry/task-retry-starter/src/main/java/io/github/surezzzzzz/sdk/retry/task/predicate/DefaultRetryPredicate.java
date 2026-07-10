package io.github.surezzzzzz.sdk.retry.task.predicate;

import io.github.surezzzzzz.sdk.retry.task.annotation.TaskRetryComponent;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 默认重试判断器
 *
 * @author surezzzzzz
 */
@TaskRetryComponent
@ConditionalOnMissingBean(RetryPredicate.class)
public class DefaultRetryPredicate implements RetryPredicate {

    @Override
    public boolean shouldRetry(Exception exception, int attempt, RetryRequest request) {
        return true;
    }
}
