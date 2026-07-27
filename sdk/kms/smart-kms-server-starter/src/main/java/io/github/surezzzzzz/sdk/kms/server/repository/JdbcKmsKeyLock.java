package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.service.KmsKeyLock;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

/**
 * 基于 JDBC 的 tenant 内逻辑密钥事务锁。
 *
 * @author surezzzzzz
 */
public class JdbcKmsKeyLock implements KmsKeyLock {

    /**
     * 执行 tenant 隔离行锁 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建逻辑密钥 JDBC 锁端口。
     *
     * @param jdbcTemplate 执行 tenant 隔离行锁 SQL 的 JDBC 模板
     */
    public JdbcKmsKeyLock(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 锁定当前事务内逻辑密钥。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 密钥存在并已锁定时返回 {@code true}
     */
    @Override
    public boolean lock(String tenantId, String keyRef) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", KmsValidationHelper.requireTenantId(tenantId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
        try {
            List<Long> rows = jdbcTemplate.query(SmartKmsServerConstant.SQL_LOCK_KEY_BY_KEY_REF, parameters,
                    new org.springframework.jdbc.core.RowMapper<Long>() {
                        @Override
                        public Long mapRow(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
                            return resultSet.getLong("id");
                        }
                    });
            return !rows.isEmpty();
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }
}
