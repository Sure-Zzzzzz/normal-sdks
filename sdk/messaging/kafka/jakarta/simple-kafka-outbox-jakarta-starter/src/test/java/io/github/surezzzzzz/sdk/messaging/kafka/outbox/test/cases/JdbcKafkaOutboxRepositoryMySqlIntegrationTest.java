package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupBatchResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.JdbcKafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.SimpleKafkaOutboxTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JDBC Kafka Outbox Repository 的真实 MySQL 状态机集成测试
 *
 * @author surezzzzzz
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = SimpleKafkaOutboxTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.enable=false",
                "io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.enable=false"
        })
public class JdbcKafkaOutboxRepositoryMySqlIntegrationTest {

    private static final String MODULE_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/01_schema.sql";
    private static final String OUTBOX_TABLE = "simple_kafka_outbox";

    @Autowired
    @Qualifier("simpleKafkaOutboxNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    @Qualifier("simpleKafkaOutboxTransactionTemplate")
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JdbcKafkaOutboxRepository repository;

    @BeforeEach
    public void recreateOutboxTable() throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (statement.trim().length() > 0) {
                jdbcTemplate.execute(statement);
            }
        }
        repository = new JdbcKafkaOutboxRepository(namedParameterJdbcTemplate, transactionTemplate, OUTBOX_TABLE);
    }

    @Test
    public void shouldClaimOnlyEligibleRowsInEligibleTimeOrderAndIncrementLeaseStateExactlyOnce() {
        long pendingId = save("claim-pending");
        long retryId = save("claim-retry");
        long expiredId = save("claim-expired");
        long futureId = save("claim-future");
        long activeId = save("claim-active");
        long sentId = save("claim-sent");
        long poisonId = save("claim-poison");

        updateState(pendingId, OutboxStatus.PENDING.getCode(), "DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 SECOND)",
                null);
        updateState(retryId, OutboxStatus.RETRY_WAIT.getCode(), "DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 4 SECOND)",
                null);
        updateState(expiredId, OutboxStatus.PROCESSING.getCode(), "DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 3 SECOND)",
                "expired-owner");
        updateState(futureId, OutboxStatus.PENDING.getCode(), "DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 SECOND)",
                null);
        updateState(activeId, OutboxStatus.PROCESSING.getCode(), "DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 SECOND)",
                "active-owner");
        updateState(sentId, OutboxStatus.SENT.getCode(), "DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 SECOND)", null);
        updateState(poisonId, OutboxStatus.POISON.getCode(), "DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 SECOND)", null);

        List<OutboxRecordEntity> firstClaim = repository.claim(2, 2_000_000L);

        assertEquals(Arrays.asList(pendingId, retryId), ids(firstClaim),
                "领取必须合并 ready 与过期 lease 后按 eligible_at、id 排序并受 candidateLimit 限制");
        assertClaimed(pendingId, firstClaim.get(0));
        assertClaimed(retryId, firstClaim.get(1));
        assertEquals(OutboxStatus.PROCESSING.getCode(), status(expiredId), "超出 candidateLimit 的过期租约不得提前领取");
        assertEquals(Integer.valueOf(0), integer(futureId, "attempt"), "未来可用记录不得被领取");
        assertEquals(Integer.valueOf(0), integer(activeId, "attempt"), "未过期 PROCESSING 记录不得被领取");
        assertEquals(Integer.valueOf(0), integer(sentId, "attempt"), "SENT 终态不得被领取");
        assertEquals(Integer.valueOf(0), integer(poisonId, "attempt"), "POISON 终态不得被领取");

        List<OutboxRecordEntity> secondClaim = repository.claim(2, 2_000_000L);

        assertEquals(Arrays.asList(expiredId), ids(secondClaim), "下一次扫描必须只领取严格过期的 PROCESSING 记录");
        assertClaimed(expiredId, secondClaim.get(0));
        assertEquals(Integer.valueOf(1), integer(expiredId, "attempt"), "过期租约重领必须仅增加一次 attempt");
        assertEquals(Long.valueOf(1L), longValue(expiredId, "version"), "过期租约重领必须仅增加一次 version");
    }

    @Test
    public void shouldApplyTransitionsOnlyForCurrentOwnerAndVersionWithoutMutatingStaleRows() {
        long sentId = save("transition-sent");
        OutboxRecordEntity sentRecord = claimOne(sentId);
        sentRecord.setBrokerTopic("mock-broker-topic");
        sentRecord.setBrokerPartition(2);
        sentRecord.setBrokerOffset(123L);
        sentRecord.setBrokerTimestamp(456L);

        assertTrue(repository.markSent(sentRecord), "当前 owner 与 version 必须可以回写 SENT");
        assertEquals(OutboxStatus.SENT.getCode(), status(sentId), "成功回写必须进入 SENT");
        assertNull(stringValue(sentId, "owner_token"), "SENT 必须清理 owner token");
        assertNull(timestamp(sentId, "lease_until"), "SENT 必须清理 lease");
        assertNull(stringValue(sentId, "last_error_code"), "SENT 必须清理历史错误码");
        assertEquals("mock-broker-topic", stringValue(sentId, "broker_topic"), "SENT 必须保存 broker topic");
        assertEquals(Integer.valueOf(2), integer(sentId, "broker_partition"), "SENT 必须保存 broker partition");
        assertEquals(Long.valueOf(123L), longValue(sentId, "broker_offset"), "SENT 必须保存 broker offset");
        assertEquals(Long.valueOf(456L), longValue(sentId, "broker_timestamp"), "SENT 必须保存 broker timestamp");
        assertEquals(Long.valueOf(2L), longValue(sentId, "version"), "SENT 必须仅增加一次 version");
        assertFalse(repository.markSent(sentRecord), "已完成记录不能被旧 owner 重复回写");

        long retryId = save("transition-retry");
        OutboxRecordEntity retryRecord = claimOne(retryId);
        OutboxRecordEntity staleRetryRecord = copy(retryRecord);
        staleRetryRecord.setOwnerToken("wrong-owner");
        Map<String, Object> beforeStaleWrite = row(retryId);

        assertFalse(repository.markRetry(staleRetryRecord, 5_000_000L, "STALE", "stale mutation"),
                "错误 owner 的 CAS 必须返回 false");
        assertEquals(beforeStaleWrite, row(retryId), "错误 owner 的 CAS 不得修改数据库整行");
        assertTrue(repository.markRetry(retryRecord, 5_000_000L, "RETRY_CODE", "retry summary"),
                "当前 owner 与 version 必须可以进入 RETRY_WAIT");
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), status(retryId), "重试必须进入 RETRY_WAIT");
        assertNull(stringValue(retryId, "owner_token"), "RETRY_WAIT 必须清理 owner token");
        assertNull(timestamp(retryId, "lease_until"), "RETRY_WAIT 必须清理 lease");
        assertEquals("RETRY_CODE", stringValue(retryId, "last_error_code"), "重试必须保留错误码");
        assertEquals("retry summary", stringValue(retryId, "last_error_summary"), "重试必须保留脱敏错误摘要");
        Map<String, Object> beforeWrongStatusWrite = row(retryId);
        assertFalse(repository.markPoison(retryRecord, "WRONG", "wrong status"),
                "旧 version 或非 PROCESSING 状态必须拒绝写入");
        assertEquals(beforeWrongStatusWrite, row(retryId), "被拒绝的状态迁移不得修改数据库整行");

        long poisonId = save("transition-poison");
        OutboxRecordEntity poisonRecord = claimOne(poisonId);
        assertTrue(repository.markPoison(poisonRecord, "POISON_CODE", "poison summary"),
                "当前 owner 与 version 必须可以进入 POISON");
        assertEquals(OutboxStatus.POISON.getCode(), status(poisonId), "毒消息必须进入 POISON");
        assertNull(stringValue(poisonId, "owner_token"), "POISON 必须清理 owner token");
        assertNull(timestamp(poisonId, "lease_until"), "POISON 必须清理 lease");
        assertEquals("POISON_CODE", stringValue(poisonId, "last_error_code"), "POISON 必须保留错误码");

        long releaseId = save("transition-release");
        OutboxRecordEntity releaseRecord = claimOne(releaseId);
        assertTrue(repository.releaseBeforeSend(releaseRecord, "STOP_CODE", "stop summary"),
                "当前 owner 与 version 必须可以在发送前释放租约");
        assertEquals(OutboxStatus.RETRY_WAIT.getCode(), status(releaseId), "发送前释放必须进入 RETRY_WAIT");
        assertNull(stringValue(releaseId, "owner_token"), "发送前释放必须清理 owner token");
        assertNull(timestamp(releaseId, "lease_until"), "发送前释放必须清理 lease");
        assertEquals("STOP_CODE", stringValue(releaseId, "last_error_code"), "发送前释放必须保留错误码");
    }

    @Test
    public void shouldTranslateDuplicateMessageIdAndDeleteOnlyExpiredSentRowsWithKeysetCursor() {
        long originalId = repository.save(record("duplicate-message"));
        Map<String, Object> original = row(originalId);

        KafkaOutboxException duplicate = assertThrows(KafkaOutboxException.class,
                () -> repository.save(record("duplicate-message")), "重复 messageId 必须转换为统一业务异常");

        assertEquals(ErrorCode.KAFKA_OUTBOX_004, duplicate.getErrorCode(), "重复 messageId 必须使用唯一键错误码");
        assertEquals(original, row(originalId), "重复保存不得改写已存在消息快照");

        Timestamp expireBefore = Timestamp.from(Instant.parse("2020-01-01T00:00:00Z"));
        long firstId = save("cleanup-first");
        long secondId = save("cleanup-second");
        long thirdId = save("cleanup-third");
        long retainedId = save("cleanup-retained");
        long pendingId = save("cleanup-pending");
        Timestamp sentAt = Timestamp.from(Instant.parse("2019-01-01T00:00:00Z"));
        setSent(firstId, sentAt);
        setSent(secondId, sentAt);
        setSent(thirdId, sentAt);
        setSent(retainedId, Timestamp.from(Instant.parse("2021-01-01T00:00:00Z")));
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, sent_at = ? WHERE id = ?",
                OutboxStatus.PENDING.getCode(), sentAt, pendingId);

        OutboxCleanupBatchResult firstBatch = repository.cleanupBatch(expireBefore, null, null, 2);
        OutboxCleanupBatchResult secondBatch = repository.cleanupBatch(expireBefore,
                firstBatch.getLastSentAt(), firstBatch.getLastId(), 2);

        assertEquals(2, firstBatch.getCandidateCount(), "首批 keyset 查询必须仅返回 batchSize 条候选");
        assertEquals(2, firstBatch.getDeletedCount(), "首批必须只删除对应的过期 SENT 候选");
        assertNotNull(firstBatch.getLastSentAt(), "首批必须返回可续跑 sent_at 游标");
        assertNotNull(firstBatch.getLastId(), "首批必须返回可续跑 id 游标");
        assertEquals(1, secondBatch.getCandidateCount(), "续跑游标必须只返回剩余过期 SENT 记录");
        assertEquals(1, secondBatch.getDeletedCount(), "续跑批次必须删除剩余过期 SENT 记录");
        assertEquals(Integer.valueOf(0), countByIds(firstId, secondId, thirdId), "所有过期 SENT 记录必须被删除一次");
        assertEquals(Integer.valueOf(1), countByIds(retainedId), "未过期 SENT 记录不得被误删");
        assertEquals(Integer.valueOf(1), countByIds(pendingId), "非 SENT 状态即使 sent_at 过期也不得被误删");
    }

    private long save(String suffix) {
        return repository.save(record("mock-repository-" + suffix));
    }

    private OutboxRecordEntity record(String messageId) {
        return OutboxRecordEntity.builder()
                .messageId(messageId)
                .topic("mock.repository.topic")
                .recordKey("mock-key-" + messageId)
                .messageType("mock.repository.message")
                .payloadKind("STRING")
                .payloadJson("mock-payload-" + messageId)
                .headersJson("{}")
                .attributesJson("{}")
                .schemaVersion(1)
                .build();
    }

    private OutboxRecordEntity claimOne(long id) {
        List<OutboxRecordEntity> claimed = repository.claim(1, 10_000_000L);
        assertEquals(1, claimed.size(), "单条待领取记录必须被真实 MySQL Repository 领取");
        assertEquals(Long.valueOf(id), claimed.get(0).getId(), "领取结果必须是目标记录");
        return claimed.get(0);
    }

    private void updateState(long id, String status, String eligibleTimeExpression, String ownerToken) {
        if (OutboxStatus.PROCESSING.getCode().equals(status)) {
            jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, owner_token = ?, lease_until = "
                            + eligibleTimeExpression + ", available_at = CURRENT_TIMESTAMP(3) WHERE id = ?",
                    status, ownerToken, id);
            return;
        }
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, owner_token = NULL, lease_until = NULL, "
                + "available_at = " + eligibleTimeExpression + " WHERE id = ?", status, id);
    }

    private void setSent(long id, Timestamp sentAt) {
        jdbcTemplate.update("UPDATE " + OUTBOX_TABLE + " SET status = ?, sent_at = ?, owner_token = NULL, "
                + "lease_until = NULL WHERE id = ?", OutboxStatus.SENT.getCode(), sentAt, id);
    }

    private void assertClaimed(long id, OutboxRecordEntity record) {
        assertEquals(Long.valueOf(id), record.getId(), "领取结果必须保留正确主键");
        assertEquals(OutboxStatus.PROCESSING.getCode(), record.getStatus(), "领取后必须进入 PROCESSING");
        assertEquals(Integer.valueOf(1), record.getAttempt(), "首次领取必须只增加一次 attempt");
        assertEquals(Long.valueOf(1L), record.getVersion(), "首次领取必须只增加一次 version");
        assertNotNull(record.getOwnerToken(), "领取后必须写入 owner token");
        assertNotNull(record.getLeaseUntil(), "领取后必须写入 lease");
        assertTrue(record.getLeaseUntil().after(Timestamp.from(Instant.now())), "领取后的 lease 必须仍在未来");
    }

    private List<Long> ids(List<OutboxRecordEntity> records) {
        return Arrays.asList(records.get(0).getId(), records.size() > 1 ? records.get(1).getId() : null)
                .subList(0, records.size());
    }

    private OutboxRecordEntity copy(OutboxRecordEntity source) {
        return OutboxRecordEntity.builder()
                .id(source.getId())
                .ownerToken(source.getOwnerToken())
                .version(source.getVersion())
                .build();
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM " + OUTBOX_TABLE + " WHERE id = ?", id);
    }

    private String status(long id) {
        return stringValue(id, "status");
    }

    private String stringValue(long id, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE id = ?",
                String.class, id);
    }

    private Integer integer(long id, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE id = ?",
                Integer.class, id);
    }

    private Long longValue(long id, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE id = ?",
                Long.class, id);
    }

    private Timestamp timestamp(long id, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + OUTBOX_TABLE + " WHERE id = ?",
                Timestamp.class, id);
    }

    private Integer countByIds(long... ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.length, "?"));
        Object[] parameters = new Object[ids.length];
        for (int index = 0; index < ids.length; index++) {
            parameters[index] = ids[index];
        }
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + OUTBOX_TABLE + " WHERE id IN (" + placeholders + ")",
                Integer.class, parameters);
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "01_schema.sql");
    }
}
