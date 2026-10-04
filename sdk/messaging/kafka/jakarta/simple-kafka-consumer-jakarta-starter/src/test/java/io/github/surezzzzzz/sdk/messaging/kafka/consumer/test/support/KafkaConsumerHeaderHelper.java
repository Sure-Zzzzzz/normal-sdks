package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Kafka Consumer 端到端 header 读取工具。
 *
 * @author surezzzzzz
 */
public final class KafkaConsumerHeaderHelper {

    private KafkaConsumerHeaderHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String headerText(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
