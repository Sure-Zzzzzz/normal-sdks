-- =====================================================
-- Simple AKSK Server 3.1.1 -> 3.2.0 数据库升级脚本
--
-- 创建 OWNER_INHERITED 所需的不可变 binding 和本地授权投影表。不会从旧 owner_username
-- 推导 身份源 人员、不会写入 reader 密钥、不会创建 binding、投影或游标，也不会启用继承。
-- 执行前请完成备份；同一数据库仅执行一次。
-- =====================================================

SET NAMES utf8mb4;

CREATE TABLE aksk_client_owner_binding (
    client_id VARCHAR(100) NOT NULL COMMENT 'AKSK客户端ID，同时关联用户级Client',
    owner_source_id VARCHAR(64) NOT NULL COMMENT '身份源部署稳定所属人来源',
    owner_subject_id VARCHAR(128) NOT NULL COMMENT '身份源稳定所属人ID',
    target_application_id BIGINT NOT NULL COMMENT '身份源可信应用ID',
    authorization_mode VARCHAR(32) NOT NULL COMMENT 'STATIC_LEGACY或OWNER_INHERITED',
    owner_state INTEGER NOT NULL DEFAULT 1 COMMENT '所属人绑定状态:1=有效,0=停用',
    lifecycle_version BIGINT NOT NULL DEFAULT 1 COMMENT '绑定生命周期版本',
    expires_at DATETIME(6) DEFAULT NULL COMMENT '绑定到期时间',
    binding_origin VARCHAR(64) NOT NULL COMMENT '创建来源',
    bound_at DATETIME(6) NOT NULL COMMENT '绑定时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (client_id),
    KEY idx_aksk_owner_binding_owner (owner_source_id, owner_subject_id),
    KEY idx_aksk_owner_binding_application (target_application_id, authorization_mode, owner_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK用户所属人授权绑定';

CREATE TABLE aksk_client_lifecycle_command (
    id VARCHAR(36) NOT NULL COMMENT '命令主键',
    owner_source_id VARCHAR(64) NOT NULL COMMENT '身份源部署稳定所属人来源',
    owner_subject_id VARCHAR(128) NOT NULL COMMENT '身份源稳定所属人ID',
    command_type VARCHAR(32) NOT NULL COMMENT '命令类型',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '调用方幂等键',
    request_fingerprint VARCHAR(64) NOT NULL COMMENT '请求配置SHA-256摘要',
    client_id VARCHAR(100) DEFAULT NULL COMMENT '成功命令生成的AKSK客户端ID',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_aksk_lifecycle_command_idempotency
        (owner_source_id, owner_subject_id, command_type, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK用户自助生命周期命令幂等账本';

CREATE TABLE aksk_owner_authorization_cursor (
    stream_key VARCHAR(64) NOT NULL COMMENT '固定身份源授权日志流标识',
    last_source_sequence BIGINT NOT NULL COMMENT '已原子应用的最后身份源日志序号',
    worker_lease_until TIMESTAMP NULL DEFAULT NULL COMMENT '多实例worker领取租约',
    worker_lease_owner VARCHAR(64) DEFAULT NULL COMMENT 'worker随机持有标识',
    synchronization_lease_until TIMESTAMP NULL DEFAULT NULL COMMENT '最近成功拉取授予的本地授权租约',
    last_successful_pull_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近成功拉取时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (stream_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK 身份源授权日志游标与租约';

CREATE TABLE aksk_owner_authorization_inbox (
    event_id VARCHAR(36) NOT NULL COMMENT '身份源事件幂等标识',
    source_sequence BIGINT NOT NULL COMMENT '身份源全局授权日志序号',
    change_type VARCHAR(32) NOT NULL COMMENT '最终态类型',
    payload_json LONGTEXT NOT NULL COMMENT '身份源契约最终态载荷',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '应用时间',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_owner_authorization_inbox_sequence (source_sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK 身份源授权事件Inbox';

CREATE TABLE aksk_owner_authorization_owner_state (
    owner_key VARCHAR(193) NOT NULL COMMENT 'ownerSourceId与ownerSubjectId稳定组合键',
    owner_source_id VARCHAR(64) NOT NULL COMMENT '身份源所属人来源',
    owner_subject_id VARCHAR(128) NOT NULL COMMENT '身份源所属人主体',
    active INT NOT NULL COMMENT '最终可用状态:1=有效,0=无效',
    owner_security_epoch BIGINT NOT NULL COMMENT '所属人安全纪元',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (owner_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK 身份源所属人授权最终态';

CREATE TABLE aksk_owner_authorization_target_application_state (
    target_application_id BIGINT NOT NULL COMMENT '身份源可信应用ID',
    active INT NOT NULL COMMENT 'OWNER_INHERITED可用状态:1=有效,0=无效',
    application_authorization_epoch BIGINT NOT NULL COMMENT '目标应用授权纪元',
    owner_inherited_access_epoch BIGINT NOT NULL COMMENT 'OWNER_INHERITED访问纪元',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (target_application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK 身份源目标应用授权最终态';

CREATE TABLE aksk_owner_authorization_projection (
    projection_key VARCHAR(320) NOT NULL COMMENT '人员-目标应用稳定组合键',
    owner_source_id VARCHAR(64) NOT NULL COMMENT '身份源所属人来源',
    owner_subject_id VARCHAR(128) NOT NULL COMMENT '身份源所属人主体',
    target_application_id BIGINT NOT NULL COMMENT '身份源可信应用ID',
    active INT NOT NULL COMMENT '投影可用状态:1=有效,0=无效',
    owner_security_epoch BIGINT NOT NULL COMMENT '所属人安全纪元',
    application_authorization_epoch BIGINT NOT NULL COMMENT '目标应用授权纪元',
    owner_inherited_access_epoch BIGINT NOT NULL COMMENT 'OWNER_INHERITED访问纪元',
    projection_access_epoch BIGINT NOT NULL COMMENT '人员-应用投影访问纪元',
    iam_authorization_json LONGTEXT DEFAULT NULL COMMENT '结构化三权快照,inactive时为空',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (projection_key),
    KEY idx_owner_authorization_projection_owner (owner_source_id, owner_subject_id, target_application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK 身份源人员应用授权最终态';

SELECT 'AKSK Server 3.1.1 to 3.2.0 database schema upgrade completed.' AS status;
