package io.github.surezzzzzz.sdk.retry.task.cases;

import io.github.surezzzzzz.sdk.retry.task.configuration.RetryConfiguration;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RetryConfiguration.class)
public class TaskRetryExecutorTest {
    @Autowired
    private TaskRetryExecutor taskRetryExecutor;

    @Test
    void testExecuteWithRetrySuccess() throws Exception {
        // 第一次就成功
        Callable<String> task = () -> "success";

        String result = taskRetryExecutor.executeWithRetry(task, 3, 1, 1.5, 30L);

        assertEquals("success", result);
    }

    @Test
    void testExecuteWithRetrySuccessAfterRetries() throws Exception {
        // �?次才成功
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Attempt " + attempt + " failed");
            }
            return "success on attempt " + attempt;
        };

        String result = taskRetryExecutor.executeWithRetry(task, 5, 1, 1.5, 30L);

        assertEquals("success on attempt 3", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void testExecuteWithRetryAllFailed() throws Exception {
        // 所有尝试都失败
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Always fail");
        };

        Exception exception = assertThrows(RuntimeException.class, () -> {
            taskRetryExecutor.executeWithRetry(task, 3, 1, 1.5, 30L);
        });

        assertEquals("Always fail", exception.getMessage());
        assertEquals(4, attemptCount.get()); // 验证尝试�?�?
    }

    @Test
    void testExecuteWithRetryBackoffCalculation() throws Exception {
        // 测试重试逻辑，虽然不能验证具体延迟时间，但能验证重试次数
        AtomicInteger attemptCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                throw new RuntimeException("Fail attempt " + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithRetry(task, 5, 1, 2.0, 30L);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());

        // 验证确实有延迟（至少应该�?1 + 2 = 3秒的延迟�?
        assertTrue(duration >= 3000, "应该有重试延迟，实际耗时: " + duration + "ms");
    }

    @Test
    void testExecuteWithRetryTwoParams() throws Exception {
        // 测试默认退避策略的重载方法
        Callable<String> task = () -> "success";

        String result = taskRetryExecutor.executeWithRetry(task, 3, 1);

        assertEquals("success", result);
    }

    @Test
    void testExecuteWithFixedDelay() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                throw new RuntimeException("Fail attempt " + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithFixedDelay(task, 5, 1);

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void testExecuteWithDefaultRetry() throws Exception {
        Callable<String> task = () -> "success";

        String result = taskRetryExecutor.executeWithRetry(task);

        assertEquals("success", result);
    }

    @Test
    void testExecuteWithFastRetry() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                throw new RuntimeException("Fail attempt " + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithFastRetry(task);

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void testExecuteWithSlowRetry() throws Exception {
        Callable<String> task = () -> "success";

        String result = taskRetryExecutor.executeWithSlowRetry(task);

        assertEquals("success", result);
    }

    @Test
    void testExecuteWithRetryZeroRetries() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Always fail");
        };

        Exception exception = assertThrows(RuntimeException.class, () -> {
            taskRetryExecutor.executeWithRetry(task, 0, 1, 1.5, 30L);
        });

        assertEquals("Always fail", exception.getMessage());
        assertEquals(1, attemptCount.get()); // 0次重试，一次都不执�?
    }

    @Test
    void testExecuteWithRetryDifferentExceptionTypes() throws Exception {
        // 测试不同类型的异常都能正确处�?
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            switch (attempt) {
                case 1:
                    throw new IllegalArgumentException("Illegal argument");
                case 2:
                    throw new NullPointerException("Null pointer");
                case 3:
                    throw new RuntimeException("Runtime exception");
                default:
                    return "success";
            }
        };

        String result = taskRetryExecutor.executeWithRetry(task, 5, 1, 1.0, 10L);

        assertEquals("success", result);
        assertEquals(4, attemptCount.get());
    }

    @Test
    void testExecuteWithRetryMaxDelayLimit() throws Exception {
        // 测试最大延迟限制功�?
        AtomicInteger attemptCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                throw new RuntimeException("Fail attempt " + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithRetry(task, 5, 10, 3.0, 5L);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());

        // 第1次失败后延迟: 10秒，但被限制为5秒
        // 第2次失败后延迟: 30秒，但被限制为5秒
        // 所以总延迟应该是10秒左右，而不是40秒
        assertTrue(duration >= 10000, "应该有至少10秒延迟");
        assertTrue(duration < 20000, "延迟不应该超过20秒，因为有最大延迟限制");
    }

    @Test
    void testExecuteWithRetryReturnValue() throws Exception {
        // 测试各种返回值类
        Callable<Integer> intTask = () -> 42;
        Callable<Boolean> boolTask = () -> true;
        Callable<String> stringTask = () -> "hello world";

        assertEquals(Integer.valueOf(42), taskRetryExecutor.executeWithRetry(intTask, 3, 1));
        assertEquals(Boolean.TRUE, taskRetryExecutor.executeWithRetry(boolTask, 3, 1));
        assertEquals("hello world", taskRetryExecutor.executeWithRetry(stringTask, 3, 1));
    }
}
