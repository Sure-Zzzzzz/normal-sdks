package io.github.surezzzzzz.sdk.kafka.route.test.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;

import java.nio.charset.StandardCharsets;
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

    public static final long CONSUME_TIMEOUT_MS = 15000L;
    public static final long NO_MESSAGE_TIMEOUT_MS = 3000L;
    public static final long FEATURE_API_PROBE_TIMEOUT_MS = 5000L;
    public static final long CONSUMER_POLL_INTERVAL_MS = 500L;
    public static final String E2E_CONSUMER_GROUP_PREFIX = "kafka-route-e2e-";

    public static final String DATASOURCE_DEFAULT = SimpleKafkaRouteConstant.DEFAULT_DATASOURCE_KEY;
    public static final String DATASOURCE_EVENT = "event";
    public static final String DATASOURCE_V110 = "v110";
    public static final String DATASOURCE_V28 = "v28";
    public static final String DATASOURCE_V37 = "v37";
    public static final String DATASOURCE_TX37 = "tx37";
    public static final String DATASOURCE_CLUSTER = "cluster";

    public static final String TOPIC_V110_PREFIX = "mock.route.v110.e2e.";
    public static final String TOPIC_V28_PREFIX = "event.v28.route.e2e.";
    public static final String TOPIC_V37_PREFIX = "event.v37.route.e2e.";
    public static final String TOPIC_ROUTE_KEY_PREFIX = "mock.route.key.e2e.";
    public static final String TOPIC_EXPLICIT_PREFIX = "event.v37.explicit.e2e.";
    public static final String TOPIC_RECORD_PREFIX = "event.v37.record.e2e.";
    public static final String TOPIC_CALLBACK_ROUTE_PREFIX = "event.v28.callback.route.e2e.";
    public static final String TOPIC_CALLBACK_INNER_PREFIX = "event.v37.callback.inner.e2e.";
    public static final String TOPIC_TRANSACTION_PREFIX = "mock.route.tx37.e2e.";
    public static final String TOPIC_ROLLBACK_PREFIX = "mock.route.tx37.rollback.e2e.";
    public static final String TOPIC_CLUSTER_PREFIX = "cluster.route.e2e.";
    public static final String TOPIC_DERIVED_CONSUMER_PREFIX = "event.v37.derived.consumer.e2e.";

    public static final String KEY_V110_PREFIX = "v110-key-";
    public static final String KEY_V28_PREFIX = "v28-key-";
    public static final String KEY_V37_PREFIX = "v37-key-";
    public static final String KEY_ROUTE_PREFIX = "route-key-";
    public static final String KEY_EXPLICIT_PREFIX = "explicit-key-";
    public static final String KEY_RECORD_PREFIX = "record-key-";
    public static final String KEY_CALLBACK_PREFIX = "callback-key-";
    public static final String KEY_TRANSACTION_PREFIX = "tx-key-";
    public static final String KEY_ROLLBACK_PREFIX = "tx-rollback-key-";
    public static final String KEY_CLUSTER_TEMPLATE = "cluster-key-%d-%s";

    public static final String VALUE_V110 = "v110-value";
    public static final String VALUE_V28 = "v28-value";
    public static final String VALUE_V37 = "v37-value";
    public static final String VALUE_ROUTE = "route-key-value";
    public static final String VALUE_EXPLICIT = "explicit-value";
    public static final String VALUE_RECORD = "record-value";
    public static final String VALUE_CALLBACK = "callback-value";
    public static final String VALUE_TRANSACTION = "tx-value";
    public static final String VALUE_ROLLBACK = "tx-rollback-value";
    public static final String VALUE_CLUSTER_TEMPLATE = "cluster-value-%d";

    public static final String ROUTE_KEY_V37 = "tenant-v37-a";
    public static final String HEADER_NAME = "mock-header";
    public static final String HEADER_VALUE = "mock-header-value";
    public static final String TX_ROLLBACK_MESSAGE = "mock transaction rollback";
    public static final byte[] HEADER_VALUE_BYTES = HEADER_VALUE.getBytes(StandardCharsets.UTF_8);

    public static final String ASSERT_DATASOURCE_COUNT_MESSAGE = "E2E 必须拿到全部 datasource 诊断结果";
    public static final String ASSERT_CLUSTER_NODE_COUNT_MESSAGE = "cluster 诊断应看到至少 3 个 broker";
    public static final String ASSERT_ADMIN_API_CAPABILITY_MESSAGE = "describeCluster 成功后基础 Admin API 应视为可用: %s";
    public static final String ASSERT_TRANSACTION_CAPABILITY_MESSAGE = "transactionSupported 不符合预期: %s";
    public static final String ASSERT_IDEMPOTENCE_CAPABILITY_MESSAGE = "idempotenceSupported 不符合预期: %s";
    public static final String ASSERT_ZSTD_CAPABILITY_MESSAGE = "zstdSupported 不符合预期: %s";
    public static final String ASSERT_DIAGNOSTIC_MISSING_MESSAGE = "未获取到 datasource 诊断结果: %s";
    public static final String ASSERT_DIAGNOSTIC_STATUS_MESSAGE = "datasource 诊断状态不符合预期: %s";
    public static final String ASSERT_NODE_COUNT_MESSAGE = "datasource 诊断应看到 broker 节点: %s";
    public static final String ASSERT_CONTROLLER_VISIBLE_MESSAGE = "datasource 诊断应看到 controller: %s";
    public static final String ASSERT_FAILURE_REASON_MESSAGE = "datasource 成功诊断不应带失败原因: %s";
    public static final String ASSERT_RECORD_MISSING_MESSAGE = "未在指定 Kafka datasource 消费到消息，bootstrapServers=%s, topic=%s";
    public static final String ASSERT_RECORD_UNEXPECTED_MESSAGE = "不应在该 Kafka datasource 消费到消息，bootstrapServers=%s, topic=%s";
    public static final String LOG_MULTI_VERSION_METADATA_MESSAGE = "多版本发送 metadata: v110={}, v28={}, v37={}";
    public static final String FORMAT_MESSAGE_WITH_DATASOURCE = "%s%s";
    public static final String FORMAT_KEY_WITH_SUFFIX = "%s%s";
    public static final String FORMAT_CLUSTER_VALUE = "cluster-value-%d";
    public static final String CREATE_TOPIC_FAILED_MESSAGE = "创建 Kafka topic 失败: %s";
    public static final String CREATE_TOPIC_INTERRUPTED_MESSAGE = "创建 Kafka topic 被中断: %s";
    public static final String CALLBACK_SEND_FAILED_MESSAGE = "callback 内部发送失败";

    public static final int EXPECTED_DATASOURCE_COUNT = 7;
    public static final int MIN_NODE_COUNT = 0;
    public static final int CLUSTER_MIN_NODE_COUNT = 3;
    public static final int SINGLE_TOPIC_PARTITION_COUNT = 1;
    public static final short SINGLE_TOPIC_REPLICATION_FACTOR = 1;
    public static final int CLUSTER_TOPIC_PARTITION_COUNT = 3;
    public static final short CLUSTER_TOPIC_REPLICATION_FACTOR = 3;
    public static final int PARTITION_ZERO = 0;
    public static final int PARTITION_ONE = 1;
    public static final int PARTITION_TWO = 2;
    public static final long TIMEOUT_SECONDS = 30L;
    public static final long SEND_TIMEOUT_SECONDS = 30L;
    public static final long RECORD_TIMESTAMP = 1000L;
    public static final String UTILITY_CLASS_MESSAGE = "Utility class";

    private KafkaRouteEndToEndHelper() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    public static void createTopic(String bootstrapServers, String topic, int partitions, short replicationFactor) {
        Properties properties = new Properties();
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            try {
                adminClient.createTopics(Collections.singletonList(
                        new NewTopic(topic, partitions, replicationFactor))).all().get();
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException(String.format(CREATE_TOPIC_FAILED_MESSAGE, topic), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(String.format(CREATE_TOPIC_INTERRUPTED_MESSAGE, topic), e);
            }
        }
    }

    public static ConsumerRecord<String, String> consumeRecord(String bootstrapServers, String topic,
                                                               String expectedKey, long timeoutMs) {
        Properties properties = consumerProperties(bootstrapServers, E2E_CONSUMER_GROUP_PREFIX + suffix());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(CONSUMER_POLL_INTERVAL_MS));
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
                SimpleKafkaRouteConstant.DEFAULT_KEY_DESERIALIZER);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                SimpleKafkaRouteConstant.DEFAULT_VALUE_DESERIALIZER);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                SimpleKafkaRouteConstant.AUTO_OFFSET_RESET_EARLIEST);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE.toString());
        properties.put(SimpleKafkaRouteConstant.PROPERTY_ISOLATION_LEVEL,
                SimpleKafkaRouteConstant.ISOLATION_LEVEL_READ_COMMITTED);
        properties.put(SimpleKafkaRouteConstant.PROPERTY_ALLOW_AUTO_CREATE_TOPICS, Boolean.FALSE.toString());
        return properties;
    }

    public static String suffix() {
        return UUID.randomUUID().toString().replace(String.valueOf('-'), "");
    }

    public static String keyWithSuffix(String prefix, String suffix) {
        return String.format(FORMAT_KEY_WITH_SUFFIX, prefix, suffix);
    }

    public static String topicWithSuffix(String prefix, String suffix) {
        return String.format(FORMAT_KEY_WITH_SUFFIX, prefix, suffix);
    }

    public static String clusterKey(int partition, String suffix) {
        return String.format(KEY_CLUSTER_TEMPLATE, partition, suffix);
    }

    public static String clusterValue(int partition) {
        return String.format(VALUE_CLUSTER_TEMPLATE, partition);
    }
}
