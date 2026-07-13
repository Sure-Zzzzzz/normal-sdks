package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerCapability;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaAdminCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.test.KafkaEndToEndProfilesResolver;
import io.github.surezzzzzz.sdk.kafka.route.test.SimpleKafkaRouteTestApplication;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteEndToEndHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
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
        KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, KafkaRouteEndToEndHelper.CLUSTER_TOPIC_PARTITION_COUNT,
                KafkaRouteEndToEndHelper.CLUSTER_TOPIC_REPLICATION_FACTOR);

        assertTopicRouteAcrossBrokerVersions(suffix, v110Topic, v28Topic, v37Topic);
        assertRouteKeyAndExplicitDatasourceIsolation(suffix, routeKeyTopic, explicitTopic);
        assertProducerRecordPreservesHeadersPartitionAndTimestamp(suffix, recordTopic);
        assertCallbackUsesSelectedDatasourceOnly(suffix, callbackRouteInputTopic, callbackInnerTopic);
        assertNativeTransactionWorksWithinSelectedDatasource(suffix, transactionTopic, rollbackTopic);
        assertClusterDatasourceCanSendToExplicitPartitions(suffix, clusterTopic);
    }

    private void assertDiagnosticsReady() {
        Map<String, KafkaRouteBrokerDiagnosticResult> results = diagnostics.getDiagnosticResults();
        assertEquals(KafkaRouteEndToEndHelper.EXPECTED_DATASOURCE_COUNT, results.size(),
                KafkaRouteEndToEndHelper.ASSERT_DATASOURCE_COUNT_MESSAGE);
        KafkaRouteBrokerCapability v28Capability = expectedCapabilityForFeatureApiBroker(
                KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS);
        KafkaRouteBrokerCapability v37Capability = expectedCapabilityForFeatureApiBroker(
                KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS);
        KafkaRouteBrokerCapability clusterCapability = expectedCapabilityForFeatureApiBroker(
                KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS);
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

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, v110Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_V110);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, v110Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix));
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, v110Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V110_PREFIX, suffix));

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, v28Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_V28);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, v28Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix));
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, v28Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V28_PREFIX, suffix));

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, v37Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_V37);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, v37Topic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_V37_PREFIX, suffix));
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, v37Topic,
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

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_ROUTE);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix));
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, routeKeyTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_ROUTE_PREFIX, suffix));

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, explicitTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_EXPLICIT_PREFIX, suffix), KafkaRouteEndToEndHelper.VALUE_EXPLICIT);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, explicitTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_EXPLICIT_PREFIX, suffix));
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, explicitTopic,
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
        ConsumerRecord<String, String> record = assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS,
                recordTopic, KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_RECORD_PREFIX, suffix),
                KafkaRouteEndToEndHelper.VALUE_RECORD);
        assertEquals(KafkaRouteEndToEndHelper.RECORD_TIMESTAMP, record.timestamp());
        Header header = record.headers().lastHeader(KafkaRouteEndToEndHelper.HEADER_NAME);
        assertNotNull(header);
        assertArrayEquals(KafkaRouteEndToEndHelper.HEADER_VALUE_BYTES, header.value());
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, recordTopic,
                KafkaRouteEndToEndHelper.keyWithSuffix(KafkaRouteEndToEndHelper.KEY_RECORD_PREFIX, suffix));
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, recordTopic,
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
        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, innerTopic,
                callbackKey, KafkaRouteEndToEndHelper.VALUE_CALLBACK);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, innerTopic,
                callbackKey);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, innerTopic,
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
        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, transactionTopic,
                transactionKey, KafkaRouteEndToEndHelper.VALUE_TRANSACTION);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, transactionTopic,
                transactionKey);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, transactionTopic,
                transactionKey);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> template.executeOn(KafkaRouteEndToEndHelper.DATASOURCE_TX37, kafkaTemplate -> kafkaTemplate.executeInTransaction(operations -> {
                    operations.send(rollbackTopic, rollbackKey, KafkaRouteEndToEndHelper.VALUE_ROLLBACK);
                    throw new IllegalStateException(KafkaRouteEndToEndHelper.TX_ROLLBACK_MESSAGE);
                })));
        assertEquals(KafkaRouteEndToEndHelper.TX_ROLLBACK_MESSAGE, exception.getMessage());
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, rollbackTopic,
                rollbackKey);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, rollbackTopic,
                rollbackKey);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, rollbackTopic,
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

        ConsumerRecord<String, String> firstRecord = assertRecord(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_ZERO, suffix),
                KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_ZERO));
        ConsumerRecord<String, String> secondRecord = assertRecord(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_ONE, suffix),
                KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_ONE));
        ConsumerRecord<String, String> thirdRecord = assertRecord(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, KafkaRouteEndToEndHelper.clusterKey(KafkaRouteEndToEndHelper.PARTITION_TWO, suffix),
                KafkaRouteEndToEndHelper.clusterValue(KafkaRouteEndToEndHelper.PARTITION_TWO));
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ZERO, firstRecord.partition());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_ONE, secondRecord.partition());
        assertEquals(KafkaRouteEndToEndHelper.PARTITION_TWO, thirdRecord.partition());
    }

    private void createOnAllVersionDatasources(String... topics) {
        for (String topic : topics) {
            KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS,
                    topic, KafkaRouteEndToEndHelper.SINGLE_TOPIC_PARTITION_COUNT,
                    KafkaRouteEndToEndHelper.SINGLE_TOPIC_REPLICATION_FACTOR);
            KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS,
                    topic, KafkaRouteEndToEndHelper.SINGLE_TOPIC_PARTITION_COUNT,
                    KafkaRouteEndToEndHelper.SINGLE_TOPIC_REPLICATION_FACTOR);
            KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS,
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
}
