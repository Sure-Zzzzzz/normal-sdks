package io.github.surezzzzzz.sdk.retry.task.model;

import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.constant.TaskRetryConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 重试请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private int retryTimes = TaskRetryConstant.DEFAULT_RETRY_TIMES;

    @Builder.Default
    private long initialDelayMillis = TaskRetryConstant.DEFAULT_INITIAL_DELAY_MILLIS;

    @Builder.Default
    private double backoffMultiplier = TaskRetryConstant.DEFAULT_BACKOFF_MULTIPLIER;

    @Builder.Default
    private long maxDelayMillis = TaskRetryConstant.DEFAULT_MAX_DELAY_MILLIS;

    @Builder.Default
    private RetryStrategyType strategyType = RetryStrategyType.EXPONENTIAL;
}
