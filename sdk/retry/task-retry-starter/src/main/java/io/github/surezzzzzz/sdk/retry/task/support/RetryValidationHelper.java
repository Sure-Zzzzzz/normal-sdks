package io.github.surezzzzzz.sdk.retry.task.support;

import io.github.surezzzzzz.sdk.retry.task.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.task.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.exception.TaskRetryValidationException;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;

import java.util.concurrent.Callable;

/**
 * 重试校验 Helper
 *
 * @author surezzzzzz
 */
public class RetryValidationHelper {

    public static void validate(Callable<?> task, RetryRequest request) {
        if (task == null) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.TASK_REQUIRED);
        }
        if (request == null) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.REQUEST_REQUIRED);
        }
        if (request.getRetryTimes() < 0) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.RETRY_TIMES_NEGATIVE);
        }
        if (request.getStrategyType() == null) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.STRATEGY_TYPE_REQUIRED);
        }
        if (request.getInitialDelayMillis() < 0) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.INITIAL_DELAY_NEGATIVE);
        }
        if (request.getMaxDelayMillis() < 0) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.MAX_DELAY_NEGATIVE);
        }
        if (RetryStrategyType.NONE != request.getStrategyType() && request.getMaxDelayMillis() < request.getInitialDelayMillis()) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.MAX_DELAY_LESS_THAN_INITIAL_DELAY);
        }
        if (request.getBackoffMultiplier() < 1D) {
            throw new TaskRetryValidationException(ErrorCode.VALIDATION_ERROR, ErrorMessage.BACKOFF_MULTIPLIER_INVALID);
        }
    }

    private RetryValidationHelper() {
    }
}
