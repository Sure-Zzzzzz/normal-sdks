package io.github.surezzzzzz.sdk.retry.task.test;

import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试重试等待器
 *
 * @author surezzzzzz
 */
public class NoopRetrySleeper implements RetrySleeper {

    private final List<Long> delays = new ArrayList<Long>();

    @Override
    public void sleep(long delayMillis) {
        delays.add(delayMillis);
    }

    public List<Long> getDelays() {
        return Collections.unmodifiableList(delays);
    }

    public void clear() {
        delays.clear();
    }
}
