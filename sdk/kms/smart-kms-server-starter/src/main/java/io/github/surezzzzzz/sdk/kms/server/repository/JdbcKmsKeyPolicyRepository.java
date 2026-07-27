package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyPolicy;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyPolicyRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * 基于 JDBC 的精确密钥策略仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsKeyPolicyRepository implements KmsKeyPolicyRepository {

    /**
     * 策略行映射器。
     */
    private static final RowMapper<KmsKeyPolicy> KEY_POLICY_ROW_MAPPER = new KmsKeyPolicyRowMapper();
    /**
     * 执行 tenant 隔离 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建密钥策略 JDBC 仓储。
     *
     * @param jdbcTemplate 执行 tenant 隔离 SQL 的 JDBC 模板
     */
    public JdbcKmsKeyPolicyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建 tenant 与 keyRef 查询参数。
     */
    private static MapSqlParameterSource createKeyParameters(String tenantId, String keyRef) {
        return new MapSqlParameterSource()
                .addValue("tenantId", KmsValidationHelper.requireTenantId(tenantId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
    }

    /**
     * 查询逻辑密钥下的全部精确策略。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 策略集合
     */
    @Override
    public List<KmsKeyPolicy> findByKeyRef(String tenantId, String keyRef) {
        MapSqlParameterSource parameters = createKeyParameters(tenantId, keyRef);
        try {
            return jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_KEY_POLICY_BY_KEY_REF,
                    parameters, KEY_POLICY_ROW_MAPPER);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 保存精确 allow-only 策略。
     *
     * @param tenantId 资源所属 tenant
     * @param policy   待保存的策略
     * @return 已持久化的策略快照
     */
    @Override
    public KmsKeyPolicy save(String tenantId, KmsKeyPolicy policy) {
        if (policy == null || !KmsValidationHelper.requireTenantId(tenantId).equals(policy.getTenantId())) {
            throw new KmsValidationException();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", policy.getTenantId())
                .addValue("keyRef", policy.getKeyRef())
                .addValue("policyId", policy.getPolicyId())
                .addValue("principalId", policy.getPrincipalId())
                .addValue("keyVersion", policy.getKeyVersion())
                .addValue("keyVersionScope", policy.getKeyVersion() == null
                        ? SmartKmsCoreConstant.ZERO : policy.getKeyVersion())
                .addValue("operation", policy.getOperation().getCode())
                .addValue("expiresAt", policy.getExpiresAt() == null
                        ? null : Timestamp.from(policy.getExpiresAt()))
                .addValue("rowVersion", policy.getRowVersion());
        try {
            int inserted = jdbcTemplate.update(SmartKmsServerConstant.SQL_INSERT_KEY_POLICY, parameters);
            if (inserted != SmartKmsCoreConstant.ONE) {
                throw new KmsPersistenceException();
            }
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
        for (KmsKeyPolicy savedPolicy : findByKeyRef(tenantId, policy.getKeyRef())) {
            if (policy.getPolicyId().equals(savedPolicy.getPolicyId())) {
                return savedPolicy;
            }
        }
        throw new KmsPersistenceException();
    }

    /**
     * 按乐观锁版本撤销策略。
     *
     * @param tenantId           资源所属 tenant
     * @param keyRef             逻辑密钥标识
     * @param policyId           策略标识
     * @param expectedRowVersion 预期乐观锁版本
     */
    @Override
    public void revoke(String tenantId, String keyRef, String policyId, long expectedRowVersion) {
        if (expectedRowVersion < SmartKmsCoreConstant.ZERO) {
            throw new KmsValidationException();
        }
        MapSqlParameterSource parameters = createKeyParameters(tenantId, keyRef)
                .addValue("policyId", KmsValidationHelper.requirePolicyId(policyId))
                .addValue("rowVersion", expectedRowVersion);
        try {
            int deleted = jdbcTemplate.update(SmartKmsServerConstant.SQL_DELETE_KEY_POLICY, parameters);
            if (deleted != SmartKmsCoreConstant.ONE) {
                throw new KmsPersistenceException();
            }
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 将策略行映射为经过 Core 构造校验的精确策略。
     */
    private static final class KmsKeyPolicyRowMapper implements RowMapper<KmsKeyPolicy> {

        /**
         * 映射一条策略持久化记录。
         *
         * @param resultSet 当前结果集
         * @param rowNumber 当前行号
         * @return 已校验的密钥策略
         * @throws SQLException 读取 JDBC 字段失败时抛出
         */
        @Override
        public KmsKeyPolicy mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp expiresAt = resultSet.getTimestamp("expires_at");
            int keyVersion = resultSet.getInt("key_version");
            Integer nullableKeyVersion = resultSet.wasNull() ? null : Integer.valueOf(keyVersion);
            try {
                return KmsKeyPolicy.builder()
                        .policyId(resultSet.getString("policy_id"))
                        .tenantId(resultSet.getString("tenant_id"))
                        .keyRef(resultSet.getString("key_ref"))
                        .principalId(resultSet.getString("principal_id"))
                        .keyVersion(nullableKeyVersion)
                        .operation(KmsOperation.fromCode(resultSet.getString("operation")))
                        .expiresAt(expiresAt == null ? null : expiresAt.toInstant())
                        .rowVersion(resultSet.getLong("row_version"))
                        .build();
            } catch (KmsValidationException exception) {
                throw new KmsPersistenceException();
            }
        }
    }
}
