package io.github.surezzzzzz.sdk.retry.task.sleeper;

import io.github.surezzzzzz.sdk.retry.task.annotation.TaskRetryComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.concurrent.TimeUnit;

/**
 * 线程重试等待器
 *
 * @author surezzzzzz
 */
@TaskRetryComponent
@ConditionalOnMissingBean(RetrySleeper.class)
public class ThreadRetrySleeper implements RetrySleeper {

    @Override
    public void sleep(long delayMillis) throws InterruptedException {
        if (delayMillis > 0) {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
        }
    }
}
