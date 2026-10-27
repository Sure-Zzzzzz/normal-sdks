package io.github.surezzzzzz.sdk.ticket.server.repository;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * 工单类型数据访问。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Repository
public class TicketTypeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketTypeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入类型（code 唯一，重复映射 409 由调用方处理）。
     *
     * @param type 类型
     * @return 自增主键
     */
    public long insert(TicketType type) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", type.getCode())
                .addValue("name", type.getName())
                .addValue("formTemplate", type.getFormTemplate(), Types.LONGVARCHAR)
                .addValue("defaultGroup", type.getDefaultGroup())
                .addValue("slaResponse", type.getSlaResponseMinutes())
                .addValue("slaResolution", type.getSlaResolutionMinutes())
                .addValue("enabled", type.isEnabled());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update("INSERT INTO ticket_type (code, name, form_template, default_group, "
                + "sla_response_minutes, sla_resolution_minutes, enabled) "
                + "VALUES (:code, :name, :formTemplate, :defaultGroup, :slaResponse, :slaResolution, :enabled)",
                params, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 更新类型（模板/SLA 变更不重算存量单——deadline 已固化，DESIGN §6.1）。
     *
     * @param type 类型
     * @return 影响行数
     */
    public int update(TicketType type) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", type.getId())
                .addValue("name", type.getName())
                .addValue("formTemplate", type.getFormTemplate(), Types.LONGVARCHAR)
                .addValue("defaultGroup", type.getDefaultGroup())
                .addValue("slaResponse", type.getSlaResponseMinutes())
                .addValue("slaResolution", type.getSlaResolutionMinutes())
                .addValue("enabled", type.isEnabled());
        return jdbcTemplate.update("UPDATE ticket_type SET name = :name, form_template = :formTemplate, "
                + "default_group = :defaultGroup, sla_response_minutes = :slaResponse, "
                + "sla_resolution_minutes = :slaResolution, enabled = :enabled WHERE id = :id", params);
    }

    /**
     * 按编码查询。
     *
     * @param code 类型编码
     * @return 类型
     */
    public Optional<TicketType> findByCode(String code) {
        List<TicketType> result = jdbcTemplate.query(
                "SELECT * FROM ticket_type WHERE code = :code",
                new MapSqlParameterSource("code", code), ROW_MAPPER);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * 全量查询（管理面；启用过滤见 service）。
     *
     * @return 类型列表
     */
    public List<TicketType> findAll() {
        return jdbcTemplate.query("SELECT * FROM ticket_type ORDER BY code", ROW_MAPPER);
    }

    private static final RowMapper<TicketType> ROW_MAPPER = new RowMapper<TicketType>() {
        @Override
        public TicketType mapRow(ResultSet rs, int rowNum) throws SQLException {
            return TicketType.builder()
                    .id(rs.getLong("id"))
                    .code(rs.getString("code"))
                    .name(rs.getString("name"))
                    .formTemplate(rs.getString("form_template"))
                    .defaultGroup(rs.getString("default_group"))
                    .slaResponseMinutes(rs.getLong("sla_response_minutes"))
                    .slaResolutionMinutes(rs.getLong("sla_resolution_minutes"))
                    .enabled(rs.getBoolean("enabled"))
                    .build();
        }
    };
}
