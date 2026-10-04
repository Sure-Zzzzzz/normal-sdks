package io.github.surezzzzzz.sdk.messaging.kafka.clientapplication.handler;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumerComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import org.springframework.context.annotation.Profile;

/**
 * 应用基础包中的纯标记消费入口。
 *
 * @author surezzzzzz
 */
@Profile("mock-external-consumer")
@SimpleKafkaConsumerComponent
public class ExternalMarkedConsumer {

    @SimpleKafkaConsumer(topic = "mock.external.application.topic", datasource = "mock", groupId = "mock-group")
    public void consume(KafkaConsumerRecord<String, String> record) {
    }
}
