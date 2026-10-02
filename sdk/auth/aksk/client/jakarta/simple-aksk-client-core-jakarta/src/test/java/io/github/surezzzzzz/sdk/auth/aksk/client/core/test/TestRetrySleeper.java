package io.github.surezzzzzz.sdk.auth.aksk.client.core.test;

import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试重试等待器：记录延迟但不真实等待
 *
 * @author surezzzzzz
 */
public class TestRetrySleeper implements RetrySleeper {

    private final List<Long> delays = new ArrayList<>();

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
