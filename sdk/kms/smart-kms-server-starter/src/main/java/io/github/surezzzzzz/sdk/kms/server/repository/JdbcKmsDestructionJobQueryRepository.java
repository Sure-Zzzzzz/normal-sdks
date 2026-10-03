package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * 基于 JDBC 的销毁任务管理页查询仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsDestructionJobQueryRepository implements KmsDestructionJobQueryRepository {

    private static final String SQL_SELECT_PAGE = "SELECT job.owner_principal_id, kms_key.key_ref, job.key_version, job.state, "
            + "job.due_at, job.claim_until, job.attempt_count, job.completed_at FROM smart_kms_destruction_job job "
            + "INNER JOIN smart_kms_key kms_key ON job.owner_principal_id = kms_key.owner_principal_id AND job.key_id = kms_key.id "
            + "WHERE job.owner_principal_id = :ownerPrincipalId ORDER BY job.due_at DESC, job.id DESC LIMIT :limit OFFSET :offset";
    private static final String SQL_SELECT_COUNT = "SELECT COUNT(1) FROM smart_kms_destruction_job "
            + "WHERE owner_principal_id = :ownerPrincipalId";
    private static final RowMapper<KmsDestructionJob> ROW_MAPPER = new KmsDestructionJobRowMapper();

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建 JDBC 查询仓储。
     *
     * @param jdbcTemplate 执行 owner 隔离 SQL 的 JDBC 模板
     */
    public JdbcKmsDestructionJobQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static void appendOwnerPredicate(StringBuilder pageSql, StringBuilder countSql,
                                             MapSqlParameterSource parameters, KmsOwnerAccessScope scope) {
        if (!scope.isAll()) {
            pageSql.append(" WHERE job.owner_principal_id IN (:ownerPrincipalIds)");
            countSql.append(" WHERE job.owner_principal_id IN (:ownerPrincipalIds)");
            parameters.addValue("ownerPrincipalIds", scope.getOwnerPrincipalIds());
        }
    }

    /**
     * 查询当前 owner 的无材料销毁任务分页。
     */
    @Override
    public KmsDestructionJobPage findPage(String ownerPrincipalId, long offset, int limit) {
        if (offset < 0L || limit < SmartKmsCoreConstant.ONE) {
            throw new KmsValidationException();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerPrincipalId", KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId))
                .addValue("offset", Long.valueOf(offset)).addValue("limit", Integer.valueOf(limit));
        try {
            List<KmsDestructionJob> items = jdbcTemplate.query(SQL_SELECT_PAGE, parameters, ROW_MAPPER);
            Long total = jdbcTemplate.queryForObject(SQL_SELECT_COUNT, parameters, Long.class);
            return new KmsDestructionJobPage(items, total == null ? 0L : total.longValue());
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 使用完整 DataPlan 归属范围查询销毁任务；列表与计数始终使用同一 owner 谓词。
     */
    @Override
    public KmsDestructionJobPage findPage(KmsOwnerAccessScope scope, long offset, int limit) {
        if (scope == null || offset < 0L || limit < SmartKmsCoreConstant.ONE) {
            throw new KmsValidationException();
        }
        StringBuilder pageSql = new StringBuilder("SELECT job.owner_principal_id, kms_key.key_ref, job.key_version, job.state, ")
                .append("job.due_at, job.claim_until, job.attempt_count, job.completed_at FROM smart_kms_destruction_job job ")
                .append("INNER JOIN smart_kms_key kms_key ON job.owner_principal_id = kms_key.owner_principal_id ")
                .append("AND job.key_id = kms_key.id");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(1) FROM smart_kms_destruction_job job");
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("offset", Long.valueOf(offset))
                .addValue("limit", Integer.valueOf(limit));
        appendOwnerPredicate(pageSql, countSql, parameters, scope);
        pageSql.append(" ORDER BY job.due_at DESC, job.id DESC LIMIT :limit OFFSET :offset");
        try {
            List<KmsDestructionJob> items = jdbcTemplate.query(pageSql.toString(), parameters, ROW_MAPPER);
            Long total = jdbcTemplate.queryForObject(countSql.toString(), parameters, Long.class);
            return new KmsDestructionJobPage(items, total == null ? 0L : total.longValue());
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 映射不含领取令牌的销毁任务管理投影。
     */
    private static final class KmsDestructionJobRowMapper implements RowMapper<KmsDestructionJob> {

        /**
         * 映射一条销毁任务。
         */
        @Override
        public KmsDestructionJob mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp dueAt = resultSet.getTimestamp("due_at");
            Timestamp claimUntil = resultSet.getTimestamp("claim_until");
            Timestamp completedAt = resultSet.getTimestamp("completed_at");
            KmsDestructionJobState state = KmsDestructionJobState.fromCode(resultSet.getString("state"));
            if (state == null || dueAt == null || resultSet.getInt("key_version") < SmartKmsCoreConstant.ONE
                    || resultSet.getInt("attempt_count") < SmartKmsCoreConstant.ZERO) {
                throw new KmsPersistenceException();
            }
            return KmsDestructionJob.builder().ownerPrincipalId(resultSet.getString("owner_principal_id"))
                    .keyRef(resultSet.getString("key_ref")).keyVersion(resultSet.getInt("key_version"))
                    .state(state).dueAt(dueAt.toInstant()).claimUntil(claimUntil == null ? null : claimUntil.toInstant())
                    .attemptCount(resultSet.getInt("attempt_count"))
                    .completedAt(completedAt == null ? null : completedAt.toInstant()).build();
        }
    }
}
