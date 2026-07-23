package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerCapability;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaAdminCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaConfigurationCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.test.KafkaEndToEndProfilesResolver;
import io.github.surezzzzzz.sdk.kafka.route.test.SimpleKafkaRouteTestApplication;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteEndToEndHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 大而全端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleKafkaRouteTestApplication.class)
@ActiveProfiles(resolver = KafkaEndToEndProfilesResolver.class)
public class KafkaRouteEndToEndTest {

    @Autowired
    private KafkaRouteTemplate template;

    @Autowired
    private KafkaRouteDiagnostics diagnostics;

    @Autowired
    private SimpleKafkaRouteProperties routeProperties;

    @Autowired
    private SimpleKafkaRouteRegistry registry;

    @Test
    public void testRouteSendAcrossKafkaBrokerVersionsAndCluster() throws Exception {
        assertDiagnosticsReady();

        String suffix = KafkaRouteEndToEndHelper.suffix();
        String v110Topic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_V110_PREFIX, suffix);
        String v28Topic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_V28_PREFIX, suffix);
        String v37Topic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_V37_PREFIX, suffix);
        String routeKeyTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_ROUTE_KEY_PREFIX, suffix);
        String explicitTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_EXPLICIT_PREFIX, suffix);
        String recordTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_RECORD_PREFIX, suffix);
        String callbackRouteInputTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_CALLBACK_ROUTE_PREFIX, suffix);
        String callbackInnerTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_CALLBACK_INNER_PREFIX, suffix);
        String transactionTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_TRANSACTION_PREFIX, suffix);
        String rollbackTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_ROLLBACK_PREFIX, suffix);
        String clusterTopic = KafkaRouteEndToEndHelper.topicWithSuffix(KafkaRouteEndToEndHelper.TOPIC_CLUSTER_PREFIX, suffix);
        createOnAllVersionDatasources(v110Topic, v28Topic, v37Topic, routeKeyTopic, explicitTopic,
                recordTopic, callbackRouteInputTopic, callbackInnerTopic, transactionTopic, rollbackTopic);
        KafkaRouteEndToEndHelper.createTopic(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER),
                clusterTopic, KafkaRouteEndToEndHelper.CLUSTER_TOPIC_PARTITION_COUNT,
                KafkaRouteEndToEndHelper.CLUSTER_TOPIC_REPLICATION_FACTOR);

        assertTopicRouteAcrossBrokerVersions(suffix, v110Topic, v28Topic, v37Topic);
        assertRouteKeyAndExplicitDatasourceIsolation(suffix, routeKeyTopic, explicitTopic);
        assertProducerRecordPreservesHeadersPartitionAndTimestamp(suffix, recordTopic);
        assertCallbackUsesSelectedDatasourceOnly(suffix, callbackRouteInputTopic, callbackInnerTopic);
        assertNativeTransactionWorksWithinSelectedDatasource(suffix, transactionTopic, rollbackTopic);
        assertClusterDatasourceCanSendToExplicitPartitions(suffix, clusterTopic);
    }

    @Test
    public void testDerivedConsumerFactoriesUseIndependentKafkaGroupsAndOwnership() throws Exception {
        String suffix = KafkaRouteEndToEndHelper.suffix();
        String topic = KafkaRouteEndToEndHelper.topicWithSuffix(
                KafkaRouteEndToEndHelper.TOPIC_DERIVED_CONSUMER_PREFIX, suffix);
        String firstGroupId = KafkaRouteEndToEndHelper.E2E_CONSUMER_GROUP_PREFIX + "derived-first-" + suffix;
        String secondGroupId = KafkaRouteEndToEndHelper.E2E_CONSUMER_GROUP_PREFIX + "derived-second-" + suffix;
        KafkaRouteEndToEndHelper.createTopic(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), topic,
                KafkaRouteEndToEndHelper.SINGLE_TOPIC_PARTITION_COUNT,
                KafkaRouteEndToEndHelper.SINGLE_TOPIC_REPLICATION_FACTOR);

        ConsumerFactory<Object, Object> firstFactory = registry.createConsumerFactory(
                KafkaRouteEndToEndHelper.DATASOURCE_V37, KafkaConsumerFactoryOverride.builder()
                        .groupId(firstGroupId)
                        .autoOffsetReset("earliest")
                        .enableAutoCommit(false)
                        .maxPollRecords(1)
                        .build());
        ConsumerFactory<Object, Object> secondFactory = registry.createConsumerFactory(
                KafkaRouteEndToEndHelper.DATASOURCE_V37, KafkaConsumerFactoryOverride.builder()
                        .groupId(secondGroupId)
                        .autoOffsetReset("earliest")
                        .enableAutoCommit(false)
                        .maxPollRecords(1)
                        .build());
        try {
            assertNotSame(firstFactory, secondFactory, "每次派生调用必须返回独立 factory");
            assertEffectiveDerivedFactoryConfiguration(firstFactory, firstGroupId);
            assertEffectiveDerivedFactoryConfiguration(secondFactory, secondGroupId);

            String key = KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix);
            template.sendOn(KafkaRouteEndToEndHelper.DATASOURCE_V37, topic, key,
                    KafkaRouteEndToEndHelper.VALUE_V37).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
            log.info("准备通过两个独立 group 消费派生 factory 消息：topic={}，firstGroup={}，secondGroup={}",
                    topic, firstGroupId, secondGroupId);

            assertDerivedFactoryConsumes(firstFactory, topic, key, KafkaRouteEndToEndHelper.VALUE_V37);
            assertDerivedFactoryConsumes(secondFactory, topic, key, KafkaRouteEndToEndHelper.VALUE_V37);

            KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(firstFactory);
            log.info("首个派生 factory 已由调用方销毁，验证第二个派生 factory 和 registry 基础 factory 仍可用");
            try (Consumer<Object, Object> baseConsumer = registry.getConsumerFactory(
                    KafkaRouteEndToEndHelper.DATASOURCE_V37).createConsumer()) {
                assertNotNull(baseConsumer);
            }
            assertDerivedFactoryConsumes(secondFactory, topic, key, KafkaRouteEndToEndHelper.VALUE_V37);
        } finally {
            KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(firstFactory);
            KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(secondFactory);
        }
    }

    private void assertEffectiveDerivedFactoryConfiguration(ConsumerFactory<Object, Object> factory,
                                                            String expectedGroupId) {
        assertTrue(factory instanceof DefaultKafkaConsumerFactory,
                "默认 route SPI 必须返回 DefaultKafkaConsumerFactory");
        DefaultKafkaConsumerFactory<Object, Object> defaultFactory =
                (DefaultKafkaConsumerFactory<Object, Object>) factory;
        Map<String, Object> properties = defaultFactory.getConfigurationProperties();
        log.info("派生 factory 生效配置：groupId={}，properties={}", expectedGroupId, properties);
        assertEquals(expectedGroupId, properties.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals("earliest", properties.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals(Boolean.FALSE, properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals(1, properties.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
    }

    private void assertDerivedFactoryConsumes(ConsumerFactory<Object, Object> factory, String topic,
                                              String expectedKey, String expectedValue) {
        try (Consumer<Object, Object> consumer = factory.createConsumer()) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + KafkaRouteEndToEndHelper.CONSUME_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<Object, Object> records = consumer.poll(
                        Duration.ofMillis(KafkaRouteEndToEndHelper.CONSUMER_POLL_INTERVAL_MS));
                for (ConsumerRecord<Object, Object> record : records) {
                    if (expectedKey.equals(record.key())) {
                        log.info("派生 factory 消费到消息：topic={}，key={}，value={}",
                                topic, record.key(), record.value());
                        assertEquals(expectedValue, record.value());
                        return;
                    }
                }
            }
        }
        fail(String.format(KafkaRouteEndToEndHelper.ASSERT_RECORD_MISSING_MESSAGE,
                bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), topic));
    }

    private void assertDiagnosticsReady() {
        Map<String, KafkaRouteBrokerDiagnosticResult> results = diagnostics.getDiagnosticResults();
        assertEquals(KafkaRouteEndToEndHelper.EXPECTED_DATASOURCE_COUNT, results.size(),
                KafkaRouteEndToEndHelper.ASSERT_DATASOURCE_COUNT_MESSAGE);
        KafkaRouteBrokerCapability v28Capability = expectedCapabilityForFeatureApiBroker(
                bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28));
        KafkaRouteBrokerCapability v37Capability = expectedCapabilityForFeatureApiBroker(
                bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37));
        KafkaRouteBrokerCapability clusterCapability = expectedCapabilityForFeatureApiBroker(
                bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER));
        assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_DEFAULT,
                KafkaRouteBrokerCapability.UNKNOWN, KafkaRouteDiagnosticStatus.SUCCESS);
        assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_EVENT,
                v37Capability, KafkaRouteDiagnosticStatus.SUCCESS);
        assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_V110,
                KafkaRouteBrokerCapability.UNKNOWN, KafkaRouteDiagnosticStatus.SUCCESS);
        assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_V28, v28Capability, KafkaRouteDiagnosticStatus.SUCCESS);
        assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_V37, v37Capability, KafkaRouteDiagnosticStatus.SUCCESS);
        KafkaRouteDiagnosticStatus tx37Status = v37Capability == KafkaRouteBrokerCapability.SUPPORTED
                ? KafkaRouteDiagnosticStatus.SUCCESS : KafkaRouteDiagnosticStatus.WARN;
        assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_TX37, v37Capability, tx37Status);
        KafkaRouteBrokerDiagnosticResult clusterResult = assertDiagnosticResult(results, KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER, clusterCapability,
                KafkaRouteDiagnosticStatus.SUCCESS);
        assertTrue(clusterResult.getNodeCount() >= KafkaRouteEndToEndHelper.CLUSTER_MIN_NODE_COUNT,
                KafkaRouteEndToEndHelper.ASSERT_CLUSTER_NODE_COUNT_MESSAGE);
    }

    private KafkaRouteBrokerCapability expectedCapabilityForFeatureApiBroker(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, bootstrapServers);
        Object adminClient = null;
        try {
            adminClient = AdminClient.create(properties);
            Object features = KafkaAdminCompatibilityHelper.describeFeaturesIfAvailable(adminClient,
                    KafkaRouteEndToEndHelper.FEATURE_API_PROBE_TIMEOUT_MS);
            return features == null ? KafkaRouteBrokerCapability.UNKNOWN : KafkaRouteBrokerCapability.SUPPORTED;
        } catch (RuntimeException e) {
            return KafkaRouteBrokerCapability.UNKNOWN;
        } finally {
            KafkaAdminCompatibilityHelper.closeAdminClient(adminClient);
        }
    }

    private void assertCapabilities(KafkaRouteBrokerDiagnosticResult result,
                                    KafkaRouteBrokerCapability expectedFeatureCapability,
                                    String datasourceKey) {
        assertEquals(KafkaRouteBrokerCapability.SUPPORTED, result.getAdminApiLevel(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_ADMIN_API_CAPABILITY_MESSAGE, datasourceKey));
        assertEquals(expectedFeatureCapability, result.getTransactionSupported(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_TRANSACTION_CAPABILITY_MESSAGE, datasourceKey));
        assertEquals(expectedFeatureCapability, result.getIdempotenceSupported(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_IDEMPOTENCE_CAPABILITY_MESSAGE, datasourceKey));
        assertEquals(expectedFeatureCapability, result.getZstdSupported(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_ZSTD_CAPABILITY_MESSAGE, datasourceKey));
    }

    private KafkaRouteBrokerDiagnosticResult assertDiagnosticResult(
            Map<String, KafkaRouteBrokerDiagnosticResult> results,
            String datasourceKey,
            KafkaRouteBrokerCapability expectedFeatureCapability,
            KafkaRouteDiagnosticStatus expectedStatus) {
        KafkaRouteBrokerDiagnosticResult result = results.get(datasourceKey);
        assertNotNull(result, String.format(KafkaRouteEndToEndHelper.ASSERT_DIAGNOSTIC_MISSING_MESSAGE, datasourceKey));
        assertEquals(expectedStatus, result.getStatus(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_DIAGNOSTIC_STATUS_MESSAGE, datasourceKey));
        assertTrue(result.getNodeCount() > KafkaRouteEndToEndHelper.MIN_NODE_COUNT,
                String.format(KafkaRouteEndToEndHelper.ASSERT_NODE_COUNT_MESSAGE, datasourceKey));
        assertTrue(result.isControllerVisible(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_CONTROLLER_VISIBLE_MESSAGE, datasourceKey));
        assertNull(result.getFailureReason(),
                String.format(KafkaRouteEndToEndHelper.ASSERT_FAILURE_REASON_MESSAGE, datasourceKey));
        assertCapabilities(result, expectedFeatureCapability, datasourceKey);
        return result;
    }

    private void assertTopicRouteAcrossBrokerVersions(String suffix, String v110Topic,
                                                      String v28Topic, String v37Topic) throws Exception {
        SendResult<String, String> v110Result = template.send(v110Topic, KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix),
                KafkaRouteEndToEndHelper.VALUE_V110).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SendResult<String, String> v28Result = template.send(v28Topic, KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix),
                KafkaRouteEndToEndHelper.VALUE_V28).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SendResult<String, String> v37Result = template.send(v37Topic, KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix),
                KafkaRouteEndToEndHelper.VALUE_V37).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info(KafkaRouteEndToEndHelper.LOG_MULTI_VERSION_METADATA_MESSAGE, v110Result.getRecordMetadata(),
                v28Result.getRecordMetadata(), v37Result.getRecordMetadata());
        assertEquals(v110Topic, v110Result.getRecordMetadata().topic());
        assertEquals(v28Topic, v28Result.getRecordMetadata().topic());
        assertEquals(v37Topic, v37Result.getRecordMetadata().topic());
        assertSame(template.kafkaTemplate(KafkaRouteEndToEndHelper.DATASOURCE_V110), template.kafkaTemplateByTopic(v110Topic));
        assertSame(template.kafkaTemplate(KafkaRouteEndToEndHelper.DATASOURCE_V28), template.kafkaTemplateByTopic(v28Topic));
        assertSame(template.kafkaTemplate(KafkaRouteEndToEndHelper.DATASOURCE_V37), template.kafkaTemplateByTopic(v37Topic));

        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), v110Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_V110);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), v110Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix));
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), v110Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix));

        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), v28Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_V28);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), v28Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix));
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), v28Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix));

        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), v37Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_V37);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), v37Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix));
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), v37Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix));
    }

    private void assertRouteKeyAndExplicitDatasourceIsolation(String suffix, String routeKeyTopic,
                                                              String explicitTopic) throws Exception {
        SendResult<String, String> routeKeyResult = template.sendByRouteKey(KafkaRouteEndToEndHelper.ROUTE_KEY_V37, routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_ROUTE).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SendResult<String, String> explicitResult = template.sendOn(KafkaRouteEndToEndHelper.DATASOURCE_V28, explicitTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_EXPLICIT_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_EXPLICIT).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(routeKeyTopic, routeKeyResult.getRecordMetadata().topic());
        assertEquals(explicitTopic, explicitResult.getRecordMetadata().topic());
        assertSame(template.kafkaTemplate(KafkaRouteEndToEndHelper.DATASOURCE_V37), template.kafkaTemplateByRouteKey(KafkaRouteEndToEndHelper.ROUTE_KEY_V37));

        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_ROUTE);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix));
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix));

        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), explicitTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_EXPLICIT_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_EXPLICIT);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), explicitTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_EXPLICIT_PREFIX, suffix));
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), explicitTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_EXPLICIT_PREFIX, suffix));
    }

    private void assertProducerRecordPreservesHeadersPartitionAndTimestamp(String suffix,
                                                                           String recordTopic) throws Exception {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(recordTopic,
                KafkaRouteEndToEndHelper.PARTITION_ZERO, KafkaRouteEndToEndHelper.RECORD_TIMESTAMP,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_RECORD_PREFIX, suffix),
                KafkaRouteEndToEndHelper.VALUE_RECORD,
                Collections.singletonList(new RecordHeader(KafkaRouteEndToEndHelper.HEADER_NAME,
                        KafkaRouteEndToEndHelper.HEADER_VALUE_BYTES)));

        SendResult<String, String> result = template.send(producerRecord).get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(recordTopic, result.getRecordMetadata().topic());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ZERO, result.getRecordMetadata().partition());
        ConsumerRecord<String, String> record = assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37),
                recordTopic, KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_RECORD_PREFIX, suffix),
                KafkaRouteEndToEndHelper.VALUE_RECORD);
        assertEquals(KafkaRouteEndToEndHelper.RECORD_TIMESTAMP, record.timestamp());
        Header header = record.headers().lastHeader(KafkaRouteEndToEndHelper.HEADER_NAME);
        assertNotNull(header);
        assertArrayEquals(KafkaRouteEndToEndHelper.HEADER_VALUE_BYTES, header.value());
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), recordTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_RECORD_PREFIX, suffix));
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), recordTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_RECORD_PREFIX, suffix));
    }

    private void assertCallbackUsesSelectedDatasourceOnly(String suffix, String routeInputTopic,
                                                          String innerTopic) throws Exception {
        String callbackKey = KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_CALLBACK_PREFIX, suffix);
        String selectedDatasource = template.execute(routeInputTopic, kafkaTemplate -> {
            assertSame(template.kafkaTemplate(KafkaRouteEndToEndHelper.DATASOURCE_V28), kafkaTemplate);
            try {
                kafkaTemplate.send(innerTopic, callbackKey, KafkaRouteEndToEndHelper.VALUE_CALLBACK)
                        .get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(KafkaRouteEndToEndHelper.CALLBACK_SEND_FAILED_MESSAGE, e);
            }
            return KafkaRouteEndToEndHelper.DATASOURCE_V28;
        });

        assertEquals(KafkaRouteEndToEndHelper.DATASOURCE_V28, selectedDatasource);
        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), innerTopic,
                callbackKey, KafkaRouteEndToEndHelper.VALUE_CALLBACK);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), innerTopic,
                callbackKey);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), innerTopic,
                callbackKey);
    }

    private void assertNativeTransactionWorksWithinSelectedDatasource(String suffix,
                                                                      String transactionTopic,
                                                                      String rollbackTopic) throws Exception {
        String transactionKey = KafkaRouteEndToEndHelper.keyWithSuffix(
                KafkaRouteEndToEndHelper.KEY_TRANSACTION_PREFIX, suffix);
        String rollbackKey = KafkaRouteEndToEndHelper.keyWithSuffix(
                KafkaRouteEndToEndHelper.KEY_ROLLBACK_PREFIX, suffix);
        Boolean result = template.executeOn(KafkaRouteEndToEndHelper.DATASOURCE_TX37, kafkaTemplate -> kafkaTemplate.executeInTransaction(operations -> {
            operations.send(transactionTopic, transactionKey, KafkaRouteEndToEndHelper.VALUE_TRANSACTION);
            return Boolean.TRUE;
        }));

        assertEquals(Boolean.TRUE, result);
        assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), transactionTopic,
                transactionKey, KafkaRouteEndToEndHelper.VALUE_TRANSACTION);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), transactionTopic,
                transactionKey);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), transactionTopic,
                transactionKey);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> template.executeOn(KafkaRouteEndToEndHelper.DATASOURCE_TX37, kafkaTemplate -> kafkaTemplate.executeInTransaction(operations -> {
                    operations.send(rollbackTopic, rollbackKey, KafkaRouteEndToEndHelper.VALUE_ROLLBACK);
                    throw new IllegalStateException(KafkaRouteEndToEndHelper.TX_ROLLBACK_MESSAGE);
                })));
        assertEquals(KafkaRouteEndToEndHelper.TX_ROLLBACK_MESSAGE, exception.getMessage());
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37), rollbackTopic,
                rollbackKey);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110), rollbackTopic,
                rollbackKey);
        assertNoRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28), rollbackTopic,
                rollbackKey);
    }

    private void assertClusterDatasourceCanSendToExplicitPartitions(String suffix, String clusterTopic) throws Exception {
        SendResult<String, String> first = template.send(clusterTopic, KafkaRouteEndToEndHelper.PARTITION_ZERO,
                        KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_ZERO, suffix),
                        KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_ZERO))
                .get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SendResult<String, String> second = template.send(clusterTopic, KafkaRouteEndToEndHelper.PARTITION_ONE,
                        KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_ONE, suffix),
                        KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_ONE))
                .get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SendResult<String, String> third = template.send(clusterTopic, KafkaRouteEndToEndHelper.PARTITION_TWO,
                        KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_TWO, suffix),
                        KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_TWO))
                .get(KafkaRouteEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(clusterTopic, first.getRecordMetadata().topic());
        assertEquals(clusterTopic, second.getRecordMetadata().topic());
        assertEquals(clusterTopic, third.getRecordMetadata().topic());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ZERO, first.getRecordMetadata().partition());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ONE, second.getRecordMetadata().partition());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_TWO, third.getRecordMetadata().partition());
        assertSame(template.kafkaTemplate(KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER), template.kafkaTemplateByTopic(clusterTopic));

        ConsumerRecord<String, String> firstRecord = assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER),
                clusterTopic, KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_ZERO, suffix),
                KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_ZERO));
        ConsumerRecord<String, String> secondRecord = assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER),
                clusterTopic, KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_ONE, suffix),
                KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_ONE));
        ConsumerRecord<String, String> thirdRecord = assertRecord(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_CLUSTER),
                clusterTopic, KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_TWO, suffix),
                KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_TWO));
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ZERO, firstRecord.partition());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ONE, secondRecord.partition());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_TWO, thirdRecord.partition());
    }

    private void createOnAllVersionDatasources(String... topics) {
        for (String topic : topics) {
            KafkaRouteEndToEndHelper.createTopic(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V110),
                    topic, KafkaRouteEndToEndHelper.SINGLE_TOPIC_PARTITION_COUNT,
                    KafkaRouteEndToEndHelper.SINGLE_TOPIC_REPLICATION_FACTOR);
            KafkaRouteEndToEndHelper.createTopic(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V28),
                    topic, KafkaRouteEndToEndHelper.SINGLE_TOPIC_PARTITION_COUNT,
                    KafkaRouteEndToEndHelper.SINGLE_TOPIC_REPLICATION_FACTOR);
            KafkaRouteEndToEndHelper.createTopic(bootstrapServers(KafkaRouteEndToEndHelper.DATASOURCE_V37),
                    topic, KafkaRouteEndToEndHelper.SINGLE_TOPIC_PARTITION_COUNT,
                    KafkaRouteEndToEndHelper.SINGLE_TOPIC_REPLICATION_FACTOR);
        }
    }

    private ConsumerRecord<String, String> assertRecord(String bootstrapServers, String topic,
                                                        String expectedKey, String expectedValue) {
        ConsumerRecord<String, String> record = KafkaRouteEndToEndHelper.consumeRecord(bootstrapServers, topic,
                expectedKey, KafkaRouteEndToEndHelper.CONSUME_TIMEOUT_MS);
        assertNotNull(record, String.format(KafkaRouteEndToEndHelper.ASSERT_RECORD_MISSING_MESSAGE,
                bootstrapServers, topic));
        assertEquals(expectedKey, record.key());
        assertEquals(expectedValue, record.value());
        return record;
    }

    private void assertNoRecord(String bootstrapServers, String topic, String expectedKey) {
        ConsumerRecord<String, String> record = KafkaRouteEndToEndHelper.consumeRecord(bootstrapServers, topic,
                expectedKey, KafkaRouteEndToEndHelper.NO_MESSAGE_TIMEOUT_MS);
        assertNull(record, String.format(KafkaRouteEndToEndHelper.ASSERT_RECORD_UNEXPECTED_MESSAGE,
                bootstrapServers, topic));
    }

    private String bootstrapServers(String datasourceKey) {
        SimpleKafkaRouteProperties.DataSourceConfig source = routeProperties.getSources().get(datasourceKey);
        if (source == null) {
            throw new IllegalStateException("缺少 Kafka Route datasource 配置: " + datasourceKey);
        }
        List<String> servers = source.getBootstrapServers();
        if (servers == null || servers.isEmpty()) {
            throw new IllegalStateException("Kafka Route datasource 未配置 bootstrap servers: " + datasourceKey);
        }
        return String.join(",", servers);
    }
}
