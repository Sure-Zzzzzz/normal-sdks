package io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记业务异常为不可重试，标在异常类上，供 {@code RetryableExceptionClassifier} 识别
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FatalConsumerException {
}
