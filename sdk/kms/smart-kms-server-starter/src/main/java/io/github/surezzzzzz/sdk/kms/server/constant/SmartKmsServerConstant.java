package io.github.surezzzzzz.sdk.kms.server.constant;

/**
 * KMS Server 常量。
 *
 * @author surezzzzzz
 */
public final class SmartKmsServerConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.kms.server";
    /**
     * 默认启用状态。
     */
    public static final boolean DEFAULT_ENABLED = true;
    /**
     * 销毁 worker 默认启用状态。
     */
    public static final boolean DEFAULT_WORKER_ENABLED = true;
    /**
     * API 根路径。
     */
    public static final String API_BASE_PATH = "/api/v1/kms";
    /**
     * 默认分页大小。
     */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /**
     * 最大分页大小。
     */
    public static final int MAX_PAGE_SIZE = 100;
    /**
     * 默认签名输入最大字节数。
     */
    public static final int DEFAULT_MAX_SIGNING_INPUT_BYTES = 65536;
    /**
     * 默认签名最大字节数。
     */
    public static final int DEFAULT_MAX_SIGNATURE_BYTES = 64;
    /**
     * 默认明文最大字节数。
     */
    public static final int DEFAULT_MAX_PLAINTEXT_BYTES = 1048576;
    /**
     * 默认外部 AAD 最大字节数。
     */
    public static final int DEFAULT_MAX_AAD_BYTES = 65536;
    /**
     * 默认 SKMS 封装最大字节数。
     */
    public static final int DEFAULT_MAX_ENVELOPE_BYTES = 1114112;
    /**
     * 默认 idempotency 保留秒数。
     */
    public static final long DEFAULT_IDEMPOTENCY_RETENTION_SECONDS = 86400L;
    /**
     * 幂等作用域锁最大等待秒数。
     */
    public static final int IDEMPOTENCY_SCOPE_LOCK_TIMEOUT_SECONDS = 5;
    /**
     * 获取当前 MySQL 会话幂等作用域锁的 SQL。
     */
    public static final String SQL_TRY_LOCK_IDEMPOTENCY_SCOPE = "SELECT GET_LOCK(?, ?)";
    /**
     * 释放当前 MySQL 会话幂等作用域锁的 SQL。
     */
    public static final String SQL_RELEASE_IDEMPOTENCY_SCOPE_LOCK = "SELECT RELEASE_LOCK(?)";
    /**
     * 默认 worker 扫描间隔毫秒数。
     */
    public static final long DEFAULT_WORKER_SCAN_INTERVAL_MILLIS = 30000L;
    /**
     * 默认 worker 租约秒数。
     */
    public static final long DEFAULT_WORKER_LEASE_SECONDS = 60L;
    /**
     * 默认 worker 连续失败阈值。
     */
    public static final int DEFAULT_WORKER_MAX_CONSECUTIVE_FAILURES = 3;
    /**
     * 查询数据库当前UTC时间的SQL。
     */
    public static final String SQL_SELECT_UTC_TIMESTAMP = "SELECT UTC_TIMESTAMP(3)";
    /**
     * 按 tenant 和 keyRef 查询逻辑密钥的SQL。
     */
    public static final String SQL_SELECT_KEY_BY_KEY_REF = "SELECT tenant_id, key_ref, key_alias, purpose, algorithm, state, "
            + "state_before_destruction, active_version, row_version, created_at, updated_at FROM smart_kms_key "
            + "WHERE tenant_id = :tenantId AND key_ref = :keyRef";
    /**
     * 按 tenant 查询全部逻辑密钥元数据的SQL。
     */
    public static final String SQL_SELECT_ALL_KEY_BY_TENANT = "SELECT tenant_id, key_ref, key_alias, purpose, "
            + "algorithm, state, state_before_destruction, active_version, row_version, created_at, updated_at "
            + "FROM smart_kms_key WHERE tenant_id = :tenantId ORDER BY updated_at DESC, key_ref ASC";
    /**
     * 按 tenant、筛选条件和稳定排序分页查询逻辑密钥元数据的 SQL。
     */
    public static final String SQL_SELECT_KEY_PAGE = "SELECT tenant_id, key_ref, key_alias, purpose, algorithm, state, "
            + "state_before_destruction, active_version, row_version, created_at, updated_at FROM smart_kms_key "
            + "WHERE tenant_id = :tenantId AND (:alias IS NULL OR key_alias LIKE :alias ESCAPE '\\\\') "
            + "AND (:purpose IS NULL OR purpose = :purpose) AND (:algorithm IS NULL OR algorithm = :algorithm) "
            + "AND (:state IS NULL OR state = :state) ORDER BY updated_at DESC, key_ref ASC LIMIT :size OFFSET :offset";
    /**
     * 统计 tenant 筛选后逻辑密钥数量的 SQL。
     */
    public static final String SQL_COUNT_KEY_PAGE = "SELECT COUNT(*) FROM smart_kms_key WHERE tenant_id = :tenantId "
            + "AND (:alias IS NULL OR key_alias LIKE :alias ESCAPE '\\\\') "
            + "AND (:purpose IS NULL OR purpose = :purpose) AND (:algorithm IS NULL OR algorithm = :algorithm) "
            + "AND (:state IS NULL OR state = :state)";
    /**
     * 新增逻辑密钥的SQL。
     */
    public static final String SQL_INSERT_KEY = "INSERT INTO smart_kms_key "
            + "(tenant_id, key_ref, key_alias, purpose, algorithm, state, state_before_destruction, "
            + "active_version, row_version, created_at, updated_at) VALUES "
            + "(:tenantId, :keyRef, :keyAlias, :purpose, :algorithm, :state, :stateBeforeDestruction, "
            + ":activeVersion, :rowVersion, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))";
    /**
     * 按乐观锁版本更新逻辑密钥的SQL。
     */
    public static final String SQL_UPDATE_KEY = "UPDATE smart_kms_key SET key_alias = :keyAlias, purpose = :purpose, "
            + "algorithm = :algorithm, state = :state, state_before_destruction = :stateBeforeDestruction, "
            + "active_version = :activeVersion, row_version = :nextRowVersion, updated_at = UTC_TIMESTAMP(3) "
            + "WHERE tenant_id = :tenantId AND key_ref = :keyRef AND row_version = :rowVersion";
    /**
     * 按 tenant、keyRef 与版本查询密钥版本的SQL。
     */
    public static final String SQL_SELECT_KEY_VERSION_BY_VERSION = "SELECT key_version.tenant_id, kms_key.key_ref, "
            + "key_version.version, key_version.algorithm, key_version.state, "
            + "key_version.state_before_destruction, key_version.private_material, "
            + "key_version.symmetric_material, key_version.public_material, key_version.destroyed_at, "
            + "key_version.row_version FROM smart_kms_key_version key_version INNER JOIN smart_kms_key kms_key "
            + "ON key_version.tenant_id = kms_key.tenant_id AND key_version.key_id = kms_key.id "
            + "WHERE key_version.tenant_id = :tenantId AND kms_key.key_ref = :keyRef "
            + "AND key_version.version = :version";
    /**
     * 按 tenant 和 keyRef 查询全部密钥版本的SQL。
     */
    public static final String SQL_SELECT_KEY_VERSION_BY_KEY_REF = "SELECT key_version.tenant_id, kms_key.key_ref, "
            + "key_version.version, key_version.algorithm, key_version.state, "
            + "key_version.state_before_destruction, key_version.private_material, "
            + "key_version.symmetric_material, key_version.public_material, key_version.destroyed_at, "
            + "key_version.row_version FROM smart_kms_key_version key_version INNER JOIN smart_kms_key kms_key "
            + "ON key_version.tenant_id = kms_key.tenant_id AND key_version.key_id = kms_key.id "
            + "WHERE key_version.tenant_id = :tenantId AND kms_key.key_ref = :keyRef "
            + "ORDER BY key_version.version ASC";
    /**
     * 新增密钥版本的SQL。
     */
    public static final String SQL_INSERT_KEY_VERSION = "INSERT INTO smart_kms_key_version "
            + "(tenant_id, key_id, version, state, algorithm, state_before_destruction, private_material, "
            + "symmetric_material, public_material, activated_at, retired_at, destroyed_at, row_version, "
            + "created_at, updated_at) SELECT :tenantId, id, :version, :state, :algorithm, "
            + ":stateBeforeDestruction, :privateMaterial, :symmetricMaterial, :publicMaterial, "
            + "CASE WHEN :state = :activeState THEN UTC_TIMESTAMP(3) ELSE NULL END, "
            + "CASE WHEN :state = :retiredState THEN UTC_TIMESTAMP(3) ELSE NULL END, :destroyedAt, "
            + ":rowVersion, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM smart_kms_key "
            + "WHERE tenant_id = :tenantId AND key_ref = :keyRef";
    /**
     * 按内部行版本更新密钥版本的SQL。
     */
    public static final String SQL_UPDATE_KEY_VERSION = "UPDATE smart_kms_key_version key_version INNER JOIN "
            + "smart_kms_key kms_key ON key_version.tenant_id = kms_key.tenant_id AND key_version.key_id = kms_key.id SET "
            + "key_version.state = :state, key_version.algorithm = :algorithm, "
            + "key_version.state_before_destruction = :stateBeforeDestruction, "
            + "key_version.private_material = :privateMaterial, key_version.symmetric_material = :symmetricMaterial, "
            + "key_version.public_material = :publicMaterial, key_version.destroyed_at = :destroyedAt, "
            + "key_version.row_version = :nextRowVersion, key_version.updated_at = UTC_TIMESTAMP(3) "
            + "WHERE key_version.tenant_id = :tenantId AND kms_key.key_ref = :keyRef "
            + "AND key_version.version = :version AND key_version.row_version = :rowVersion";
    /**
     * 按完整幂等作用域查询记录的SQL。
     */
    public static final String SQL_SELECT_IDEMPOTENCY_RECORD = "SELECT tenant_id, principal_id, endpoint, "
            + "idempotency_key, request_hash, resource_ref, http_status, expires_at FROM smart_kms_idempotency_record "
            + "WHERE tenant_id = :tenantId AND principal_id = :principalId AND endpoint = :endpoint "
            + "AND idempotency_key = :idempotencyKey FOR UPDATE";
    /**
     * 新增幂等记录的SQL。
     */
    public static final String SQL_INSERT_IDEMPOTENCY_RECORD = "INSERT INTO smart_kms_idempotency_record "
            + "(tenant_id, principal_id, endpoint, idempotency_key, request_hash, resource_ref, http_status, "
            + "response_snapshot, created_at, expires_at) VALUES (:tenantId, :principalId, :endpoint, "
            + ":idempotencyKey, :requestHash, :resourceRef, :httpStatus, :responseSnapshot, UTC_TIMESTAMP(3), "
            + ":expiresAt)";
    /**
     * 按完整幂等作用域读取响应快照的SQL。
     */
    public static final String SQL_SELECT_IDEMPOTENCY_SNAPSHOT = "SELECT response_snapshot FROM "
            + "smart_kms_idempotency_record WHERE tenant_id = :tenantId AND principal_id = :principalId "
            + "AND endpoint = :endpoint AND idempotency_key = :idempotencyKey";
    /**
     * 删除已到期的完整幂等作用域记录的 SQL。
     */
    public static final String SQL_DELETE_EXPIRED_IDEMPOTENCY_RECORD = "DELETE FROM smart_kms_idempotency_record "
            + "WHERE tenant_id = :tenantId AND principal_id = :principalId AND endpoint = :endpoint "
            + "AND idempotency_key = :idempotencyKey AND expires_at <= :now";
    /**
     * 按 tenant 与 keyRef 查询密钥策略的SQL。
     */
    public static final String SQL_SELECT_KEY_POLICY_BY_KEY_REF = "SELECT policy.policy_id, policy.tenant_id, kms_key.key_ref, "
            + "policy.principal_id, policy.key_version, policy.operation, policy.expires_at, policy.row_version "
            + "FROM smart_kms_key_policy policy INNER JOIN smart_kms_key kms_key ON policy.tenant_id = kms_key.tenant_id "
            + "AND policy.key_id = kms_key.id WHERE policy.tenant_id = :tenantId AND kms_key.key_ref = :keyRef "
            + "ORDER BY policy.id ASC";
    /**
     * 新增密钥策略的SQL。
     */
    public static final String SQL_INSERT_KEY_POLICY = "INSERT INTO smart_kms_key_policy "
            + "(tenant_id, key_id, key_ref, policy_id, principal_id, key_version, key_version_scope, operation, "
            + "expires_at, row_version, created_at, updated_at) SELECT :tenantId, id, :keyRef, :policyId, "
            + ":principalId, :keyVersion, :keyVersionScope, :operation, :expiresAt, :rowVersion, "
            + "UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM smart_kms_key WHERE tenant_id = :tenantId "
            + "AND key_ref = :keyRef";
    /**
     * 按 tenant、keyRef、策略标识与行版本撤销策略的SQL。
     */
    public static final String SQL_DELETE_KEY_POLICY = "DELETE policy FROM smart_kms_key_policy policy "
            + "INNER JOIN smart_kms_key kms_key ON policy.tenant_id = kms_key.tenant_id AND policy.key_id = kms_key.id "
            + "WHERE policy.tenant_id = :tenantId AND kms_key.key_ref = :keyRef AND policy.policy_id = :policyId "
            + "AND policy.row_version = :rowVersion";
    /**
     * 新增销毁任务的SQL。
     */
    public static final String SQL_INSERT_DESTRUCTION_JOB = "INSERT INTO smart_kms_destruction_job "
            + "(tenant_id, key_id, key_version, state, due_at, claim_token, claim_until, attempt_count, "
            + "completed_at, created_at, updated_at) SELECT :tenantId, id, :keyVersion, :state, :dueAt, "
            + ":claimToken, :claimUntil, :attemptCount, :completedAt, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
            + "FROM smart_kms_key WHERE tenant_id = :tenantId AND key_ref = :keyRef";
    /**
     * 按 tenant 和 keyRef 查询销毁任务的SQL。
     */
    public static final String SQL_SELECT_DESTRUCTION_JOB_BY_KEY_REF = "SELECT job.tenant_id, kms_key.key_ref, "
            + "job.key_version, job.state, job.due_at, job.claim_token, job.claim_until, job.attempt_count, "
            + "job.completed_at FROM smart_kms_destruction_job job INNER JOIN smart_kms_key kms_key "
            + "ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id WHERE job.tenant_id = :tenantId "
            + "AND kms_key.key_ref = :keyRef ORDER BY job.key_version ASC";
    /**
     * 查询到期或租约过期销毁任务的SQL。
     */
    public static final String SQL_SELECT_DUE_OR_EXPIRED_DESTRUCTION_JOB = "SELECT job.tenant_id, kms_key.key_ref, "
            + "job.key_version, job.state, job.due_at, job.claim_token, job.claim_until, job.attempt_count, "
            + "job.completed_at FROM smart_kms_destruction_job job INNER JOIN smart_kms_key kms_key "
            + "ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id WHERE (job.state = :pendingState "
            + "AND job.due_at <= :now) OR (job.state = :claimedState AND job.claim_until <= :now) "
            + "ORDER BY job.due_at ASC, job.id ASC";
    /**
     * 通过状态和过期租约条件领取销毁任务的SQL。
     */
    public static final String SQL_CLAIM_DESTRUCTION_JOB = "UPDATE smart_kms_destruction_job job INNER JOIN "
            + "smart_kms_key kms_key ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id SET "
            + "job.state = :claimedState, job.claim_token = :claimToken, job.claim_until = :claimUntil, "
            + "job.first_claimed_at = CASE WHEN job.first_claimed_at IS NULL THEN :now ELSE job.first_claimed_at END, "
            + "job.attempt_count = job.attempt_count + 1, job.updated_at = UTC_TIMESTAMP(3) "
            + "WHERE job.tenant_id = :tenantId AND kms_key.key_ref = :keyRef AND job.key_version = :keyVersion "
            + "AND ((job.state = :pendingState AND job.due_at <= :now) OR (job.state = :claimedState "
            + "AND job.claim_until <= :now))";
    /**
     * 使用领取令牌续租销毁任务的SQL。
     */
    public static final String SQL_RENEW_DESTRUCTION_JOB_CLAIM = "UPDATE smart_kms_destruction_job job INNER JOIN "
            + "smart_kms_key kms_key ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id SET "
            + "job.claim_until = :claimUntil, job.updated_at = UTC_TIMESTAMP(3) WHERE job.tenant_id = :tenantId "
            + "AND kms_key.key_ref = :keyRef AND job.key_version = :keyVersion AND job.state = :claimedState "
            + "AND job.claim_token = :claimToken AND job.claim_until > :now";
    /**
     * 使用领取令牌释放销毁任务的SQL。
     */
    public static final String SQL_RELEASE_DESTRUCTION_JOB_CLAIM = "UPDATE smart_kms_destruction_job job INNER JOIN "
            + "smart_kms_key kms_key ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id SET "
            + "job.state = :pendingState, job.claim_token = NULL, job.claim_until = NULL, "
            + "job.updated_at = UTC_TIMESTAMP(3) WHERE job.tenant_id = :tenantId AND kms_key.key_ref = :keyRef "
            + "AND job.key_version = :keyVersion AND job.state = :claimedState AND job.claim_token = :claimToken";
    /**
     * 使用领取令牌完成销毁任务的SQL。
     */
    public static final String SQL_COMPLETE_DESTRUCTION_JOB = "UPDATE smart_kms_destruction_job job INNER JOIN "
            + "smart_kms_key kms_key ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id SET "
            + "job.state = :completedState, job.claim_until = NULL, job.completed_at = :completedAt, "
            + "job.updated_at = UTC_TIMESTAMP(3) WHERE job.tenant_id = :tenantId AND kms_key.key_ref = :keyRef "
            + "AND job.key_version = :keyVersion AND job.state = :claimedState AND job.claim_token = :claimToken";
    /**
     * 查询最早逾期销毁任务到期时间的SQL。
     */
    public static final String SQL_SELECT_OLDEST_OVERDUE_DESTRUCTION_DUE_AT = "SELECT MIN(job.due_at) FROM "
            + "smart_kms_destruction_job job WHERE job.state <> :completedState AND job.due_at < :now";
    /**
     * 删除从未被领取的逻辑密钥销毁任务的SQL。
     */
    public static final String SQL_DELETE_UNCLAIMED_DESTRUCTION_JOB = "DELETE job FROM smart_kms_destruction_job job "
            + "INNER JOIN smart_kms_key kms_key ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id "
            + "WHERE job.tenant_id = :tenantId AND kms_key.key_ref = :keyRef AND job.first_claimed_at IS NULL";
    /**
     * 锁定逻辑密钥销毁任务并查询历史领取事实的SQL。
     */
    public static final String SQL_LOCK_UNCLAIMED_DESTRUCTION_JOB = "SELECT job.id FROM smart_kms_destruction_job job "
            + "INNER JOIN smart_kms_key kms_key ON job.tenant_id = kms_key.tenant_id AND job.key_id = kms_key.id "
            + "WHERE job.tenant_id = :tenantId AND kms_key.key_ref = :keyRef AND job.first_claimed_at IS NOT NULL "
            + "FOR UPDATE";
    /**
     * 按实例标识查询 worker 状态的SQL。
     */
    public static final String SQL_SELECT_DESTRUCTION_WORKER_STATE = "SELECT instance_id, last_successful_scan_at, "
            + "last_failure_at, consecutive_failure_count FROM smart_kms_destruction_worker_state "
            + "WHERE instance_id = :instanceId";
    /**
     * 记录成功扫描并清零连续失败次数的SQL。
     */
    public static final String SQL_RECORD_DESTRUCTION_WORKER_SUCCESS = "INSERT INTO smart_kms_destruction_worker_state "
            + "(instance_id, last_successful_scan_at, consecutive_failure_count, last_failure_at, updated_at) "
            + "VALUES (:instanceId, :scannedAt, 0, NULL, UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE "
            + "last_successful_scan_at = VALUES(last_successful_scan_at), consecutive_failure_count = 0, "
            + "updated_at = UTC_TIMESTAMP(3)";
    /**
     * 记录 worker 失败并递增连续失败次数的SQL。
     */
    public static final String SQL_RECORD_DESTRUCTION_WORKER_FAILURE = "INSERT INTO smart_kms_destruction_worker_state "
            + "(instance_id, last_successful_scan_at, consecutive_failure_count, last_failure_at, updated_at) "
            + "VALUES (:instanceId, NULL, 1, :failedAt, UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE "
            + "last_failure_at = VALUES(last_failure_at), consecutive_failure_count = consecutive_failure_count + 1, "
            + "updated_at = UTC_TIMESTAMP(3)";
    /**
     * 锁定 tenant 内逻辑密钥的 SQL。
     */
    public static final String SQL_LOCK_KEY_BY_KEY_REF = "SELECT id FROM smart_kms_key "
            + "WHERE tenant_id = :tenantId AND key_ref = :keyRef FOR UPDATE";
    /**
     * KMS 管理 scope。
     */
    public static final String SCOPE_MANAGE = "kms.manage";
    /**
     * KMS 签名 scope。
     */
    public static final String SCOPE_SIGN = "kms.sign";
    /**
     * KMS 验签 scope。
     */
    public static final String SCOPE_VERIFY = "kms.verify";
    /**
     * KMS 加密 scope。
     */
    public static final String SCOPE_ENCRYPT = "kms.encrypt";
    /**
     * KMS 解密 scope。
     */
    public static final String SCOPE_DECRYPT = "kms.decrypt";
    /**
     * KMS 公钥读取 scope。
     */
    public static final String SCOPE_READ_PUBLIC_KEY = "kms.read-public-key";
    /**
     * JCA 椭圆曲线密钥对算法名称。
     */
    public static final String JCA_EC_KEY_PAIR_ALGORITHM = "EC";
    /**
     * ES256 固定 P-256 曲线名称。
     */
    public static final String JCA_ES256_CURVE_NAME = "secp256r1";
    /**
     * JCA AES 密钥算法名称。
     */
    public static final String JCA_AES_KEY_ALGORITHM = "AES";
    /**
     * JCA AES-GCM 算法转换名称。
     */
    public static final String JCA_AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    /**
     * JCA ES256 签名算法名称。
     */
    public static final String JCA_ES256_SIGNATURE_ALGORITHM = "SHA256withECDSA";
    /**
     * JCA EC 密钥工厂算法名称。
     */
    public static final String JCA_EC_KEY_FACTORY_ALGORITHM = "EC";
    /**
     * AES-256 生成密钥位数。
     */
    public static final int JCA_AES_256_KEY_SIZE_BITS = 256;
    /**
     * GCM 认证标签位数。
     */
    public static final int JCA_GCM_TAG_SIZE_BITS = 128;
    /**
     * 密钥版本处于活动状态的稳定编码。
     */
    public static final String KEY_VERSION_STATE_ACTIVE = "ACTIVE";
    /**
     * 密钥版本处于退役状态的稳定编码。
     */
    public static final String KEY_VERSION_STATE_RETIRED = "RETIRED";
    /**
     * 常量类禁止实例化消息。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";

    private SmartKmsServerConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
