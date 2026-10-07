package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsDestructionJobState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.support.KmsStateHelper;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsKeyDestructionDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 一次已提交数据读取产生密钥、任务和历史领取资格，不锁定或暂停执行器。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcKmsKeyDestructionQueryRepository implements KmsKeyDestructionQueryRepository {

    /**
     * 命名参数 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static Optional<KmsKeyDestructionDetails> readDetails(ResultSet rows, String keyRef) throws SQLException {
        if (!rows.next()) {
            return Optional.empty();
        }
        KmsKeyState keyState = KmsKeyState.fromCode(rows.getString("key_state"));
        KmsKeyState previousKeyState = KmsKeyState.fromCode(rows.getString("key_previous_state"));
        long rowVersion = rows.getLong("key_row_version");
        if (keyState == null || rowVersion < SmartKmsCoreConstant.ZERO) {
            throw new KmsPersistenceException();
        }
        boolean cancelEligible = keyState == KmsKeyState.PENDING_DESTRUCTION
                && (previousKeyState == KmsKeyState.ACTIVE || previousKeyState == KmsKeyState.DISABLED);
        List<KmsKeyDestructionDetails.Job> items = new ArrayList<KmsKeyDestructionDetails.Job>();
        int versionCount = SmartKmsCoreConstant.ZERO;
        boolean hasUncompleted = false;
        do {
            int version = rows.getInt("version_number");
            if (rows.wasNull() || version < SmartKmsCoreConstant.ONE) {
                throw new KmsPersistenceException();
            }
            versionCount++;
            KmsKeyVersionState versionState = KmsKeyVersionState.fromCode(rows.getString("version_state"));
            KmsKeyVersionState previousVersionState = KmsKeyVersionState.fromCode(rows.getString("version_previous_state"));
            if (versionState == null) {
                throw new KmsPersistenceException();
            }
            rows.getLong("job_id");
            if (rows.wasNull()) {
                if (!KmsStateHelper.isPublishablePublicKey(keyState, versionState)) {
                    throw new KmsPersistenceException();
                }
                cancelEligible = false;
                continue;
            }
            KmsDestructionJobState jobState = KmsDestructionJobState.fromCode(rows.getString("job_state"));
            Timestamp dueAt = rows.getTimestamp("due_at");
            Timestamp completedAt = rows.getTimestamp("completed_at");
            Timestamp firstClaimedAt = rows.getTimestamp("first_claimed_at");
            if (jobState == null || dueAt == null
                    || (jobState == KmsDestructionJobState.COMPLETED) != (completedAt != null)
                    || (keyState != KmsKeyState.PENDING_DESTRUCTION && keyState != KmsKeyState.DESTROYED)
                    || (jobState == KmsDestructionJobState.COMPLETED
                    ? versionState != KmsKeyVersionState.DESTROYED : versionState != KmsKeyVersionState.PENDING_DESTRUCTION)
                    || (keyState == KmsKeyState.DESTROYED && jobState != KmsDestructionJobState.COMPLETED)
                    || (jobState != KmsDestructionJobState.PENDING && firstClaimedAt == null)) {
                throw new KmsPersistenceException();
            }
            hasUncompleted |= jobState != KmsDestructionJobState.COMPLETED;
            cancelEligible &= jobState == KmsDestructionJobState.PENDING && firstClaimedAt == null
                    && versionState == KmsKeyVersionState.PENDING_DESTRUCTION
                    && (previousVersionState == KmsKeyVersionState.ACTIVE || previousVersionState == KmsKeyVersionState.RETIRED);
            items.add(new KmsKeyDestructionDetails.Job(version, jobState, dueAt.toInstant(),
                    completedAt == null ? null : completedAt.toInstant()));
        } while (rows.next());
        if ((keyState == KmsKeyState.PENDING_DESTRUCTION || keyState == KmsKeyState.DESTROYED)
                && (items.size() != versionCount || (keyState == KmsKeyState.PENDING_DESTRUCTION && !hasUncompleted))) {
            throw new KmsPersistenceException();
        }
        return Optional.of(new KmsKeyDestructionDetails(keyRef, keyState, rowVersion,
                cancelEligible && !items.isEmpty(), items));
    }

    /**
     * 在明确归属内读取同一数据库快照。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Optional<KmsKeyDestructionDetails> findDetails(String ownerPrincipalId, String keyRef) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerPrincipalId", KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
        try {
            ResultSetExtractor<Optional<KmsKeyDestructionDetails>> extractor = rows -> readDetails(rows, keyRef);
            Optional<KmsKeyDestructionDetails> result = jdbcTemplate.query(
                    SmartKmsServerConstant.SQL_SELECT_KEY_DESTRUCTION_DETAILS, parameters, extractor);
            log.debug("KMS 密钥销毁明细读取 keyRef={} found={} jobs={}", keyRef, result.isPresent(),
                    result.isPresent() ? result.get().getItems().size() : SmartKmsCoreConstant.ZERO);
            return result;
        } catch (DataAccessException exception) {
            log.debug("KMS 密钥销毁明细持久化失败 keyRef={}", keyRef);
            throw new KmsPersistenceException();
        } catch (KmsPersistenceException exception) {
            log.debug("KMS 密钥销毁明细快照状态异常 keyRef={}", keyRef);
            throw exception;
        }
    }
}
