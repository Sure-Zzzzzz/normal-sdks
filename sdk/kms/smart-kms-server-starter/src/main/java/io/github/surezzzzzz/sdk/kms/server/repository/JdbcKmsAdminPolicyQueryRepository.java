package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * 基于 JDBC 的跨钥策略分页查询仓储；列表与计数始终使用同一筛选谓词。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcKmsAdminPolicyQueryRepository implements KmsAdminPolicyQueryRepository {

    private static final String SQL_SELECT_PAGE = "SELECT policy.owner_principal_id, policy.key_ref, policy.policy_id, "
            + "policy.principal_id, policy.key_version, policy.operation, policy.expires_at, policy.row_version, "
            + "policy.created_at, kms_key.key_alias FROM smart_kms_key_policy policy "
            + "INNER JOIN smart_kms_key kms_key ON policy.owner_principal_id = kms_key.owner_principal_id "
            + "AND policy.key_id = kms_key.id";
    private static final String SQL_COUNT_PAGE = "SELECT COUNT(1) FROM smart_kms_key_policy policy "
            + "INNER JOIN smart_kms_key kms_key ON policy.owner_principal_id = kms_key.owner_principal_id "
            + "AND policy.key_id = kms_key.id";
    private static final RowMapper<KmsAdminPolicyPage.KmsAdminPolicyEntry> ROW_MAPPER = new KmsAdminPolicyEntryRowMapper();

    /**
     * 执行命名参数 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 转义 SQL LIKE 的保留字符，使别名筛选保持字面量包含语义。
     */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 按归属范围和可选筛选分页查询策略。
     */
    @Override
    public KmsAdminPolicyPage findPage(KmsOwnerAccessScope scope, String keyAlias, String principalId, String operation,
                                       long offset, int size) {
        if (scope == null || offset < 0L || size < SmartKmsCoreConstant.ONE) {
            throw new KmsValidationException();
        }
        StringBuilder filters = new StringBuilder(" WHERE (:keyAlias IS NULL OR kms_key.key_alias LIKE :keyAlias ESCAPE '\\\\') "
                + "AND (:principalId IS NULL OR policy.principal_id = :principalId) "
                + "AND (:operation IS NULL OR policy.operation = :operation)");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("keyAlias", keyAlias == null || keyAlias.isEmpty() ? null : "%" + escapeLike(keyAlias) + "%")
                .addValue("principalId", principalId == null || principalId.isEmpty() ? null : principalId)
                .addValue("operation", operation)
                .addValue("offset", Long.valueOf(offset)).addValue("size", Integer.valueOf(size));
        if (!scope.isAll()) {
            filters.append(" AND policy.owner_principal_id IN (:ownerPrincipalIds)");
            parameters.addValue("ownerPrincipalIds", scope.getOwnerPrincipalIds());
        }
        String itemsSql = SQL_SELECT_PAGE + filters + " ORDER BY policy.created_at DESC, policy.id DESC "
                + "LIMIT :size OFFSET :offset";
        String countSql = SQL_COUNT_PAGE + filters;
        try {
            List<KmsAdminPolicyPage.KmsAdminPolicyEntry> items = jdbcTemplate.query(itemsSql, parameters, ROW_MAPPER);
            Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
            if (total == null) {
                throw new KmsPersistenceException();
            }
            log.debug("KMS 跨钥策略分页读取 ownerAll={} total={} pageSize={}", scope.isAll(), total.longValue(), items.size());
            return new KmsAdminPolicyPage(items, total.longValue());
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 映射附带密钥别名的策略管理页投影。
     */
    private static final class KmsAdminPolicyEntryRowMapper implements RowMapper<KmsAdminPolicyPage.KmsAdminPolicyEntry> {

        /**
         * 映射一条策略条目。
         */
        @Override
        public KmsAdminPolicyPage.KmsAdminPolicyEntry mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            KmsOperation operation = KmsOperation.fromCode(resultSet.getString("operation"));
            int rawVersion = resultSet.getInt("key_version");
            boolean versionNull = resultSet.wasNull();
            Timestamp expiresAt = resultSet.getTimestamp("expires_at");
            Timestamp createdAt = resultSet.getTimestamp("created_at");
            if (operation == null || createdAt == null) {
                throw new KmsPersistenceException();
            }
            Integer version = versionNull ? null : Integer.valueOf(rawVersion);
            if (version != null && version.intValue() < SmartKmsCoreConstant.ONE) {
                throw new KmsPersistenceException();
            }
            KmsKeyPolicy policy = KmsKeyPolicy.builder()
                    .ownerPrincipalId(resultSet.getString("owner_principal_id"))
                    .keyRef(resultSet.getString("key_ref"))
                    .policyId(resultSet.getString("policy_id"))
                    .principalId(resultSet.getString("principal_id"))
                    .keyVersion(version)
                    .operation(operation)
                    .expiresAt(expiresAt == null ? null : expiresAt.toInstant())
                    .rowVersion(resultSet.getLong("row_version"))
                    .build();
            return new KmsAdminPolicyPage.KmsAdminPolicyEntry(policy, resultSet.getString("key_alias"),
                    createdAt.toInstant());
        }
    }
}
