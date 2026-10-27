package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Portal 创建幂等记录。人员主体与幂等键组成隔离域，避免网络重试重复建单。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketPortalIdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketPortalIdempotencyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查找幂等记录。
     *
     * @param ownerSubjectId 提单人
     * @param idempotencyKey 幂等键
     * @return 记录
     */
    public Optional<Record> find(String ownerSubjectId, String idempotencyKey) {
        List<Record> result = jdbcTemplate.query(
                "SELECT ticket_id, request_fingerprint FROM ticket_portal_idempotency "
                        + "WHERE owner_subject_id = :owner AND idempotency_key = :key",
                new MapSqlParameterSource("owner", ownerSubjectId).addValue("key", idempotencyKey),
                (rs, rowNum) -> new Record(rs.getLong("ticket_id"), rs.getString("request_fingerprint")));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 写入幂等记录。
     *
     * @param ownerSubjectId 提单人
     * @param idempotencyKey 幂等键
     * @param fingerprint 请求摘要
     * @param ticketId 工单 ID
     */
    public void insert(String ownerSubjectId, String idempotencyKey, String fingerprint, long ticketId) {
        jdbcTemplate.update("INSERT INTO ticket_portal_idempotency (owner_subject_id, idempotency_key, "
                        + "request_fingerprint, ticket_id, created_at) VALUES (:owner, :key, :fingerprint, "
                        + ":ticketId, :createdAt)",
                new MapSqlParameterSource("owner", ownerSubjectId)
                        .addValue("key", idempotencyKey)
                        .addValue("fingerprint", fingerprint)
                        .addValue("ticketId", ticketId)
                        .addValue("createdAt", Timestamp.from(Instant.now())));
    }

    /** 幂等记录。 */
    public static final class Record {

        private final long ticketId;

        private final String requestFingerprint;

        private Record(long ticketId, String requestFingerprint) {
            this.ticketId = ticketId;
            this.requestFingerprint = requestFingerprint;
        }

        public long getTicketId() {
            return ticketId;
        }

        public String getRequestFingerprint() {
            return requestFingerprint;
        }
    }
}
