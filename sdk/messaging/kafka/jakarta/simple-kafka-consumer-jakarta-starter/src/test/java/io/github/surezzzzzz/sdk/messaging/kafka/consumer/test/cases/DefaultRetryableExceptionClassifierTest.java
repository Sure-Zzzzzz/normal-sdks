package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.FatalConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.RetryableConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DefaultRetryableExceptionClassifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可重试异常分类器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultRetryableExceptionClassifierTest {

    private final DefaultRetryableExceptionClassifier classifier = new DefaultRetryableExceptionClassifier();

    @Test
    public void testRetryableCauseIsRecognized() {
        boolean retryable = classifier.isRetryable(new IllegalStateException(new TimeoutException("mock timeout")));

        log.info("TimeoutException cause 分类结果：{}", retryable);
        assertTrue(retryable, "嵌套 TimeoutException 应识别为可重试");
    }

    @Test
    public void testFatalCauseOverridesRetryableCause() {
        boolean retryable = classifier.isRetryable(new RetryableAnnotatedException(
                new IllegalArgumentException("mock fatal")));

        log.info("fatal cause 优先分类结果：{}", retryable);
        assertFalse(retryable, "fatal cause 应覆盖外层可重试异常");
    }

    @Test
    public void testFatalAnnotationOverridesRetryableAnnotation() {
        boolean retryable = classifier.isRetryable(new RetryableAnnotatedException(
                new FatalAnnotatedException("mock fatal annotation")));

        log.info("注解优先级分类结果：{}", retryable);
        assertFalse(retryable, "@FatalConsumerException 应覆盖 @RetryableConsumerException");
    }

    @RetryableConsumerException
    private static class RetryableAnnotatedException extends Exception {

        private RetryableAnnotatedException(Throwable cause) {
            super(cause);
        }
    }

    @FatalConsumerException
    private static class FatalAnnotatedException extends Exception {

        private FatalAnnotatedException(String message) {
            super(message);
        }
    }
}
