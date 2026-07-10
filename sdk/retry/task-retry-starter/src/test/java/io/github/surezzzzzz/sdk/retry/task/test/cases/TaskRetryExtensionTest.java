package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.configuration.TaskRetryProperties;
import io.github.surezzzzzz.sdk.retry.task.executor.DefaultTaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.listener.RetryListener;
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task Retry 扩展点测试
 *
 * @author surezzzzzz
 */
@Slf4j
class TaskRetryExtensionTest {

    @Test
    @DisplayName("测试自定义 RetryPredicate 阻止后续重试")
    void shouldStopRetryWhenPredicateReturnsFalse() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        List<Long> delays = new ArrayList<Long>();
        RetryPredicate retryPredicate = (exception, attempt, request) -> false;
        TaskRetryExecutor executor = newExecutor(delays::add, retryPredicate, new RecordingRetryListener());
        Callable<String> task = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("stop");
        };

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> executor.executeWithRetry(task, 5, 1000L, 2.0D, 10000L),
                "自定义判断器返回 false 时应直接抛出异常");

        assertEquals("stop", exception.getMessage(), "异常消息应保持原始值");
        assertEquals(1, attemptCount.get(), "判断器阻止重试后只应执行 1 次");
        assertEquals(0, delays.size(), "判断器阻止重试后不应等待");
    }

    @Test
    @DisplayName("测试 RetryListener 回调次数")
    void shouldCallListenerWithExactAttempts() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        List<Long> delays = new ArrayList<Long>();
        RecordingRetryListener listener = new RecordingRetryListener();
        TaskRetryExecutor executor = newExecutor(delays::add, (exception, attempt, request) -> true, listener);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success";
        };

        String result = executor.executeWithRetry(task, 5, 1000L, 2.0D, 10000L);

        assertEquals("success", result, "执行结果应正确");
        assertEquals(3, attemptCount.get(), "应执行 3 次");
        assertEquals(3, listener.getBeforeAttempts().size(), "执行前回调次数应正确");
        assertEquals(2, listener.getFailureAttempts().size(), "失败回调次数应正确");
        assertEquals(1, listener.getSuccessAttempts().size(), "成功回调次数应正确");
        assertEquals("1/6", listener.getBeforeAttempts().get(0), "第 1 次执行前回调参数应正确");
        assertEquals("2/6", listener.getBeforeAttempts().get(1), "第 2 次执行前回调参数应正确");
        assertEquals("3/6", listener.getBeforeAttempts().get(2), "第 3 次执行前回调参数应正确");
        assertEquals("1/6", listener.getFailureAttempts().get(0), "第 1 次失败回调参数应正确");
        assertEquals("2/6", listener.getFailureAttempts().get(1), "第 2 次失败回调参数应正确");
        assertEquals("3/6", listener.getSuccessAttempts().get(0), "成功回调参数应正确");
        assertEquals(2, delays.size(), "成功前应等待 2 次");
    }

    private TaskRetryExecutor newExecutor(RetrySleeper sleeper, RetryPredicate retryPredicate, RetryListener listener) {
        return new DefaultTaskRetryExecutor(new TaskRetryProperties(), sleeper, retryPredicate, listener);
    }

    private static class RecordingRetryListener implements RetryListener {

        private final List<String> beforeAttempts = new ArrayList<String>();
        private final List<String> failureAttempts = new ArrayList<String>();
        private final List<String> successAttempts = new ArrayList<String>();

        @Override
        public void onBeforeAttempt(int attempt, int totalAttempts) {
            beforeAttempts.add(attempt + "/" + totalAttempts);
        }

        @Override
        public void onFailure(int attempt, int totalAttempts, Exception exception) {
            failureAttempts.add(attempt + "/" + totalAttempts);
        }

        @Override
        public void onSuccess(int attempt, int totalAttempts) {
            successAttempts.add(attempt + "/" + totalAttempts);
        }

        public List<String> getBeforeAttempts() {
            return beforeAttempts;
        }

        public List<String> getFailureAttempts() {
            return failureAttempts;
        }

        public List<String> getSuccessAttempts() {
            return successAttempts;
        }
    }
}
