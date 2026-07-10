package io.github.surezzzzzz.sdk.retry.task.sleeper;

/**
 * 重试等待器
 *
 * @author surezzzzzz
 */
public interface RetrySleeper {

    void sleep(long delayMillis) throws InterruptedException;
}
