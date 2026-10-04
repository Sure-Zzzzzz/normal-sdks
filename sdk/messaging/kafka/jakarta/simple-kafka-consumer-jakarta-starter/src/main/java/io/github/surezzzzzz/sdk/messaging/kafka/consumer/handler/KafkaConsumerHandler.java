package io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

/**
 * 消费处理器 SPI，业务方实现消息处理逻辑
 *
 * @param <K> key 类型
 * @param <V> value 类型
 * @author surezzzzzz
 */
public interface KafkaConsumerHandler<K, V> {

    /**
     * 处理单条消息，抛出的异常由 {@code KafkaConsumerErrorHandler} 接管
     *
     * @param record 消费记录
     * @throws Exception 处理异常
     */
    void handle(KafkaConsumerRecord<K, V> record) throws Exception;

    /**
     * 解析当前 topic 对应的消费注册项标识。
     *
     * @param topic 消费 topic
     * @return 注册项标识，不可解析时返回 null
     */
    default String resolveRegistrationId(String topic) {
        return null;
    }
}
