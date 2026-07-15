package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.SimpleKafkaPublisherTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherEndToEndHelper;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kafka Publisher 大而全端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleKafkaPublisherTestApplication.class)
public class KafkaPublisherEndToEndTest {

    @Autowired
    private KafkaPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testPublishAcrossKafkaVersionsAndCluster() throws Exception {
        String suffix = KafkaPublisherEndToEndHelper.suffix();
        String topicV110 = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V110_PREFIX, suffix);
        String topicV28 = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V28_PREFIX, suffix);
        String topicV37Route = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V37_ROUTE_PREFIX, suffix);
        String topicCluster = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_CLUSTER_PREFIX, suffix);
        createTopics(topicV110, topicV28, topicV37Route, topicCluster);

        assertTopicPublisherEnvelopeAndHeaders(suffix, topicV110);
        assertExplicitDatasourceIsolation(suffix, topicV28);
        assertRouteKeyIsolation(suffix, topicV37Route);
        assertClusterPartitions(suffix, topicCluster);
    }

    @Test
    public void testPublishAndWaitAgainstRealBroker() throws Exception {
        String suffix = KafkaPublisherEndToEndHelper.suffix();
        String topic = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V110_PREFIX, suffix);
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-wait");
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS,
                topic, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        KafkaPublishMessage<String> message = message(topic, key, "message-wait-" + suffix);

        KafkaPublishResult result = publisher.publishAndWait(message);

        ConsumerRecord<String, String> record = assertRecord(
                KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS, topic, key);
        log.info("publishAndWait 真实 broker 结果: {}", result);
        assertEquals(topic, result.getTopic(), "同步结果 topic 应来自 broker metadata");
        assertEquals(key, record.key(), "消费记录 key 应精确一致");
        assertEquals(key, result.getKey(), "结果 key 应回填");
        assertEquals(KafkaPublisherEndToEndHelper.PARTITION_ZERO, result.getPartition(),
                "单分区 topic 结果 partition 应为 0");
        assertEquals(record.partition(), result.getPartition(),
                "结果 partition 应与消费记录一致");
        assertNotNull(result.getOffset(), "同步结果 offset 应非空");
        assertNotNull(result.getTimestamp(), "同步结果 timestamp 应非空");
    }

    @Test
    public void testObjectPayloadEnvelopeConsumedAndReconstructed() throws Exception {
        String suffix = KafkaPublisherEndToEndHelper.suffix();
        String topic = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V28_PREFIX, suffix);
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-obj");
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V28_BOOTSTRAP_SERVERS,
                topic, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        MockE2ePayload payload = new MockE2ePayload("mock-field-value", 42);
        KafkaPublishMessage<MockE2ePayload> message = KafkaPublishMessage.<MockE2ePayload>builder()
                .topic(topic)
                .key(key)
                .messageId("message-obj-" + suffix)
                .messageType(KafkaPublisherEndToEndHelper.MESSAGE_TYPE)
                .payload(payload)
                .build();

        publisher.publish(message).get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = assertRecord(
                KafkaPublisherEndToEndHelper.V28_BOOTSTRAP_SERVERS, topic, key);
        JsonNode envelope = objectMapper.readTree(record.value());

        log.info("对象 payload envelope: {}", record.value());
        assertEquals("mock-field-value", envelope.get("payload").get("mockField").asText(),
                "对象 payload 字段应精确序列化进 envelope");
        assertEquals(42, envelope.get("payload").get("mockCount").asInt(),
                "对象 payload 数值字段应精确序列化进 envelope");
        MockE2ePayload reconstructed = objectMapper.treeToValue(envelope.get("payload"),
                MockE2ePayload.class);
        assertEquals(payload, reconstructed, "消费端应能从 envelope 还原对象 payload");
    }

    @Test
    public void testStringPassthroughConsumedExactly() throws Exception {
        String suffix = KafkaPublisherEndToEndHelper.suffix();
        String topic = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V37_ROUTE_PREFIX, suffix);
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-raw");
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS,
                topic, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        String rawPayload = "raw-string-payload-模拟-✓";
        KafkaPublishMessage<String> message = message(topic, key, "message-raw-" + suffix);
        message.setPayload(rawPayload);
        message.setEnvelopeEnabled(false);

        publisher.publish(message).get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = assertRecord(
                KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS, topic, key);

        log.info("String passthrough 消费值: {}", record.value());
        assertEquals(rawPayload, record.value(),
                "envelope 关闭时 String payload 应原样到达消费端，不额外 JSON quote");
    }

    @Test
    public void testTimestampPreservedThroughBroker() throws Exception {
        String suffix = KafkaPublisherEndToEndHelper.suffix();
        String topic = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V110_PREFIX, suffix);
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-ts");
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS,
                topic, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        long expectedTimestamp = 1700000000000L;
        KafkaPublishMessage<String> message = message(topic, key, "message-ts-" + suffix);
        message.setTimestamp(expectedTimestamp);

        publisher.publish(message).get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = assertRecord(
                KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS, topic, key);

        log.info("broker 消费 timestamp: {}", record.timestamp());
        assertEquals(expectedTimestamp, record.timestamp(),
                "ProducerRecord timestamp 应作为 CREATE_TIME 保留到消费端");
    }

    private void assertTopicPublisherEnvelopeAndHeaders(String suffix, String topic) throws Exception {
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-v110");
        KafkaPublishMessage<String> message = message(topic, key, "message-v110-" + suffix);
        message.setHeaders(Collections.singletonMap(KafkaPublisherEndToEndHelper.CUSTOM_HEADER,
                KafkaPublisherEndToEndHelper.CUSTOM_HEADER_VALUE));

        KafkaPublishResult result = publisher.publish(message)
                .get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = assertRecord(
                KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS, topic, key);
        JsonNode envelope = objectMapper.readTree(record.value());

        log.info("topic 发布结果: {}", result);
        assertEquals(topic, result.getTopic(), "结果 topic 应与 broker metadata 一致");
        assertEquals(key, record.key(), "消费记录 key 应精确一致");
        assertEquals(message.getMessageId(), result.getMessageId(), "结果 messageId 应精确一致");
        assertEquals(message.getMessageId(), envelope.get("messageId").asText(),
                "envelope messageId 应精确一致");
        assertEquals(KafkaPublisherEndToEndHelper.MESSAGE_TYPE, envelope.get("messageType").asText(),
                "envelope messageType 应精确一致");
        assertEquals(KafkaPublisherEndToEndHelper.APP_NAME, envelope.get("source").asText(),
                "envelope source 应精确一致");
        assertEquals(KafkaPublisherEndToEndHelper.PAYLOAD, envelope.get("payload").asText(),
                "envelope payload 应精确一致");
        assertEquals(message.getMessageId(), headerText(record,
                KafkaPublisherEndToEndHelper.DEFAULT_HEADER_MESSAGE_ID),
                "messageId header 应与 envelope 一致");
        assertEquals(KafkaPublisherEndToEndHelper.MESSAGE_TYPE, headerText(record,
                KafkaPublisherEndToEndHelper.DEFAULT_HEADER_MESSAGE_TYPE),
                "messageType header 应与 envelope 一致");
        assertEquals(KafkaPublisherEndToEndHelper.APP_NAME, headerText(record,
                KafkaPublisherEndToEndHelper.DEFAULT_HEADER_SOURCE),
                "source header 应与 envelope 一致");
        assertEquals(envelope.get("timestamp").asText(), headerText(record,
                KafkaPublisherEndToEndHelper.DEFAULT_HEADER_PUBLISHED_AT),
                "publishedAt header 应与 envelope timestamp 一致");
        assertArrayEquals(KafkaPublisherEndToEndHelper.CUSTOM_HEADER_VALUE.getBytes(StandardCharsets.UTF_8),
                record.headers().lastHeader(KafkaPublisherEndToEndHelper.CUSTOM_HEADER).value(),
                "自定义 Unicode header 应按 UTF-8 原样消费");
        assertNoRecord(KafkaPublisherEndToEndHelper.V28_BOOTSTRAP_SERVERS, topic, key);
        assertNoRecord(KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS, topic, key);
    }

    private void assertExplicitDatasourceIsolation(String suffix, String topic) throws Exception {
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-v28");
        KafkaPublishMessage<String> message = message(topic, key, "message-v28-" + suffix);

        KafkaPublishResult result = publisher.publishOn(KafkaPublisherEndToEndHelper.DATASOURCE_V28, message)
                .get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("显式 datasource 发布结果: {}", result);
        assertEquals(KafkaPublisherEndToEndHelper.DATASOURCE_V28, result.getDatasourceKey(),
                "publishOn 应回填显式 datasourceKey");
        assertRecord(KafkaPublisherEndToEndHelper.V28_BOOTSTRAP_SERVERS, topic, key);
        assertNoRecord(KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS, topic, key);
        assertNoRecord(KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS, topic, key);
    }

    private void assertRouteKeyIsolation(String suffix, String topic) throws Exception {
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-route-v37");
        KafkaPublishMessage<String> message = message(topic, key, "message-route-v37-" + suffix);

        KafkaPublishResult result = publisher.publishByRouteKey(
                        KafkaPublisherEndToEndHelper.ROUTE_KEY_V37, message)
                .get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.info("routeKey 发布结果: {}", result);
        assertNull(result.getDatasourceKey(), "routeKey 模式不应伪造 datasourceKey");
        assertRecord(KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS, topic, key);
        assertNoRecord(KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS, topic, key);
        assertNoRecord(KafkaPublisherEndToEndHelper.V28_BOOTSTRAP_SERVERS, topic, key);
    }

    private void assertClusterPartitions(String suffix, String topic) throws Exception {
        for (int partition = KafkaPublisherEndToEndHelper.PARTITION_ZERO;
             partition <= KafkaPublisherEndToEndHelper.PARTITION_TWO; partition++) {
            String key = KafkaPublisherEndToEndHelper.key(suffix + "-cluster-" + partition);
            KafkaPublishMessage<String> message = message(topic, key,
                    "message-cluster-" + partition + "-" + suffix);
            message.setPartition(partition);

            KafkaPublishResult result = publisher.publishOn(
                            KafkaPublisherEndToEndHelper.DATASOURCE_CLUSTER, message)
                    .get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ConsumerRecord<String, String> record = assertRecord(
                    KafkaPublisherEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS, topic, key);

            log.info("cluster 分区 {} 发布结果: {}", partition, result);
            assertEquals(partition, result.getPartition(), "结果 partition 应精确一致");
            assertEquals(partition, record.partition(), "消费记录 partition 应精确一致");
        }
    }

    private void createTopics(String topicV110, String topicV28, String topicV37Route,
                              String topicCluster) {
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V110_BOOTSTRAP_SERVERS,
                topicV110, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V28_BOOTSTRAP_SERVERS,
                topicV28, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS,
                topicV37Route, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.CLUSTER_BOOTSTRAP_SERVERS,
                topicCluster, KafkaPublisherEndToEndHelper.CLUSTER_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.CLUSTER_REPLICATION_FACTOR);
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType(KafkaPublisherEndToEndHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherEndToEndHelper.PAYLOAD)
                .build();
    }

    private ConsumerRecord<String, String> assertRecord(String bootstrapServers, String topic,
                                                        String expectedKey) {
        ConsumerRecord<String, String> record = KafkaPublisherEndToEndHelper.consumeRecord(
                bootstrapServers, topic, expectedKey, KafkaPublisherEndToEndHelper.CONSUME_TIMEOUT_MS);
        assertNotNull(record, String.format(KafkaPublisherEndToEndHelper.ASSERT_RECORD_MISSING,
                bootstrapServers, topic));
        return record;
    }

    private void assertNoRecord(String bootstrapServers, String topic, String expectedKey) {
        ConsumerRecord<String, String> record = KafkaPublisherEndToEndHelper.consumeRecord(
                bootstrapServers, topic, expectedKey, KafkaPublisherEndToEndHelper.NO_MESSAGE_TIMEOUT_MS);
        assertNull(record, String.format(KafkaPublisherEndToEndHelper.ASSERT_RECORD_UNEXPECTED,
                bootstrapServers, topic));
    }

    private String headerText(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        assertNotNull(header, "默认 header 应存在: " + headerName);
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    static class MockE2ePayload {

        private String mockField;
        private int mockCount;
    }
}
