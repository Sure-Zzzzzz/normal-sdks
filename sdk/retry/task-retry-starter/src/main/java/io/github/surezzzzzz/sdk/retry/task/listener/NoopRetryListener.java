package io.github.surezzzzzz.sdk.retry.task.listener;

import io.github.surezzzzzz.sdk.retry.task.annotation.TaskRetryComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 空重试监听器
 *
 * @author surezzzzzz
 */
@TaskRetryComponent
@ConditionalOnMissingBean(RetryListener.class)
public class NoopRetryListener implements RetryListener {

    @Override
    public void onBeforeAttempt(int attempt, int totalAttempts) {
    }

    @Override
    public void onFailure(int attempt, int totalAttempts, Exception exception) {
    }

    @Override
    public void onSuccess(int attempt, int totalAttempts) {
    }
}
