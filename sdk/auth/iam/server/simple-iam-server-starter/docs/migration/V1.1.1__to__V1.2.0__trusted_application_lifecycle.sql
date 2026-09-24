-- IAM Server 1.1.1 -> 1.2.0：可信应用全局安全状态、OAuth 安全纪元与异步删除操作。
-- 适用于已完成 1.1.1 的 MySQL 5.7+ / MySQL 8.0+ 数据库；本脚本不可重复执行。

-- ===== 前检（只读）=====
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'iam_trusted_application' AND column_name IN ('status', 'application_security_epoch'))
    OR (table_name = 'iam_consent' AND column_name = 'application_security_epoch'));

-- ===== 升级 =====
ALTER TABLE iam_trusted_application
    ADD COLUMN status INT NOT NULL DEFAULT 1 COMMENT '应用全局安全状态：1=可用，0=停用' AFTER icon,
    ADD COLUMN application_security_epoch BIGINT NOT NULL DEFAULT 1 COMMENT '应用OAuth安全纪元，停用/恢复单调递增' AFTER status;

ALTER TABLE iam_consent
    ADD COLUMN application_security_epoch BIGINT NOT NULL DEFAULT 1 COMMENT '授予时应用安全纪元' AFTER status;

CREATE TABLE iam_trusted_application_cleanup_operation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作ID',
    application_id BIGINT NOT NULL COMMENT '应用ID审计快照，不设外键',
    action VARCHAR(32) NOT NULL COMMENT '操作类型，当前固定DELETE',
    state VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/RETRYING/COMPLETED/FAILED',
    registered_client_ids_json LONGTEXT NOT NULL COMMENT '删除接收时冻结的SAS registered client ID快照',
    cursor_value BIGINT NOT NULL DEFAULT 0 COMMENT '清理游标',
    attempt_count BIGINT NOT NULL DEFAULT 0 COMMENT '尝试次数',
    lease_until TIMESTAMP NULL DEFAULT NULL COMMENT 'worker租约截止时间',
    lease_owner VARCHAR(64) DEFAULT NULL COMMENT 'worker租约随机持有标识，不对管理端暴露',
    failure_category VARCHAR(64) DEFAULT NULL COMMENT '对管理端可见的失败类别',
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_cleanup_application_state (application_id, state, id),
    KEY idx_cleanup_state_lease (state, lease_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM可信应用异步删除操作表';

ALTER TABLE iam_application_authorization
    ADD COLUMN owner_security_epoch BIGINT NOT NULL DEFAULT 1 COMMENT '所属人安全纪元：iam_user.permission_version + 1' AFTER authorization_version,
    ADD COLUMN application_authorization_epoch BIGINT NOT NULL DEFAULT 1 COMMENT '可信应用 AKU 授权纪元' AFTER owner_security_epoch,
    ADD COLUMN projection_access_epoch BIGINT NOT NULL DEFAULT 1 COMMENT '所属人应用访问纪元，撤权后旧AKU不可复活' AFTER application_authorization_epoch;

-- 历史授权行新增列时不能保留 DDL 默认值 1：在线 reader 会将其与人员当前安全纪元比较，
-- 不匹配即失败关闭。升级瞬间按现有用户权限版本建立首个可信快照，不改业务授权内容或版本。
UPDATE iam_application_authorization authorization
INNER JOIN iam_user user ON user.id = authorization.user_id
SET authorization.owner_security_epoch = user.permission_version + 1;

CREATE TABLE iam_application_authorization_state (
    application_id BIGINT NOT NULL COMMENT '可信应用ID',
    authorization_epoch BIGINT NOT NULL DEFAULT 1 COMMENT 'AKU应用授权纪元，只增不减',
    owner_inherited_access_epoch BIGINT NOT NULL DEFAULT 1 COMMENT 'OWNER_INHERITED目标应用访问纪元，只增不减',
    owner_inheritance_enabled INT NOT NULL DEFAULT 0 COMMENT '是否允许创建和使用OWNER_INHERITED AKU',
    recompute_barrier INT NOT NULL DEFAULT 0 COMMENT '批量投影重算屏障，1时reader失败关闭',
    barrier_version BIGINT NOT NULL DEFAULT 0 COMMENT '屏障版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (application_id),
    KEY idx_inheritance_barrier (owner_inheritance_enabled, recompute_barrier, application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM可信应用AKU授权状态表';

CREATE TABLE iam_aksk_authorization_change (
    source_sequence BIGINT NOT NULL AUTO_INCREMENT COMMENT '全局授权变更顺序',
    event_id VARCHAR(36) NOT NULL COMMENT '事件幂等标识',
    change_type VARCHAR(32) NOT NULL COMMENT '最终状态类型',
    aggregate_key VARCHAR(320) NOT NULL COMMENT '稳定聚合键',
    reason_code VARCHAR(64) NOT NULL COMMENT '脱敏变更原因',
    schema_version BIGINT NOT NULL DEFAULT 1 COMMENT '载荷结构版本',
    payload_json LONGTEXT NOT NULL COMMENT '最终授权状态快照',
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交前记录时间',
    PRIMARY KEY (source_sequence),
    UNIQUE KEY uk_iam_aksk_authorization_change_event (event_id),
    KEY idx_iam_aksk_authorization_change_occurred (occurred_at, source_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM到AKSK授权控制面变更日志';

INSERT INTO iam_application_authorization_state
        (application_id, authorization_epoch, owner_inherited_access_epoch, owner_inheritance_enabled, recompute_barrier, barrier_version, created_at, updated_at)
SELECT id, 1, 1, 0, 0, 0, NOW(), NOW()
FROM iam_trusted_application;

-- ===== 后检（只读）=====
SELECT table_name, column_name, column_type, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'iam_trusted_application' AND column_name IN ('status', 'application_security_epoch'))
    OR (table_name = 'iam_consent' AND column_name = 'application_security_epoch')
    OR (table_name = 'iam_trusted_application_cleanup_operation' AND column_name = 'lease_owner')
    OR (table_name = 'iam_application_authorization' AND column_name IN ('owner_security_epoch', 'application_authorization_epoch')))
ORDER BY table_name, ordinal_position;

SELECT index_name, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_cleanup_operation'
ORDER BY index_name, seq_in_index;

SELECT COUNT(*) AS stale_owner_security_epoch_count
FROM iam_application_authorization authorization
INNER JOIN iam_user user ON user.id = authorization.user_id
WHERE authorization.owner_security_epoch <> user.permission_version + 1;
