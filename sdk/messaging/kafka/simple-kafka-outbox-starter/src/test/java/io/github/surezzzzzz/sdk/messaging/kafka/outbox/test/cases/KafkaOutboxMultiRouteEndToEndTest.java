package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.support.KafkaOutboxEndToEndHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 多路由多集群端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class)
public class KafkaOutboxMultiRouteEndToEndTest extends KafkaOutboxEndToEndTestSupport {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";
    private static final String STATUS_SQL = "SELECT status FROM simple_kafka_outbox WHERE message_id = ?";
    private static final long SENT_TIMEOUT_MS = 45000L;
    private static final long POLL_INTERVAL_MS = 200L;

    @Autowired
    private KafkaOutboxEngine outboxEngine;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, OUTBOX_TABLE);

        log.info("重建 Outbox 测试表，tableCount={}", tableCount);
        assertEquals(Integer.valueOf(1), tableCount, "真实 MySQL 中应存在唯一 Outbox 表");
    }

    @Test
    public void testOutboxSwitchesAcrossTopicRouteKeyAndDatasourceWithoutStickyState() {
        String suffix = KafkaOutboxEndToEndHelper.suffix();
        String topic = KafkaOutboxEndToEndHelper.SHARED_TOPIC_PREFIX + suffix;
        createSharedTopic(topic);

        String keyV110First = "mock-key-" + suffix + "-v110-first";
        String keyV28 = "mock-key-" + suffix + "-v28";
        String keyV37 = "mock-key-" + suffix + "-v37";
        String keyCluster = "mock-key-" + suffix + "-cluster";
        String keyV110Return = "mock-key-" + suffix + "-v110-return";
        Set<String> allKeys = new LinkedHashSet<>(Arrays.asList(
                keyV110First, keyV28, keyV37, keyCluster, keyV110Return));

        OutboxSaveResult v110First = save(message(topic, keyV110First, "mock-message-" + suffix + "-v110-first",
                null, null));
        OutboxSaveResult v28 = save(message(topic, keyV28, "mock-message-" + suffix + "-v28",
                null, KafkaOutboxEndToEndHelper.DATASOURCE_V28));
        OutboxSaveResult v37 = save(message(topic, keyV37, "mock-message-" + suffix + "-v37",
                KafkaOutboxEndToEndHelper.ROUTE_KEY_V37, null));
        OutboxSaveResult cluster = save(message(topic, keyCluster, "mock-message-" + suffix + "-cluster",
                null, KafkaOutboxEndToEndHelper.DATASOURCE_CLUSTER));
        OutboxSaveResult v110Return = save(message(topic, keyV110Return,
                "mock-message-" + suffix + "-v110-return", null, null));

        List<OutboxSaveResult> results = Arrays.asList(v110First, v28, v37, cluster, v110Return);
        for (OutboxSaveResult result : results) {
            awaitSent(result.getMessageId());
        }

        List<ConsumerRecord<String, String>> v110Records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V110), topic, allKeys, 2);
        List<ConsumerRecord<String, String>> v28Records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V28), topic, allKeys, 1);
        List<ConsumerRecord<String, String>> v37Records = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_V37), topic, allKeys, 1);
        List<ConsumerRecord<String, String>> clusterRecords = KafkaOutboxEndToEndHelper.consumeRecords(
                bootstrapServers(KafkaOutboxEndToEndHelper.DATASOURCE_CLUSTER), topic, allKeys, 1);

        log.info("Outbox 多路由往返结果: v110={}, v28={}, v37={}, cluster={}",
                keys(v110Records), keys(v28Records), keys(v37Records), keys(clusterRecords));
        assertEquals(2, v110Records.size(), "默认 topic 路由首发和返回消息必须在 v110 精确投递两条");
        assertEquals(new LinkedHashSet<>(Arrays.asList(keyV110First, keyV110Return)), keys(v110Records),
                "默认 topic 路由首发和返回消息应且只能落入 v110");
        assertEquals(1, v28Records.size(), "message.datasourceKey=v28 的消息必须在 v28 精确投递一条");
        assertEquals(Collections.singleton(keyV28), keys(v28Records),
                "message.datasourceKey=v28 的消息应且只能落入 v28");
        assertEquals(1, v37Records.size(), "message.routeKey 的消息必须在 v37 精确投递一条");
        assertEquals(Collections.singleton(keyV37), keys(v37Records),
                "message.routeKey 的消息应且只能落入 v37");
        assertEquals(1, clusterRecords.size(), "message.datasourceKey=cluster 的消息必须在三 Broker 集群精确投递一条");
        assertEquals(Collections.singleton(keyCluster), keys(clusterRecords),
                "message.datasourceKey=cluster 的消息应且只能落入三 Broker 集群");
    }

    private Set<String> keys(List<ConsumerRecord<String, String>> records) {
        Set<String> keys = new LinkedHashSet<>();
        for (ConsumerRecord<String, String> record : records) {
            keys.add(record.key());
        }
        return keys;
    }

    private OutboxSaveResult save(KafkaPublishMessage<String> message) {
        OutboxSaveResult result = transactionTemplate.execute(status -> outboxEngine.save(message));
        log.info("事务内保存 Outbox，messageId={}, recordId={}, topic={}, routeKey={}, datasourceKey={}",
                result == null ? null : result.getMessageId(), result == null ? null : result.getOutboxRecordId(),
                message.getTopic(), message.getRouteKey(), message.getDatasourceKey());
        assertNotNull(result, "事务内保存应返回结果");
        assertNotNull(result.getOutboxRecordId(), "保存结果应包含数据库主键");
        assertEquals(message.getMessageId(), result.getMessageId(), "保存结果 messageId 应保持稳定");
        return result;
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId,
                                                String routeKey, String datasourceKey) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType("mock.message.created")
                .payload("mock-payload-" + key)
                .routeKey(routeKey)
                .datasourceKey(datasourceKey)
                .build();
    }

    private void awaitSent(String messageId) {
        long deadline = System.currentTimeMillis() + SENT_TIMEOUT_MS;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = jdbcTemplate.queryForObject(STATUS_SQL, String.class, messageId);
            if (OutboxStatus.SENT.getCode().equals(status)) {
                break;
            }
            sleep();
        }
        log.info("等待 Outbox SENT，messageId={}, 最终状态={}", messageId, status);
        assertEquals(OutboxStatus.SENT.getCode(), status, "每条多路由消息都必须逐条回写 SENT");
        Long offset = jdbcTemplate.queryForObject(
                "SELECT broker_offset FROM simple_kafka_outbox WHERE message_id = ?", Long.class, messageId);
        assertNotNull(offset, "SENT 记录必须回填 broker offset");
        assertTrue(offset >= 0L, "broker offset 必须大于等于 0");
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Outbox 状态时线程被中断", e);
        }
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }
}
