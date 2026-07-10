package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;
import io.github.surezzzzzz.sdk.retry.task.support.RetryBackoffHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 重试退避 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
class RetryBackoffHelperTest {

    @Test
    @DisplayName("测试指数退避计算")
    void shouldCalculateExponentialDelay() {
        RetryRequest request = RetryRequest.builder()
                .initialDelayMillis(1000L)
                .backoffMultiplier(2.0D)
                .maxDelayMillis(10000L)
                .strategyType(RetryStrategyType.EXPONENTIAL)
                .build();

        assertEquals(1000L, RetryBackoffHelper.calculateDelayMillis(request, 1), "第 1 次失败后等待应正确");
        assertEquals(2000L, RetryBackoffHelper.calculateDelayMillis(request, 2), "第 2 次失败后等待应正确");
        assertEquals(4000L, RetryBackoffHelper.calculateDelayMillis(request, 3), "第 3 次失败后等待应正确");
    }

    @Test
    @DisplayName("测试最大延迟截断")
    void shouldLimitExponentialDelay() {
        RetryRequest request = RetryRequest.builder()
                .initialDelayMillis(1000L)
                .backoffMultiplier(3.0D)
                .maxDelayMillis(2000L)
                .strategyType(RetryStrategyType.EXPONENTIAL)
                .build();

        assertEquals(1000L, RetryBackoffHelper.calculateDelayMillis(request, 1), "第 1 次等待不应截断");
        assertEquals(2000L, RetryBackoffHelper.calculateDelayMillis(request, 2), "第 2 次等待应被截断");
        assertEquals(2000L, RetryBackoffHelper.calculateDelayMillis(request, 3), "第 3 次等待应被截断");
    }

    @Test
    @DisplayName("测试固定延迟计算")
    void shouldCalculateFixedDelay() {
        RetryRequest request = RetryRequest.builder()
                .initialDelayMillis(3000L)
                .backoffMultiplier(2.0D)
                .maxDelayMillis(10000L)
                .strategyType(RetryStrategyType.FIXED)
                .build();

        assertEquals(3000L, RetryBackoffHelper.calculateDelayMillis(request, 1), "固定延迟第 1 次等待应正确");
        assertEquals(3000L, RetryBackoffHelper.calculateDelayMillis(request, 2), "固定延迟第 2 次等待应正确");
    }

    @Test
    @DisplayName("测试无延迟计算")
    void shouldCalculateNoDelay() {
        RetryRequest request = RetryRequest.builder()
                .initialDelayMillis(3000L)
                .backoffMultiplier(2.0D)
                .maxDelayMillis(10000L)
                .strategyType(RetryStrategyType.NONE)
                .build();

        assertEquals(0L, RetryBackoffHelper.calculateDelayMillis(request, 1), "无延迟策略等待应为 0");
        assertEquals(0L, RetryBackoffHelper.calculateDelayMillis(request, 2), "无延迟策略等待应为 0");
    }
}
