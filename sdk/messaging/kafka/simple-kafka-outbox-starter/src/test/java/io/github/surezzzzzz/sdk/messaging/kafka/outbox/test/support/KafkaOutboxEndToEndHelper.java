package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Kafka Outbox 端到端测试 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaOutboxEndToEndHelper {

    public static final String DATASOURCE_V110 = "v110";
    public static final String DATASOURCE_V28 = "v28";
    public static final String DATASOURCE_V37 = "v37";
    public static final String DATASOURCE_CLUSTER = "cluster";
    public static final String ROUTE_KEY_V37 = "mock-outbox-route-v37-a";
    public static final String SHARED_TOPIC_PREFIX = "mock.outbox.switch.e2e.";
    public static final String GROUP_PREFIX = "kafka-outbox-e2e-";
    public static final long CONSUME_TIMEOUT_MS = 15000L;
    public static final long SETTLE_TIMEOUT_MS = 2000L;
    public static final long POLL_INTERVAL_MS = 500L;
    public static final int SINGLE_PARTITION_COUNT = 1;
    public static final int CLUSTER_PARTITION_COUNT = 3;
    public static final short SINGLE_REPLICATION_FACTOR = 1;
    public static final short CLUSTER_REPLICATION_FACTOR = 3;
    public static final String UTILITY_CLASS_MESSAGE = "Utility class";

    private KafkaOutboxEndToEndHelper() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    /**
     * 在已配置的四套 Kafka 集群创建同名 topic
     *
     * @param topic                   topic
     * @param v110BootstrapServers    v110 集群地址
     * @param v28BootstrapServers     v28 集群地址
     * @param v37BootstrapServers     v37 集群地址
     * @param clusterBootstrapServers 三 broker 集群地址
     */
    public static void createSharedTopic(String topic, String v110BootstrapServers, String v28BootstrapServers,
                                         String v37BootstrapServers, String clusterBootstrapServers) {
        createTopic(v110BootstrapServers, topic, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(v28BootstrapServers, topic, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(v37BootstrapServers, topic, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(clusterBootstrapServers, topic, CLUSTER_PARTITION_COUNT, CLUSTER_REPLICATION_FACTOR);
    }

    /**
     * 创建 topic
     *
     * @param bootstrapServers  Kafka 地址
     * @param topic             topic
     * @param partitions        分区数
     * @param replicationFactor 副本数
     */
    public static void createTopic(String bootstrapServers, String topic, int partitions, short replicationFactor) {
        Properties properties = new Properties();
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            try {
                adminClient.createTopics(Collections.singletonList(
                        new NewTopic(topic, partitions, replicationFactor))).all().get();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException("创建 Kafka topic 失败", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("创建 Kafka topic 被中断", e);
            }
        }
    }

    /**
     * 消费候选消息 key
     *
     * @param bootstrapServers Kafka 地址
     * @param topic            topic
     * @param candidateKeys    候选 key
     * @return 实际消费到的候选 key
     */
    public static Set<String> consumeKeys(String bootstrapServers, String topic, Set<String> candidateKeys) {
        Set<String> foundKeys = new LinkedHashSet<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                consumerProperties(bootstrapServers, GROUP_PREFIX + suffix()))) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + CONSUME_TIMEOUT_MS;
            Long settleDeadline = null;
            while (System.currentTimeMillis() < deadline
                    && (settleDeadline == null || System.currentTimeMillis() < settleDeadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_INTERVAL_MS));
                for (ConsumerRecord<String, String> record : records) {
                    if (candidateKeys.contains(record.key())) {
                        foundKeys.add(record.key());
                    }
                }
                if (!foundKeys.isEmpty() && settleDeadline == null) {
                    settleDeadline = Math.min(deadline, System.currentTimeMillis() + SETTLE_TIMEOUT_MS);
                }
            }
        }
        return foundKeys;
    }

    /**
     * 消费候选消息的全部 record，用于校验重复投递和 broker offset。
     *
     * @param bootstrapServers Kafka 地址
     * @param topic            topic
     * @param candidateKeys    候选 key
     * @param expectedCount    预期至少消费到的记录数
     * @return 实际消费到的候选 record
     */
    public static List<ConsumerRecord<String, String>> consumeRecords(String bootstrapServers, String topic,
                                                                      Set<String> candidateKeys,
                                                                      int expectedCount) {
        List<ConsumerRecord<String, String>> foundRecords = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                consumerProperties(bootstrapServers, GROUP_PREFIX + suffix()))) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + CONSUME_TIMEOUT_MS;
            Long settleDeadline = null;
            while (System.currentTimeMillis() < deadline
                    && (settleDeadline == null || System.currentTimeMillis() < settleDeadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_INTERVAL_MS));
                for (ConsumerRecord<String, String> record : records) {
                    if (candidateKeys.contains(record.key())) {
                        foundRecords.add(record);
                    }
                }
                if (foundRecords.size() >= expectedCount && settleDeadline == null) {
                    settleDeadline = Math.min(deadline, System.currentTimeMillis() + SETTLE_TIMEOUT_MS);
                }
            }
        }
        return foundRecords;
    }

    /**
     * 生成随机后缀
     *
     * @return 后缀
     */
    public static String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
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
