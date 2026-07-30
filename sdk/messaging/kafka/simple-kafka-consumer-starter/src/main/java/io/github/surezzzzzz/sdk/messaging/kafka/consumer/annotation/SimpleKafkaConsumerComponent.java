package io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple Kafka Consumer Component 注解，作为 ComponentScan 的 includeFilter 标记
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleKafkaConsumerComponent {
}
