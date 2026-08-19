-- =====================================================
-- Simple AKSK Server 3.0.0 数据库结构初始化脚本
-- 依赖：Spring Authorization Server 0.4.1
-- 数据库：MySQL 5.7+ / MySQL 8.0+
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 注意：请先创建目标数据库并执行 USE <database> 后再执行本脚本。
-- 本脚本为 AKSK 3.0.0 完整初始化脚本，不修改已冻结的 2.x 表结构脚本。

-- =====================================================
-- 1. Spring Authorization Server 标准表结构
-- =====================================================

DROP TABLE IF EXISTS aksk_application_authorization;
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
    KEY idx_oauth2_authorization_registered_client_id (registered_client_id)
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

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'AKSK 3.0.0 schema initialization completed!' AS status;
