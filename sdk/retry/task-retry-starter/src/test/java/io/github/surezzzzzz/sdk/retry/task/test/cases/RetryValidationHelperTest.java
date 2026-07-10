package io.github.surezzzzzz.sdk.retry.task.test.cases;

import io.github.surezzzzzz.sdk.retry.task.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.exception.TaskRetryValidationException;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;
import io.github.surezzzzzz.sdk.retry.task.support.RetryValidationHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 重试校验 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
class RetryValidationHelperTest {

    @Test
    @DisplayName("测试任务为空")
    void shouldRejectNullTask() {
        TaskRetryValidationException exception = assertThrows(TaskRetryValidationException.class,
                () -> RetryValidationHelper.validate(null, RetryRequest.builder().build()),
                "任务为空应抛校验异常");

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应正确");
    }

    @Test
    @DisplayName("测试请求为空")
    void shouldRejectNullRequest() {
        TaskRetryValidationException exception = assertThrows(TaskRetryValidationException.class,
                () -> RetryValidationHelper.validate(() -> "success", null),
                "请求为空应抛校验异常");

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应正确");
    }

    @Test
    @DisplayName("测试重试次数为负数")
    void shouldRejectNegativeRetryTimes() {
        RetryRequest request = RetryRequest.builder().retryTimes(-1).build();

        TaskRetryValidationException exception = assertThrows(TaskRetryValidationException.class,
                () -> RetryValidationHelper.validate(() -> "success", request),
                "重试次数为负数应抛校验异常");

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应正确");
    }

    @Test
    @DisplayName("测试延迟参数非法")
    void shouldRejectInvalidDelay() {
        RetryRequest request = RetryRequest.builder()
                .initialDelayMillis(2000L)
                .maxDelayMillis(1000L)
                .strategyType(RetryStrategyType.EXPONENTIAL)
                .build();

        TaskRetryValidationException exception = assertThrows(TaskRetryValidationException.class,
                () -> RetryValidationHelper.validate(() -> "success", request),
                "最大延迟小于初始延迟应抛校验异常");

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应正确");
    }

    @Test
    @DisplayName("测试退避倍数非法")
    void shouldRejectInvalidBackoffMultiplier() {
        RetryRequest request = RetryRequest.builder().backoffMultiplier(0.5D).build();

        TaskRetryValidationException exception = assertThrows(TaskRetryValidationException.class,
                () -> RetryValidationHelper.validate(() -> "success", request),
                "退避倍数小于 1 应抛校验异常");

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode(), "错误码应正确");
    }

    @Test
    @DisplayName("测试无延迟策略允许最大延迟小于初始延迟")
    void shouldAllowNoDelayWithSmallMaxDelay() {
        RetryRequest request = RetryRequest.builder()
                .initialDelayMillis(2000L)
                .maxDelayMillis(1000L)
                .strategyType(RetryStrategyType.NONE)
                .build();

        RetryValidationHelper.validate(() -> "success", request);
    }
}
