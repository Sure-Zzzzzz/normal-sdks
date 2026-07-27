package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionWorkerState;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionWorkerStateRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
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
 * 基于 JDBC 的销毁 worker 运行状态仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsDestructionWorkerStateRepository implements KmsDestructionWorkerStateRepository {

    /**
     * worker 状态行映射器。
     */
    private static final RowMapper<KmsDestructionWorkerState> WORKER_STATE_ROW_MAPPER = new KmsDestructionWorkerStateRowMapper();
    /**
     * 执行 worker 状态 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建 worker 状态 JDBC 仓储。
     *
     * @param jdbcTemplate 执行 worker 状态 SQL 的 JDBC 模板
     */
    public JdbcKmsDestructionWorkerStateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建经过文本边界校验的实例参数。
     */
    private static MapSqlParameterSource instanceParameters(String instanceId) {
        return new MapSqlParameterSource().addValue("instanceId", KmsValidationHelper.requireText(instanceId,
                SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH));
    }

    /**
     * 创建实例与权威时间参数。
     */
    private static MapSqlParameterSource timeParameters(String instanceId, Instant timestamp, String parameterName) {
        if (timestamp == null) {
            throw new KmsValidationException();
        }
        return instanceParameters(instanceId).addValue(parameterName, Timestamp.from(timestamp));
    }

    /**
     * 查询实例持久化状态。
     *
     * @param instanceId worker 实例标识
     * @return 已持久化状态；不存在时为空
     */
    @Override
    public Optional<KmsDestructionWorkerState> findByInstanceId(String instanceId) {
        try {
            List<KmsDestructionWorkerState> states = jdbcTemplate.query(
                    SmartKmsServerConstant.SQL_SELECT_DESTRUCTION_WORKER_STATE, instanceParameters(instanceId),
                    WORKER_STATE_ROW_MAPPER);
            return states.isEmpty() ? Optional.<KmsDestructionWorkerState>empty() : Optional.of(states.get(0));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 记录成功扫描。
     *
     * @param instanceId worker 实例标识
     * @param scannedAt  成功扫描时间
     */
    @Override
    public void recordSuccess(String instanceId, Instant scannedAt) {
        execute(SmartKmsServerConstant.SQL_RECORD_DESTRUCTION_WORKER_SUCCESS,
                timeParameters(instanceId, scannedAt, "scannedAt"));
    }

    /**
     * 记录失败事实。
     *
     * @param instanceId worker 实例标识
     * @param failedAt   失败时间
     */
    @Override
    public void recordFailure(String instanceId, Instant failedAt) {
        execute(SmartKmsServerConstant.SQL_RECORD_DESTRUCTION_WORKER_FAILURE,
                timeParameters(instanceId, failedAt, "failedAt"));
    }

    /**
     * 执行 worker 状态写入。
     */
    private void execute(String sql, MapSqlParameterSource parameters) {
        try {
            jdbcTemplate.update(sql, parameters);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 映射 worker 无敏感运行状态。
     */
    private static final class KmsDestructionWorkerStateRowMapper implements RowMapper<KmsDestructionWorkerState> {

        /**
         * 映射一条 worker 状态。
         *
         * @param resultSet 当前结果集
         * @param rowNumber 当前行号
         * @return 无敏感 worker 状态
         * @throws SQLException 读取 JDBC 字段失败时抛出
         */
        @Override
        public KmsDestructionWorkerState mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp lastSuccessfulScanAt = resultSet.getTimestamp("last_successful_scan_at");
            Timestamp lastFailureAt = resultSet.getTimestamp("last_failure_at");
            int consecutiveFailureCount = resultSet.getInt("consecutive_failure_count");
            if (consecutiveFailureCount < SmartKmsCoreConstant.ZERO) {
                throw new KmsPersistenceException();
            }
            try {
                return KmsDestructionWorkerState.builder()
                        .instanceId(KmsValidationHelper.requireText(resultSet.getString("instance_id"),
                                SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH))
                        .lastSuccessfulScanAt(lastSuccessfulScanAt == null ? null : lastSuccessfulScanAt.toInstant())
                        .lastFailureAt(lastFailureAt == null ? null : lastFailureAt.toInstant())
                        .consecutiveFailureCount(consecutiveFailureCount).build();
            } catch (KmsValidationException exception) {
                throw new KmsPersistenceException();
            }
        }
    }
}
