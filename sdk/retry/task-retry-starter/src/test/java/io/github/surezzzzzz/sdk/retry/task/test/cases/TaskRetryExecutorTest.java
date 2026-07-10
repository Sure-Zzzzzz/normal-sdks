package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.exception.TaskRetryValidationException;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;
import io.github.surezzzzzz.sdk.retry.task.test.NoopRetrySleeper;
import io.github.surezzzzzz.sdk.retry.task.test.TaskRetryTestApplication;
import io.github.surezzzzzz.sdk.retry.task.test.TaskRetryTestConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task Retry 执行器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = TaskRetryTestApplication.class)
@Import(TaskRetryTestConfiguration.class)
class TaskRetryExecutorTest {

    @Autowired
    private TaskRetryExecutor taskRetryExecutor;

    @Autowired
    private NoopRetrySleeper noopRetrySleeper;

    @BeforeEach
    void setUp() {
        noopRetrySleeper.clear();
    }

    @Test
    @DisplayName("测试首次执行成功")
    void shouldExecuteOnceWhenFirstAttemptSuccess() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            attemptCount.incrementAndGet();
            return "success";
        };

        String result = taskRetryExecutor.executeWithRetry(task, 3, 1000L, 1.5D, 30000L);

        log.info("执行结果: {}, 执行次数: {}", result, attemptCount.get());
        assertEquals("success", result, "执行结果应正确");
        assertEquals(1, attemptCount.get(), "首次成功只应执行 1 次");
        assertTrue(noopRetrySleeper.getDelays().isEmpty(), "首次成功不应等待");
    }

    @Test
    @DisplayName("测试重试后执行成功")
    void shouldReturnResultWhenRetrySuccess() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success-" + attempt;
        };

        String result = taskRetryExecutor.executeWithRetry(task, 5, 1000L, 2.0D, 10000L);

        log.info("执行结果: {}, 执行次数: {}, 等待序列: {}", result, attemptCount.get(), noopRetrySleeper.getDelays());
        assertEquals("success-3", result, "重试后结果应正确");
        assertEquals(3, attemptCount.get(), "应在第 3 次执行成功");
        assertEquals(Arrays.asList(1000L, 2000L), noopRetrySleeper.getDelays(), "指数退避等待序列应正确");
    }

    @Test
    @DisplayName("测试全部执行失败抛最后一次原始异常")
    void shouldThrowLastOriginalExceptionWhenAllAttemptsFail() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        RuntimeException lastException = new RuntimeException("final-fail");
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt == 3) {
                throw lastException;
            }
            throw new RuntimeException("fail-" + attempt);
        };

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> taskRetryExecutor.executeWithRetry(task, 2, 1000L, 2.0D, 10000L),
                "全部失败应抛出最后一次原始异常");

        log.info("最终异常: {}, 执行次数: {}", exception.getMessage(), attemptCount.get());
        assertSame(lastException, exception, "应抛出最后一次原始异常实例");
        assertEquals(3, attemptCount.get(), "retryTimes=2 时总执行次数应为 3");
        assertEquals(Arrays.asList(1000L, 2000L), noopRetrySleeper.getDelays(), "失败等待序列应正确");
    }

    @Test
    @DisplayName("测试零重试只执行一次")
    void shouldExecuteOnceWhenRetryTimesIsZero() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("fail");
        };

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> taskRetryExecutor.executeWithRetry(task, 0, 1000L, 2.0D, 10000L),
                "零重试失败应直接抛异常");

        assertEquals("fail", exception.getMessage(), "异常消息应正确");
        assertEquals(1, attemptCount.get(), "零重试只应执行一次");
        assertTrue(noopRetrySleeper.getDelays().isEmpty(), "零重试不应等待");
    }

    @Test
    @DisplayName("测试固定延迟等待序列")
    void shouldUseFixedDelay() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithFixedDelay(task, 5, 3000L);

        assertEquals("success", result, "固定延迟重试结果应正确");
        assertEquals(4, attemptCount.get(), "应执行 4 次");
        assertEquals(Arrays.asList(3000L, 3000L, 3000L), noopRetrySleeper.getDelays(), "固定延迟序列应正确");
    }

    @Test
    @DisplayName("测试最大延迟截断")
    void shouldLimitDelayByMaxDelay() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithRetry(task, 5, 1000L, 3.0D, 2000L);

        assertEquals("success", result, "最大延迟截断后结果应正确");
        assertEquals(Arrays.asList(1000L, 2000L, 2000L), noopRetrySleeper.getDelays(), "延迟应被 maxDelayMillis 截断");
    }

    @Test
    @DisplayName("测试无延迟策略")
    void shouldUseNoDelayStrategy() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        RetryRequest request = RetryRequest.builder()
                .retryTimes(2)
                .initialDelayMillis(1000L)
                .backoffMultiplier(2.0D)
                .maxDelayMillis(2000L)
                .strategyType(RetryStrategyType.NONE)
                .build();
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.execute(task, request);

        assertEquals("success", result, "无延迟策略结果应正确");
        assertEquals(Arrays.asList(0L, 0L), noopRetrySleeper.getDelays(), "无延迟策略应记录 0 毫秒等待");
    }

    @Test
    @DisplayName("测试默认策略")
    void shouldUseDefaultPolicy() throws Exception {
        Callable<String> task = () -> "success";

        String result = taskRetryExecutor.execute(task);

        assertEquals("success", result, "默认策略执行结果应正确");
    }

    @Test
    @DisplayName("测试快速策略")
    void shouldUseFastPolicy() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithFastRetry(task);

        assertEquals("success", result, "快速策略执行结果应正确");
        assertEquals(Arrays.asList(2000L, 2400L), noopRetrySleeper.getDelays(), "快速策略等待序列应正确");
    }

    @Test
    @DisplayName("测试慢速策略")
    void shouldUseSlowPolicy() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("fail-" + attempt);
            }
            return "success";
        };

        String result = taskRetryExecutor.executeWithSlowRetry(task);

        assertEquals("success", result, "慢速策略执行结果应正确");
        assertEquals(Arrays.asList(10000L, 20000L), noopRetrySleeper.getDelays(), "慢速策略等待序列应正确");
    }

    @Test
    @DisplayName("测试参数校验异常")
    void shouldThrowValidationExceptionWhenRequestInvalid() {
        TaskRetryValidationException exception = assertThrows(TaskRetryValidationException.class,
                () -> taskRetryExecutor.executeWithRetry(() -> "success", -1, 1000L),
                "重试次数为负数应抛校验异常");

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应为参数校验错误");
    }
}
