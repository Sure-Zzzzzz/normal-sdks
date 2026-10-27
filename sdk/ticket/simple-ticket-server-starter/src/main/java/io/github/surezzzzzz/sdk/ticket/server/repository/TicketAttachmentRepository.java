package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 工单附件数据访问（commentId nullable——提交期挂单、回复期挂留言）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketAttachmentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketAttachmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 登记附件。
     *
     * @param attachment 附件
     * @return 自增主键
     */
    public long insert(TicketAttachment attachment) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("ticketId", attachment.getTicketId())
                .addValue("commentId", attachment.getCommentId())
                .addValue("objectKey", attachment.getObjectKey())
                .addValue("fileName", attachment.getFileName())
                .addValue("size", attachment.getSize())
                .addValue("contentType", attachment.getContentType())
                .addValue("uploadedBy", attachment.getUploadedBy())
                .addValue("createdAt", Timestamp.from(attachment.getCreatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket_attachment (ticket_id, comment_id, object_key, file_name, "
                + "size, content_type, uploaded_by, created_at) VALUES (:ticketId, :commentId, :objectKey, "
                + ":fileName, :size, :contentType, :uploadedBy, :createdAt)", p, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 工单附件列表。
     *
     * @param ticketId 工单 ID
     * @return 附件列表
     */
    public List<TicketAttachment> findByTicket(long ticketId) {
        return jdbcTemplate.query("SELECT * FROM ticket_attachment WHERE ticket_id = :ticketId "
                + "ORDER BY created_at ASC", new MapSqlParameterSource("ticketId", ticketId), ROW_MAPPER);
    }

    /**
     * 按 ID 查附件（下载签发前取对象键）。
     *
     * @param id 附件 ID
     * @return 附件
     */
    public Optional<TicketAttachment> findById(long id) {
        List<TicketAttachment> result = jdbcTemplate.query(
                "SELECT * FROM ticket_attachment WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 按对象键查已登记附件，防止同一上传对象被重复挂靠到不同工单。
     *
     * @param objectKey 对象键
     * @return 已登记附件
     */
    public Optional<TicketAttachment> findByObjectKey(String objectKey) {
        List<TicketAttachment> result = jdbcTemplate.query(
                "SELECT * FROM ticket_attachment WHERE object_key = :objectKey LIMIT 1",
                new MapSqlParameterSource("objectKey", objectKey), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 工单附件计数（上限校验）。
     *
     * @param ticketId 工单 ID
     * @return 计数
     */
    public long countByTicket(long ticketId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_attachment WHERE ticket_id = :ticketId",
                new MapSqlParameterSource("ticketId", ticketId), Long.class);
        return count == null ? 0 : count;
    }

    private static final RowMapper<TicketAttachment> ROW_MAPPER = new RowMapper<TicketAttachment>() {
        @Override
        public TicketAttachment mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long commentId = rs.getObject("comment_id") == null ? null : rs.getLong("comment_id");
            return TicketAttachment.builder()
                    .id(rs.getLong("id"))
                    .ticketId(rs.getLong("ticket_id"))
                    .commentId(commentId)
                    .objectKey(rs.getString("object_key"))
                    .fileName(rs.getString("file_name"))
                    .size(rs.getLong("size"))
                    .contentType(rs.getString("content_type"))
                    .uploadedBy(rs.getString("uploaded_by"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        }
    };
}
