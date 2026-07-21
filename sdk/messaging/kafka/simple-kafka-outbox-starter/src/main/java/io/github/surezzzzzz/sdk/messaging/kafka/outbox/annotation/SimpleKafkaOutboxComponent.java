package io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple Kafka Outbox 组件标记注解
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleKafkaOutboxComponent {
}
