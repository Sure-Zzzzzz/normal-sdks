package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.query.OutboxRecordBrowseQuery;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository.JdbcKafkaOutboxManagementRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.SimpleKafkaOutboxManagementTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support.OutboxTestSchemaHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Management JDBC Repository 的真实 MySQL 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxManagementTestApplication.class)
class JdbcKafkaOutboxManagementRepositoryMySqlIntegrationTest {
    private static final String TABLE = "simple_kafka_outbox";
    private static final String TEST_USERNAME = "management-test";
    private static final String TEST_PASSWORD = "test-" + Long.toUnsignedString(System.nanoTime(), 36);
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    @Qualifier("simpleKafkaOutboxManagementNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired
    @Qualifier("simpleKafkaOutboxManagementTransactionTemplate")
    private TransactionTemplate transactionTemplate;
    private JdbcKafkaOutboxManagementRepository repository;

    @DynamicPropertySource
    static void managementProperties(DynamicPropertyRegistry registry) {
        registry.add("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.admin.username", () -> TEST_USERNAME);
        registry.add("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.admin.password", () -> TEST_PASSWORD);
    }

    @BeforeEach
    void recreateTable() throws IOException {
        OutboxTestSchemaHelper.recreateTable(jdbcTemplate);
        repository = new JdbcKafkaOutboxManagementRepository(namedJdbcTemplate, transactionTemplate, TABLE);
    }

    @Test
    void shouldReadOnlySafeProjectionAndBrowseWithKeyset() {
        long first = save("safe-projection-first", OutboxStatus.POISON, Instant.parse("2026-01-02T00:00:00Z"));
        long second = save("safe-projection-second", OutboxStatus.POISON, Instant.parse("2026-01-01T00:00:00Z"));
        jdbcTemplate.update("UPDATE " + TABLE + " SET record_key = 'secret-key', trace_id = 'secret-trace' WHERE id = ?", first);
        List<io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord> page = repository.browse(OutboxRecordBrowseQuery.builder()
                .status(OutboxStatus.POISON).size(1).build());
        assertEquals(2, repository.browse(OutboxRecordBrowseQuery.builder().status(OutboxStatus.POISON).size(1).build()).size(),
                "Repository 必须额外读取一条记录判断是否有下一批");
        assertEquals(Long.valueOf(first), page.get(0).getRecordId(), "首批必须按 available_at、id 倒序返回");
        assertNull(page.get(0).getRecordKey(), "管理查询不得读取 record_key");
        assertNull(page.get(0).getTraceId(), "管理查询不得读取 trace_id");
        List<io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord> next = repository.browse(OutboxRecordBrowseQuery.builder()
                .status(OutboxStatus.POISON).cursorAvailableAt(page.get(0).getAvailableAt()).cursorId(page.get(0).getRecordId()).size(1).build());
        assertEquals(Long.valueOf(second), next.get(0).getRecordId(), "游标必须继续读取下一条记录");
    }

    @Test
    void shouldContinueKeysetPageWhenAvailableAtIsEqual() {
        Instant availableAt = Instant.parse("2026-01-02T00:00:00Z");
        long first = save("keyset-equal-first", OutboxStatus.PENDING, availableAt);
        long second = save("keyset-equal-second", OutboxStatus.PENDING, availableAt);
        List<io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord> page = repository.browse(
                OutboxRecordBrowseQuery.builder().status(OutboxStatus.PENDING).size(1).build());
        assertEquals(Long.valueOf(second), page.get(0).getRecordId(), "相同 available_at 必须按 ID 倒序读取");
        List<io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord> next = repository.browse(
                OutboxRecordBrowseQuery.builder().status(OutboxStatus.PENDING).cursorAvailableAt(availableAt)
                        .cursorId(second).size(1).build());
        assertEquals(Long.valueOf(first), next.get(0).getRecordId(), "相同 available_at 的下一页不得跳过较小 ID");
    }

    @Test
    void shouldResetOnlyPoisonAndPreserveSnapshotFields() {
        long poison = save("reset-poison", OutboxStatus.POISON, Instant.now().minusSeconds(5));
        jdbcTemplate.update("UPDATE " + TABLE + " SET record_key = 'record-key', route_key = 'route-key', "
                + "datasource_key = 'datasource-key', `partition` = 3, message_timestamp = 1000, "
                + "headers_json = '{\"header\":\"value\"}', attributes_json = '{\"attribute\":\"value\"}', "
                + "envelope_enabled = 1, trace_id = 'trace-id', broker_topic = 'broker-topic', broker_partition = 2, "
                + "broker_offset = 9, broker_timestamp = 2000, sent_at = CURRENT_TIMESTAMP(3), attempt = 4, "
                + "owner_token = 'owner', lease_until = CURRENT_TIMESTAMP(3), last_error_code = 'ERROR', "
                + "last_error_summary = 'error summary', version = 8, updated_at = '2020-01-01 00:00:00.000' WHERE id = ?", poison);
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM " + TABLE + " WHERE id = ?", poison);
        assertEquals(1, repository.resetPoison(poison), "POISON 必须可重置一次");
        Map<String, Object> after = jdbcTemplate.queryForMap("SELECT * FROM " + TABLE + " WHERE id = ?", poison);
        assertEquals(OutboxStatus.PENDING.getCode(), after.get("status"));
        assertEquals(0, ((Number) after.get("attempt")).intValue());
        assertNull(after.get("owner_token"));
        assertNull(after.get("lease_until"));
        assertNull(after.get("last_error_code"));
        assertNull(after.get("last_error_summary"));
        assertEquals(((Number) before.get("version")).longValue() + 1, ((Number) after.get("version")).longValue());
        assertNotEquals(before.get("updated_at"), after.get("updated_at"), "重置必须更新时间");
        for (String column : Arrays.asList("message_id", "topic", "record_key", "route_key", "datasource_key", "partition",
                "message_timestamp", "message_type", "payload_kind", "payload_json", "headers_json", "attributes_json",
                "envelope_enabled", "trace_id", "schema_version", "broker_topic", "broker_partition", "broker_offset",
                "broker_timestamp", "created_at", "sent_at")) {
            assertEquals(before.get(column), after.get(column), "重置不得修改 " + column);
        }
        assertEquals(0, repository.resetPoison(poison), "已经重置的记录不能重复重置");
        Integer eligible = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE
                + " WHERE id = ? AND status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(3)", Integer.class, poison);
        assertEquals(Integer.valueOf(1), eligible, "重置后记录必须满足 runtime worker 的 PENDING 领取条件");
    }

    @Test
    void shouldAllowOnlyOneConcurrentPoisonReset() throws InterruptedException {
        long poison = save("concurrent-reset", OutboxStatus.POISON, Instant.now());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        Runnable reset = () -> {
            ready.countDown();
            try {
                start.await();
                if (repository.resetPoison(poison) == 1) successes.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
        new Thread(reset, "management-reset-first").start();
        new Thread(reset, "management-reset-second").start();
        assertTrue(ready.await(10, TimeUnit.SECONDS), "并发 reset 线程必须全部就绪");
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发 reset 线程必须全部结束");
        assertEquals(1, successes.get(), "两个并发 reset 必须恰好一个成功");
        Map<String, Object> after = jdbcTemplate.queryForMap("SELECT status, attempt, version FROM " + TABLE + " WHERE id = ?", poison);
        assertEquals(OutboxStatus.PENDING.getCode(), after.get("status"), "并发 reset 后记录必须处于 PENDING");
        assertEquals(0, ((Number) after.get("attempt")).intValue(), "并发 reset 后 attempt 必须归零");
        assertEquals(1L, ((Number) after.get("version")).longValue(), "并发 reset 只能递增一次版本号");
    }

    @Test
    void shouldUsePublishedIndexesForManagementQueries() {
        long id = save("explain-index", OutboxStatus.POISON, Instant.now());
        assertPlan("EXPLAIN SELECT id FROM " + TABLE + " WHERE id = " + id, "PRIMARY");
        assertPlan("EXPLAIN SELECT id FROM " + TABLE + " WHERE message_id = 'explain-index'", "uk_message_id");
        assertPlan("EXPLAIN SELECT id FROM " + TABLE + " WHERE status = 'POISON' ORDER BY available_at DESC, id DESC LIMIT 2", "idx_status_available_at_id");
    }

    private long save(String messageId, OutboxStatus status, Instant availableAt) {
        jdbcTemplate.update("INSERT INTO " + TABLE + " (message_id, topic, message_type, payload_kind, payload_json, schema_version, status, available_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                messageId, "mock.management.topic", "mock.management.type", "STRING", "mock-payload", 1, status.getCode(), Timestamp.from(availableAt));
        return jdbcTemplate.queryForObject("SELECT id FROM " + TABLE + " WHERE message_id = ?", Long.class, messageId);
    }

    private void assertPlan(String sql, String expectedKey) {
        Map<String, Object> plan = jdbcTemplate.queryForMap(sql);
        log.info("Management 查询计划：{}", plan);
        assertEquals(expectedKey, plan.get("key"), "查询必须使用已发布 DDL 的预期索引");
    }
}
