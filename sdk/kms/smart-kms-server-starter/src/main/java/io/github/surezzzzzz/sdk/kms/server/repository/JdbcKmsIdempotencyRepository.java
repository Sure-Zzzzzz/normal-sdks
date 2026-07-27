package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsIdempotencyRecord;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsIdempotencyRepository;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的管理操作幂等仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsIdempotencyRepository implements KmsIdempotencyRepository,
        KmsIdempotencyResponseSnapshotRepository {

    /**
     * 幂等记录行映射器。
     */
    private static final RowMapper<KmsIdempotencyRecord> IDEMPOTENCY_ROW_MAPPER = new KmsIdempotencyRowMapper();
    /**
     * 执行 tenant 隔离 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建幂等 JDBC 仓储。
     *
     * @param jdbcTemplate 执行 tenant 隔离 SQL 的 JDBC 模板
     */
    public JdbcKmsIdempotencyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建幂等作用域参数。
     */
    private static MapSqlParameterSource parameters(String tenantId, String principalId, String endpoint,
                                                    String idempotencyKey) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("principalId", principalId)
                .addValue("endpoint", endpoint).addValue("idempotencyKey", idempotencyKey);
    }

    /**
     * 将 Core 幂等模型转换为 SQL 参数。
     */
    private static MapSqlParameterSource parameters(KmsIdempotencyRecord record) {
        return parameters(record.getTenantId(), record.getPrincipalId(), record.getEndpoint(),
                record.getIdempotencyKey()).addValue("requestHash", record.getRequestHash())
                .addValue("resourceRef", record.getResourceRef()).addValue("httpStatus", record.getHttpStatus())
                .addValue("expiresAt", Timestamp.from(record.getExpiresAt()));
    }

    /**
     * 按完整幂等作用域查询已有记录。
     *
     * @param tenantId       发起操作的 tenant
     * @param principalId    发起操作的主体标识
     * @param endpoint       管理操作稳定端点标识
     * @param idempotencyKey 客户端提供的幂等键
     * @return 匹配的记录；不存在时为空
     */
    @Override
    public Optional<KmsIdempotencyRecord> find(String tenantId, String principalId, String endpoint,
                                               String idempotencyKey) {
        List<KmsIdempotencyRecord> records = query(tenantId, principalId, endpoint, idempotencyKey);
        return records.isEmpty() ? Optional.<KmsIdempotencyRecord>empty() : Optional.of(records.get(0));
    }

    /**
     * 保存幂等记录。
     *
     * @param record 仅包含无敏感请求摘要的幂等记录
     * @return 已持久化的记录快照
     */
    @Override
    public KmsIdempotencyRecord save(KmsIdempotencyRecord record) {
        if (record == null) {
            throw new KmsPersistenceException();
        }
        MapSqlParameterSource parameters = parameters(record).addValue("responseSnapshot", null);
        try {
            jdbcTemplate.update(SmartKmsServerConstant.SQL_INSERT_IDEMPOTENCY_RECORD, parameters);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
        return find(record.getTenantId(), record.getPrincipalId(), record.getEndpoint(), record.getIdempotencyKey())
                .orElseThrow(KmsPersistenceException::new);
    }

    /**
     * 保存含安全响应快照的幂等记录。
     *
     * @param record           无敏感幂等记录
     * @param responseSnapshot 模块私有 JSON 响应快照；204 响应可为空
     */
    public void save(KmsIdempotencyRecord record, byte[] responseSnapshot) {
        saveResponseSnapshot(record, responseSnapshot);
    }

    /**
     * 保存含安全响应快照的幂等记录。
     */
    @Override
    public void saveResponseSnapshot(KmsIdempotencyRecord record, byte[] responseSnapshot) {
        if (record == null || responseSnapshot == null) {
            throw new KmsPersistenceException();
        }
        try {
            jdbcTemplate.update(SmartKmsServerConstant.SQL_INSERT_IDEMPOTENCY_RECORD,
                    parameters(record).addValue("responseSnapshot", responseSnapshot));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 查询完整幂等作用域的响应快照。
     *
     * @param tenantId       发起操作的租户标识
     * @param principalId    发起操作的认证主体标识
     * @param endpoint       规范化具体端点路径
     * @param idempotencyKey 客户端幂等键
     * @return 已保存的无敏感响应快照；不存在时为空
     */
    @Override
    public Optional<byte[]> findResponseSnapshot(String tenantId, String principalId, String endpoint,
                                                 String idempotencyKey) {
        try {
            List<byte[]> snapshots = jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_IDEMPOTENCY_SNAPSHOT,
                    parameters(tenantId, principalId, endpoint, idempotencyKey),
                    new org.springframework.jdbc.core.RowMapper<byte[]>() {
                        @Override
                        public byte[] mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
                            return resultSet.getBytes("response_snapshot");
                        }
                    });
            return snapshots.isEmpty() ? Optional.<byte[]>empty() : Optional.ofNullable(snapshots.get(0));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 删除当前完整作用域内已到期的记录。
     */
    @Override
    public void deleteExpired(String tenantId, String principalId, String endpoint, String idempotencyKey, Instant now) {
        if (now == null) {
            throw new KmsPersistenceException();
        }
        try {
            jdbcTemplate.update(SmartKmsServerConstant.SQL_DELETE_EXPIRED_IDEMPOTENCY_RECORD,
                    parameters(tenantId, principalId, endpoint, idempotencyKey).addValue("now", Timestamp.from(now)));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 查询完整幂等作用域的记录。
     */
    private List<KmsIdempotencyRecord> query(String tenantId, String principalId, String endpoint,
                                             String idempotencyKey) {
        try {
            return jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_IDEMPOTENCY_RECORD,
                    parameters(tenantId, principalId, endpoint, idempotencyKey), IDEMPOTENCY_ROW_MAPPER);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 映射无敏感幂等记录。
     */
    private static final class KmsIdempotencyRowMapper implements RowMapper<KmsIdempotencyRecord> {

        /**
         * 映射幂等记录。
         *
         * @param resultSet 当前结果集
         * @param rowNumber 当前行号
         * @return 无敏感幂等记录
         * @throws SQLException 读取 JDBC 字段失败时抛出
         */
        @Override
        public KmsIdempotencyRecord mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp expiresAt = resultSet.getTimestamp("expires_at");
            return KmsIdempotencyRecord.builder().tenantId(resultSet.getString("tenant_id"))
                    .principalId(resultSet.getString("principal_id")).endpoint(resultSet.getString("endpoint"))
                    .idempotencyKey(resultSet.getString("idempotency_key"))
                    .requestHash(resultSet.getString("request_hash")).resourceRef(resultSet.getString("resource_ref"))
                    .httpStatus(resultSet.getInt("http_status"))
                    .expiresAt(expiresAt == null ? null : expiresAt.toInstant()).build();
        }
    }
}
