package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 工单数据访问。乐观锁更新：WHERE version = :expectVersion，影响行数 0 映射 409。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入工单。
     *
     * @param ticket 工单
     * @return 自增主键
     */
    public long insert(Ticket ticket) {
        MapSqlParameterSource p = params(ticket);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket (ticket_no, type_code, subject, description, form_answers, "
                + "priority, status, owner_subject_id, owner_name, assignee_group, assignee_subject_id, "
                + "assignee_name, response_deadline, resolution_deadline, confirm_deadline, "
                + "pending_accumulated_ms, pause_started_at, overdue_response, overdue_resolution, source, "
                + "creator_credential_id, idempotency_key, request_fingerprint, related_ticket_no, "
                + "last_activity_at, accepted_at, "
                + "resolved_at, closed_at, created_at, version) VALUES (:ticketNo, :typeCode, :subject, "
                + ":description, :formAnswers, :priority, :status, :ownerSubjectId, :ownerName, "
                + ":assigneeGroup, :assigneeSubjectId, :assigneeName, :responseDeadline, :resolutionDeadline, "
                + ":confirmDeadline, :pendingAccumulatedMs, :pauseStartedAt, :overdueResponse, "
                + ":overdueResolution, :source, :creatorCredentialId, :idempotencyKey, :requestFingerprint, "
                + ":relatedTicketNo, "
                + ":lastActivityAt, :acceptedAt, :resolvedAt, :closedAt, :createdAt, :version)", p, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 乐观锁更新（领域变更后整行落库；版本不符影响 0 行）。
     *
     * @param ticket 工单
     * @return 影响行数
     */
    public int update(Ticket ticket) {
        MapSqlParameterSource p = params(ticket)
                .addValue("id", ticket.getId())
                .addValue("expectVersion", ticket.getVersion() - 1);
        return jdbcTemplate.update("UPDATE ticket SET type_code = :typeCode, subject = :subject, "
                + "description = :description, form_answers = :formAnswers, priority = :priority, "
                + "status = :status, owner_subject_id = :ownerSubjectId, owner_name = :ownerName, "
                + "assignee_group = :assigneeGroup, assignee_subject_id = :assigneeSubjectId, "
                + "assignee_name = :assigneeName, response_deadline = :responseDeadline, "
                + "resolution_deadline = :resolutionDeadline, confirm_deadline = :confirmDeadline, "
                + "pending_accumulated_ms = :pendingAccumulatedMs, pause_started_at = :pauseStartedAt, "
                + "overdue_response = :overdueResponse, overdue_resolution = :overdueResolution, "
                + "source = :source, creator_credential_id = :creatorCredentialId, "
                + "idempotency_key = :idempotencyKey, request_fingerprint = :requestFingerprint, "
                + "related_ticket_no = :relatedTicketNo, "
                + "last_activity_at = :lastActivityAt, accepted_at = :acceptedAt, resolved_at = :resolvedAt, "
                + "closed_at = :closedAt, version = :version WHERE id = :id AND version = :expectVersion", p);
    }

    /**
     * 按单号查询。
     *
     * @param ticketNo 单号
     * @return 工单
     */
    public Optional<Ticket> findByNo(String ticketNo) {
        List<Ticket> result = jdbcTemplate.query(
                "SELECT * FROM ticket WHERE ticket_no = :ticketNo",
                new MapSqlParameterSource("ticketNo", ticketNo), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 按主键查询工单。
     *
     * @param id 工单 ID
     * @return 工单
     */
    public Optional<Ticket> findById(long id) {
        List<Ticket> result = jdbcTemplate.query(
                "SELECT * FROM ticket WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 幂等键查单（openapi 提交；同键异体由 service 判 409）。
     *
     * @param source 来源
     * @param credentialId 凭据标识
     * @param idempotencyKey 幂等键
     * @return 工单
     */
    public Optional<Ticket> findByIdempotency(TicketSource source, String credentialId, String idempotencyKey) {
        List<Ticket> result = jdbcTemplate.query(
                "SELECT * FROM ticket WHERE source = :source AND creator_credential_id = :credential "
                        + "AND idempotency_key = :key",
                new MapSqlParameterSource("source", source.name())
                        .addValue("credential", credentialId)
                        .addValue("key", idempotencyKey), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 插入冲突后读取最新已提交记录，避免事务的可重复读快照仍看不到竞争者。
     *
     * @param source 来源
     * @param credentialId 凭据标识
     * @param idempotencyKey 幂等键
     * @return 工单
     */
    public Optional<Ticket> findByIdempotencyCurrent(TicketSource source, String credentialId,
                                                      String idempotencyKey) {
        List<Ticket> result = jdbcTemplate.query(
                "SELECT * FROM ticket WHERE source = :source AND creator_credential_id = :credential "
                        + "AND idempotency_key = :key FOR UPDATE",
                new MapSqlParameterSource("source", source.name())
                        .addValue("credential", credentialId)
                        .addValue("key", idempotencyKey), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 我的工单（owner 维度，lastActivityAt 降序）。
     *
     * @param ownerSubjectId 提单人
     * @param statuses 状态过滤（空=全部）
     * @param offset 偏移
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> findMine(String ownerSubjectId, Collection<TicketStatus> statuses,
                                 int offset, int size) {
        return findMine(ownerSubjectId, statuses, null, null, null, null, offset, size);
    }

    /** 带类型、日期和关键词过滤的我的工单查询。 */
    public List<Ticket> findMine(String ownerSubjectId, Collection<TicketStatus> statuses,
                                 String typeCode, Instant from, Instant to, String query,
                                 int offset, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ticket WHERE owner_subject_id = :owner");
        MapSqlParameterSource p = new MapSqlParameterSource("owner", ownerSubjectId);
        sql.append(statusClause(statuses, p));
        appendCommonFilters(sql, p, typeCode, from, to, query);
        sql.append(" ORDER BY last_activity_at DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", size).addValue("offset", offset);
        return jdbcTemplate.query(sql.toString(), p, ROW_MAPPER);
    }

    /**
     * 组队列（组内维度，priority 降序+创建升序——DESIGN §6.3 排序钉死）。
     *
     * @param groupCodes 组编码集
     * @param statuses 状态过滤
     * @param offset 偏移
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> findQueue(Collection<String> groupCodes, Collection<TicketStatus> statuses,
                                  int offset, int size) {
        return findQueue(groupCodes, statuses, null, null, null, offset, size);
    }

    /** 带优先级、超时和关键词过滤的组队列查询。 */
    public List<Ticket> findQueue(Collection<String> groupCodes, Collection<TicketStatus> statuses,
                                  TicketPriority priority, Boolean overdue, String query,
                                  int offset, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM ticket WHERE (assignee_group IN (:groups) "
                        + "OR (assignee_group IS NULL AND type_code IN "
                        + "(SELECT code FROM ticket_type WHERE default_group IN (:groups) AND enabled = 1)))");
        MapSqlParameterSource p = new MapSqlParameterSource("groups", groupCodes);
        sql.append(statusClause(statuses, p));
        if (priority != null) {
            sql.append(" AND priority = :priority");
            p.addValue("priority", priority.name());
        }
        if (Boolean.TRUE.equals(overdue)) {
            sql.append(" AND (overdue_response = 1 OR overdue_resolution = 1)");
        }
        appendQueryFilter(sql, p, query);
        sql.append(" ORDER BY FIELD(priority, 'URGENT', 'HIGH', 'NORMAL', 'LOW'), created_at ASC "
                + "LIMIT :limit OFFSET :offset");
        p.addValue("limit", size).addValue("offset", offset);
        return jdbcTemplate.query(sql.toString(), p, ROW_MAPPER);
    }

    /**
     * 全部工单（admin）。
     *
     * @param statuses 状态过滤
     * @param offset 偏移
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> findAll(Collection<TicketStatus> statuses, int offset, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ticket WHERE 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        sql.append(statusClause(statuses, p));
        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", size).addValue("offset", offset);
        return jdbcTemplate.query(sql.toString(), p, ROW_MAPPER);
    }

    /**
     * openapi 凭据的工单列表（消费方轮询）。
     *
     * @param credentialId 凭据标识
     * @param statuses 状态过滤
     * @param offset 偏移
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> findByCredential(String credentialId, Collection<TicketStatus> statuses,
                                         int offset, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM ticket WHERE creator_credential_id = :credential");
        MapSqlParameterSource p = new MapSqlParameterSource("credential", credentialId);
        sql.append(statusClause(statuses, p));
        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        p.addValue("limit", size).addValue("offset", offset);
        return jdbcTemplate.query(sql.toString(), p, ROW_MAPPER);
    }

    /**
     * 待扫描批（SLA：未终态+未置超时+到限；或 RESOLVED 确认到限）。
     *
     * @param now 判定时刻
     * @param limit 批大小
     * @return 列表
     */
    public List<Ticket> findScanCandidates(Instant now, int limit) {
        return jdbcTemplate.query("SELECT * FROM ticket WHERE "
                + "(status = 'SUBMITTED' AND overdue_response = 0 AND response_deadline < :now1) "
                + "OR (status IN ('PROCESSING', 'PENDING_INFO') AND overdue_resolution = 0 "
                + "AND DATE_ADD(DATE_ADD(resolution_deadline, "
                + "INTERVAL (pending_accumulated_ms * 1000) MICROSECOND), "
                + "INTERVAL (CASE WHEN pause_started_at IS NULL THEN 0 "
                + "ELSE TIMESTAMPDIFF(MICROSECOND, pause_started_at, :now2) END) MICROSECOND) < :now3) "
                + "OR (status = 'RESOLVED' AND confirm_deadline < :now4) "
                + "LIMIT :limit",
                new MapSqlParameterSource("now1", Timestamp.from(now))
                        .addValue("now2", Timestamp.from(now))
                        .addValue("now3", Timestamp.from(now))
                        .addValue("now4", Timestamp.from(now))
                        .addValue("limit", limit), ROW_MAPPER);
    }

    private String statusClause(Collection<TicketStatus> statuses, MapSqlParameterSource p) {
        if (statuses == null || statuses.isEmpty()) {
            return "";
        }
        List<String> names = new java.util.ArrayList<>();
        for (TicketStatus status : statuses) {
            names.add(status.name());
        }
        p.addValue("statuses", names);
        return " AND status IN (:statuses)";
    }

    private void appendCommonFilters(StringBuilder sql, MapSqlParameterSource p, String typeCode,
                                     Instant from, Instant to, String query) {
        if (typeCode != null && !typeCode.trim().isEmpty()) {
            sql.append(" AND type_code = :typeCode");
            p.addValue("typeCode", typeCode.trim());
        }
        if (from != null) {
            sql.append(" AND created_at >= :from");
            p.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND created_at < :to");
            p.addValue("to", Timestamp.from(to));
        }
        appendQueryFilter(sql, p, query);
    }

    private void appendQueryFilter(StringBuilder sql, MapSqlParameterSource p, String query) {
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (ticket_no = :query OR subject LIKE :queryPrefix)");
            p.addValue("query", query.trim()).addValue("queryPrefix", query.trim() + "%");
        }
    }

    private MapSqlParameterSource params(Ticket t) {
        return new MapSqlParameterSource()
                .addValue("ticketNo", t.getTicketNo())
                .addValue("typeCode", t.getTypeCode())
                .addValue("subject", t.getSubject())
                .addValue("description", t.getDescription(), Types.LONGVARCHAR)
                .addValue("formAnswers", t.getFormAnswers(), Types.LONGVARCHAR)
                .addValue("priority", t.getPriority() == null ? null : t.getPriority().name())
                .addValue("status", t.getStatus().name())
                .addValue("ownerSubjectId", t.getOwnerSubjectId())
                .addValue("ownerName", t.getOwnerName(), Types.VARCHAR)
                .addValue("assigneeGroup", t.getAssigneeGroup(), Types.VARCHAR)
                .addValue("assigneeSubjectId", t.getAssigneeSubjectId(), Types.VARCHAR)
                .addValue("assigneeName", t.getAssigneeName(), Types.VARCHAR)
                .addValue("responseDeadline", Timestamp.from(t.getResponseDeadline()))
                .addValue("resolutionDeadline", Timestamp.from(t.getResolutionDeadline()))
                .addValue("confirmDeadline", toSql(t.getConfirmDeadline()))
                .addValue("pendingAccumulatedMs", t.getPendingAccumulatedMs())
                .addValue("pauseStartedAt", toSql(t.getPauseStartedAt()))
                .addValue("overdueResponse", t.isOverdueResponse())
                .addValue("overdueResolution", t.isOverdueResolution())
                .addValue("source", t.getSource().name())
                .addValue("creatorCredentialId", t.getCreatorCredentialId(), Types.VARCHAR)
                .addValue("idempotencyKey", t.getIdempotencyKey(), Types.VARCHAR)
                .addValue("requestFingerprint", t.getRequestFingerprint(), Types.CHAR)
                .addValue("relatedTicketNo", t.getRelatedTicketNo(), Types.VARCHAR)
                .addValue("lastActivityAt", Timestamp.from(t.getLastActivityAt()))
                .addValue("acceptedAt", toSql(t.getAcceptedAt()))
                .addValue("resolvedAt", toSql(t.getResolvedAt()))
                .addValue("closedAt", toSql(t.getClosedAt()))
                .addValue("createdAt", Timestamp.from(t.getCreatedAt()))
                .addValue("version", t.getVersion());
    }

    private static Timestamp toSql(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static final RowMapper<Ticket> ROW_MAPPER = new RowMapper<Ticket>() {
        @Override
        public Ticket mapRow(ResultSet rs, int rowNum) throws SQLException {
            Ticket ticket = new Ticket();
            ticket.setId(rs.getLong("id"));
            ticket.setTicketNo(rs.getString("ticket_no"));
            ticket.setTypeCode(rs.getString("type_code"));
            ticket.setSubject(rs.getString("subject"));
            ticket.setDescription(rs.getString("description"));
            ticket.setFormAnswers(rs.getString("form_answers"));
            ticket.setPriority(TicketPriority.valueOf(rs.getString("priority")));
            ticket.setStatus(TicketStatus.valueOf(rs.getString("status")));
            ticket.setOwnerSubjectId(rs.getString("owner_subject_id"));
            ticket.setOwnerName(rs.getString("owner_name"));
            ticket.setAssigneeGroup(rs.getString("assignee_group"));
            ticket.setAssigneeSubjectId(rs.getString("assignee_subject_id"));
            ticket.setAssigneeName(rs.getString("assignee_name"));
            ticket.setResponseDeadline(rs.getTimestamp("response_deadline").toInstant());
            ticket.setResolutionDeadline(rs.getTimestamp("resolution_deadline").toInstant());
            Timestamp confirm = rs.getTimestamp("confirm_deadline");
            ticket.setConfirmDeadline(confirm == null ? null : confirm.toInstant());
            ticket.setPendingAccumulatedMs(rs.getLong("pending_accumulated_ms"));
            Timestamp pause = rs.getTimestamp("pause_started_at");
            ticket.setPauseStartedAt(pause == null ? null : pause.toInstant());
            ticket.setOverdueResponse(rs.getBoolean("overdue_response"));
            ticket.setOverdueResolution(rs.getBoolean("overdue_resolution"));
            ticket.setSource(TicketSource.valueOf(rs.getString("source")));
            ticket.setCreatorCredentialId(rs.getString("creator_credential_id"));
            ticket.setIdempotencyKey(rs.getString("idempotency_key"));
            ticket.setRequestFingerprint(rs.getString("request_fingerprint"));
            ticket.setRelatedTicketNo(rs.getString("related_ticket_no"));
            ticket.setLastActivityAt(rs.getTimestamp("last_activity_at").toInstant());
            Timestamp accepted = rs.getTimestamp("accepted_at");
            ticket.setAcceptedAt(accepted == null ? null : accepted.toInstant());
            Timestamp resolved = rs.getTimestamp("resolved_at");
            ticket.setResolvedAt(resolved == null ? null : resolved.toInstant());
            Timestamp closed = rs.getTimestamp("closed_at");
            ticket.setClosedAt(closed == null ? null : closed.toInstant());
            ticket.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            ticket.setVersion(rs.getLong("version"));
            return ticket;
        }
    };
}
