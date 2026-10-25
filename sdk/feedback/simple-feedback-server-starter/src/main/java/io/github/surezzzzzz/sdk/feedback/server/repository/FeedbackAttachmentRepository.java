package io.github.surezzzzzz.sdk.feedback.server.repository;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

/**
 * 附件数据访问。
 *
 * @author surezzzzzz
 */
@SimpleFeedbackServerComponent
@Repository
public class FeedbackAttachmentRepository {

    private static final RowMapper<FeedbackAttachment> ROW_MAPPER = new RowMapper<FeedbackAttachment>() {
        @Override
        public FeedbackAttachment mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp created = rs.getTimestamp("created_time");
            return FeedbackAttachment.builder()
                    .id(rs.getString("id"))
                    .submissionId(rs.getString("submission_id"))
                    .storageKey(rs.getString("storage_key"))
                    .originalFilename(rs.getString("original_filename"))
                    .contentType(rs.getString("content_type"))
                    .sizeBytes(rs.getLong("size_bytes"))
                    .createdTime(created == null ? null : created.toInstant())
                    .build();
        }
    };
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeedbackAttachmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入附件行。
     *
     * @param attachment 附件
     */
    public void insert(FeedbackAttachment attachment) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", attachment.getId())
                .addValue("submissionId", attachment.getSubmissionId())
                .addValue("storageKey", attachment.getStorageKey())
                .addValue("originalFilename", attachment.getOriginalFilename())
                .addValue("contentType", attachment.getContentType())
                .addValue("sizeBytes", attachment.getSizeBytes())
                .addValue("createdTime", Timestamp.from(attachment.getCreatedTime()));
        jdbcTemplate.update("INSERT INTO feedback_attachment (id, submission_id, storage_key, original_filename, "
                + "content_type, size_bytes, created_time) VALUES (:id, :submissionId, :storageKey, "
                + ":originalFilename, :contentType, :sizeBytes, :createdTime)", params);
    }

    /**
     * 按所属反馈查询附件列表。
     *
     * @param submissionId 反馈 ID
     * @return 附件列表
     */
    public List<FeedbackAttachment> findBySubmissionId(String submissionId) {
        return jdbcTemplate.query(
                "SELECT id, submission_id, storage_key, original_filename, content_type, size_bytes, created_time "
                        + "FROM feedback_attachment WHERE submission_id = :submissionId ORDER BY created_time",
                new MapSqlParameterSource("submissionId", submissionId), ROW_MAPPER);
    }

    /**
     * 列表批量取附件（1.0.1 优化：消灭列表逐行 N+1 点查）。
     *
     * @param submissionIds 反馈 ID 集（空集由调用方短路，不进 SQL）
     * @return 附件列表（按 submission_id、created_time 升序，调用方自行分组）
     */
    public List<FeedbackAttachment> findBySubmissionIds(Collection<String> submissionIds) {
        return jdbcTemplate.query(
                "SELECT id, submission_id, storage_key, original_filename, content_type, size_bytes, created_time "
                        + "FROM feedback_attachment WHERE submission_id IN (:submissionIds) "
                        + "ORDER BY submission_id, created_time",
                new MapSqlParameterSource("submissionIds", submissionIds), ROW_MAPPER);
    }

    /**
     * 按 ID 查询附件。
     *
     * @param id 附件 ID
     * @return 附件；不存在返回 null
     */
    public FeedbackAttachment findById(String id) {
        List<FeedbackAttachment> rows = jdbcTemplate.query(
                "SELECT id, submission_id, storage_key, original_filename, content_type, size_bytes, created_time "
                        + "FROM feedback_attachment WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
