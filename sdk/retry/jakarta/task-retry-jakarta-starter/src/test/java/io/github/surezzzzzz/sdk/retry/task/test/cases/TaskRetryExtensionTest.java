package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.configuration.TaskRetryProperties;
import io.github.surezzzzzz.sdk.retry.task.executor.DefaultTaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.listener.RetryListener;
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;
import io.github.surezzzzzz.sdk.retry.task.sleeper.ThreadRetrySleeper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    @DisplayName("测试 RetryPredicate 自身异常原样传播")
    void shouldPropagatePredicateException() {
        RuntimeException predicateException = new RuntimeException("predicate-failure");
        List<Long> delays = new ArrayList<Long>();
        TaskRetryExecutor executor = newExecutor(delays::add,
                (exception, attempt, request) -> {
                    throw predicateException;
                }, new RecordingRetryListener());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> executor.executeWithRetry(() -> {
                    throw new RuntimeException("task-failure");
                }, 1, 100L),
                "判断器异常应向调用方传播");

        assertSame(predicateException, exception, "应保留判断器原始异常实例");
        assertTrue(delays.isEmpty(), "判断器异常后不应等待或继续重试");
    }

    @Test
    @DisplayName("测试 RetryListener 异常不阻断任务")
    void shouldIgnoreListenerExceptions() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        List<Long> delays = new ArrayList<Long>();
        RetryListener listener = new RetryListener() {
            @Override
            public void onBeforeAttempt(int attempt, int totalAttempts) {
                throw new RuntimeException("before-failure");
            }

            @Override
            public void onFailure(int attempt, int totalAttempts, Exception exception) {
                throw new RuntimeException("failure-failure");
            }

            @Override
            public void onSuccess(int attempt, int totalAttempts) {
                throw new RuntimeException("success-failure");
            }
        };
        TaskRetryExecutor executor = newExecutor(delays::add, (exception, attempt, request) -> true, listener);

        String result = executor.executeWithRetry(() -> {
            if (attemptCount.incrementAndGet() == 1) {
                throw new RuntimeException("task-failure");
            }
            return "success";
        }, 1, 100L);

        assertEquals("success", result, "监听器异常不能覆盖任务结果");
        assertEquals(2, attemptCount.get(), "监听器异常不能阻断后续重试");
        assertEquals(1, delays.size(), "首次失败后应按原语义等待一次");
    }

    @Test
    @DisplayName("测试等待中断恢复线程中断标记")
    void shouldRestoreInterruptFlagWhenSleeperInterrupted() {
        Thread.interrupted();
        TaskRetryExecutor executor = newExecutor(new ThreadRetrySleeper(),
                (exception, attempt, request) -> true, new RecordingRetryListener());

        try {
            InterruptedException exception = assertThrows(InterruptedException.class,
                    () -> {
                        Thread.currentThread().interrupt();
                        executor.executeWithRetry(() -> {
                            throw new RuntimeException("task-failure");
                        }, 1, 100L);
                    },
                    "等待被中断时应抛 InterruptedException");

            assertTrue(Thread.currentThread().isInterrupted(), "等待被中断后应恢复中断标记");
            assertEquals(InterruptedException.class, exception.getClass(), "应保留等待中断语义");
        } finally {
            Thread.interrupted();
        }
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
