package io.github.surezzzzzz.sdk.crm.server.repository;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmAuditFact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.port.audit.CrmAuditPort;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerAuditEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 业务事实审计 JDBC 仓储（写命令事实随业务事务；查询面供审计端点）。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcAuditRepository extends SimpleCrmServerJdbcRepositorySupport implements CrmAuditPort {

    /**
     * 创建审计仓储。
     *
     * @param jdbc 模块专用 JDBC 模板
     */
    public SimpleCrmServerJdbcAuditRepository(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public void record(CrmOwner actor, CrmAction action, CrmResourceType targetResourceType,
                       String targetResourceId, long targetAggregateVersion, String correlationId,
                       CrmErrorCode resultCode, Instant occurredAt) {
        jdbc.update("INSERT INTO crm_audit (owner, action, target_resource_type, target_resource_id,"
                        + " target_aggregate_version, correlation_id, result_code, occurred_at)"
                        + " VALUES (:owner, :action, :resourceType, :resourceId, :version,"
                        + " :correlationId, :resultCode, :occurredAt)",
                new MapSqlParameterSource("owner", actor.getOwner())
                        .addValue("action", action.getCode())
                        .addValue("resourceType", targetResourceType.getCode())
                        .addValue("resourceId", targetResourceId)
                        .addValue("version", targetAggregateVersion)
                        .addValue("correlationId", correlationId)
                        .addValue("resultCode", resultCode == null ? null : resultCode.getCode())
                        .addValue("occurredAt", Timestamp.from(occurredAt)));
    }

    /**
     * keyset 审计查询（管理端点用）。
     *
     * @param targetResourceId 资源过滤（可空）
     * @param cursor 游标主键（可空）
     * @param limit 每页上限
     * @return 当页审计事实
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<CrmAuditFact> list(
            String targetResourceId, Long cursor, int limit) {
        return list(targetResourceId, cursor, limit, null);
    }

    /**
     * 同一过滤条件的页码或游标查询；request 非空时使用真实总数与数据库偏移。
     *
     * @param targetResourceId 查询条件或分页边界
     * @param cursor 查询条件或分页边界
     * @param limit 查询条件或分页边界
     * @param request 页码请求，游标查询为空
     * @return 当页结果
     */
    public io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<CrmAuditFact> list(
            String targetResourceId, Long cursor, int limit, io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest request) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, owner, action, target_resource_type, target_resource_id,"
                        + " target_aggregate_version, correlation_id, result_code, occurred_at FROM"
                        + " crm_audit WHERE 1 = 1");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        if (targetResourceId != null) {
            sql.append(" AND target_resource_id = :resourceId");
            params.addValue("resourceId", targetResourceId);
        }
        if (cursor != null) {
            sql.append(" AND id > :cursor");
            params.addValue("cursor", cursor);
        }
        Long total = prepareWindow(sql, params, limit, request);
        List<Long> primaryKeys = new java.util.ArrayList<Long>();
        List<CrmAuditFact> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            primaryKeys.add(rs.getLong("id"));
            return new SimpleCrmServerAuditEntity(
                    rs.getString("owner"), rs.getString("action"),
                    rs.getString("target_resource_type"), rs.getString("target_resource_id"),
                    rs.getLong("target_aggregate_version"), rs.getString("correlation_id"),
                    rs.getString("result_code"), rs.getTimestamp("occurred_at").toInstant()).toFact();
        });
        return new io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaged<CrmAuditFact>(rows,
                primaryKeys, limit, total);
    }
}
