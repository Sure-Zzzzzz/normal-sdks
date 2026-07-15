package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
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
 * Kafka Publisher 端到端测试 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaPublisherEndToEndHelper {

    public static final String V110_BOOTSTRAP_SERVERS = "localhost:18091";
    public static final String V28_BOOTSTRAP_SERVERS = "localhost:18092";
    public static final String V37_BOOTSTRAP_SERVERS = "localhost:18094";
    public static final String CLUSTER_BOOTSTRAP_SERVERS = "localhost:19192,localhost:19193,localhost:19194";
    public static final String DATASOURCE_V28 = "v28";
    public static final String DATASOURCE_CLUSTER = "cluster";
    public static final String ROUTE_KEY_V37 = "mock-route-v37-a";
    public static final String TOPIC_V110_PREFIX = "mock.publisher.v110.e2e.";
    public static final String TOPIC_V28_PREFIX = "mock.publisher.v28.e2e.";
    public static final String TOPIC_V37_ROUTE_PREFIX = "mock.publisher.route.e2e.";
    public static final String TOPIC_CLUSTER_PREFIX = "mock.publisher.cluster.e2e.";
    public static final String MESSAGE_TYPE = "mock.message.created";
    public static final String PAYLOAD = "mock-payload";
    public static final String CUSTOM_HEADER = "mock-unicode-header";
    public static final String CUSTOM_HEADER_VALUE = "模拟值-✓";
    public static final String APP_NAME = "mock-publisher-app";
    public static final String DEFAULT_HEADER_MESSAGE_ID = "x-message-id";
    public static final String DEFAULT_HEADER_MESSAGE_TYPE = "x-message-type";
    public static final String DEFAULT_HEADER_SOURCE = "x-source";
    public static final String DEFAULT_HEADER_PUBLISHED_AT = "x-published-at";
    public static final String GROUP_PREFIX = "kafka-publisher-e2e-";
    public static final String KEY_TEMPLATE = "mock-key-%s";
    public static final String TOPIC_TEMPLATE = "%s%s";
    public static final String CREATE_TOPIC_FAILED = "创建 Kafka topic 失败: %s";
    public static final String CREATE_TOPIC_INTERRUPTED = "创建 Kafka topic 被中断: %s";
    public static final String ASSERT_RECORD_MISSING = "未消费到 Publisher 消息，bootstrapServers=%s, topic=%s";
    public static final String ASSERT_RECORD_UNEXPECTED = "不应在该 datasource 消费到消息，bootstrapServers=%s, topic=%s";
    public static final long CONSUME_TIMEOUT_MS = 15000L;
    public static final long NO_MESSAGE_TIMEOUT_MS = 3000L;
    public static final long POLL_INTERVAL_MS = 500L;
    public static final long SEND_TIMEOUT_SECONDS = 30L;
    public static final int SINGLE_PARTITION_COUNT = 1;
    public static final short SINGLE_REPLICATION_FACTOR = 1;
    public static final int CLUSTER_PARTITION_COUNT = 3;
    public static final short CLUSTER_REPLICATION_FACTOR = 3;
    public static final int PARTITION_ZERO = 0;
    public static final int PARTITION_ONE = 1;
    public static final int PARTITION_TWO = 2;
    public static final String UTILITY_CLASS_MESSAGE = "Utility class";

    private KafkaPublisherEndToEndHelper() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    public static void createTopic(String bootstrapServers, String topic, int partitions,
                                   short replicationFactor) {
        Properties properties = new Properties();
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            try {
                adminClient.createTopics(Collections.singletonList(
                        new NewTopic(topic, partitions, replicationFactor))).all().get();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException(String.format(CREATE_TOPIC_FAILED, topic), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(String.format(CREATE_TOPIC_INTERRUPTED, topic), e);
            }
        }
    }

    public static ConsumerRecord<String, String> consumeRecord(String bootstrapServers, String topic,
                                                               String expectedKey, long timeoutMs) {
        Properties properties = consumerProperties(bootstrapServers, GROUP_PREFIX + suffix());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_INTERVAL_MS));
                for (ConsumerRecord<String, String> record : records) {
                    if (expectedKey.equals(record.key())) {
                        return record;
                    }
                }
            }
            return null;
        }
    }

    public static String suffix() {
        return UUID.randomUUID().toString().replace(String.valueOf('-'), "");
    }

    public static String topic(String prefix, String suffix) {
        return String.format(TOPIC_TEMPLATE, prefix, suffix);
    }

    public static String key(String suffix) {
        return String.format(KEY_TEMPLATE, suffix);
    }

    private static Properties consumerProperties(String bootstrapServers, String groupId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
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
