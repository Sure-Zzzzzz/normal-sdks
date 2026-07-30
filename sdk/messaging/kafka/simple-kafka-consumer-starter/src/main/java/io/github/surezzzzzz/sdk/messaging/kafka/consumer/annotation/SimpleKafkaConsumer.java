package io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消费注册注解，标在 {@code KafkaConsumerHandler} 实现类的方法上声明一个消费入口
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SimpleKafkaConsumer {

    /**
     * 消费的单个 topic（与 topics 二选一）
     */
    String topic() default "";

    /**
     * 消费的多个 topic 列表（与 topic 二选一）
     */
    String[] topics() default {};

    /**
     * 显式 datasource key，空时走 route 规则解析
     */
    String datasource() default "";

    /**
     * 覆盖 route datasource 的 group-id；空时用 datasource 配置的 group-id
     */
    String groupId() default "";

    /**
     * 覆盖 route datasource 的 auto-offset-reset
     */
    String autoOffsetReset() default "";

    /**
     * 注册项标识，用于日志与事件关联，默认用方法全名
     */
    String id() default "";
}
