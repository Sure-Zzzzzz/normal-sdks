-- =====================================================
-- Smart KMS Server 数据库结构初始化脚本
-- 版本：1.0.0
-- 数据库：MySQL 5.7+
-- 警告：本脚本会删除所有同名 smart_kms_* 表及其数据。
-- 仅适用于首次安装或明确可销毁的环境；生产已有表禁止重复执行。
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `smart_kms_destruction_worker_state`;
DROP TABLE IF EXISTS `smart_kms_destruction_job`;
DROP TABLE IF EXISTS `smart_kms_idempotency_record`;
DROP TABLE IF EXISTS `smart_kms_key_policy`;
DROP TABLE IF EXISTS `smart_kms_key_version`;
DROP TABLE IF EXISTS `smart_kms_key`;

CREATE TABLE `smart_kms_key` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '逻辑密钥主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '逻辑密钥所属租户标识',
    `key_ref` VARCHAR(64) NOT NULL COMMENT '服务端生成的逻辑密钥稳定标识',
    `key_alias` VARCHAR(128) NOT NULL COMMENT '租户内可读的逻辑密钥别名',
    `purpose` VARCHAR(16) NOT NULL COMMENT '密钥用途：SIGN-签名，ENCRYPT-加密',
    `algorithm` VARCHAR(32) NOT NULL COMMENT '密码算法：ES256，AES_256_GCM',
    `state` VARCHAR(32) NOT NULL COMMENT '逻辑密钥状态：ACTIVE，DISABLED，PENDING_DESTRUCTION，DESTROYED',
    `state_before_destruction` VARCHAR(32) DEFAULT NULL COMMENT '安排销毁前的逻辑密钥状态：ACTIVE或DISABLED',
    `active_version` INT UNSIGNED DEFAULT NULL COMMENT '当前活动密钥版本号',
    `row_version` BIGINT NOT NULL COMMENT '逻辑密钥乐观锁版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '逻辑密钥创建UTC时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT '逻辑密钥最近更新UTC时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_kms_key_tenant_id_id` (`tenant_id`, `id`),
    UNIQUE KEY `uk_smart_kms_key_tenant_id_key_ref` (`tenant_id`, `key_ref`),
    UNIQUE KEY `uk_smart_kms_key_tenant_id_key_alias` (`tenant_id`, `key_alias`),
    KEY `idx_smart_kms_key_tenant_id_state_updated_at_id` (`tenant_id`, `state`, `updated_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KMS逻辑密钥元数据表';

CREATE TABLE `smart_kms_key_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '密钥版本主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '密钥版本所属租户标识',
    `key_id` BIGINT NOT NULL COMMENT '所属逻辑密钥主键ID',
    `version` INT UNSIGNED NOT NULL COMMENT '逻辑密钥内严格递增的版本号',
    `state` VARCHAR(32) NOT NULL COMMENT '版本状态：ACTIVE，RETIRED，PENDING_DESTRUCTION，DESTROYED',
    `algorithm` VARCHAR(32) NOT NULL COMMENT '密码算法：ES256，AES_256_GCM',
    `state_before_destruction` VARCHAR(32) DEFAULT NULL COMMENT '安排销毁前的版本状态：ACTIVE或RETIRED',
    `private_material` BLOB DEFAULT NULL COMMENT 'ES256私钥PKCS#8 DER材料，仅KMS可信边界可读',
    `symmetric_material` BLOB DEFAULT NULL COMMENT 'AES_256_GCM对称材料，仅KMS可信边界可读',
    `public_material` BLOB DEFAULT NULL COMMENT 'ES256公钥X.509 SPKI DER材料',
    `activated_at` DATETIME(3) DEFAULT NULL COMMENT '版本进入ACTIVE状态的UTC时间',
    `retired_at` DATETIME(3) DEFAULT NULL COMMENT '版本进入RETIRED状态的UTC时间',
    `destroyed_at` DATETIME(3) DEFAULT NULL COMMENT '版本材料被销毁的UTC时间',
    `row_version` BIGINT NOT NULL COMMENT '版本乐观锁版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '密钥版本创建UTC时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT '密钥版本最近更新UTC时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_kms_key_version_tenant_id_key_id_version` (`tenant_id`, `key_id`, `version`),
    KEY `idx_smart_kms_key_version_tenant_id_key_id_state_version` (`tenant_id`, `key_id`, `state`, `version`),
    CONSTRAINT `fk_smart_kms_key_version_key`
        FOREIGN KEY (`tenant_id`, `key_id`) REFERENCES `smart_kms_key` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KMS密钥版本与材料表';

CREATE TABLE `smart_kms_key_policy` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '密钥策略主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '策略所属租户标识',
    `key_id` BIGINT NOT NULL COMMENT '所属逻辑密钥主键ID',
    `key_ref` VARCHAR(64) NOT NULL COMMENT '所属逻辑密钥稳定标识',
    `policy_id` VARCHAR(64) NOT NULL COMMENT '服务端生成的密钥策略稳定标识',
    `principal_id` VARCHAR(128) NOT NULL COMMENT '被精确授权的认证主体标识',
    `key_version` INT UNSIGNED DEFAULT NULL COMMENT '被授权的精确版本号，NULL表示所有版本',
    `key_version_scope` INT NOT NULL COMMENT '唯一约束辅助范围，具体版本为版本号，所有版本固定为0',
    `operation` VARCHAR(32) NOT NULL COMMENT '允许操作：SIGN，VERIFY，ENCRYPT，DECRYPT，READ_PUBLIC_KEY',
    `expires_at` DATETIME(3) DEFAULT NULL COMMENT '策略到期UTC时间，NULL表示不设置到期',
    `row_version` BIGINT NOT NULL COMMENT '策略乐观锁版本',
    `created_at` DATETIME(3) NOT NULL COMMENT '策略创建UTC时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT '策略最近更新UTC时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_kms_key_policy_tenant_id_policy_id` (`tenant_id`, `policy_id`),
    UNIQUE KEY `uk_smart_kms_key_policy_scope` (`tenant_id`, `key_id`, `principal_id`, `key_version_scope`, `operation`),
    KEY `idx_smart_kms_key_policy_tenant_id_key_id` (`tenant_id`, `key_id`),
    CONSTRAINT `fk_smart_kms_key_policy_key`
        FOREIGN KEY (`tenant_id`, `key_id`) REFERENCES `smart_kms_key` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KMS精确允许密钥策略表';

CREATE TABLE `smart_kms_idempotency_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '幂等记录主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '发起管理操作的租户标识',
    `principal_id` VARCHAR(128) NOT NULL COMMENT '发起管理操作的认证主体标识',
    `endpoint` VARCHAR(256) NOT NULL COMMENT '包含具体资源标识的规范化管理端点路径',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '调用方提供的幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '服务端计算的无敏感规范化请求SHA-256摘要',
    `resource_ref` VARCHAR(256) NOT NULL COMMENT '成功操作对应的资源标识',
    `http_status` INT NOT NULL COMMENT '首次成功响应的HTTP状态码',
    `response_snapshot` MEDIUMBLOB DEFAULT NULL COMMENT '模块私有ObjectMapper生成的无敏感响应JSON快照，204时为空',
    `created_at` DATETIME(3) NOT NULL COMMENT '幂等记录创建UTC时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '幂等记录失效UTC时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_kms_idempotency_record_scope` (`tenant_id`, `principal_id`, `endpoint`, `idempotency_key`),
    KEY `idx_smart_kms_idempotency_record_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KMS管理操作幂等记录表';

CREATE TABLE `smart_kms_destruction_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '销毁任务主键ID',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '待销毁版本所属租户标识',
    `key_id` BIGINT NOT NULL COMMENT '待销毁版本所属逻辑密钥主键ID',
    `key_version` INT UNSIGNED NOT NULL COMMENT '待销毁的精确密钥版本号',
    `state` VARCHAR(32) NOT NULL COMMENT '任务状态：PENDING，CLAIMED，COMPLETED',
    `due_at` DATETIME(3) NOT NULL COMMENT '允许开始销毁的最早UTC时间',
    `claim_token` VARCHAR(128) DEFAULT NULL COMMENT '当前领取任务的随机令牌，仅worker内部使用',
    `claim_until` DATETIME(3) DEFAULT NULL COMMENT '当前领取租约到期UTC时间',
    `first_claimed_at` DATETIME(3) DEFAULT NULL COMMENT '首次成功领取UTC时间，一经写入永不清除',
    `attempt_count` INT UNSIGNED NOT NULL COMMENT '已尝试执行销毁的次数',
    `completed_at` DATETIME(3) DEFAULT NULL COMMENT '任务成功完成UTC时间',
    `created_at` DATETIME(3) NOT NULL COMMENT '任务创建UTC时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT '任务最近更新UTC时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_kms_destruction_job_version` (`tenant_id`, `key_id`, `key_version`),
    KEY `idx_smart_kms_destruction_job_state_due_at_claim_until_id` (`state`, `due_at`, `claim_until`, `id`),
    KEY `idx_smart_kms_destruction_job_tenant_id_key_id_key_version_state` (`tenant_id`, `key_id`, `key_version`, `state`),
    CONSTRAINT `fk_smart_kms_destruction_job_key`
        FOREIGN KEY (`tenant_id`, `key_id`) REFERENCES `smart_kms_key` (`tenant_id`, `id`),
    CONSTRAINT `fk_smart_kms_destruction_job_version`
        FOREIGN KEY (`tenant_id`, `key_id`, `key_version`)
        REFERENCES `smart_kms_key_version` (`tenant_id`, `key_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KMS密钥版本销毁任务表';

CREATE TABLE `smart_kms_destruction_worker_state` (
    `instance_id` VARCHAR(128) NOT NULL COMMENT 'KMS服务实例稳定标识',
    `last_successful_scan_at` DATETIME(3) DEFAULT NULL COMMENT '最近一次成功扫描UTC时间',
    `consecutive_failure_count` INT UNSIGNED NOT NULL COMMENT '自最近一次成功扫描后的连续失败次数',
    `last_failure_at` DATETIME(3) DEFAULT NULL COMMENT '最近一次扫描或执行失败UTC时间',
    `updated_at` DATETIME(3) NOT NULL COMMENT 'worker状态最近更新UTC时间',
    PRIMARY KEY (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KMS销毁worker实例运行状态表';

SET FOREIGN_KEY_CHECKS = 1;
