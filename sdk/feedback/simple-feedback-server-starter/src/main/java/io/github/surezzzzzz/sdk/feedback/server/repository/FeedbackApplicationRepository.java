package io.github.surezzzzzz.sdk.feedback.server.repository;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
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
import java.util.List;

/**
 * 应用注册表数据访问。
 *
 * @author surezzzzzz
 */
@SimpleFeedbackServerComponent
@Repository
public class FeedbackApplicationRepository {

    private static final RowMapper<FeedbackApplication> ROW_MAPPER = new RowMapper<FeedbackApplication>() {
        @Override
        public FeedbackApplication mapRow(ResultSet rs, int rowNum) throws SQLException {
            return FeedbackApplication.builder()
                    .applicationCode(rs.getString("application_code"))
                    .displayName(rs.getString("display_name"))
                    .description(rs.getString("description"))
                    .enabled(rs.getBoolean("enabled"))
                    .ownerGroup(rs.getString("owner_group"))
                    .rowVersion(rs.getInt("row_version"))
                    .createdBy(rs.getString("created_by"))
                    .createdTime(toInstant(rs.getTimestamp("created_time")))
                    .updatedTime(toInstant(rs.getTimestamp("updated_time")))
                    .build();
        }
    };
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeedbackApplicationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * 插入注册项（application_code 唯一键天然幂等：重复插入抛唯一键冲突由调用方映射 409）。
     *
     * @param application 注册项
     * @return 自增主键
     */
    public long insert(FeedbackApplication application) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicationCode", application.getApplicationCode())
                .addValue("displayName", application.getDisplayName())
                .addValue("description", application.getDescription(), Types.VARCHAR)
                .addValue("enabled", application.isEnabled())
                .addValue("ownerGroup", application.getOwnerGroup(), Types.VARCHAR)
                .addValue("createdBy", application.getCreatedBy())
                .addValue("createdTime", Timestamp.from(application.getCreatedTime()))
                .addValue("updatedTime", Timestamp.from(application.getUpdatedTime()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO feedback_application (application_code, display_name, description, "
                + "enabled, owner_group, row_version, created_by, created_time, updated_time) VALUES ("
                + ":applicationCode, :displayName, :description, :enabled, :ownerGroup, 0, :createdBy, "
                + ":createdTime, :updatedTime)", params, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    /**
     * 按编码查询。
     *
     * @param applicationCode 应用编码
     * @return 注册项；不存在返回 null
     */
    public FeedbackApplication findByCode(String applicationCode) {
        List<FeedbackApplication> rows = jdbcTemplate.query(
                "SELECT id, application_code, display_name, description, enabled, owner_group, row_version, "
                        + "created_by, created_time, updated_time FROM feedback_application "
                        + "WHERE application_code = :applicationCode",
                new MapSqlParameterSource("applicationCode", applicationCode), ROW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 管理列表（DATA 范围进 SQL：受限时 application IN，不做先全量取数再内存过滤）。
     *
     * @param applicationCodes DATA application IN 集（null=全量）
     * @return 注册项列表
     */
    public List<FeedbackApplication> listAll(java.util.Set<String> applicationCodes) {
        if (applicationCodes == null) {
            return jdbcTemplate.query(
                    "SELECT id, application_code, display_name, description, enabled, owner_group, row_version, "
                            + "created_by, created_time, updated_time FROM feedback_application "
                            + "ORDER BY application_code", ROW_MAPPER);
        }
        if (applicationCodes.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return jdbcTemplate.query(
                "SELECT id, application_code, display_name, description, enabled, owner_group, row_version, "
                        + "created_by, created_time, updated_time FROM feedback_application "
                        + "WHERE application_code IN (:rApplications) ORDER BY application_code",
                new MapSqlParameterSource("rApplications", applicationCodes), ROW_MAPPER);
    }

    /**
     * 启用中列表（提交人选择用）。
     *
     * @return 启用中的注册项列表
     */
    public List<FeedbackApplication> listEnabled() {
        return jdbcTemplate.query(
                "SELECT id, application_code, display_name, description, enabled, owner_group, row_version, "
                        + "created_by, created_time, updated_time FROM feedback_application "
                        + "WHERE enabled = 1 ORDER BY application_code", ROW_MAPPER);
    }

    /**
     * 更新（行版本 CAS：影响行数 0=版本冲突或不存在）。
     *
     * @param application        目标注册项（携带新字段值）
     * @param expectedRowVersion 期望的当前行版本
     * @return 影响行数（0=冲突）
     */
    public int update(FeedbackApplication application, int expectedRowVersion) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicationCode", application.getApplicationCode())
                .addValue("displayName", application.getDisplayName())
                .addValue("description", application.getDescription(), Types.VARCHAR)
                .addValue("enabled", application.isEnabled())
                .addValue("ownerGroup", application.getOwnerGroup(), Types.VARCHAR)
                .addValue("rowVersion", application.getRowVersion())
                .addValue("expectedRowVersion", expectedRowVersion)
                .addValue("updatedTime", Timestamp.from(Instant.now()));
        return jdbcTemplate.update("UPDATE feedback_application SET display_name = :displayName, "
                + "description = :description, enabled = :enabled, owner_group = :ownerGroup, "
                + "row_version = :rowVersion, updated_time = :updatedTime "
                + "WHERE application_code = :applicationCode AND row_version = :expectedRowVersion", params);
    }
}
