package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsDestructionJob;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionJobRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的销毁任务仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsDestructionJobRepository implements KmsDestructionJobRepository,
        KmsDestructionCancellationGuard {

    /**
     * 销毁任务行映射器。
     */
    private static final RowMapper<KmsDestructionJob> DESTRUCTION_JOB_ROW_MAPPER = new KmsDestructionJobRowMapper();
    /**
     * 执行 tenant 隔离 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建销毁任务 JDBC 仓储。
     *
     * @param jdbcTemplate 执行 tenant 隔离 SQL 的 JDBC 模板
     */
    public JdbcKmsDestructionJobRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建销毁任务插入参数。
     */
    private static MapSqlParameterSource jobParameters(KmsDestructionJob job) {
        return keyParameters(job.getTenantId(), job.getKeyRef()).addValue("keyVersion", job.getKeyVersion())
                .addValue("state", job.getState().getCode()).addValue("dueAt", Timestamp.from(job.getDueAt()))
                .addValue("claimToken", job.getClaimToken())
                .addValue("claimUntil", job.getClaimUntil() == null ? null : Timestamp.from(job.getClaimUntil()))
                .addValue("attemptCount", job.getAttemptCount())
                .addValue("completedAt", job.getCompletedAt() == null ? null : Timestamp.from(job.getCompletedAt()));
    }

    /**
     * 创建任务状态参数。
     */
    private static MapSqlParameterSource stateParameters(Instant now) {
        return new MapSqlParameterSource().addValue("now", Timestamp.from(now))
                .addValue("pendingState", KmsDestructionJobState.PENDING.getCode())
                .addValue("claimedState", KmsDestructionJobState.CLAIMED.getCode())
                .addValue("completedState", KmsDestructionJobState.COMPLETED.getCode());
    }

    /**
     * 创建 tenant 和逻辑密钥参数。
     */
    private static MapSqlParameterSource keyParameters(String tenantId, String keyRef) {
        return new MapSqlParameterSource().addValue("tenantId", KmsValidationHelper.requireTenantId(tenantId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
    }

    /**
     * 创建需要领取令牌的任务参数。
     */
    private static MapSqlParameterSource jobTokenParameters(String tenantId, String keyRef, int keyVersion,
                                                            String claimToken) {
        if (keyVersion < SmartKmsCoreConstant.ONE) {
            throw new KmsValidationException();
        }
        return keyParameters(tenantId, keyRef).addValue("keyVersion", keyVersion)
                .addValue("claimToken", KmsValidationHelper.requireText(claimToken,
                        SmartKmsCoreConstant.IDEMPOTENCY_KEY_MAX_LENGTH))
                .addValue("pendingState", KmsDestructionJobState.PENDING.getCode())
                .addValue("claimedState", KmsDestructionJobState.CLAIMED.getCode())
                .addValue("completedState", KmsDestructionJobState.COMPLETED.getCode());
    }

    /**
     * 校验新建任务的状态组合。
     */
    private static void validateNewJob(String tenantId, KmsDestructionJob job) {
        if (job == null || !KmsValidationHelper.requireTenantId(tenantId).equals(job.getTenantId())
                || job.getKeyVersion() < SmartKmsCoreConstant.ONE || job.getDueAt() == null
                || job.getState() != KmsDestructionJobState.PENDING || job.getClaimToken() != null
                || job.getClaimUntil() != null || job.getAttemptCount() != SmartKmsCoreConstant.ZERO
                || job.getCompletedAt() != null) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireKeyRef(job.getKeyRef());
    }

    /**
     * 校验领取或续租参数。
     */
    private static void validateClaimArguments(String tenantId, String keyRef, int keyVersion, String claimToken,
                                               Instant claimUntil, Instant now) {
        jobTokenParameters(tenantId, keyRef, keyVersion, claimToken);
        if (claimUntil == null || now == null || !claimUntil.isAfter(now)) {
            throw new KmsValidationException();
        }
    }

    /**
     * 保存新的销毁任务。
     *
     * @param tenantId 资源所属 tenant
     * @param job      待保存的无材料任务
     * @return 已持久化的任务快照
     */
    @Override
    public KmsDestructionJob save(String tenantId, KmsDestructionJob job) {
        validateNewJob(tenantId, job);
        try {
            int inserted = jdbcTemplate.update(SmartKmsServerConstant.SQL_INSERT_DESTRUCTION_JOB,
                    jobParameters(job));
            if (inserted != SmartKmsCoreConstant.ONE) {
                throw new KmsPersistenceException();
            }
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
        for (KmsDestructionJob savedJob : findByKeyRef(tenantId, job.getKeyRef())) {
            if (savedJob.getKeyVersion() == job.getKeyVersion()) {
                return savedJob;
            }
        }
        throw new KmsPersistenceException();
    }

    /**
     * 查询逻辑密钥下全部销毁任务。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 已排序的销毁任务集合
     */
    @Override
    public List<KmsDestructionJob> findByKeyRef(String tenantId, String keyRef) {
        try {
            return jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_DESTRUCTION_JOB_BY_KEY_REF,
                    keyParameters(tenantId, keyRef), DESTRUCTION_JOB_ROW_MAPPER);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 查询已到期或租约过期的候选任务。
     *
     * @param now 权威数据库当前时间
     * @return 可尝试领取的任务集合
     */
    @Override
    public List<KmsDestructionJob> findDueOrExpiredClaim(Instant now) {
        if (now == null) {
            throw new KmsValidationException();
        }
        try {
            return jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_DUE_OR_EXPIRED_DESTRUCTION_JOB,
                    stateParameters(now), DESTRUCTION_JOB_ROW_MAPPER);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 以 compare-and-set 领取任务。
     *
     * @param tenantId   资源所属 tenant
     * @param keyRef     逻辑密钥标识
     * @param keyVersion 密钥版本号
     * @param claimToken 本次随机领取令牌
     * @param claimUntil 新租约到期时间
     * @param now        权威数据库当前时间
     * @return 成功取得租约时返回 {@code true}
     */
    @Override
    public boolean claim(String tenantId, String keyRef, int keyVersion, String claimToken,
                         Instant claimUntil, Instant now) {
        validateClaimArguments(tenantId, keyRef, keyVersion, claimToken, claimUntil, now);
        MapSqlParameterSource parameters = stateParameters(now).addValues(keyParameters(tenantId, keyRef).getValues())
                .addValue("keyVersion", keyVersion).addValue("claimToken", claimToken)
                .addValue("claimUntil", Timestamp.from(claimUntil));
        return update(SmartKmsServerConstant.SQL_CLAIM_DESTRUCTION_JOB, parameters);
    }

    /**
     * 使用原领取令牌续租任务。
     *
     * @param tenantId   资源所属 tenant
     * @param keyRef     逻辑密钥标识
     * @param keyVersion 密钥版本号
     * @param claimToken 当前领取令牌
     * @param claimUntil 新租约到期时间
     * @param now        权威数据库当前时间
     * @return 租约仍属当前 worker 时返回 {@code true}
     */
    @Override
    public boolean renewClaim(String tenantId, String keyRef, int keyVersion, String claimToken,
                              Instant claimUntil, Instant now) {
        validateClaimArguments(tenantId, keyRef, keyVersion, claimToken, claimUntil, now);
        MapSqlParameterSource parameters = stateParameters(now).addValues(keyParameters(tenantId, keyRef).getValues())
                .addValue("keyVersion", keyVersion).addValue("claimToken", claimToken)
                .addValue("claimUntil", Timestamp.from(claimUntil));
        return update(SmartKmsServerConstant.SQL_RENEW_DESTRUCTION_JOB_CLAIM, parameters);
    }

    /**
     * 使用领取令牌释放任务。
     *
     * @param tenantId   资源所属 tenant
     * @param keyRef     逻辑密钥标识
     * @param keyVersion 密钥版本号
     * @param claimToken 当前领取令牌
     * @return 成功释放时返回 {@code true}
     */
    @Override
    public boolean release(String tenantId, String keyRef, int keyVersion, String claimToken) {
        MapSqlParameterSource parameters = jobTokenParameters(tenantId, keyRef, keyVersion, claimToken);
        return update(SmartKmsServerConstant.SQL_RELEASE_DESTRUCTION_JOB_CLAIM, parameters);
    }

    /**
     * 使用领取令牌完成任务。
     *
     * @param tenantId    资源所属 tenant
     * @param keyRef      逻辑密钥标识
     * @param keyVersion  密钥版本号
     * @param claimToken  当前领取令牌
     * @param completedAt 成功完成时间
     * @return 成功完成时返回 {@code true}
     */
    @Override
    public boolean complete(String tenantId, String keyRef, int keyVersion, String claimToken,
                            Instant completedAt) {
        if (completedAt == null) {
            throw new KmsValidationException();
        }
        MapSqlParameterSource parameters = jobTokenParameters(tenantId, keyRef, keyVersion, claimToken)
                .addValue("completedAt", Timestamp.from(completedAt));
        return update(SmartKmsServerConstant.SQL_COMPLETE_DESTRUCTION_JOB, parameters);
    }

    /**
     * 查询最早已逾期任务的延迟。
     *
     * @param now 权威数据库当前时间
     * @return 最早逾期任务延迟；无逾期任务时为空
     */
    @Override
    public Optional<Duration> findOldestOverdueDelay(Instant now) {
        if (now == null) {
            throw new KmsValidationException();
        }
        try {
            Timestamp dueAt = jdbcTemplate.queryForObject(
                    SmartKmsServerConstant.SQL_SELECT_OLDEST_OVERDUE_DESTRUCTION_DUE_AT,
                    new MapSqlParameterSource().addValue("completedState", KmsDestructionJobState.COMPLETED.getCode())
                            .addValue("now", Timestamp.from(now)), Timestamp.class);
            if (dueAt == null) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(dueAt.toInstant(), now));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 在调用方已锁定逻辑密钥的事务中检查全部任务的历史领取事实。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 从未被领取时返回 {@code true}
     */
    @Override
    public boolean areAllJobsUnclaimed(String tenantId, String keyRef) {
        try {
            List<Long> claimedJobs = jdbcTemplate.query(SmartKmsServerConstant.SQL_LOCK_UNCLAIMED_DESTRUCTION_JOB,
                    keyParameters(tenantId, keyRef), new RowMapper<Long>() {
                        @Override
                        public Long mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
                            return resultSet.getLong("id");
                        }
                    });
            return claimedJobs.isEmpty();
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 删除已经确认从未领取的逻辑密钥全部销毁任务。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     */
    @Override
    public void deleteUnclaimedJobs(String tenantId, String keyRef) {
        try {
            jdbcTemplate.update(SmartKmsServerConstant.SQL_DELETE_UNCLAIMED_DESTRUCTION_JOB,
                    keyParameters(tenantId, keyRef));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 执行条件更新并转换基础设施异常。
     */
    private boolean update(String sql, MapSqlParameterSource parameters) {
        try {
            return jdbcTemplate.update(sql, parameters) == SmartKmsCoreConstant.ONE;
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 映射并校验销毁任务持久化行。
     */
    private static final class KmsDestructionJobRowMapper implements RowMapper<KmsDestructionJob> {

        /**
         * 映射一条销毁任务。
         *
         * @param resultSet 当前结果集
         * @param rowNumber 当前行号
         * @return 已校验的销毁任务
         * @throws SQLException 读取 JDBC 字段失败时抛出
         */
        @Override
        public KmsDestructionJob mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp claimUntil = resultSet.getTimestamp("claim_until");
            Timestamp completedAt = resultSet.getTimestamp("completed_at");
            Timestamp dueAt = resultSet.getTimestamp("due_at");
            KmsDestructionJobState state = KmsDestructionJobState.fromCode(resultSet.getString("state"));
            if (state == null || dueAt == null || resultSet.getInt("key_version") < SmartKmsCoreConstant.ONE
                    || resultSet.getInt("attempt_count") < SmartKmsCoreConstant.ZERO) {
                throw new KmsPersistenceException();
            }
            return KmsDestructionJob.builder().tenantId(resultSet.getString("tenant_id"))
                    .keyRef(resultSet.getString("key_ref")).keyVersion(resultSet.getInt("key_version"))
                    .state(state).dueAt(dueAt.toInstant()).claimToken(resultSet.getString("claim_token"))
                    .claimUntil(claimUntil == null ? null : claimUntil.toInstant())
                    .attemptCount(resultSet.getInt("attempt_count"))
                    .completedAt(completedAt == null ? null : completedAt.toInstant()).build();
        }
    }
}
