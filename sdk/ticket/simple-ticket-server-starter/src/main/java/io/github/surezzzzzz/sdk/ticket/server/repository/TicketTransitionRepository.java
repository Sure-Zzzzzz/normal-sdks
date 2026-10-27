package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketTransition;
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
import java.util.List;

/**
 * 状态转移记录数据访问（不可变只增）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketTransitionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketTransitionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 追加转移记录。
     *
     * @param transition 转移
     * @return 自增主键
     */
    public long insert(TicketTransition transition) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("ticketId", transition.getTicketId())
                .addValue("fromStatus", transition.getFromStatus().name())
                .addValue("toStatus", transition.getToStatus().name())
                .addValue("action", transition.getAction())
                .addValue("actorSubjectId", transition.getActorSubjectId(), Types.VARCHAR)
                .addValue("actorRole", transition.getActorRole().name())
                .addValue("reasonCode", transition.getReasonCode() == null ? null
                        : transition.getReasonCode().name(), Types.VARCHAR)
                .addValue("createdAt", Timestamp.from(transition.getCreatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket_transition (ticket_id, from_status, to_status, action, "
                + "actor_subject_id, actor_role, reason_code, created_at) VALUES (:ticketId, :fromStatus, "
                + ":toStatus, :action, :actorSubjectId, :actorRole, :reasonCode, :createdAt)", p, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 工单时间线（转移段，时间升序）。
     *
     * @param ticketId 工单 ID
     * @return 转移列表
     */
    public List<TicketTransition> findByTicket(long ticketId) {
        return jdbcTemplate.query("SELECT * FROM ticket_transition WHERE ticket_id = :ticketId "
                + "ORDER BY created_at ASC", new MapSqlParameterSource("ticketId", ticketId), ROW_MAPPER);
    }

    /**
     * 判断工单是否发生过指定动作，用于区分人工确认关闭与系统确认超时关闭。
     *
     * @param ticketId 工单 ID
     * @param action 动作
     * @return 是否存在
     */
    public boolean existsAction(long ticketId, String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_transition WHERE ticket_id = :ticketId AND action = :action",
                new MapSqlParameterSource().addValue("ticketId", ticketId).addValue("action", action),
                Integer.class);
        return count != null && count > 0;
    }

    /**
     * 状态转移游标分页（倒序，和留言游标保持同一口径）。
     *
     * @param ticketId 工单 ID
     * @param before 上一页最早时间
     * @param size 页大小
     * @return 转移记录
     */
    public List<TicketTransition> findByTicketBefore(long ticketId, String before, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM ticket_transition WHERE ticket_id = :ticketId ");
        MapSqlParameterSource p = new MapSqlParameterSource("ticketId", ticketId);
        if (before != null && !before.trim().isEmpty()) {
            sql.append("AND created_at < :before ");
            p.addValue("before", Timestamp.from(java.time.Instant.parse(before)));
        }
        sql.append("ORDER BY created_at DESC LIMIT :limit");
        p.addValue("limit", size);
        return jdbcTemplate.query(sql.toString(), p, ROW_MAPPER);
    }

    private static final RowMapper<TicketTransition> ROW_MAPPER = new RowMapper<TicketTransition>() {
        @Override
        public TicketTransition mapRow(ResultSet rs, int rowNum) throws SQLException {
            String reason = rs.getString("reason_code");
            return TicketTransition.builder()
                    .id(rs.getLong("id"))
                    .ticketId(rs.getLong("ticket_id"))
                    .fromStatus(TicketStatus.valueOf(rs.getString("from_status")))
                    .toStatus(TicketStatus.valueOf(rs.getString("to_status")))
                    .action(rs.getString("action"))
                    .actorSubjectId(rs.getString("actor_subject_id"))
                    .actorRole(ActorRole.valueOf(rs.getString("actor_role")))
                    .reasonCode(reason == null ? null : RequestInfoReason.valueOf(reason))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        }
    };
}
