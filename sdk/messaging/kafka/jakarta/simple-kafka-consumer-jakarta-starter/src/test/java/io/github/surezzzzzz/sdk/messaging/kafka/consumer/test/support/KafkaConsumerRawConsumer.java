package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

/**
 * Consumer 端到端原生 Kafka 读取器。
 *
 * @author surezzzzzz
 */
public final class KafkaConsumerRawConsumer {

    private static final long POLL_INTERVAL_MS = 500L;

    private KafkaConsumerRawConsumer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ConsumerRecord<String, String> consumeByMessageId(String bootstrapServers, String topic,
                                                                    String messageId, long timeoutMs) {
        Properties properties = consumerProperties(bootstrapServers);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_INTERVAL_MS));
                for (ConsumerRecord<String, String> record : records) {
                    if (messageId.equals(KafkaConsumerHeaderHelper.headerText(record, "x-message-id"))) {
                        return record;
                    }
                }
            }
            return null;
        }
    }

    private static Properties consumerProperties(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-consumer-e2e-raw-"
                + UUID.randomUUID().toString().replace("-", ""));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                SimpleKafkaRouteConstant.DEFAULT_KEY_DESERIALIZER);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                SimpleKafkaRouteConstant.DEFAULT_VALUE_DESERIALIZER);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                SimpleKafkaRouteConstant.AUTO_OFFSET_RESET_EARLIEST);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE.toString());
        properties.put(SimpleKafkaRouteConstant.PROPERTY_ALLOW_AUTO_CREATE_TOPICS, Boolean.FALSE.toString());
        return properties;
    }
}
