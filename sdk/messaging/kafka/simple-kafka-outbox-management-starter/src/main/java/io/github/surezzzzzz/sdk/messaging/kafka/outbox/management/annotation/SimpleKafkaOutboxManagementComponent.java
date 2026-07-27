package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple Kafka Outbox Management 组件扫描标记。
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SimpleKafkaOutboxManagementComponent {
}
