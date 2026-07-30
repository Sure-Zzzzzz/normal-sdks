package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.*;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.SimpleKafkaConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * 默认错误处理器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaConsumerErrorHandlerTest {

    @Test
    public void testRetryableFailureBeforeLimitUsesBackoff() {
        RetryableExceptionClassifier classifier = mock(RetryableExceptionClassifier.class);
        KafkaConsumerBackoffPolicy backoffPolicy = mock(KafkaConsumerBackoffPolicy.class);
        when(classifier.isRetryable(org.mockito.ArgumentMatchers.any(Exception.class))).thenReturn(true);
        when(backoffPolicy.computeBackoffMs(2)).thenReturn(345L);
        DefaultKafkaConsumerErrorHandler<String, String> handler = handler(3, classifier, backoffPolicy);

        ErrorHandlerDecision decision = handler.onError(record(), new IllegalStateException("retry"), 2);
        log.info("可重试决策：outcome={}，backoffMs={}，errorCode={}", decision.getOutcome(),
                decision.getBackoffMs(), decision.getErrorCode());

        assertEquals(ErrorHandlerOutcome.RETRY, decision.getOutcome());
        assertEquals(345L, decision.getBackoffMs());
        assertEquals(ErrorCode.CONSUME_RETRYABLE, decision.getErrorCode());
        verify(backoffPolicy).computeBackoffMs(2);
    }

    @Test
    public void testRetryableFailureAtLimitGoesToDeadLetterWithoutBackoff() {
        RetryableExceptionClassifier classifier = mock(RetryableExceptionClassifier.class);
        KafkaConsumerBackoffPolicy backoffPolicy = mock(KafkaConsumerBackoffPolicy.class);
        when(classifier.isRetryable(org.mockito.ArgumentMatchers.any(Exception.class))).thenReturn(true);
        DefaultKafkaConsumerErrorHandler<String, String> handler = handler(3, classifier, backoffPolicy);

        ErrorHandlerDecision decision = handler.onError(record(), new IllegalStateException("retry"), 3);
        log.info("达到上限决策：outcome={}，retryable={}", decision.getOutcome(), decision.isRetryable());

        assertEquals(ErrorHandlerOutcome.DEAD_LETTER, decision.getOutcome());
        assertEquals(0L, decision.getBackoffMs());
        assertEquals(ErrorCode.CONSUME_RETRYABLE, decision.getErrorCode());
        verify(backoffPolicy, never()).computeBackoffMs(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    public void testFatalFailureUsesBusinessErrorCodeWithoutBackoff() {
        RetryableExceptionClassifier classifier = mock(RetryableExceptionClassifier.class);
        KafkaConsumerBackoffPolicy backoffPolicy = mock(KafkaConsumerBackoffPolicy.class);
        when(classifier.isRetryable(org.mockito.ArgumentMatchers.any(Exception.class))).thenReturn(false);
        DefaultKafkaConsumerErrorHandler<String, String> handler = handler(3, classifier, backoffPolicy);

        ErrorHandlerDecision decision = handler.onError(record(),
                new SimpleKafkaConsumerException("BUSINESS_001", "fatal"), 1);
        log.info("不可重试业务异常决策：outcome={}，errorCode={}", decision.getOutcome(), decision.getErrorCode());

        assertEquals(ErrorHandlerOutcome.DEAD_LETTER, decision.getOutcome());
        assertEquals("BUSINESS_001", decision.getErrorCode());
        verify(backoffPolicy, never()).computeBackoffMs(org.mockito.ArgumentMatchers.anyInt());
    }

    private DefaultKafkaConsumerErrorHandler<String, String> handler(int maxAttempts,
                                                                     RetryableExceptionClassifier classifier,
                                                                     KafkaConsumerBackoffPolicy backoffPolicy) {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getError().setMaxAttempts(maxAttempts);
        return new DefaultKafkaConsumerErrorHandler<>(properties, classifier, backoffPolicy);
    }

    private KafkaConsumerRecord<String, String> record() {
        return KafkaConsumerRecord.of(new ConsumerRecord<>("mock-topic", 0, 0L, "mock-key", "mock-value"),
                "mock-message", "mock-datasource", null);
    }
}
