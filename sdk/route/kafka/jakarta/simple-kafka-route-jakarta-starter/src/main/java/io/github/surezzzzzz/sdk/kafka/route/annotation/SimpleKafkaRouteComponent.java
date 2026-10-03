package io.github.surezzzzzz.sdk.kafka.route.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple Kafka Route 组件标记
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SimpleKafkaRouteComponent {
}
