-- =====================================================
-- Simple AKSK Server 3.2.x 数据库初始化脚本（全量重建）
-- 依赖：Spring Authorization Server 0.4.1 / simple-aksk-server-core 3.0.4
-- 适用：全新部署，或清库重建。已运行旧版本的库走 migration/ 逐版升级，不要执行本文件。
-- 结构口径与 3.2.0 相同（3.2.1 为纯接口增量，无表结构变化）。
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 注意：请先创建目标数据库并执行 USE <database> 后再执行本脚本。
-- 本脚本为 AKSK 3.2.0 完整初始化脚本，不修改已冻结的 2.x 表结构脚本。

-- =====================================================
-- 1. Spring Authorization Server 标准表结构
-- =====================================================

DROP TABLE IF EXISTS aksk_application_authorization;
DROP TABLE IF EXISTS aksk_client_lifecycle_command;
DROP TABLE IF EXISTS aksk_client_owner_binding;
DROP TABLE IF EXISTS aksk_owner_authorization_projection;
DROP TABLE IF EXISTS aksk_owner_authorization_target_application_state;
DROP TABLE IF EXISTS aksk_owner_authorization_owner_state;
DROP TABLE IF EXISTS aksk_owner_authorization_inbox;
DROP TABLE IF EXISTS aksk_owner_authorization_cursor;
DROP TABLE IF EXISTS oauth2_authorization;
DROP TABLE IF EXISTS oauth2_registered_client;

CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    client_id_issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '客户端ID签发时间',
    client_secret VARCHAR(200) DEFAULT NULL COMMENT '客户端密钥',
    client_secret_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT '客户端密钥过期时间',
    client_name VARCHAR(200) NOT NULL COMMENT '客户端名称',
    client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方法',
    authorization_grant_types VARCHAR(1000) NOT NULL COMMENT '授权类型',
    redirect_uris VARCHAR(1000) DEFAULT NULL COMMENT '重定向URI',
    scopes VARCHAR(1000) NOT NULL COMMENT '权限范围',
    client_settings VARCHAR(2000) NOT NULL COMMENT '客户端设置',
    token_settings VARCHAR(2000) NOT NULL COMMENT '令牌设置',
    owner_user_id VARCHAR(255) DEFAULT NULL COMMENT '所属用户ID',
    owner_username VARCHAR(255) DEFAULT NULL COMMENT '所属用户名',
    client_type INTEGER NOT NULL DEFAULT 1 COMMENT '客户端类型:1=平台级,2=用户级',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth2_registered_client_client_id (client_id),
    KEY idx_oauth2_registered_client_owner_user_id (owner_user_id),
    KEY idx_oauth2_registered_client_client_type (client_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2客户端注册信息';

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    registered_client_id VARCHAR(100) NOT NULL COMMENT '注册客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '主体名称',
    authorization_grant_type VARCHAR(100) NOT NULL COMMENT '授权类型',
    authorized_scopes VARCHAR(1000) DEFAULT NULL COMMENT '已授权范围',
    attributes BLOB DEFAULT NULL COMMENT '授权属性',
    state VARCHAR(500) DEFAULT NULL COMMENT '状态值',
    authorization_code_value BLOB DEFAULT NULL COMMENT '授权码值',
    authorization_code_issued_at TIMESTAMP NULL DEFAULT NULL COMMENT '授权码签发时间',
    authorization_code_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT '授权码过期时间',
    authorization_code_metadata BLOB DEFAULT NULL COMMENT '授权码元数据',
    access_token_value BLOB DEFAULT NULL COMMENT '访问令牌值',
    access_token_issued_at TIMESTAMP NULL DEFAULT NULL COMMENT '访问令牌签发时间',
    access_token_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT '访问令牌过期时间',
    access_token_metadata BLOB DEFAULT NULL COMMENT '访问令牌元数据',
    access_token_type VARCHAR(100) DEFAULT NULL COMMENT '访问令牌类型',
    access_token_scopes VARCHAR(1000) DEFAULT NULL COMMENT '访问令牌范围',
    oidc_id_token_value BLOB DEFAULT NULL COMMENT 'OIDC令牌值',
    oidc_id_token_issued_at TIMESTAMP NULL DEFAULT NULL COMMENT 'OIDC令牌签发时间',
    oidc_id_token_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT 'OIDC令牌过期时间',
    oidc_id_token_metadata BLOB DEFAULT NULL COMMENT 'OIDC令牌元数据',
    refresh_token_value BLOB DEFAULT NULL COMMENT '刷新令牌值',
    refresh_token_issued_at TIMESTAMP NULL DEFAULT NULL COMMENT '刷新令牌签发时间',
    refresh_token_expires_at TIMESTAMP NULL DEFAULT NULL COMMENT '刷新令牌过期时间',
    refresh_token_metadata BLOB DEFAULT NULL COMMENT '刷新令牌元数据',
    PRIMARY KEY (id),
    KEY idx_oauth2_authorization_registered_client_id (registered_client_id),
    KEY idx_oauth2_authorization_access_token_expires_at (access_token_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权信息';

-- =====================================================
-- 2. AKSK服务主体应用授权投影
-- =====================================================

CREATE TABLE aksk_application_authorization (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    client_id VARCHAR(100) NOT NULL COMMENT 'AKSK服务主体标识',
    application_code VARCHAR(64) NOT NULL COMMENT '目标应用标识',
    admitted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已准入',
    roles_json LONGTEXT NOT NULL COMMENT '角色集合JSON',
    page_permissions_json LONGTEXT NOT NULL COMMENT '页面权限集合JSON',
    api_permissions_json LONGTEXT NOT NULL COMMENT '精确API权限集合JSON',
    data_grant_document_json LONGTEXT DEFAULT NULL COMMENT '数据授权文档JSON',
    authorization_version BIGINT NOT NULL COMMENT '对外授权版本',
    lock_version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA乐观锁版本',
    manifest_version VARCHAR(128) NOT NULL COMMENT '权限清单版本',
    manifest_digest VARCHAR(256) NOT NULL COMMENT '权限清单摘要',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(6) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(6) NOT NULL COMMENT '更新时间',
    revoked_at DATETIME(6) DEFAULT NULL COMMENT '撤销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_aksk_application_authorization_client_id (client_id),
    KEY idx_aksk_application_authorization_application_code (application_code),
    KEY idx_aksk_application_authorization_active (enabled, admitted, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AKSK服务主体应用授权投影';

-- =====================================================
-- 3. 身份源 所属人继承绑定（AKU 只存绑定，不复制三权）
-- =====================================================

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

-- =====================================================
-- 4. AKU 自助命令幂等账本（不保存 secret、Token 或三权快照）
-- =====================================================

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

-- =====================================================
-- 5. 身份源 所属人授权本地投影（只保存已验证的最终态）
-- =====================================================

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

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'AKSK 3.2.0 schema initialization completed!' AS status;
