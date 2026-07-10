package io.github.surezzzzzz.sdk.retry.task.listener;

/**
 * 重试监听器
 *
 * @author surezzzzzz
 */
public interface RetryListener {

    void onBeforeAttempt(int attempt, int totalAttempts);

    void onFailure(int attempt, int totalAttempts, Exception exception);

    void onSuccess(int attempt, int totalAttempts);
}
