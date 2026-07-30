package io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;

/**
 * 消费事件监听器 SPI，用于 metrics / audit 等下游接入
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerEventListener {

    /**
     * 消费事件回调
     *
     * @param context 事件上下文
     */
    void onEvent(KafkaConsumerEventContext context);
}
