package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.query.OutboxRecordBrowseQuery;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDBC 的 Outbox 管理数据访问。
 *
 * @author surezzzzzz
 */
public class JdbcKafkaOutboxManagementRepository implements KafkaOutboxManagementRepository {
    private static final String COLUMNS = "id, message_id, topic, route_key, datasource_key, message_type, payload_kind, "
            + "status, attempt, available_at, last_error_code, last_error_summary, broker_topic, broker_partition, "
            + "broker_offset, broker_timestamp, created_at, sent_at, updated_at";
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String tableName;

    public JdbcKafkaOutboxManagementRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                               TransactionTemplate transactionTemplate, String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.tableName = tableName;
    }

    @Override
    public Map<OutboxStatus, Long> countByStatus() {
        try {
            Map<OutboxStatus, Long> result = new EnumMap<OutboxStatus, Long>(OutboxStatus.class);
            for (OutboxStatus status : OutboxStatus.values()) {
                result.put(status, 0L);
            }
            jdbcTemplate.query("SELECT status, COUNT(*) AS count FROM " + tableName + " GROUP BY status", rs -> {
                OutboxStatus status = requiredStatus(rs.getString("status"));
                result.put(status, rs.getLong("count"));
            });
            return result;
        } catch (DataAccessException ex) {
            throw failed(ex);
        }
    }

    @Override
    public OutboxRecord findById(Long recordId) {
        return find(" WHERE id = :recordId", new MapSqlParameterSource("recordId", recordId));
    }

    @Override
    public OutboxRecord findByMessageId(String messageId) {
        return find(" WHERE message_id = :messageId", new MapSqlParameterSource("messageId", messageId));
    }

    @Override
    public List<OutboxRecord> browse(OutboxRecordBrowseQuery query) {
        String sql = "SELECT " + COLUMNS + " FROM " + tableName + " WHERE status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("status", query.getStatus().getCode());
        if (query.getCursorAvailableAt() != null) {
            sql += " AND (available_at < :cursorAvailableAt OR (available_at = :cursorAvailableAt AND id < :cursorId))";
            params.addValue("cursorAvailableAt", Timestamp.from(query.getCursorAvailableAt())).addValue("cursorId", query.getCursorId());
        }
        sql += " ORDER BY available_at DESC, id DESC LIMIT :limit";
        params.addValue("limit", query.getSize() + 1);
        try {
            return jdbcTemplate.query(sql, params, rowMapper());
        } catch (DataAccessException ex) {
            throw failed(ex);
        }
    }

    @Override
    public int resetPoison(Long recordId) {
        String sql = "UPDATE " + tableName + " SET status = :pendingStatus, attempt = 0, available_at = CURRENT_TIMESTAMP(3), "
                + "owner_token = NULL, lease_until = NULL, last_error_code = NULL, last_error_summary = NULL, "
                + "version = version + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE id = :recordId AND status = :poisonStatus";
        try {
            Integer count = transactionTemplate.execute(status -> jdbcTemplate.update(sql, new MapSqlParameterSource()
                    .addValue("pendingStatus", OutboxStatus.PENDING.getCode()).addValue("poisonStatus", OutboxStatus.POISON.getCode())
                    .addValue("recordId", recordId)));
            return count == null ? 0 : count;
        } catch (DataAccessException ex) {
            throw failed(ex);
        }
    }

    private OutboxRecord find(String condition, MapSqlParameterSource params) {
        try {
            List<OutboxRecord> records = jdbcTemplate.query("SELECT " + COLUMNS + " FROM " + tableName + condition, params, rowMapper());
            return records.isEmpty() ? null : records.get(0);
        } catch (DataAccessException ex) {
            throw failed(ex);
        }
    }

    private RowMapper<OutboxRecord> rowMapper() {
        return new RowMapper<OutboxRecord>() {
            public OutboxRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                return OutboxRecord.builder().recordId(rs.getLong("id")).messageId(rs.getString("message_id")).topic(rs.getString("topic"))
                        .routeKey(rs.getString("route_key")).datasourceKey(rs.getString("datasource_key"))
                        .messageType(rs.getString("message_type")).payloadKind(requiredPayloadKind(rs.getString("payload_kind")))
                        .status(requiredStatus(rs.getString("status"))).attempt(nullableInteger(rs, "attempt"))
                        .availableAt(instant(rs, "available_at"))
                        .lastErrorCode(rs.getString("last_error_code")).lastErrorSummary(rs.getString("last_error_summary"))
                        .brokerTopic(rs.getString("broker_topic")).brokerPartition(nullableInteger(rs, "broker_partition"))
                        .brokerOffset(nullableLong(rs, "broker_offset")).brokerTimestamp(nullableLong(rs, "broker_timestamp"))
                        .createdAt(instant(rs, "created_at")).sentAt(instant(rs, "sent_at")).updatedAt(instant(rs, "updated_at")).build();
            }
        };
    }

    private OutboxStatus requiredStatus(String value) {
        OutboxStatus status = OutboxStatus.fromCode(value);
        if (status == null) throw failed(null);
        return status;
    }

    private OutboxPayloadKind requiredPayloadKind(String value) {
        OutboxPayloadKind kind = OutboxPayloadKind.fromCode(value);
        if (kind == null) throw failed(null);
        return kind;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private KafkaOutboxManagementException failed(Throwable cause) {
        return new KafkaOutboxManagementException(ErrorCode.PERSISTENCE_FAILED, ErrorMessage.PERSISTENCE_FAILED, cause);
    }
}
