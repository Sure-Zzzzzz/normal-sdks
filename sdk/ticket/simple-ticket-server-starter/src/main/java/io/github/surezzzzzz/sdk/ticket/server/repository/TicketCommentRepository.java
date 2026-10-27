package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketComment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.sql.Types;
import java.util.List;

/**
 * 工单留言数据访问（不可变只增；internal 可见性过滤在 service 层按角色执行）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketCommentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketCommentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 追加留言。
     *
     * @param comment 留言
     * @return 自增主键
     */
    public long insert(TicketComment comment) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("ticketId", comment.getTicketId())
                .addValue("authorSubjectId", comment.getAuthorSubjectId())
                .addValue("authorRole", comment.getAuthorRole().name())
                .addValue("authorName", comment.getAuthorName(), Types.VARCHAR)
                .addValue("body", comment.getBody())
                .addValue("internal", comment.isInternal())
                .addValue("createdAt", Timestamp.from(comment.getCreatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket_comment (ticket_id, author_subject_id, author_role, "
                + "author_name, body, internal, created_at) VALUES (:ticketId, :authorSubjectId, :authorRole, "
                + ":authorName, :body, :internal, :createdAt)", p, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 工单留言（时间升序；internal 过滤在 service）。
     *
     * @param ticketId 工单 ID
     * @return 留言列表
     */
    public List<TicketComment> findByTicket(long ticketId) {
        return jdbcTemplate.query("SELECT * FROM ticket_comment WHERE ticket_id = :ticketId "
                + "ORDER BY created_at ASC", new MapSqlParameterSource("ticketId", ticketId), ROW_MAPPER);
    }

    /**
     * 工单留言游标分页（before 为上一页最早留言时刻 ISO-8601，null=从最新开始；倒序取 size 条）。
     *
     * @param ticketId 工单 ID
     * @param before 游标时刻
     * @param size 条数
     * @return 留言列表
     */
    public List<TicketComment> findByTicketBefore(long ticketId, String before, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM ticket_comment WHERE ticket_id = :ticketId ");
        MapSqlParameterSource p = new MapSqlParameterSource("ticketId", ticketId);
        if (before != null && !before.trim().isEmpty()) {
            sql.append("AND created_at < :before ");
            p.addValue("before", java.sql.Timestamp.from(java.time.Instant.parse(before)));
        }
        sql.append("ORDER BY created_at DESC LIMIT :limit");
        p.addValue("limit", size);
        return jdbcTemplate.query(sql.toString(), p, ROW_MAPPER);
    }

    private static final RowMapper<TicketComment> ROW_MAPPER = new RowMapper<TicketComment>() {
        @Override
        public TicketComment mapRow(ResultSet rs, int rowNum) throws SQLException {
            return TicketComment.builder()
                    .id(rs.getLong("id"))
                    .ticketId(rs.getLong("ticket_id"))
                    .authorSubjectId(rs.getString("author_subject_id"))
                    .authorRole(ActorRole.valueOf(rs.getString("author_role")))
                    .authorName(rs.getString("author_name"))
                    .body(rs.getString("body"))
                    .internal(rs.getBoolean("internal"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        }
    };
}
