package io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCandidate;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupBatchResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupCandidate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * JDBC Kafka Outbox 持久化实现
 *
 * @author surezzzzzz
 */
public class JdbcKafkaOutboxRepository implements KafkaOutboxRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    /**
     * 预编译的各状态迁移 SQL，表名已在构造时格式化注入
     */
    private final String insertSql;
    private final String readyCandidateSql;
    private final String expiredCandidateSql;
    private final String claimSql;
    private final String selectByOwnerSql;
    private final String markSentSql;
    private final String markRetrySql;
    private final String markPoisonSql;
    private final String releaseSql;
    private final String cleanupFirstCandidateSql;
    private final String cleanupNextCandidateSql;
    private final String cleanupDeleteSql;

    /**
     * 创建 JDBC Repository
     *
     * @param jdbcTemplate        模块专用 NamedParameterJdbcTemplate
     * @param transactionTemplate 模块专用事务模板
     * @param tableName           已校验表名
     */
    public JdbcKafkaOutboxRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                     TransactionTemplate transactionTemplate,
                                     String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.insertSql = format(SimpleKafkaOutboxConstant.SQL_INSERT_TEMPLATE, tableName);
        this.readyCandidateSql = format(SimpleKafkaOutboxConstant.SQL_SELECT_READY_CANDIDATE_TEMPLATE, tableName);
        this.expiredCandidateSql = format(SimpleKafkaOutboxConstant.SQL_SELECT_EXPIRED_CANDIDATE_TEMPLATE, tableName);
        this.claimSql = format(SimpleKafkaOutboxConstant.SQL_CLAIM_TEMPLATE, tableName);
        this.selectByOwnerSql = format(SimpleKafkaOutboxConstant.SQL_SELECT_BY_OWNER_TEMPLATE, tableName);
        this.markSentSql = format(SimpleKafkaOutboxConstant.SQL_MARK_SENT_TEMPLATE, tableName);
        this.markRetrySql = format(SimpleKafkaOutboxConstant.SQL_MARK_RETRY_TEMPLATE, tableName);
        this.markPoisonSql = format(SimpleKafkaOutboxConstant.SQL_MARK_POISON_TEMPLATE, tableName);
        this.releaseSql = format(SimpleKafkaOutboxConstant.SQL_RELEASE_TEMPLATE, tableName);
        this.cleanupFirstCandidateSql = format(
                SimpleKafkaOutboxConstant.SQL_SELECT_CLEANUP_FIRST_CANDIDATE_TEMPLATE, tableName);
        this.cleanupNextCandidateSql = format(
                SimpleKafkaOutboxConstant.SQL_SELECT_CLEANUP_NEXT_CANDIDATE_TEMPLATE, tableName);
        this.cleanupDeleteSql = format(SimpleKafkaOutboxConstant.SQL_DELETE_CLEANUP_TEMPLATE, tableName);
    }

    /**
     * 在当前事务中保存记录
     *
     * @param record 记录
     * @return 主键
     */
    @Override
    public Long save(OutboxRecordEntity record) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(SimpleKafkaOutboxConstant.PARAM_MESSAGE_ID, record.getMessageId())
                .addValue(SimpleKafkaOutboxConstant.PARAM_TOPIC, record.getTopic())
                .addValue(SimpleKafkaOutboxConstant.PARAM_RECORD_KEY, record.getRecordKey())
                .addValue(SimpleKafkaOutboxConstant.PARAM_ROUTE_KEY, record.getRouteKey())
                .addValue(SimpleKafkaOutboxConstant.PARAM_DATASOURCE_KEY, record.getDatasourceKey())
                .addValue(SimpleKafkaOutboxConstant.PARAM_PARTITION, record.getPartition())
                .addValue(SimpleKafkaOutboxConstant.PARAM_MESSAGE_TIMESTAMP, record.getMessageTimestamp())
                .addValue(SimpleKafkaOutboxConstant.PARAM_MESSAGE_TYPE, record.getMessageType())
                .addValue(SimpleKafkaOutboxConstant.PARAM_PAYLOAD_KIND, record.getPayloadKind())
                .addValue(SimpleKafkaOutboxConstant.PARAM_PAYLOAD_JSON, record.getPayloadJson())
                .addValue(SimpleKafkaOutboxConstant.PARAM_HEADERS_JSON, record.getHeadersJson())
                .addValue(SimpleKafkaOutboxConstant.PARAM_ATTRIBUTES_JSON, record.getAttributesJson())
                .addValue(SimpleKafkaOutboxConstant.PARAM_ENVELOPE_ENABLED, record.getEnvelopeEnabled())
                .addValue(SimpleKafkaOutboxConstant.PARAM_TRACE_ID, record.getTraceId())
                .addValue(SimpleKafkaOutboxConstant.PARAM_SCHEMA_VERSION, record.getSchemaVersion())
                .addValue(SimpleKafkaOutboxConstant.PARAM_STATUS, OutboxStatus.PENDING.getCode());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(insertSql, parameters, keyHolder, new String[]{"id"});
            Number key = keyHolder.getKey();
            if (key == null) {
                throw persistenceFailed(null);
            }
            return key.longValue();
        } catch (DuplicateKeyException e) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_004, ErrorMessage.KAFKA_OUTBOX_004, e);
        } catch (DataIntegrityViolationException e) {
            throw persistenceFailed(e);
        } catch (KafkaOutboxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw persistenceFailed(e);
        }
    }

    /**
     * 在短事务中扫描并领取记录
     *
     * @param candidateLimit 候选上限
     * @param leaseMicros    租约微秒数
     * @return 已领取记录
     */
    @Override
    public List<OutboxRecordEntity> claim(final int candidateLimit, final long leaseMicros) {
        try {
            return transactionTemplate.execute(status -> doClaim(candidateLimit, leaseMicros));
        } catch (KafkaOutboxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw persistenceFailed(e);
        }
    }

    /**
     * 在短事务中合并待投递/重试候选与租约到期候选，按时间+主键排序后逐条 CAS 领取。
     */
    private List<OutboxRecordEntity> doClaim(int candidateLimit, long leaseMicros) {
        MapSqlParameterSource queryParameters = new MapSqlParameterSource()
                .addValue(SimpleKafkaOutboxConstant.PARAM_CANDIDATE_LIMIT, candidateLimit)
                .addValue(SimpleKafkaOutboxConstant.PARAM_PENDING_STATUS, OutboxStatus.PENDING.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_RETRY_WAIT_STATUS, OutboxStatus.RETRY_WAIT.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode());
        List<OutboxCandidate> merged = new ArrayList<>();
        merged.addAll(jdbcTemplate.query(readyCandidateSql, queryParameters, candidateRowMapper()));
        merged.addAll(jdbcTemplate.query(expiredCandidateSql, queryParameters, candidateRowMapper()));
        Collections.sort(merged, (left, right) -> {
            int timeCompare = left.getEligibleAt().compareTo(right.getEligibleAt());
            return timeCompare != SimpleKafkaOutboxConstant.ZERO
                    ? timeCompare : left.getRecordId().compareTo(right.getRecordId());
        });
        Set<Long> seen = new HashSet<>();
        List<OutboxRecordEntity> claimed = new ArrayList<>();
        for (OutboxCandidate candidate : merged) {
            if (claimed.size() >= candidateLimit || !seen.add(candidate.getRecordId())) {
                continue;
            }
            String ownerToken = UUID.randomUUID().toString();
            MapSqlParameterSource claimParameters = ownerParameters(candidate.getRecordId(), ownerToken,
                    candidate.getVersion())
                    .addValue(SimpleKafkaOutboxConstant.PARAM_LEASE_MICROS, leaseMicros)
                    .addValue(SimpleKafkaOutboxConstant.PARAM_PENDING_STATUS, OutboxStatus.PENDING.getCode())
                    .addValue(SimpleKafkaOutboxConstant.PARAM_RETRY_WAIT_STATUS, OutboxStatus.RETRY_WAIT.getCode())
                    .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode());
            if (jdbcTemplate.update(claimSql, claimParameters) == SimpleKafkaOutboxConstant.ONE) {
                long claimedVersion = candidate.getVersion() + SimpleKafkaOutboxConstant.ONE;
                MapSqlParameterSource selectParameters = ownerParameters(candidate.getRecordId(), ownerToken,
                        claimedVersion)
                        .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode());
                List<OutboxRecordEntity> records = jdbcTemplate.query(selectByOwnerSql, selectParameters,
                        recordRowMapper());
                if (!records.isEmpty()) {
                    claimed.add(records.get(SimpleKafkaOutboxConstant.ZERO));
                }
            }
        }
        return claimed;
    }

    /**
     * 条件标记发送成功。
     */
    @Override
    public boolean markSent(OutboxRecordEntity record) {
        MapSqlParameterSource parameters = ownerParameters(record)
                .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_SENT_STATUS, OutboxStatus.SENT.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_BROKER_TOPIC, record.getBrokerTopic())
                .addValue(SimpleKafkaOutboxConstant.PARAM_BROKER_PARTITION, record.getBrokerPartition())
                .addValue(SimpleKafkaOutboxConstant.PARAM_BROKER_OFFSET, record.getBrokerOffset())
                .addValue(SimpleKafkaOutboxConstant.PARAM_BROKER_TIMESTAMP, record.getBrokerTimestamp());
        return updateState(markSentSql, parameters);
    }

    /**
     * 条件标记等待重试。
     */
    @Override
    public boolean markRetry(OutboxRecordEntity record, long delayMicros, String errorCode, String errorSummary) {
        MapSqlParameterSource parameters = failureParameters(record, errorCode, errorSummary)
                .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_RETRY_WAIT_STATUS, OutboxStatus.RETRY_WAIT.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_DELAY_MICROS, delayMicros);
        return updateState(markRetrySql, parameters);
    }

    /**
     * 条件标记毒消息。
     */
    @Override
    public boolean markPoison(OutboxRecordEntity record, String errorCode, String errorSummary) {
        MapSqlParameterSource parameters = failureParameters(record, errorCode, errorSummary)
                .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_POISON_STATUS, OutboxStatus.POISON.getCode());
        return updateState(markPoisonSql, parameters);
    }

    /**
     * 停机发送前条件释放租约。
     */
    @Override
    public boolean releaseBeforeSend(OutboxRecordEntity record, String errorCode, String errorSummary) {
        MapSqlParameterSource parameters = failureParameters(record, errorCode, errorSummary)
                .addValue(SimpleKafkaOutboxConstant.PARAM_PROCESSING_STATUS, OutboxStatus.PROCESSING.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_RETRY_WAIT_STATUS, OutboxStatus.RETRY_WAIT.getCode());
        return updateState(releaseSql, parameters);
    }

    /**
     * 按数据库时钟计算固定过期边界。
     */
    @Override
    public Timestamp resolveExpireBefore(int retentionDays) {
        try {
            String sql = String.format(SimpleKafkaOutboxConstant.SQL_SELECT_EXPIRE_BEFORE_TEMPLATE, retentionDays);
            return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Timestamp.class);
        } catch (RuntimeException e) {
            throw persistenceFailed(e);
        }
    }

    /**
     * 在单个短事务中查询并删除一批清理候选。
     */
    @Override
    public OutboxCleanupBatchResult cleanupBatch(final Timestamp expireBefore, final Timestamp lastSentAt,
                                                 final Long lastId, final int batchSize) {
        try {
            return transactionTemplate.execute(status -> doCleanupBatch(expireBefore, lastSentAt, lastId, batchSize));
        } catch (KafkaOutboxException e) {
            throw e;
        } catch (RuntimeException e) {
            throw persistenceFailed(e);
        }
    }

    /**
     * 在单个短事务中按 keyset 游标查询并删除一批过期 SENT 记录。
     */
    private OutboxCleanupBatchResult doCleanupBatch(Timestamp expireBefore, Timestamp lastSentAt,
                                                    Long lastId, int batchSize) {
        MapSqlParameterSource queryParameters = new MapSqlParameterSource()
                .addValue(SimpleKafkaOutboxConstant.PARAM_SENT_STATUS, OutboxStatus.SENT.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_EXPIRE_BEFORE, expireBefore)
                .addValue(SimpleKafkaOutboxConstant.PARAM_LAST_SENT_AT, lastSentAt)
                .addValue(SimpleKafkaOutboxConstant.PARAM_LAST_ID,
                        lastId == null ? SimpleKafkaOutboxConstant.ZERO_LONG : lastId)
                .addValue(SimpleKafkaOutboxConstant.PARAM_BATCH_SIZE, batchSize);
        String sql = lastSentAt == null ? cleanupFirstCandidateSql : cleanupNextCandidateSql;
        List<OutboxCleanupCandidate> candidates = jdbcTemplate.query(sql, queryParameters,
                (resultSet, rowNum) -> new OutboxCleanupCandidate(
                        resultSet.getLong("id"), resultSet.getTimestamp("sent_at")));
        if (candidates.isEmpty()) {
            return new OutboxCleanupBatchResult(SimpleKafkaOutboxConstant.ZERO, lastSentAt, lastId,
                    SimpleKafkaOutboxConstant.ZERO);
        }
        List<Long> candidateIds = new ArrayList<>();
        for (OutboxCleanupCandidate candidate : candidates) {
            candidateIds.add(candidate.getRecordId());
        }
        MapSqlParameterSource deleteParameters = new MapSqlParameterSource()
                .addValue(SimpleKafkaOutboxConstant.PARAM_SENT_STATUS, OutboxStatus.SENT.getCode())
                .addValue(SimpleKafkaOutboxConstant.PARAM_EXPIRE_BEFORE, expireBefore)
                .addValue(SimpleKafkaOutboxConstant.PARAM_CANDIDATE_IDS, candidateIds);
        int deletedCount = jdbcTemplate.update(cleanupDeleteSql, deleteParameters);
        OutboxCleanupCandidate cursor = candidates.get(candidates.size() - SimpleKafkaOutboxConstant.ONE);
        return new OutboxCleanupBatchResult(deletedCount, cursor.getSentAt(), cursor.getRecordId(),
                candidates.size());
    }

    /**
     * 执行状态迁移 SQL，仅当影响行数为 1 时视为 CAS 成功。
     */
    private boolean updateState(String sql, MapSqlParameterSource parameters) {
        try {
            return jdbcTemplate.update(sql, parameters) == SimpleKafkaOutboxConstant.ONE;
        } catch (RuntimeException e) {
            throw persistenceFailed(e);
        }
    }

    /**
     * 构造记录级 owner+version CAS 参数。
     */
    private MapSqlParameterSource ownerParameters(OutboxRecordEntity record) {
        return ownerParameters(record.getId(), record.getOwnerToken(), record.getVersion());
    }

    /**
     * 构造 owner+version CAS 参数，供领取与回写复用。
     */
    private MapSqlParameterSource ownerParameters(Long id, String ownerToken, Long version) {
        return new MapSqlParameterSource()
                .addValue(SimpleKafkaOutboxConstant.PARAM_ID, id)
                .addValue(SimpleKafkaOutboxConstant.PARAM_OWNER_TOKEN, ownerToken)
                .addValue(SimpleKafkaOutboxConstant.PARAM_VERSION, version);
    }

    /**
     * 构造失败回写参数：owner+version CAS 基础上加错误码与脱敏摘要。
     */
    private MapSqlParameterSource failureParameters(OutboxRecordEntity record, String errorCode,
                                                    String errorSummary) {
        return ownerParameters(record)
                .addValue(SimpleKafkaOutboxConstant.PARAM_ERROR_CODE, errorCode)
                .addValue(SimpleKafkaOutboxConstant.PARAM_ERROR_SUMMARY, errorSummary);
    }

    /**
     * 候选行映射器：仅读取领取所需的主键、版本与可领取时间。
     */
    private RowMapper<OutboxCandidate> candidateRowMapper() {
        return (resultSet, rowNum) -> new OutboxCandidate(resultSet.getLong("id"),
                resultSet.getLong("version"), resultSet.getTimestamp("eligible_at"));
    }

    /**
     * 领取后按 owner+version 复查的完整记录行映射器，保证返回的是本 worker 真正持有的记录。
     */
    private RowMapper<OutboxRecordEntity> recordRowMapper() {
        return new RowMapper<OutboxRecordEntity>() {
            @Override
            public OutboxRecordEntity mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                return OutboxRecordEntity.builder()
                        .id(resultSet.getLong("id"))
                        .messageId(resultSet.getString("message_id"))
                        .topic(resultSet.getString("topic"))
                        .recordKey(resultSet.getString("record_key"))
                        .routeKey(resultSet.getString("route_key"))
                        .datasourceKey(resultSet.getString("datasource_key"))
                        .partition(nullableInteger(resultSet, "partition"))
                        .messageTimestamp(nullableLong(resultSet, "message_timestamp"))
                        .messageType(resultSet.getString("message_type"))
                        .payloadKind(resultSet.getString("payload_kind"))
                        .payloadJson(resultSet.getString("payload_json"))
                        .headersJson(resultSet.getString("headers_json"))
                        .attributesJson(resultSet.getString("attributes_json"))
                        .envelopeEnabled(nullableBoolean(resultSet, "envelope_enabled"))
                        .traceId(resultSet.getString("trace_id"))
                        .schemaVersion(nullableInteger(resultSet, "schema_version"))
                        .status(resultSet.getString("status"))
                        .attempt(nullableInteger(resultSet, "attempt"))
                        .availableAt(resultSet.getTimestamp("available_at"))
                        .ownerToken(resultSet.getString("owner_token"))
                        .leaseUntil(resultSet.getTimestamp("lease_until"))
                        .lastErrorCode(resultSet.getString("last_error_code"))
                        .lastErrorSummary(resultSet.getString("last_error_summary"))
                        .brokerTopic(resultSet.getString("broker_topic"))
                        .brokerPartition(nullableInteger(resultSet, "broker_partition"))
                        .brokerOffset(nullableLong(resultSet, "broker_offset"))
                        .brokerTimestamp(nullableLong(resultSet, "broker_timestamp"))
                        .createdAt(resultSet.getTimestamp("created_at"))
                        .sentAt(resultSet.getTimestamp("sent_at"))
                        .updatedAt(resultSet.getTimestamp("updated_at"))
                        .version(nullableLong(resultSet, "version"))
                        .build();
            }
        };
    }

    /**
     * 读取可空 Integer，按 wasNull 判断是否为数据库 NULL。
     */
    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * 读取可空 Long，按 wasNull 判断是否为数据库 NULL。
     */
    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * 读取可空 Boolean，按 wasNull 判断是否为数据库 NULL。
     */
    private Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * 将 SQL 模板按已校验表名格式化为可执行 SQL。
     */
    private String format(String template, String tableName) {
        return String.format(template, tableName);
    }

    /**
     * 构造持久化失败异常，错误码统一为 KAFKA_OUTBOX_006。
     */
    private KafkaOutboxException persistenceFailed(Throwable cause) {
        return cause == null
                ? new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_006, ErrorMessage.KAFKA_OUTBOX_006)
                : new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_006, ErrorMessage.KAFKA_OUTBOX_006, cause);
    }
}
