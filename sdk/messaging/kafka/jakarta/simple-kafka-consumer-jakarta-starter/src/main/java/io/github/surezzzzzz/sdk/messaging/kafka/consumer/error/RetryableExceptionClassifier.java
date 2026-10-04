package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

/**
 * 异常分类器 SPI，判断异常是否可重试
 *
 * @author surezzzzzz
 */
public interface RetryableExceptionClassifier {

    /**
     * 判断异常是否可重试
     *
     * @param exception 异常
     * @return true 可重试，false 不可重试
     */
    boolean isRetryable(Exception exception);
}
