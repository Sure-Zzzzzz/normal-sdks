-- =====================================================
-- Simple AKSK Server 数据库结构初始化脚本
-- 版本：1.0.0
-- 依赖：Spring Authorization Server 0.4.0
-- 数据库：MySQL 5.7+ / MySQL 8.0+
-- =====================================================

-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 注意：请先执行 00_database.sql 创建数据库，然后 USE sure_auth_aksk 后再执行本脚本

-- =====================================================
-- 1. Spring Authorization Server 0.4.0 标准表结构
-- =====================================================

-- 1.1 oauth2_registered_client 表（客户端注册信息）
DROP TABLE IF EXISTS oauth2_registered_client;
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端ID签发时间',
    client_secret VARCHAR(200) DEFAULT NULL COMMENT '客户端密钥',
    client_secret_expires_at TIMESTAMP DEFAULT NULL COMMENT '客户端密钥过期时间',
    client_name VARCHAR(200) NOT NULL COMMENT '客户端名称',
    client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方法',
    authorization_grant_types VARCHAR(1000) NOT NULL COMMENT '授权类型',
    redirect_uris VARCHAR(1000) DEFAULT NULL COMMENT '重定向URI',
    scopes VARCHAR(1000) NOT NULL COMMENT '权限范围',
    client_settings VARCHAR(2000) NOT NULL COMMENT '客户端设置',
    token_settings VARCHAR(2000) NOT NULL COMMENT 'Token设置',
    owner_user_id VARCHAR(255) DEFAULT NULL COMMENT '所属用户ID(用户级AKSK必填)',
    owner_username VARCHAR(255) DEFAULT NULL COMMENT '所属用户名(用户级AKSK必填)',
    client_type INTEGER NOT NULL DEFAULT 1 COMMENT '客户端类型:1=平台级,2=用户级',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用:1=启用,0=禁用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    KEY idx_owner_user_id (owner_user_id),
    KEY idx_client_type (client_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2客户端注册信息表';

-- 1.2 oauth2_authorization 表（授权信息）
DROP TABLE IF EXISTS oauth2_authorization;
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    registered_client_id VARCHAR(100) NOT NULL COMMENT '注册客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户主体名称',
    authorization_grant_type VARCHAR(100) NOT NULL COMMENT '授权类型',
    authorized_scopes VARCHAR(1000) DEFAULT NULL COMMENT '已授权的权限范围',
    attributes BLOB DEFAULT NULL COMMENT '授权属性',
    state VARCHAR(500) DEFAULT NULL COMMENT '状态值',
    authorization_code_value BLOB DEFAULT NULL COMMENT '授权码值',
    authorization_code_issued_at TIMESTAMP DEFAULT NULL COMMENT '授权码签发时间',
    authorization_code_expires_at TIMESTAMP DEFAULT NULL COMMENT '授权码过期时间',
    authorization_code_metadata BLOB DEFAULT NULL COMMENT '授权码元数据',
    access_token_value BLOB DEFAULT NULL COMMENT '访问令牌值',
    access_token_issued_at TIMESTAMP DEFAULT NULL COMMENT '访问令牌签发时间',
    access_token_expires_at TIMESTAMP DEFAULT NULL COMMENT '访问令牌过期时间',
    access_token_metadata BLOB DEFAULT NULL COMMENT '访问令牌元数据',
    access_token_type VARCHAR(100) DEFAULT NULL COMMENT '访问令牌类型',
    access_token_scopes VARCHAR(1000) DEFAULT NULL COMMENT '访问令牌权限范围',
    oidc_id_token_value BLOB DEFAULT NULL COMMENT 'OIDC ID令牌值',
    oidc_id_token_issued_at TIMESTAMP DEFAULT NULL COMMENT 'OIDC ID令牌签发时间',
    oidc_id_token_expires_at TIMESTAMP DEFAULT NULL COMMENT 'OIDC ID令牌过期时间',
    oidc_id_token_metadata BLOB DEFAULT NULL COMMENT 'OIDC ID令牌元数据',
    refresh_token_value BLOB DEFAULT NULL COMMENT '刷新令牌值',
    refresh_token_issued_at TIMESTAMP DEFAULT NULL COMMENT '刷新令牌签发时间',
    refresh_token_expires_at TIMESTAMP DEFAULT NULL COMMENT '刷新令牌过期时间',
    refresh_token_metadata BLOB DEFAULT NULL COMMENT '刷新令牌元数据',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权信息表';

-- =====================================================
-- 2. 完成
-- =====================================================

SET FOREIGN_KEY_CHECKS = 1;

-- 验证表结构
SELECT 'Schema initialization completed!' AS status;

-- 以下查询需要 information_schema 访问权限，如果报错可以忽略
-- SELECT TABLE_NAME, TABLE_COMMENT
-- FROM information_schema.TABLES
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME LIKE 'oauth2_%';

