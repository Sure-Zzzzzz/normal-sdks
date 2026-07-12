package io.github.surezzzzzz.sdk.kafka.route.test.support;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Kafka route 端到端测试 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaRouteEndToEndHelper {

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:19092";
    public static final String EVENT_BOOTSTRAP_SERVERS = "localhost:19093";
    public static final String CLUSTER_BOOTSTRAP_SERVERS = "localhost:19192,localhost:19193,localhost:19194";
    public static final String KAFKA_V110_BOOTSTRAP_SERVERS = "localhost:18091";
    public static final String KAFKA_V28_BOOTSTRAP_SERVERS = "localhost:18092";
    public static final String KAFKA_V37_BOOTSTRAP_SERVERS = "localhost:18094";
    public static final long CONSUME_TIMEOUT_MS = 15000L;
    public static final long NO_MESSAGE_TIMEOUT_MS = 3000L;

    private KafkaRouteEndToEndHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void createTopic(String bootstrapServers, String topic, int partitions, short replicationFactor) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            try {
                adminClient.createTopics(Collections.singletonList(
                        new NewTopic(topic, partitions, replicationFactor))).all().get();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException("创建 Kafka topic 失败: " + topic, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("创建 Kafka topic 被中断: " + topic, e);
            }
        }
    }

    public static ConsumerRecord<String, String> consumeRecord(String bootstrapServers, String topic,
                                                               String expectedKey, long timeoutMs) {
        Properties properties = consumerProperties(bootstrapServers, "kafka-route-e2e-" + suffix());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500L));
                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return record;
                    }
                }
            }
            return null;
        }
    }

    public static Properties consumerProperties(String bootstrapServers, String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put("isolation.level", "read_committed");
        properties.put("allow.auto.create.topics", "false");
        return properties;
    }

    public static String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
