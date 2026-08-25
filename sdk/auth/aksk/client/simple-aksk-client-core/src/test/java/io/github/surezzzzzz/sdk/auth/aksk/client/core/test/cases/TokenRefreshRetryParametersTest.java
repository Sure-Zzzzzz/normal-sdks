package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.SimpleAkskClientCoreTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.TestRetrySleeper;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.test.TokenRefreshRetryTestConfiguration;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
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

/**
 * TokenRefreshExecutor 重试参数验证测试
 *
 * <p>验证 {@link SimpleAkskClientCoreConstant} 中 TOKEN_REFRESH_* 常量的绝对数值，
 * 以及这些参数传给 TaskRetryExecutor 后实际产生的重试延迟序列。
 *
 * <p>断言均写死具体数值，不引用同一常量，以防止延迟单位换算错误（如秒/毫秒混淆）被掩盖。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskClientCoreTestApplication.class)
@Import(TokenRefreshRetryTestConfiguration.class)
class TokenRefreshRetryParametersTest {

    @Autowired
    private TaskRetryExecutor taskRetryExecutor;

    @Autowired
    private TestRetrySleeper testRetrySleeper;

    @BeforeEach
    void setUp() {
        testRetrySleeper.clear();
    }

    @Test
    @DisplayName("TOKEN_REFRESH 常量应为毫秒级数值，不是秒级")
    void tokenRefreshConstantsShouldBeMillisecondLevel() {
        assertEquals(3, SimpleAkskClientCoreConstant.TOKEN_REFRESH_RETRY_TIMES,
                "TOKEN_REFRESH_RETRY_TIMES 应为 3");
        assertEquals(1000L, SimpleAkskClientCoreConstant.TOKEN_REFRESH_INITIAL_DELAY_MS,
                "TOKEN_REFRESH_INITIAL_DELAY_MS 应为 1000 毫秒（不是 1 秒）");
        assertEquals(1.5, SimpleAkskClientCoreConstant.TOKEN_REFRESH_BACKOFF_MULTIPLIER, 0.001,
                "TOKEN_REFRESH_BACKOFF_MULTIPLIER 应为 1.5");
        assertEquals(5000L, SimpleAkskClientCoreConstant.TOKEN_REFRESH_MAX_DELAY_MS,
                "TOKEN_REFRESH_MAX_DELAY_MS 应为 5000 毫秒（不是 5 秒）");
    }

    @Test
    @DisplayName("按 TOKEN_REFRESH 常量重试应产生 [1000ms, 1500ms] 延迟序列")
    void shouldProduceExpectedDelaySequenceWhenRetryingWithTokenRefreshConstants() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> task = () -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Simulated failure #" + attempt);
            }
            return "success-" + attempt;
        };

        String result = taskRetryExecutor.executeWithRetry(
                task,
                SimpleAkskClientCoreConstant.TOKEN_REFRESH_RETRY_TIMES,
                SimpleAkskClientCoreConstant.TOKEN_REFRESH_INITIAL_DELAY_MS,
                SimpleAkskClientCoreConstant.TOKEN_REFRESH_BACKOFF_MULTIPLIER,
                SimpleAkskClientCoreConstant.TOKEN_REFRESH_MAX_DELAY_MS
        );

        log.info("执行结果: {}, 执行次数: {}, 延迟序列: {}", result, attemptCount.get(), testRetrySleeper.getDelays());

        assertEquals("success-3", result, "第 3 次应执行成功");
        assertEquals(3, attemptCount.get(), "前 2 次失败 + 第 3 次成功，共执行 3 次");
        assertEquals(Arrays.asList(1000L, 1500L), testRetrySleeper.getDelays(),
                "重试延迟序列应为 [1000ms, 1500ms]（初始延迟 1000ms，退避系数 1.5）");
    }
}
