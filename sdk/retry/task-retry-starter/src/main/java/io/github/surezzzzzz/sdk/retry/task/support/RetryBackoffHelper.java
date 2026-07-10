package io.github.surezzzzzz.sdk.retry.task.support;

import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;

/**
 * 重试退避 Helper
 *
 * @author surezzzzzz
 */
public class RetryBackoffHelper {

    public static long calculateDelayMillis(RetryRequest request, int attemptIndex) {
        if (RetryStrategyType.NONE == request.getStrategyType()) {
            return 0L;
        }
        if (RetryStrategyType.FIXED == request.getStrategyType()) {
            return request.getInitialDelayMillis();
        }
        double delay = request.getInitialDelayMillis() * Math.pow(request.getBackoffMultiplier(), attemptIndex - 1);
        return Math.min((long) delay, request.getMaxDelayMillis());
    }

    private RetryBackoffHelper() {
    }
}
