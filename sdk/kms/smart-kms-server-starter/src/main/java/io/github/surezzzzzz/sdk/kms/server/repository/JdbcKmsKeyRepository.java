package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的逻辑密钥仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsKeyRepository implements KmsKeyRepository, KmsKeyQueryRepository {

    /**
     * 逻辑密钥行映射器。
     */
    private static final RowMapper<KmsKey> KEY_ROW_MAPPER = new KmsKeyRowMapper();
    /**
     * 逻辑密钥管理元数据行映射器。
     */
    private static final RowMapper<KmsKeyMetadata> KEY_METADATA_ROW_MAPPER = new KmsKeyMetadataRowMapper();
    /**
     * 执行 owner 隔离 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建逻辑密钥 JDBC 仓储。
     *
     * @param jdbcTemplate 执行 owner 隔离 SQL 的 JDBC 模板
     */
    public JdbcKmsKeyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 转义 SQL LIKE 的保留字符，使别名筛选保持字面量包含语义。
     */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * 校验待保存逻辑密钥的 owner、标识和状态组合。
     */
    private static void validateKey(String ownerPrincipalId, KmsKey key) {
        if (key == null || !KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId).equals(key.getOwnerPrincipalId())) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireKeyRef(key.getKeyRef());
        KmsValidationHelper.requireKeyAlias(key.getKeyAlias());
        if (key.getPurpose() == null || key.getAlgorithm() == null || key.getState() == null
                || key.getAlgorithm().getPurpose() != key.getPurpose()
                || key.getRowVersion() < SmartKmsCoreConstant.ZERO) {
            throw new KmsValidationException();
        }
        validateState(key);
    }

    /**
     * 校验逻辑密钥状态、恢复状态与活动版本的一致性。
     */
    private static void validateState(KmsKey key) {
        if (key.getState() == KmsKeyState.PENDING_DESTRUCTION) {
            if (key.getStateBeforeDestruction() != KmsKeyState.ACTIVE
                    && key.getStateBeforeDestruction() != KmsKeyState.DISABLED) {
                throw new KmsValidationException();
            }
            return;
        }
        if (key.getStateBeforeDestruction() != null) {
            throw new KmsValidationException();
        }
        if (key.getState() == KmsKeyState.DESTROYED) {
            if (key.getActiveVersion() != null) {
                throw new KmsValidationException();
            }
            return;
        }
        if (key.getActiveVersion() == null || key.getActiveVersion().intValue() < SmartKmsCoreConstant.ONE) {
            throw new KmsValidationException();
        }
    }

    /**
     * 将逻辑密钥转换为命名 SQL 参数。
     */
    private static MapSqlParameterSource createParameters(KmsKey key) {
        return new MapSqlParameterSource()
                .addValue("ownerPrincipalId", key.getOwnerPrincipalId())
                .addValue("keyRef", key.getKeyRef())
                .addValue("keyAlias", key.getKeyAlias())
                .addValue("purpose", key.getPurpose().getCode())
                .addValue("algorithm", key.getAlgorithm().getCode())
                .addValue("state", key.getState().getCode())
                .addValue("stateBeforeDestruction", key.getStateBeforeDestruction() == null
                        ? null : key.getStateBeforeDestruction().getCode())
                .addValue("activeVersion", key.getActiveVersion())
                .addValue("rowVersion", key.getRowVersion());
    }

    private static MapSqlParameterSource pageParameters(String alias, String purpose, String algorithm, String state,
                                                        long offset, int size) {
        return new MapSqlParameterSource().addValue("alias", alias == null ? null : "%" + escapeLike(alias) + "%")
                .addValue("purpose", purpose).addValue("algorithm", algorithm).addValue("state", state)
                .addValue("offset", Long.valueOf(offset)).addValue("size", Integer.valueOf(size));
    }

    private static void appendOwnerPredicate(StringBuilder sql, MapSqlParameterSource parameters,
                                             KmsOwnerAccessScope scope) {
        if (!scope.isAll()) {
            sql.append(" AND owner_principal_id IN (:ownerPrincipalIds)");
            parameters.addValue("ownerPrincipalIds", scope.getOwnerPrincipalIds());
        }
    }

    /**
     * 按 owner 和 keyRef 查询逻辑密钥。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @param keyRef           逻辑密钥标识
     * @return 匹配的逻辑密钥；不存在时为空
     */
    @Override
    public Optional<KmsKey> findByKeyRef(String ownerPrincipalId, String keyRef) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerPrincipalId", KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
        try {
            List<KmsKey> keys = jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_KEY_BY_KEY_REF,
                    parameters, KEY_ROW_MAPPER);
            return keys.isEmpty() ? Optional.<KmsKey>empty() : Optional.of(keys.get(SmartKmsCoreConstant.ZERO));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 查询当前 owner 下单个无材料逻辑密钥元数据。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @param keyRef           逻辑密钥标识
     * @return 无材料密钥元数据；不存在时为空
     */
    @Override
    public Optional<KmsKeyMetadata> findMetadata(String ownerPrincipalId, String keyRef) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerPrincipalId", KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
        try {
            List<KmsKeyMetadata> keys = jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_KEY_BY_KEY_REF,
                    parameters, KEY_METADATA_ROW_MAPPER);
            return keys.isEmpty() ? Optional.<KmsKeyMetadata>empty() : Optional.of(keys.get(SmartKmsCoreConstant.ZERO));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 使用完整 DataPlan 归属范围查询单个密钥，隐藏无权与不存在的差异。
     */
    @Override
    public Optional<KmsKeyMetadata> findMetadata(KmsOwnerAccessScope scope, String keyRef) {
        if (scope == null) {
            throw new KmsValidationException();
        }
        StringBuilder sql = new StringBuilder("SELECT owner_principal_id, key_ref, key_alias, purpose, algorithm, state, ")
                .append("state_before_destruction, active_version, row_version, created_at, updated_at FROM smart_kms_key ")
                .append("WHERE key_ref = :keyRef");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
        appendOwnerPredicate(sql, parameters, scope);
        try {
            List<KmsKeyMetadata> keys = jdbcTemplate.query(sql.toString(), parameters, KEY_METADATA_ROW_MAPPER);
            return keys.isEmpty() ? Optional.<KmsKeyMetadata>empty() : Optional.of(keys.get(SmartKmsCoreConstant.ZERO));
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 查询当前 owner 下全部无材料逻辑密钥元数据。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @return 稳定排序的逻辑密钥元数据集合
     */
    @Override
    public List<KmsKeyMetadata> findAllMetadata(String ownerPrincipalId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerPrincipalId", KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId));
        try {
            return jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_ALL_KEY_BY_OWNER, parameters,
                    KEY_METADATA_ROW_MAPPER);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 按 owner 和筛选条件读取稳定排序的逻辑密钥分页投影。
     */
    @Override
    public KmsKeyPage findPage(String ownerPrincipalId, String alias, String purpose, String algorithm, String state,
                               long offset, int size) {
        if (offset < 0L || size < 1) {
            throw new KmsValidationException();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ownerPrincipalId", KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId))
                .addValue("alias", alias == null ? null : "%" + escapeLike(alias) + "%")
                .addValue("purpose", purpose).addValue("algorithm", algorithm).addValue("state", state)
                .addValue("offset", Long.valueOf(offset)).addValue("size", Integer.valueOf(size));
        try {
            List<KmsKeyMetadata> items = jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_KEY_PAGE, parameters,
                    KEY_METADATA_ROW_MAPPER);
            Long total = jdbcTemplate.queryForObject(SmartKmsServerConstant.SQL_COUNT_KEY_PAGE, parameters, Long.class);
            if (total == null) {
                throw new KmsPersistenceException();
            }
            return new KmsKeyPage(items, total.longValue());
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 使用完整 DataPlan 归属范围执行同一列表与 count 谓词。
     */
    @Override
    public KmsKeyPage findPage(KmsOwnerAccessScope scope, String alias, String purpose, String algorithm, String state,
                               long offset, int size) {
        return findPageInternal(scope, alias, purpose, algorithm, state, null, offset, size);
    }

    /**
     * 使用完整 DataPlan 归属范围和精确归属主体执行同一列表与 count 谓词；归属筛选只能在范围内收窄。
     */
    @Override
    public KmsKeyPage findPage(KmsOwnerAccessScope scope, String alias, String purpose, String algorithm, String state,
                               String ownerPrincipalId, long offset, int size) {
        String filterOwner = ownerPrincipalId == null || ownerPrincipalId.isEmpty() ? null
                : KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId);
        return findPageInternal(scope, alias, purpose, algorithm, state, filterOwner, offset, size);
    }

    /**
     * 组装并执行归属范围、可选筛选与可选精确归属主体的分页查询。
     */
    private KmsKeyPage findPageInternal(KmsOwnerAccessScope scope, String alias, String purpose, String algorithm,
                                        String state, String filterOwnerPrincipalId, long offset, int size) {
        if (scope == null || offset < 0L || size < 1) {
            throw new KmsValidationException();
        }
        String filters = " WHERE (:alias IS NULL OR key_alias LIKE :alias ESCAPE '\\\\') "
                + "AND (:purpose IS NULL OR purpose = :purpose) AND (:algorithm IS NULL OR algorithm = :algorithm) "
                + "AND (:state IS NULL OR state = :state)";
        StringBuilder itemsSql = new StringBuilder("SELECT owner_principal_id, key_ref, key_alias, purpose, algorithm, state, ")
                .append("state_before_destruction, active_version, row_version, created_at, updated_at FROM smart_kms_key")
                .append(filters);
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM smart_kms_key").append(filters);
        MapSqlParameterSource parameters = pageParameters(alias, purpose, algorithm, state, offset, size);
        if (filterOwnerPrincipalId != null) {
            String filterPredicate = " AND owner_principal_id = :filterOwnerPrincipalId";
            itemsSql.append(filterPredicate);
            countSql.append(filterPredicate);
            parameters.addValue("filterOwnerPrincipalId", filterOwnerPrincipalId);
        }
        appendOwnerPredicate(itemsSql, parameters, scope);
        appendOwnerPredicate(countSql, parameters, scope);
        itemsSql.append(" ORDER BY updated_at DESC, key_ref ASC LIMIT :size OFFSET :offset");
        try {
            List<KmsKeyMetadata> items = jdbcTemplate.query(itemsSql.toString(), parameters, KEY_METADATA_ROW_MAPPER);
            Long total = jdbcTemplate.queryForObject(countSql.toString(), parameters, Long.class);
            if (total == null) {
                throw new KmsPersistenceException();
            }
            return new KmsKeyPage(items, total.longValue());
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 保存逻辑密钥元数据。
     *
     * @param ownerPrincipalId 资源所属 owner
     * @param key              待保存的逻辑密钥
     * @return 已持久化的逻辑密钥快照
     */
    @Override
    public KmsKey save(String ownerPrincipalId, KmsKey key) {
        validateKey(ownerPrincipalId, key);
        Optional<KmsKey> existing = findByKeyRef(ownerPrincipalId, key.getKeyRef());
        MapSqlParameterSource parameters = createParameters(key);
        try {
            if (existing.isPresent()) {
                parameters.addValue("nextRowVersion", key.getRowVersion() + SmartKmsCoreConstant.ONE);
                int updated = jdbcTemplate.update(SmartKmsServerConstant.SQL_UPDATE_KEY, parameters);
                if (updated != SmartKmsCoreConstant.ONE) {
                    throw new KmsPersistenceException();
                }
            } else {
                jdbcTemplate.update(SmartKmsServerConstant.SQL_INSERT_KEY, parameters);
            }
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
        return findByKeyRef(ownerPrincipalId, key.getKeyRef()).orElseThrow(KmsPersistenceException::new);
    }

    /**
     * 将持久化行转换为逻辑密钥，并在未知编码或损坏状态时失败关闭。
     */

    /**
     * 将逻辑密钥及其时间元数据映射为管理查询投影。
     */
    private static final class KmsKeyMetadataRowMapper implements RowMapper<KmsKeyMetadata> {

        /**
         * 映射一条带时间元数据的逻辑密钥记录。
         */
        @Override
        public KmsKeyMetadata mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            KmsKey key = KEY_ROW_MAPPER.mapRow(resultSet, rowNumber);
            Timestamp createdAt = resultSet.getTimestamp("created_at");
            Timestamp updatedAt = resultSet.getTimestamp("updated_at");
            if (createdAt == null || updatedAt == null) {
                throw new KmsPersistenceException();
            }
            return new KmsKeyMetadata(key, createdAt.toInstant(), updatedAt.toInstant());
        }
    }

    private static final class KmsKeyRowMapper implements RowMapper<KmsKey> {

        /**
         * 映射一条逻辑密钥持久化记录。
         *
         * @param resultSet 当前结果集
         * @param rowNumber 当前行号
         * @return 已校验的逻辑密钥快照
         * @throws SQLException 读取 JDBC 字段失败时抛出
         */
        @Override
        public KmsKey mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            int activeVersion = resultSet.getInt("active_version");
            Integer nullableActiveVersion = resultSet.wasNull() ? null : Integer.valueOf(activeVersion);
            KmsKey key = KmsKey.builder()
                    .ownerPrincipalId(resultSet.getString("owner_principal_id"))
                    .keyRef(resultSet.getString("key_ref"))
                    .keyAlias(resultSet.getString("key_alias"))
                    .purpose(io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose
                            .fromCode(resultSet.getString("purpose")))
                    .algorithm(io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm
                            .fromCode(resultSet.getString("algorithm")))
                    .state(KmsKeyState.fromCode(resultSet.getString("state")))
                    .stateBeforeDestruction(KmsKeyState
                            .fromCode(resultSet.getString("state_before_destruction")))
                    .activeVersion(nullableActiveVersion)
                    .rowVersion(resultSet.getLong("row_version"))
                    .build();
            try {
                validateKey(key.getOwnerPrincipalId(), key);
                return key;
            } catch (KmsValidationException exception) {
                throw new KmsPersistenceException();
            }
        }
    }
}
