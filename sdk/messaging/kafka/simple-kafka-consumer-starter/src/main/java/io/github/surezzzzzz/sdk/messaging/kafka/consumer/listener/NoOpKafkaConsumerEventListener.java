package io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;

/**
 * 空消费事件监听器。
 *
 * @author surezzzzzz
 */
public class NoOpKafkaConsumerEventListener implements KafkaConsumerEventListener {

    @Override
    public void onEvent(KafkaConsumerEventContext context) {
        // 空实现
    }
}
