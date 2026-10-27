package io.github.surezzzzzz.sdk.ticket.server.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketServiceException;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketJson;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 处理组数据访问（组员 JSON 数组存 TEXT；组员增删即时生效——DESIGN §6.2）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketGroupRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketGroupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入组。
     *
     * @param group 组
     * @return 自增主键
     */
    public long insert(TicketGroup group) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", group.getCode())
                .addValue("name", group.getName())
                .addValue("members", writeMembers(group.getMemberSubjectIds()), Types.LONGVARCHAR)
                .addValue("leader", group.getLeaderSubjectId(), Types.VARCHAR)
                .addValue("enabled", group.isEnabled());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket_group (code, name, member_subject_ids, leader_subject_id, "
                + "enabled) VALUES (:code, :name, :members, :leader, :enabled)", params, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 更新组。
     *
     * @param group 组
     * @return 影响行数
     */
    public int update(TicketGroup group) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", group.getId())
                .addValue("name", group.getName())
                .addValue("members", writeMembers(group.getMemberSubjectIds()), Types.LONGVARCHAR)
                .addValue("leader", group.getLeaderSubjectId(), Types.VARCHAR)
                .addValue("enabled", group.isEnabled());
        return jdbcTemplate.update("UPDATE ticket_group SET name = :name, "
                + "member_subject_ids = :members, leader_subject_id = :leader, enabled = :enabled "
                + "WHERE id = :id", params);
    }

    /**
     * 按编码查询。
     *
     * @param code 组编码
     * @return 组
     */
    public Optional<TicketGroup> findByCode(String code) {
        List<TicketGroup> result = jdbcTemplate.query(
                "SELECT * FROM ticket_group WHERE code = :code",
                new MapSqlParameterSource("code", code), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 全量查询（管理面）。
     *
     * @return 组列表
     */
    public List<TicketGroup> findAll() {
        return jdbcTemplate.query("SELECT * FROM ticket_group ORDER BY code", ROW_MAPPER);
    }

    /**
     * 查人员所属的启用组（DATA 组内可见维度解析）。
     *
     * @param subjectId 人员标识
     * @return 组编码列表
     */
    public List<String> findEnabledGroupCodesOf(String subjectId) {
        List<TicketGroup> groups = jdbcTemplate.query(
                "SELECT * FROM ticket_group WHERE enabled = 1", ROW_MAPPER);
        List<String> codes = new ArrayList<>();
        for (TicketGroup group : groups) {
            if (group.hasMember(subjectId)) {
                codes.add(group.getCode());
            }
        }
        return codes;
    }

    private static String writeMembers(List<String> members) {
        try {
            return TicketJson.mapper().writeValueAsString(members == null ? new ArrayList<String>() : members);
        } catch (Exception e) {
            throw new TicketServiceException(e);
        }
    }

    private static List<String> readMembers(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> members = TicketJson.mapper().readValue(json, new TypeReference<List<String>>() {
            });
            return members == null ? new ArrayList<>() : members;
        } catch (Exception e) {
            throw new TicketServiceException(e);
        }
    }

    private static final RowMapper<TicketGroup> ROW_MAPPER = new RowMapper<TicketGroup>() {
        @Override
        public TicketGroup mapRow(ResultSet rs, int rowNum) throws SQLException {
            return TicketGroup.builder()
                    .id(rs.getLong("id"))
                    .code(rs.getString("code"))
                    .name(rs.getString("name"))
                    .memberSubjectIds(readMembers(rs.getString("member_subject_ids")))
                    .leaderSubjectId(rs.getString("leader_subject_id"))
                    .enabled(rs.getBoolean("enabled"))
                    .build();
        }
    };
}
