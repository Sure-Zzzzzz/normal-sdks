package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.SimpleKafkaConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import lombok.RequiredArgsConstructor;

/**
 * 默认错误处理器：
 * 可重试且未达最大尝试次数 -> RETRY（退避由 BackoffPolicy 计算）；
 * 否则 -> DEAD_LETTER。
 * 错误码优先取业务异常自带的码，其次按可重试性回退到 CONSUME_RETRYABLE / CONSUME_FATAL。
 *
 * @param <K> key 类型
 * @param <V> value 类型
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultKafkaConsumerErrorHandler<K, V> implements KafkaConsumerErrorHandler<K, V> {

    private final SimpleKafkaConsumerProperties properties;
    private final RetryableExceptionClassifier classifier;
    private final KafkaConsumerBackoffPolicy backoffPolicy;

    @Override
    public ErrorHandlerDecision onError(KafkaConsumerRecord<K, V> record, Exception cause, int attempt) {
        boolean retryable = classifier.isRetryable(cause);
        String errorCode = resolveErrorCode(cause, retryable);
        int maxAttempts = properties.getError().getMaxAttempts();
        if (retryable && attempt < maxAttempts) {
            long backoffMs = backoffPolicy.computeBackoffMs(attempt);
            return ErrorHandlerDecision.builder()
                    .outcome(ErrorHandlerOutcome.RETRY)
                    .backoffMs(backoffMs)
                    .errorCode(errorCode)
                    .retryable(true)
                    .build();
        }
        return ErrorHandlerDecision.builder()
                .outcome(ErrorHandlerOutcome.DEAD_LETTER)
                .backoffMs(0L)
                .errorCode(errorCode)
                .retryable(retryable)
                .build();
    }

    private String resolveErrorCode(Exception cause, boolean retryable) {
        if (cause instanceof SimpleKafkaConsumerException) {
            String code = ((SimpleKafkaConsumerException) cause).getErrorCode();
            if (KafkaConsumerStringHelper.hasText(code)) {
                return code;
            }
        }
        return retryable ? ErrorCode.CONSUME_RETRYABLE : ErrorCode.CONSUME_FATAL;
    }
}
