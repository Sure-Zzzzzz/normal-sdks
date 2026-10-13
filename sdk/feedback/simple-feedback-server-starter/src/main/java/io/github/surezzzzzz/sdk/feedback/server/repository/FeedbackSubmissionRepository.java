package io.github.surezzzzzz.sdk.feedback.server.repository;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 反馈提交数据访问（DATA 范围条件并入 SQL：application IN 集由 scope 拼装，不做先授权后无条件查询）。
 *
 * @author surezzzzzz
 */
@SimpleFeedbackServerComponent
@Repository
public class FeedbackSubmissionRepository {

    private static final String ALL_COLUMNS =
            "id, application_code, kind, type, score, content, contact_email, submitter_subject_id, "
                    + "submitter_display_name, owner, context_path, environment, status, handled_by, handled_at, "
                    + "handler_note, reply, replied_by, replied_at, row_version, idempotency_key, "
                    + "idempotency_digest, first_response, created_time, updated_time";
    private static final RowMapper<FeedbackSubmission> ROW_MAPPER = new RowMapper<FeedbackSubmission>() {
        @Override
        public FeedbackSubmission mapRow(ResultSet rs, int rowNum) throws SQLException {
            return FeedbackSubmission.builder()
                    .id(rs.getString("id"))
                    .applicationCode(rs.getString("application_code"))
                    .kind(rs.getString("kind"))
                    .type(rs.getString("type"))
                    .score(rs.getInt("score"))
                    .content(rs.getString("content"))
                    .contactEmail(rs.getString("contact_email"))
                    .submitterSubjectId(rs.getString("submitter_subject_id"))
                    .submitterDisplayName(rs.getString("submitter_display_name"))
                    .owner(rs.getString("owner"))
                    .contextPath(rs.getString("context_path"))
                    .environment(rs.getString("environment"))
                    .status(rs.getString("status"))
                    .handledBy(rs.getString("handled_by"))
                    .handledAt(toInstant(rs.getTimestamp("handled_at")))
                    .handlerNote(rs.getString("handler_note"))
                    .reply(rs.getString("reply"))
                    .repliedBy(rs.getString("replied_by"))
                    .repliedAt(toInstant(rs.getTimestamp("replied_at")))
                    .rowVersion(rs.getInt("row_version"))
                    .idempotencyKey(rs.getString("idempotency_key"))
                    .idempotencyDigest(rs.getString("idempotency_digest"))
                    .firstResponse(rs.getString("first_response"))
                    .createdTime(toInstant(rs.getTimestamp("created_time")))
                    .updatedTime(toInstant(rs.getTimestamp("updated_time")))
                    .build();
        }
    };
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeedbackSubmissionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static void appendFilter(StringBuilder sql, MapSqlParameterSource params, Filter filter,
                                     Set<String> applicationCodes) {
        if (filter.applicationCode != null) {
            sql.append(" AND application_code = :fApplication");
            params.addValue("fApplication", filter.applicationCode);
        }
        if (filter.status != null) {
            sql.append(" AND status = :fStatus");
            params.addValue("fStatus", filter.status);
        }
        if (filter.type != null) {
            sql.append(" AND type = :fType");
            params.addValue("fType", filter.type);
        }
        if (filter.kind != null) {
            sql.append(" AND kind = :fKind");
            params.addValue("fKind", filter.kind);
        }
        if (filter.score != null) {
            sql.append(" AND score = :fScore");
            params.addValue("fScore", filter.score);
        }
        if (applicationCodes != null) {
            sql.append(" AND application_code IN (:fApplications)");
            params.addValue("fApplications", applicationCodes);
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * 插入提交行。
     *
     * @param submission 反馈提交
     */
    public void insert(FeedbackSubmission submission) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", submission.getId())
                .addValue("applicationCode", submission.getApplicationCode())
                .addValue("kind", submission.getKind())
                .addValue("type", submission.getType())
                .addValue("score", submission.getScore())
                .addValue("content", submission.getContent())
                .addValue("contactEmail", submission.getContactEmail())
                .addValue("submitterSubjectId", submission.getSubmitterSubjectId())
                .addValue("submitterDisplayName", submission.getSubmitterDisplayName())
                .addValue("owner", submission.getOwner())
                .addValue("contextPath", submission.getContextPath())
                .addValue("environment", submission.getEnvironment())
                .addValue("status", submission.getStatus())
                .addValue("rowVersion", submission.getRowVersion())
                .addValue("idempotencyKey", submission.getIdempotencyKey())
                .addValue("idempotencyDigest", submission.getIdempotencyDigest())
                .addValue("firstResponse", submission.getFirstResponse(), java.sql.Types.VARCHAR)
                .addValue("createdTime", Timestamp.from(submission.getCreatedTime()))
                .addValue("updatedTime", Timestamp.from(submission.getUpdatedTime()));
        jdbcTemplate.update("INSERT INTO feedback_submission (" + ALL_COLUMNS + ") VALUES ("
                        + ":id, :applicationCode, :kind, :type, :score, :content, :contactEmail, :submitterSubjectId, "
                        + ":submitterDisplayName, :owner, :contextPath, :environment, :status, NULL, NULL, NULL, NULL, "
                        + "NULL, NULL, :rowVersion, :idempotencyKey, :idempotencyDigest, :firstResponse, "
                        + ":createdTime, :updatedTime)",
                params);
    }

    /**
     * 按 ID 查询。
     *
     * @param id 反馈 ID
     * @return 反馈；不存在返回 null
     */
    public FeedbackSubmission findById(String id) {
        List<FeedbackSubmission> rows = jdbcTemplate.query(
                "SELECT " + ALL_COLUMNS + " FROM feedback_submission WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 幂等查找（owner+幂等键唯一）。
     *
     * @param owner          归属
     * @param idempotencyKey 幂等键
     * @return 已有反馈；不存在返回 null
     */
    public FeedbackSubmission findByOwnerAndIdempotencyKey(String owner, String idempotencyKey) {
        List<FeedbackSubmission> rows = jdbcTemplate.query(
                "SELECT " + ALL_COLUMNS + " FROM feedback_submission WHERE owner = :owner "
                        + "AND idempotency_key = :idempotencyKey",
                new MapSqlParameterSource("owner", owner).addValue("idempotencyKey", idempotencyKey), ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 频控计数（同 owner 自指定时刻以来的提交数；窗口边界并发轻微超限可接受，DESIGN §5.3）。
     *
     * @param owner 归属
     * @param since 起始时刻（含）
     * @return 提交数
     */
    public int countByOwnerSince(String owner, Instant since) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedback_submission WHERE owner = :owner AND created_time >= :since",
                new MapSqlParameterSource("owner", owner).addValue("since", Timestamp.from(since)), Long.class);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 本人总数（owner 等值）。
     *
     * @param owner 归属
     * @return 总数
     */
    public long countByOwner(String owner) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedback_submission WHERE owner = :owner",
                new MapSqlParameterSource("owner", owner), Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 管理列表（筛选+DATA 范围+分页+固定排序 created_time DESC）。
     *
     * @param filter           筛选条件
     * @param applicationCodes DATA application IN 集（null=全量；空集由调用方先短路为空结果）
     * @param offset           偏移
     * @param size             页大小
     * @return 反馈列表
     */
    public List<FeedbackSubmission> listByFilter(Filter filter, Set<String> applicationCodes, int offset, int size) {
        StringBuilder sql = new StringBuilder("SELECT " + ALL_COLUMNS + " FROM feedback_submission WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendFilter(sql, params, filter, applicationCodes);
        sql.append(" ORDER BY created_time DESC, id DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", size).addValue("offset", offset);
        return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
    }

    /**
     * 管理列表总数（与 listByFilter 同条件）。
     *
     * @param filter           筛选条件
     * @param applicationCodes DATA application IN 集（null=全量）
     * @return 总数
     */
    public long countByFilter(Filter filter, Set<String> applicationCodes) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM feedback_submission WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendFilter(sql, params, filter, applicationCodes);
        Long count = jdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 本人列表（owner 等值，固定排序 created_time DESC）。
     *
     * @param owner  归属
     * @param offset 偏移
     * @param size   页大小
     * @return 反馈列表
     */
    public List<FeedbackSubmission> listByOwner(String owner, int offset, int size) {
        return jdbcTemplate.query(
                "SELECT " + ALL_COLUMNS + " FROM feedback_submission WHERE owner = :owner "
                        + "ORDER BY created_time DESC, id DESC LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource("owner", owner).addValue("limit", size).addValue("offset", offset),
                ROW_MAPPER);
    }

    /**
     * 处理落库（行版本 CAS：UPDATE 影响行数 0=版本冲突或范围不可见，由调用方按行存在性判定 409/404）。
     *
     * @param submission         已应用处理的目标反馈（携带新状态/留痕与新版本号）
     * @param expectedRowVersion 期望的当前行版本
     * @param applicationCodes   DATA application IN 集（null=全量；修正③：处理写范围条件同样进 SQL，不做先授权后无条件更新）
     * @return 影响行数（0=冲突或范围不可见）
     */
    public int updateHandling(FeedbackSubmission submission, int expectedRowVersion, Set<String> applicationCodes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", submission.getId())
                .addValue("status", submission.getStatus())
                .addValue("handledBy", submission.getHandledBy())
                .addValue("handledAt", Timestamp.from(submission.getHandledAt()))
                .addValue("handlerNote", submission.getHandlerNote())
                .addValue("reply", submission.getReply())
                .addValue("repliedBy", submission.getRepliedBy())
                .addValue("repliedAt", submission.getRepliedAt() == null ? null
                        : Timestamp.from(submission.getRepliedAt()))
                .addValue("rowVersion", submission.getRowVersion())
                .addValue("expectedRowVersion", expectedRowVersion)
                .addValue("updatedTime", Timestamp.from(Instant.now()));
        if (applicationCodes != null) {
            params.addValue("hApplications", applicationCodes);
        }
        return jdbcTemplate.update("UPDATE feedback_submission SET status = :status, handled_by = :handledBy, "
                + "handled_at = :handledAt, handler_note = :handlerNote, reply = :reply, replied_by = :repliedBy, "
                + "replied_at = :repliedAt, row_version = :rowVersion, updated_time = :updatedTime "
                + "WHERE id = :id AND row_version = :expectedRowVersion"
                + (applicationCodes == null ? "" : " AND application_code IN (:hApplications)"), params);
    }

    /**
     * 提交查询过滤条件（管理列表）。
     */
    public static class Filter {

        /**
         * 目标应用（可空）
         */
        private final String applicationCode;

        /**
         * 状态（可空）
         */
        private final String status;

        /**
         * 类型（可空）
         */
        private final String type;

        /**
         * 发起形态（可空）
         */
        private final String kind;

        /**
         * 打分（可空）
         */
        private final Integer score;

        public Filter(String applicationCode, String status, String type, String kind, Integer score) {
            this.applicationCode = applicationCode;
            this.status = status;
            this.type = type;
            this.kind = kind;
            this.score = score;
        }
    }
}
