package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsOwnerDestructionPolicy;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsOwnerDestructionPolicyRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * 基于 JDBC 的 owner 级销毁窗口政策仓储。
 *
 * <p>无行 = 不限制（能力态默认）；upsert 以 owner 为唯一键，写入总是产出新版本号。</p>
 *
 * @author surezzzzzz
 */
public class JdbcKmsOwnerDestructionPolicyRepository implements KmsOwnerDestructionPolicyRepository {

    /**
     * 政策行映射器。
     */
    private static final RowMapper<KmsOwnerDestructionPolicy> POLICY_ROW_MAPPER =
            new KmsOwnerDestructionPolicyRowMapper();
    /**
     * 执行政策 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建政策 JDBC 仓储。
     *
     * @param jdbcTemplate 执行政策 SQL 的 JDBC 模板
     */
    public JdbcKmsOwnerDestructionPolicyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<KmsOwnerDestructionPolicy> find(String ownerPrincipalId) {
        try {
            return jdbcTemplate.query(
                    "SELECT owner_principal_id, min_schedule_ahead_seconds, max_schedule_ahead_seconds,"
                            + " updated_at, row_version FROM smart_kms_owner_destruction_policy"
                            + " WHERE owner_principal_id = :ownerPrincipalId",
                    new MapSqlParameterSource().addValue("ownerPrincipalId", KmsValidationHelper
                            .requireText(ownerPrincipalId, SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH)),
                    POLICY_ROW_MAPPER).stream().findFirst();
        } catch (DataAccessException | KmsValidationException exception) {
            throw new KmsPersistenceException();
        }
    }

    @Override
    public KmsOwnerDestructionPolicy save(KmsOwnerDestructionPolicy policy) {
        if (policy == null || policy.getUpdatedAt() == null) {
            throw new KmsValidationException();
        }
        String ownerPrincipalId = KmsValidationHelper.requireText(policy.getOwnerPrincipalId(),
                SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH);
        long nextVersion = policy.getRowVersion() + 1L;
        try {
            jdbcTemplate.update(
                    "INSERT INTO smart_kms_owner_destruction_policy"
                            + " (owner_principal_id, min_schedule_ahead_seconds, max_schedule_ahead_seconds,"
                            + " updated_at, row_version) VALUES (:ownerPrincipalId, :minSeconds, :maxSeconds,"
                            + " :updatedAt, :rowVersion)"
                            + " ON DUPLICATE KEY UPDATE min_schedule_ahead_seconds = VALUES(min_schedule_ahead_seconds),"
                            + " max_schedule_ahead_seconds = VALUES(max_schedule_ahead_seconds),"
                            + " updated_at = VALUES(updated_at), row_version = VALUES(row_version)",
                    new MapSqlParameterSource()
                            .addValue("ownerPrincipalId", ownerPrincipalId)
                            .addValue("minSeconds", policy.getMinScheduleAheadSeconds())
                            .addValue("maxSeconds", policy.getMaxScheduleAheadSeconds())
                            .addValue("updatedAt", Timestamp.from(policy.getUpdatedAt()))
                            .addValue("rowVersion", Long.valueOf(nextVersion)));
            return KmsOwnerDestructionPolicy.builder()
                    .ownerPrincipalId(ownerPrincipalId)
                    .minScheduleAheadSeconds(policy.getMinScheduleAheadSeconds())
                    .maxScheduleAheadSeconds(policy.getMaxScheduleAheadSeconds())
                    .updatedAt(policy.getUpdatedAt())
                    .rowVersion(nextVersion).build();
        } catch (DataAccessException | KmsValidationException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 政策行映射。
     *
     * @author surezzzzzz
     */
    private static final class KmsOwnerDestructionPolicyRowMapper implements RowMapper<KmsOwnerDestructionPolicy> {

        @Override
        public KmsOwnerDestructionPolicy mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp updatedAt = resultSet.getTimestamp("updated_at");
            long minSeconds = resultSet.getLong("min_schedule_ahead_seconds");
            boolean minAbsent = resultSet.wasNull();
            long maxSeconds = resultSet.getLong("max_schedule_ahead_seconds");
            boolean maxAbsent = resultSet.wasNull();
            try {
                return KmsOwnerDestructionPolicy.builder()
                        .ownerPrincipalId(KmsValidationHelper.requireText(
                                resultSet.getString("owner_principal_id"),
                                SmartKmsCoreConstant.PRINCIPAL_ID_MAX_LENGTH))
                        .minScheduleAheadSeconds(minAbsent ? null : Long.valueOf(minSeconds))
                        .maxScheduleAheadSeconds(maxAbsent ? null : Long.valueOf(maxSeconds))
                        .updatedAt(updatedAt == null ? null : updatedAt.toInstant())
                        .rowVersion(resultSet.getLong("row_version")).build();
            } catch (KmsValidationException exception) {
                throw new KmsPersistenceException();
            }
        }
    }
}
