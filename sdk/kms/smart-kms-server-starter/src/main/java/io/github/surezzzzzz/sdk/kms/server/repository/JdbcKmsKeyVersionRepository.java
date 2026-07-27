package io.github.surezzzzzz.sdk.kms.server.repository;

import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyVersionState;
import io.github.surezzzzzz.sdk.kms.core.constant.SmartKmsCoreConstant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsCryptoException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKeyVersion;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.core.support.KmsKeyMaterialHelper;
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
import java.util.Optional;

/**
 * 基于 JDBC 的密钥版本仓储。
 *
 * @author surezzzzzz
 */
public class JdbcKmsKeyVersionRepository implements KmsKeyVersionRepository {

    /**
     * 密钥版本行映射器。
     */
    private static final RowMapper<StoredKmsKeyVersion> KEY_VERSION_ROW_MAPPER = new KmsKeyVersionRowMapper();
    /**
     * 执行 tenant 隔离 SQL 的 JDBC 模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建密钥版本 JDBC 仓储。
     *
     * @param jdbcTemplate 执行 tenant 隔离 SQL 的 JDBC 模板
     */
    public JdbcKmsKeyVersionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建 tenant 与逻辑密钥查询参数。
     */
    private static MapSqlParameterSource createKeyParameters(String tenantId, String keyRef) {
        return new MapSqlParameterSource()
                .addValue("tenantId", KmsValidationHelper.requireTenantId(tenantId))
                .addValue("keyRef", KmsValidationHelper.requireKeyRef(keyRef));
    }

    /**
     * 校验待保存版本的租户、标识、状态与材料组合。
     */
    private static void validateKeyVersion(String tenantId, KmsKeyVersion keyVersion) {
        if (keyVersion == null || !KmsValidationHelper.requireTenantId(tenantId).equals(keyVersion.getTenantId())) {
            throw new KmsValidationException();
        }
        KmsValidationHelper.requireKeyRef(keyVersion.getKeyRef());
        if (keyVersion.getVersion() < SmartKmsCoreConstant.ONE || keyVersion.getAlgorithm() == null
                || keyVersion.getState() == null) {
            throw new KmsValidationException();
        }
        if (keyVersion.getState() == KmsKeyVersionState.PENDING_DESTRUCTION) {
            if (keyVersion.getStateBeforeDestruction() != KmsKeyVersionState.ACTIVE
                    && keyVersion.getStateBeforeDestruction() != KmsKeyVersionState.RETIRED) {
                throw new KmsValidationException();
            }
        } else if (keyVersion.getStateBeforeDestruction() != null) {
            throw new KmsValidationException();
        }
        KmsKeyMaterialHelper.validate(keyVersion);
    }

    /**
     * 将密钥版本转换为命名 SQL 参数。
     */
    private static MapSqlParameterSource createVersionParameters(KmsKeyVersion keyVersion) {
        return new MapSqlParameterSource()
                .addValue("tenantId", keyVersion.getTenantId())
                .addValue("keyRef", keyVersion.getKeyRef())
                .addValue("version", keyVersion.getVersion())
                .addValue("state", keyVersion.getState().getCode())
                .addValue("algorithm", keyVersion.getAlgorithm().getCode())
                .addValue("stateBeforeDestruction", keyVersion.getStateBeforeDestruction() == null
                        ? null : keyVersion.getStateBeforeDestruction().getCode())
                .addValue("privateMaterial", keyVersion.getPrivateMaterial())
                .addValue("symmetricMaterial", keyVersion.getSymmetricMaterial())
                .addValue("publicMaterial", keyVersion.getPublicMaterial())
                .addValue("destroyedAt", keyVersion.getDestroyedAt() == null
                        ? null : Timestamp.from(keyVersion.getDestroyedAt()))
                .addValue("rowVersion", SmartKmsCoreConstant.ZERO)
                .addValue("activeState", SmartKmsServerConstant.KEY_VERSION_STATE_ACTIVE)
                .addValue("retiredState", SmartKmsServerConstant.KEY_VERSION_STATE_RETIRED);
    }

    /**
     * 按 tenant、keyRef 与版本查询密钥版本。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @param version  版本号
     * @return 匹配的密钥版本；不存在时为空
     */
    @Override
    public Optional<KmsKeyVersion> findByVersion(String tenantId, String keyRef, int version) {
        List<StoredKmsKeyVersion> versions = queryByVersion(tenantId, keyRef, version);
        return versions.isEmpty() ? Optional.<KmsKeyVersion>empty() : Optional.of(versions.get(
                SmartKmsCoreConstant.ZERO).getKeyVersion());
    }

    /**
     * 按 tenant 和 keyRef 查询全部密钥版本。
     *
     * @param tenantId 资源所属 tenant
     * @param keyRef   逻辑密钥标识
     * @return 已排序的密钥版本集合
     */
    @Override
    public List<KmsKeyVersion> findByKeyRef(String tenantId, String keyRef) {
        MapSqlParameterSource parameters = createKeyParameters(tenantId, keyRef);
        try {
            List<StoredKmsKeyVersion> storedVersions = jdbcTemplate.query(
                    SmartKmsServerConstant.SQL_SELECT_KEY_VERSION_BY_KEY_REF, parameters, KEY_VERSION_ROW_MAPPER);
            java.util.List<KmsKeyVersion> keyVersions = new java.util.ArrayList<KmsKeyVersion>();
            for (StoredKmsKeyVersion storedVersion : storedVersions) {
                keyVersions.add(storedVersion.getKeyVersion());
            }
            return keyVersions;
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 保存密钥版本快照。
     *
     * @param tenantId   资源所属 tenant
     * @param keyVersion 待保存的版本
     * @return 已持久化的密钥版本快照
     */
    @Override
    public KmsKeyVersion save(String tenantId, KmsKeyVersion keyVersion) {
        validateKeyVersion(tenantId, keyVersion);
        List<StoredKmsKeyVersion> existing = queryByVersion(tenantId, keyVersion.getKeyRef(),
                keyVersion.getVersion());
        MapSqlParameterSource parameters = createVersionParameters(keyVersion);
        try {
            if (existing.isEmpty()) {
                int inserted = jdbcTemplate.update(SmartKmsServerConstant.SQL_INSERT_KEY_VERSION, parameters);
                if (inserted != SmartKmsCoreConstant.ONE) {
                    throw new KmsPersistenceException();
                }
            } else {
                StoredKmsKeyVersion storedVersion = existing.get(SmartKmsCoreConstant.ZERO);
                parameters.addValue("rowVersion", storedVersion.getRowVersion())
                        .addValue("nextRowVersion", storedVersion.getRowVersion() + SmartKmsCoreConstant.ONE);
                int updated = jdbcTemplate.update(SmartKmsServerConstant.SQL_UPDATE_KEY_VERSION, parameters);
                if (updated != SmartKmsCoreConstant.ONE) {
                    throw new KmsPersistenceException();
                }
            }
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
        return findByVersion(tenantId, keyVersion.getKeyRef(), keyVersion.getVersion())
                .orElseThrow(KmsPersistenceException::new);
    }

    /**
     * 按完整 tenant、keyRef 和版本读取内部行版本。
     */
    private List<StoredKmsKeyVersion> queryByVersion(String tenantId, String keyRef, int version) {
        if (version < SmartKmsCoreConstant.ONE) {
            throw new KmsValidationException();
        }
        MapSqlParameterSource parameters = createKeyParameters(tenantId, keyRef).addValue("version", version);
        try {
            return jdbcTemplate.query(SmartKmsServerConstant.SQL_SELECT_KEY_VERSION_BY_VERSION,
                    parameters, KEY_VERSION_ROW_MAPPER);
        } catch (DataAccessException exception) {
            throw new KmsPersistenceException();
        }
    }

    /**
     * 读取密钥版本，并将未知编码、损坏状态或材料组合统一转换为安全持久化失败。
     */
    private static final class KmsKeyVersionRowMapper implements RowMapper<StoredKmsKeyVersion> {

        /**
         * 映射一条密钥版本持久化记录。
         *
         * @param resultSet 当前结果集
         * @param rowNumber 当前行号
         * @return 带内部行版本的已校验密钥版本
         * @throws SQLException 读取 JDBC 字段失败时抛出
         */
        @Override
        public StoredKmsKeyVersion mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            Timestamp destroyedAt = resultSet.getTimestamp("destroyed_at");
            KmsKeyVersion keyVersion = new KmsKeyVersion(resultSet.getString("tenant_id"),
                    resultSet.getString("key_ref"), resultSet.getInt("version"),
                    KmsAlgorithm.fromCode(resultSet.getString("algorithm")),
                    KmsKeyVersionState.fromCode(resultSet.getString("state")),
                    KmsKeyVersionState.fromCode(resultSet.getString("state_before_destruction")),
                    resultSet.getBytes("private_material"), resultSet.getBytes("symmetric_material"),
                    resultSet.getBytes("public_material"), destroyedAt == null ? null : destroyedAt.toInstant());
            try {
                validateKeyVersion(keyVersion.getTenantId(), keyVersion);
                return new StoredKmsKeyVersion(keyVersion, resultSet.getLong("row_version"));
            } catch (KmsValidationException exception) {
                throw new KmsPersistenceException();
            } catch (KmsCryptoException exception) {
                throw new KmsPersistenceException();
            }
        }
    }

    /**
     * 密钥版本与仅仓储内部使用的行版本快照。
     */
    private static final class StoredKmsKeyVersion {

        /**
         * 已校验的密钥版本。
         */
        private final KmsKeyVersion keyVersion;
        /**
         * 持久化行版本。
         */
        private final long rowVersion;

        /**
         * 创建内部持久化快照。
         *
         * @param keyVersion 已校验的密钥版本
         * @param rowVersion 持久化行版本
         */
        private StoredKmsKeyVersion(KmsKeyVersion keyVersion, long rowVersion) {
            this.keyVersion = keyVersion;
            this.rowVersion = rowVersion;
        }

        /**
         * 获取密钥版本。
         *
         * @return 已校验的密钥版本
         */
        private KmsKeyVersion getKeyVersion() {
            return keyVersion;
        }

        /**
         * 获取持久化行版本。
         *
         * @return 持久化行版本
         */
        private long getRowVersion() {
            return rowVersion;
        }
    }
}
