package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Kafka Consumer 端到端测试 Helper。
 *
 * @author surezzzzzz
 */
public final class KafkaConsumerEndToEndHelper {

    public static final String DATASOURCE_V110 = "v110";
    public static final String DATASOURCE_V28 = "v28";
    public static final String DATASOURCE_V37 = "v37";
    public static final String DATASOURCE_CLUSTER = "cluster";
    public static final String BOOTSTRAP_V110 = "localhost:18091";
    public static final String BOOTSTRAP_V28 = "localhost:18092";
    public static final String BOOTSTRAP_V37 = "localhost:18094";
    public static final String BOOTSTRAP_CLUSTER = "localhost:19192,localhost:19193,localhost:19194";
    public static final String TOPIC_V110 = "mock.consumer.v110.e2e";
    public static final String TOPIC_V28 = "mock.consumer.v28.e2e";
    public static final String TOPIC_V37 = "mock.consumer.v37.e2e";
    public static final String TOPIC_CLUSTER = "mock.consumer.cluster.e2e";
    public static final String TOPIC_IDEMPOTENCY = "mock.consumer.idempotency.e2e";
    public static final String TOPIC_RETRY = "mock.consumer.retry.e2e";
    public static final String TOPIC_AUTO_COMMIT = "mock.consumer.auto-commit.e2e";
    public static final String TOPIC_REFRESH = "mock.consumer.refresh.e2e";
    public static final String TOPIC_SAME_TOPIC_GROUP = "mock.consumer.same-topic-group.e2e";
    public static final String TOPIC_DEAD_LETTER_RECOVERY = "mock.consumer.dead-letter-recovery.e2e";
    public static final String TOPIC_PROCESSING_LEASE = "mock.consumer.processing-lease.e2e";
    public static final long WAIT_TIMEOUT_MS = 30000L;
    public static final int SINGLE_PARTITION_COUNT = 1;
    public static final short SINGLE_REPLICATION_FACTOR = 1;
    public static final int CLUSTER_PARTITION_COUNT = 3;
    public static final short CLUSTER_REPLICATION_FACTOR = 3;

    private KafkaConsumerEndToEndHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void createRequiredTopics() {
        createTopic(BOOTSTRAP_V110, TOPIC_V110, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V28, TOPIC_V28, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_V37, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_IDEMPOTENCY, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_RETRY, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_RETRY + ".DLT", SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_AUTO_COMMIT, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_REFRESH, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_SAME_TOPIC_GROUP, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_DEAD_LETTER_RECOVERY, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_DEAD_LETTER_RECOVERY + ".DLT", SINGLE_PARTITION_COUNT,
                SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_PROCESSING_LEASE, SINGLE_PARTITION_COUNT, SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_V37, TOPIC_PROCESSING_LEASE + ".DLT", SINGLE_PARTITION_COUNT,
                SINGLE_REPLICATION_FACTOR);
        createTopic(BOOTSTRAP_CLUSTER, TOPIC_CLUSTER, CLUSTER_PARTITION_COUNT, CLUSTER_REPLICATION_FACTOR);
    }

    public static String messageId() {
        return "mock-consumer-message-" + UUID.randomUUID().toString().replace("-", "");
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
                    throw new IllegalStateException("创建 Kafka E2E topic 失败: " + topic, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("创建 Kafka E2E topic 被中断: " + topic, e);
            }
        }
    }
}
