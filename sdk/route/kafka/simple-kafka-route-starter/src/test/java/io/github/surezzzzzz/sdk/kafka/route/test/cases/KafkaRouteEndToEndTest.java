package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.test.KafkaEndToEndProfilesResolver;
import io.github.surezzzzzz.sdk.kafka.route.test.SimpleKafkaRouteTestApplication;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteEndToEndHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 大而全端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfSystemProperty(named = "kafka.route.e2e.test", matches = "true")
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
        String v110Topic = "mock.route.v110.e2e." + suffix;
        String v28Topic = "event.v28.route.e2e." + suffix;
        String v37Topic = "event.v37.route.e2e." + suffix;
        String routeKeyTopic = "mock.route.key.e2e." + suffix;
        String explicitTopic = "event.v37.explicit.e2e." + suffix;
        String recordTopic = "event.v37.record.e2e." + suffix;
        String callbackRouteInputTopic = "event.v28.callback.route.e2e." + suffix;
        String callbackInnerTopic = "event.v37.callback.inner.e2e." + suffix;
        String transactionTopic = "mock.route.tx37.e2e." + suffix;
        String rollbackTopic = "mock.route.tx37.rollback.e2e." + suffix;
        String clusterTopic = "cluster.route.e2e." + suffix;
        createOnAllVersionDatasources(v110Topic, v28Topic, v37Topic, routeKeyTopic, explicitTopic,
                recordTopic, callbackRouteInputTopic, callbackInnerTopic, transactionTopic, rollbackTopic);
        KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, 3, (short) 3);

        assertTopicRouteAcrossBrokerVersions(suffix, v110Topic, v28Topic, v37Topic);
        assertRouteKeyAndExplicitDatasourceIsolation(suffix, routeKeyTopic, explicitTopic);
        assertProducerRecordPreservesHeadersPartitionAndTimestamp(suffix, recordTopic);
        assertCallbackUsesSelectedDatasourceOnly(suffix, callbackRouteInputTopic, callbackInnerTopic);
        assertNativeTransactionWorksWithinSelectedDatasource(suffix, transactionTopic, rollbackTopic);
        assertClusterDatasourceCanSendToExplicitPartitions(suffix, clusterTopic);
    }

    private void assertDiagnosticsReady() {
        Map<String, KafkaRouteBrokerDiagnosticResult> results = diagnostics.getDiagnosticResults();
        assertDiagnosticSuccess(results, "default");
        assertDiagnosticSuccess(results, "event");
        assertDiagnosticSuccess(results, "v110");
        assertDiagnosticSuccess(results, "v28");
        assertDiagnosticSuccess(results, "v37");
        assertDiagnosticSuccess(results, "tx37");
        KafkaRouteBrokerDiagnosticResult clusterResult = assertDiagnosticSuccess(results, "cluster");
        assertTrue(clusterResult.getNodeCount() >= 3, "cluster 诊断应看到至少 3 个 broker");
    }

    private KafkaRouteBrokerDiagnosticResult assertDiagnosticSuccess(
            Map<String, KafkaRouteBrokerDiagnosticResult> results, String datasourceKey) {
        KafkaRouteBrokerDiagnosticResult result = results.get(datasourceKey);
        assertNotNull(result, "未获取到 datasource 诊断结果: " + datasourceKey);
        assertTrue(result.getStatus() == KafkaRouteDiagnosticStatus.SUCCESS
                        || result.getStatus() == KafkaRouteDiagnosticStatus.WARN,
                "datasource 诊断必须成功或仅 capability 告警: " + datasourceKey);
        assertTrue(result.getNodeCount() > 0, "datasource 诊断应看到 broker 节点: " + datasourceKey);
        assertTrue(result.isControllerVisible(), "datasource 诊断应看到 controller: " + datasourceKey);
        assertNull(result.getFailureReason(), "datasource 成功诊断不应带失败原因: " + datasourceKey);
        return result;
    }

    private void assertTopicRouteAcrossBrokerVersions(String suffix, String v110Topic,
                                                       String v28Topic, String v37Topic) throws Exception {
        SendResult<String, String> v110Result = template.send(v110Topic, "v110-key-" + suffix,
                "v110-value").get(30, TimeUnit.SECONDS);
        SendResult<String, String> v28Result = template.send(v28Topic, "v28-key-" + suffix,
                "v28-value").get(30, TimeUnit.SECONDS);
        SendResult<String, String> v37Result = template.send(v37Topic, "v37-key-" + suffix,
                "v37-value").get(30, TimeUnit.SECONDS);

        log.info("多版本发送 metadata: v110={}, v28={}, v37={}", v110Result.getRecordMetadata(),
                v28Result.getRecordMetadata(), v37Result.getRecordMetadata());
        assertEquals(v110Topic, v110Result.getRecordMetadata().topic());
        assertEquals(v28Topic, v28Result.getRecordMetadata().topic());
        assertEquals(v37Topic, v37Result.getRecordMetadata().topic());
        assertSame(template.kafkaTemplate("v110"), template.kafkaTemplateByTopic(v110Topic));
        assertSame(template.kafkaTemplate("v28"), template.kafkaTemplateByTopic(v28Topic));
        assertSame(template.kafkaTemplate("v37"), template.kafkaTemplateByTopic(v37Topic));

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, v110Topic,
                "v110-key-" + suffix, "v110-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, v110Topic,
                "v110-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, v110Topic,
                "v110-key-" + suffix);

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, v28Topic,
                "v28-key-" + suffix, "v28-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, v28Topic,
                "v28-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, v28Topic,
                "v28-key-" + suffix);

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, v37Topic,
                "v37-key-" + suffix, "v37-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, v37Topic,
                "v37-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, v37Topic,
                "v37-key-" + suffix);
    }

    private void assertRouteKeyAndExplicitDatasourceIsolation(String suffix, String routeKeyTopic,
                                                               String explicitTopic) throws Exception {
        SendResult<String, String> routeKeyResult = template.sendByRouteKey("tenant-v37-a", routeKeyTopic,
                "route-key-" + suffix, "route-key-value").get(30, TimeUnit.SECONDS);
        SendResult<String, String> explicitResult = template.sendOn("v28", explicitTopic,
                "explicit-key-" + suffix, "explicit-value").get(30, TimeUnit.SECONDS);

        assertEquals(routeKeyTopic, routeKeyResult.getRecordMetadata().topic());
        assertEquals(explicitTopic, explicitResult.getRecordMetadata().topic());
        assertSame(template.kafkaTemplate("v37"), template.kafkaTemplateByRouteKey("tenant-v37-a"));

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, routeKeyTopic,
                "route-key-" + suffix, "route-key-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, routeKeyTopic,
                "route-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, routeKeyTopic,
                "route-key-" + suffix);

        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, explicitTopic,
                "explicit-key-" + suffix, "explicit-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, explicitTopic,
                "explicit-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, explicitTopic,
                "explicit-key-" + suffix);
    }

    private void assertProducerRecordPreservesHeadersPartitionAndTimestamp(String suffix,
                                                                           String recordTopic) throws Exception {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(recordTopic, 0, 1000L,
                "record-key-" + suffix, "record-value",
                Collections.singletonList(new RecordHeader("mock-header", "mock-header-value".getBytes("UTF-8"))));

        SendResult<String, String> result = template.send(producerRecord).get(30, TimeUnit.SECONDS);

        assertEquals(recordTopic, result.getRecordMetadata().topic());
        assertEquals(0, result.getRecordMetadata().partition());
        ConsumerRecord<String, String> record = assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS,
                recordTopic, "record-key-" + suffix, "record-value");
        assertEquals(1000L, record.timestamp());
        Header header = record.headers().lastHeader("mock-header");
        assertNotNull(header);
        assertArrayEquals("mock-header-value".getBytes("UTF-8"), header.value());
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, recordTopic,
                "record-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, recordTopic,
                "record-key-" + suffix);
    }

    private void assertCallbackUsesSelectedDatasourceOnly(String suffix, String routeInputTopic,
                                                           String innerTopic) throws Exception {
        String selectedDatasource = template.execute(routeInputTopic, kafkaTemplate -> {
            assertSame(template.kafkaTemplate("v28"), kafkaTemplate);
            try {
                kafkaTemplate.send(innerTopic, "callback-key-" + suffix, "callback-value")
                        .get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("callback 内部发送失败", e);
            }
            return "v28";
        });

        assertEquals("v28", selectedDatasource);
        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, innerTopic,
                "callback-key-" + suffix, "callback-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, innerTopic,
                "callback-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, innerTopic,
                "callback-key-" + suffix);
    }

    private void assertNativeTransactionWorksWithinSelectedDatasource(String suffix,
                                                                       String transactionTopic,
                                                                       String rollbackTopic) throws Exception {
        Boolean result = template.executeOn("tx37", kafkaTemplate -> kafkaTemplate.executeInTransaction(operations -> {
            operations.send(transactionTopic, "tx-key-" + suffix, "tx-value");
            return Boolean.TRUE;
        }));

        assertEquals(Boolean.TRUE, result);
        assertRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, transactionTopic,
                "tx-key-" + suffix, "tx-value");
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, transactionTopic,
                "tx-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, transactionTopic,
                "tx-key-" + suffix);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> template.executeOn("tx37", kafkaTemplate -> kafkaTemplate.executeInTransaction(operations -> {
                    operations.send(rollbackTopic, "tx-rollback-key-" + suffix, "tx-rollback-value");
                    throw new IllegalStateException("mock transaction rollback");
                })));
        assertEquals("mock transaction rollback", exception.getMessage());
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS, rollbackTopic,
                "tx-rollback-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS, rollbackTopic,
                "tx-rollback-key-" + suffix);
        assertNoRecord(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS, rollbackTopic,
                "tx-rollback-key-" + suffix);
    }

    private void assertClusterDatasourceCanSendToExplicitPartitions(String suffix, String clusterTopic) throws Exception {
        SendResult<String, String> first = template.send(clusterTopic, 0, "cluster-key-0-" + suffix,
                "cluster-value-0").get(30, TimeUnit.SECONDS);
        SendResult<String, String> second = template.send(clusterTopic, 1, "cluster-key-1-" + suffix,
                "cluster-value-1").get(30, TimeUnit.SECONDS);
        SendResult<String, String> third = template.send(clusterTopic, 2, "cluster-key-2-" + suffix,
                "cluster-value-2").get(30, TimeUnit.SECONDS);

        assertEquals(clusterTopic, first.getRecordMetadata().topic());
        assertEquals(clusterTopic, second.getRecordMetadata().topic());
        assertEquals(clusterTopic, third.getRecordMetadata().topic());
        assertEquals(0, first.getRecordMetadata().partition());
        assertEquals(1, second.getRecordMetadata().partition());
        assertEquals(2, third.getRecordMetadata().partition());
        assertSame(template.kafkaTemplate("cluster"), template.kafkaTemplateByTopic(clusterTopic));

        ConsumerRecord<String, String> firstRecord = assertRecord(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, "cluster-key-0-" + suffix, "cluster-value-0");
        ConsumerRecord<String, String> secondRecord = assertRecord(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, "cluster-key-1-" + suffix, "cluster-value-1");
        ConsumerRecord<String, String> thirdRecord = assertRecord(KafkaRouteEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                clusterTopic, "cluster-key-2-" + suffix, "cluster-value-2");
        assertEquals(0, firstRecord.partition());
        assertEquals(1, secondRecord.partition());
        assertEquals(2, thirdRecord.partition());
    }

    private void createOnAllVersionDatasources(String... topics) {
        for (String topic : topics) {
            KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.KAFKA_V110_BOOTSTRAP_SERVERS,
                    topic, 1, (short) 1);
            KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.KAFKA_V28_BOOTSTRAP_SERVERS,
                    topic, 1, (short) 1);
            KafkaRouteEndToEndHelper.createTopic(KafkaRouteEndToEndHelper.KAFKA_V37_BOOTSTRAP_SERVERS,
                    topic, 1, (short) 1);
        }
    }

    private ConsumerRecord<String, String> assertRecord(String bootstrapServers, String topic,
                                                        String expectedKey, String expectedValue) {
        ConsumerRecord<String, String> record = KafkaRouteEndToEndHelper.consumeRecord(bootstrapServers, topic,
                expectedKey, KafkaRouteEndToEndHelper.CONSUME_TIMEOUT_MS);
        assertNotNull(record, "未在指定 Kafka datasource 消费到消息，bootstrapServers=" + bootstrapServers + ", topic=" + topic);
        assertEquals(expectedKey, record.key());
        assertEquals(expectedValue, record.value());
        return record;
    }

    private void assertNoRecord(String bootstrapServers, String topic, String expectedKey) {
        ConsumerRecord<String, String> record = KafkaRouteEndToEndHelper.consumeRecord(bootstrapServers, topic,
                expectedKey, KafkaRouteEndToEndHelper.NO_MESSAGE_TIMEOUT_MS);
        assertNull(record, "不应在该 Kafka datasource 消费到消息，bootstrapServers=" + bootstrapServers + ", topic=" + topic);
    }
}
