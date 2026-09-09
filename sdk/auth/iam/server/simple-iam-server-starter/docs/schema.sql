-- =====================================================
-- Simple IAM Server 1.0.0 数据库初始化脚本
-- 依赖：Spring Authorization Server 0.4.1
-- 数据库：MySQL 5.7+ / MySQL 8.0+
-- 说明：仅用于全新初始化，会删除同名既有表。
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 执行前请先创建并选择数据库 sure_auth_iam。

-- =====================================================
-- 1. Spring Authorization Server 标准表
-- =====================================================

DROP TABLE IF EXISTS oauth2_registered_client;
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    application_id BIGINT DEFAULT NULL COMMENT '所属可信应用ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端ID签发时间',
    client_secret VARCHAR(200) DEFAULT NULL COMMENT '客户端密钥（BCrypt加密）',
    client_secret_expires_at TIMESTAMP DEFAULT NULL COMMENT '客户端密钥过期时间',
    client_name VARCHAR(200) NOT NULL COMMENT '客户端名称',
    client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方法',
    authorization_grant_types VARCHAR(1000) NOT NULL COMMENT '授权类型',
    redirect_uris VARCHAR(1000) DEFAULT NULL COMMENT '重定向URI',
    scopes VARCHAR(1000) NOT NULL COMMENT '权限范围',
    client_settings VARCHAR(2000) NOT NULL COMMENT '客户端设置',
    token_settings VARCHAR(2000) NOT NULL COMMENT 'Token设置',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    KEY idx_application_id (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2客户端注册信息表';

DROP TABLE IF EXISTS oauth2_authorization;
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    registered_client_id VARCHAR(100) NOT NULL COMMENT '注册客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户主体名称',
    authorization_grant_type VARCHAR(100) NOT NULL COMMENT '授权类型',
    authorized_scopes VARCHAR(1000) DEFAULT NULL COMMENT '已授权范围',
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
    PRIMARY KEY (id),
    KEY idx_authorization_registered_client (registered_client_id),
    KEY idx_authorization_refresh_expires (refresh_token_expires_at),
    KEY idx_authorization_access_expires (access_token_expires_at),
    KEY idx_authorization_code_expires (authorization_code_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权信息表';

DROP TABLE IF EXISTS oauth2_authorization_consent;
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL COMMENT '注册客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户主体名称',
    authorities VARCHAR(1000) NOT NULL COMMENT '已授权范围',
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权确认信息表';

-- =====================================================
-- 2. IAM 身份与组织表
-- =====================================================

DROP TABLE IF EXISTS iam_user_theme_preference;
DROP TABLE IF EXISTS iam_user;
CREATE TABLE iam_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(200) NOT NULL COMMENT '密码哈希',
    display_name VARCHAR(128) DEFAULT NULL COMMENT '显示名',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    department_id BIGINT DEFAULT NULL COMMENT '所属部门ID',
    identity_source VARCHAR(64) DEFAULT NULL COMMENT '外部身份源编码，本地账号为 NULL',
    external_id VARCHAR(128) DEFAULT NULL COMMENT '外部体系稳定 ID',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    failed_login_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until TIMESTAMP NULL DEFAULT NULL COMMENT '锁定到期时间',
    must_change_password TINYINT(1) NOT NULL DEFAULT 0 COMMENT '须改密标记：1=下次登录强制修改密码（管理员建号/重置密码置 1，自助改密成功清 0）',
    last_login_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近登录时间',
    permission_version BIGINT NOT NULL DEFAULT 0 COMMENT '权限版本：角色/权限/绑定任一变更时 +1，会话校验对比后热刷新登录态权限',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_identity_src_ext (identity_source, external_id),
    KEY idx_email (email),
    KEY idx_phone (phone),
    KEY idx_department_id (department_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM用户表';

CREATE TABLE iam_user_theme_preference (
    user_id BIGINT NOT NULL COMMENT 'IAM用户ID（主键，一名用户一条主题偏好）',
    contract_version INT NOT NULL COMMENT '主题契约版本',
    mode VARCHAR(16) NOT NULL COMMENT '主题模式：light、dark、custom',
    custom_tokens_json LONGTEXT DEFAULT NULL COMMENT '自定义主题完整颜色令牌JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM用户Portal主题偏好表';

DROP TABLE IF EXISTS iam_role;
CREATE TABLE iam_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    code VARCHAR(64) NOT NULL COMMENT '角色编码',
    name VARCHAR(128) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    built_in INT NOT NULL DEFAULT 0 COMMENT '是否内置：1=内置，0=自定义',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM角色表';

DROP TABLE IF EXISTS iam_permission;
CREATE TABLE iam_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    code VARCHAR(128) NOT NULL COMMENT '权限编码',
    name VARCHAR(128) NOT NULL COMMENT '权限名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '权限描述',
    type VARCHAR(20) NOT NULL DEFAULT 'page' COMMENT '权限类型：page、api、data',
    built_in INT NOT NULL DEFAULT 0 COMMENT '是否内置：1=内置，0=自定义',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_type (type),
    KEY idx_built_in (built_in)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM权限表';

DROP TABLE IF EXISTS iam_user_role;
CREATE TABLE iam_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM用户角色关联表';

DROP TABLE IF EXISTS iam_role_permission;
CREATE TABLE iam_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM角色权限关联表';

DROP TABLE IF EXISTS iam_department_role;
CREATE TABLE iam_department_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    department_id BIGINT NOT NULL COMMENT '部门ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_role (department_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM部门角色关联表';

DROP TABLE IF EXISTS iam_department;
CREATE TABLE iam_department (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    code VARCHAR(64) NOT NULL COMMENT '部门编码',
    name VARCHAR(128) NOT NULL COMMENT '部门名称',
    parent_id BIGINT DEFAULT NULL COMMENT '父部门ID',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_parent_id (parent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM部门表';

DROP TABLE IF EXISTS iam_user_group;
CREATE TABLE iam_user_group (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    code VARCHAR(64) NOT NULL COMMENT '协作组编码',
    name VARCHAR(128) NOT NULL COMMENT '协作组名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '协作组描述',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM协作组表';

DROP TABLE IF EXISTS iam_user_group_member;
CREATE TABLE iam_user_group_member (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    group_id BIGINT NOT NULL COMMENT '协作组ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_user (group_id, user_id),
    KEY idx_group_id (group_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM协作组成员表';

-- =====================================================
-- 3. IAM 会话与授权表
-- =====================================================

DROP TABLE IF EXISTS iam_session;
CREATE TABLE iam_session (
    id VARCHAR(100) NOT NULL COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    issuer VARCHAR(255) DEFAULT NULL COMMENT '签发者',
    access_token_jti VARCHAR(100) DEFAULT NULL COMMENT '关联访问令牌JTI',
    refresh_token_family_id VARCHAR(100) DEFAULT NULL COMMENT '关联刷新令牌族ID',
    client_id VARCHAR(100) DEFAULT NULL COMMENT '客户端ID',
    servlet_session_id_hash VARCHAR(64) DEFAULT NULL COMMENT 'Servlet会话ID哈希',
    auth_time TIMESTAMP NULL DEFAULT NULL COMMENT '认证完成时间',
    last_active_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近活跃时间',
    mfa_level INT NOT NULL DEFAULT 0 COMMENT 'MFA认证等级',
    remote_ip VARCHAR(64) DEFAULT NULL COMMENT '登录IP',
    user_agent VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT '撤销时间',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=活跃，0=已撤销',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_access_token_jti (access_token_jti),
    KEY idx_refresh_family (refresh_token_family_id),
    KEY idx_servlet_session_hash (servlet_session_id_hash),
    KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM会话表';

DROP TABLE IF EXISTS iam_refresh_token_family;
CREATE TABLE iam_refresh_token_family (
    id VARCHAR(100) NOT NULL COMMENT '族ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    session_id VARCHAR(100) DEFAULT NULL COMMENT '关联会话ID',
    current_token_hash VARCHAR(128) NOT NULL COMMENT '当前令牌哈希',
    previous_token_hash VARCHAR(128) DEFAULT NULL COMMENT '上一个令牌哈希',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    rotated_at TIMESTAMP NULL DEFAULT NULL COMMENT '最近轮换时间',
    revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT '撤销时间',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=活跃，0=已撤销',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_session_id (session_id),
    KEY idx_token_hash (current_token_hash),
    KEY idx_previous_token_hash (previous_token_hash),
    KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM刷新令牌族表';

DROP TABLE IF EXISTS iam_password_reset;
CREATE TABLE iam_password_reset (
    id VARCHAR(100) NOT NULL COMMENT '凭证ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    token_hash VARCHAR(128) NOT NULL COMMENT '凭证哈希',
    requested_by VARCHAR(64) NOT NULL COMMENT '发起人',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    used_at TIMESTAMP NULL DEFAULT NULL COMMENT '使用时间',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=有效，0=已使用或撤销',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_token_hash (token_hash),
    KEY idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM密码重置凭证表';

DROP TABLE IF EXISTS iam_authorize_context;
CREATE TABLE iam_authorize_context (
    id VARCHAR(100) NOT NULL COMMENT '授权交易ID',
    registered_client_id VARCHAR(100) NOT NULL COMMENT 'SAS注册客户端ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    redirect_uri VARCHAR(1000) NOT NULL COMMENT '已验证重定向URI',
    requested_scopes VARCHAR(1000) NOT NULL COMMENT '规范化请求范围',
    state_hash VARCHAR(64) DEFAULT NULL COMMENT 'state哈希',
    nonce_hash VARCHAR(64) DEFAULT NULL COMMENT 'nonce哈希',
    code_challenge_hash VARCHAR(64) DEFAULT NULL COMMENT 'PKCE challenge哈希',
    code_challenge_method VARCHAR(32) DEFAULT NULL COMMENT 'PKCE challenge方法',
    servlet_session_id_hash VARCHAR(64) DEFAULT NULL COMMENT 'Servlet会话ID哈希',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    iam_session_id VARCHAR(100) DEFAULT NULL COMMENT 'IAM会话ID',
    status VARCHAR(32) NOT NULL COMMENT '交易状态',
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    approved_at TIMESTAMP NULL DEFAULT NULL COMMENT '批准时间',
    denied_at TIMESTAMP NULL DEFAULT NULL COMMENT '拒绝时间',
    completed_at TIMESTAMP NULL DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    KEY idx_client_state_hash (client_id, state_hash),
    KEY idx_servlet_session_status (servlet_session_id_hash, status),
    KEY idx_expires_status (expires_at, status),
    KEY idx_user_id (user_id),
    KEY idx_iam_session_id (iam_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM授权交易上下文表';

DROP TABLE IF EXISTS iam_consent;
CREATE TABLE iam_consent (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    registered_client_id VARCHAR(100) NOT NULL COMMENT 'SAS注册客户端ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    authorized_scopes VARCHAR(1000) NOT NULL COMMENT '规范化已授权范围',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=有效，0=已撤销',
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT '撤销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_registered_client (user_id, registered_client_id),
    KEY idx_client_id (client_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM授权确认投影表';

-- =====================================================
-- 4. 可信应用与 Portal 表
-- =====================================================

DROP TABLE IF EXISTS iam_resource_verification_client;
DROP TABLE IF EXISTS iam_application_authorization;
DROP TABLE IF EXISTS iam_role_authorization_rule;
DROP TABLE IF EXISTS iam_application_permission_manifest;
DROP TABLE IF EXISTS iam_trusted_application_menu;
DROP TABLE IF EXISTS iam_trusted_application_portal;
DROP TABLE IF EXISTS iam_trusted_application;
CREATE TABLE iam_trusted_application (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    application_code VARCHAR(64) NOT NULL COMMENT '应用编码',
    application_name VARCHAR(128) NOT NULL COMMENT '应用展示名',
    description VARCHAR(255) DEFAULT NULL COMMENT '应用描述',
    icon VARCHAR(64) DEFAULT NULL COMMENT '应用图标标识',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_application_code (application_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM可信应用主表';

CREATE TABLE iam_application_authorization (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT 'IAM用户ID',
    application_id BIGINT NOT NULL COMMENT '可信应用ID',
    admitted INT NOT NULL DEFAULT 0 COMMENT '是否已通过应用准入：1=是，0=否',
    roles_json LONGTEXT NOT NULL COMMENT '应用局部角色JSON数组',
    page_permissions_json LONGTEXT NOT NULL COMMENT '应用页面权限JSON数组',
    api_permissions_json LONGTEXT NOT NULL COMMENT '应用精确API权限JSON数组',
    data_grant_document_json LONGTEXT DEFAULT NULL COMMENT '数据授权文档JSON',
    authorization_version BIGINT NOT NULL COMMENT '单调递增的应用授权版本',
    manifest_version VARCHAR(128) NOT NULL COMMENT '权限清单版本',
    manifest_digest VARCHAR(256) NOT NULL COMMENT '权限清单摘要',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=有效，0=撤销',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT '撤销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_application (user_id, application_id),
    KEY idx_application_user_status (application_id, user_id, status),
    KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM用户应用授权投影表';

CREATE TABLE iam_application_permission_manifest (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    application_id BIGINT NOT NULL COMMENT '可信应用ID',
    roles_json LONGTEXT NOT NULL COMMENT '应用局部角色码JSON数组',
    page_permissions_json LONGTEXT NOT NULL COMMENT '应用页面权限码JSON数组',
    api_permissions_json LONGTEXT NOT NULL COMMENT '应用接口权限码JSON数组',
    data_resources_json LONGTEXT NOT NULL COMMENT 'DATA资源声明JSON数组',
    manifest_version BIGINT NOT NULL COMMENT '单调递增的权限清单版本',
    manifest_digest VARCHAR(256) NOT NULL COMMENT '权限清单内容摘要（SHA-256）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_manifest_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM可信应用权限清单表';

CREATE TABLE iam_role_authorization_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    application_id BIGINT NOT NULL COMMENT '可信应用ID',
    page_permissions_json LONGTEXT NOT NULL COMMENT '页面权限JSON数组',
    api_permissions_json LONGTEXT NOT NULL COMMENT 'API权限JSON数组',
    data_grant_template_json LONGTEXT DEFAULT NULL COMMENT '数据权限授权模板',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_app (role_id, application_id),
    KEY idx_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM角色应用授权规则表';

CREATE TABLE iam_resource_verification_client (
    id VARCHAR(100) NOT NULL COMMENT '主键ID',
    application_id BIGINT NOT NULL COMMENT '可信应用ID',
    client_id VARCHAR(100) NOT NULL COMMENT '资源验证客户端ID',
    client_secret_hash VARCHAR(200) NOT NULL COMMENT '资源验证客户端密钥哈希',
    status INT NOT NULL DEFAULT 1 COMMENT '状态：1=有效，0=撤销',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    revoked_at TIMESTAMP NULL DEFAULT NULL COMMENT '撤销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    KEY idx_application_status (application_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM资源令牌验证客户端表';

CREATE TABLE iam_trusted_application_portal (
    application_id BIGINT NOT NULL COMMENT '应用ID（主键，1:1共享主键）',
    enabled INT NOT NULL DEFAULT 0 COMMENT '是否启用Portal集成：1=启用，0=禁用',
    route_prefix VARCHAR(64) NOT NULL COMMENT 'Portal路由前缀',
    entry VARCHAR(512) DEFAULT NULL COMMENT '微前端entry URL',
    api_base VARCHAR(512) DEFAULT NULL COMMENT '后端API基地址',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (application_id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM可信应用Portal集成配置表';

CREATE TABLE iam_trusted_application_menu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    application_id BIGINT NOT NULL COMMENT '所属应用ID',
    code VARCHAR(64) NOT NULL COMMENT '菜单项编码',
    name VARCHAR(128) NOT NULL COMMENT '菜单项名称',
    route VARCHAR(255) NOT NULL COMMENT '相对路由',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_menu_code (application_id, code),
    KEY idx_application_id_sort (application_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM可信应用Portal菜单项表';

-- =====================================================
-- 5. 站内信表
-- =====================================================

DROP TABLE IF EXISTS iam_message;
CREATE TABLE iam_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    recipient_user_id BIGINT NOT NULL COMMENT '收件人用户ID',
    sender_user_id BIGINT NOT NULL COMMENT '发送人用户ID',
    sender_username VARCHAR(64) NOT NULL COMMENT '发送人用户名',
    title VARCHAR(128) NOT NULL COMMENT '标题',
    content VARCHAR(2000) NOT NULL COMMENT '内容',
    send_batch_id VARCHAR(64) DEFAULT NULL COMMENT '发送批次ID',
    target_summary VARCHAR(1000) DEFAULT NULL COMMENT '发送目标摘要',
    read_at TIMESTAMP NULL DEFAULT NULL COMMENT '读取时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_recipient_created (recipient_user_id, created_at),
    KEY idx_recipient_created_id (recipient_user_id, created_at, id),
    KEY idx_recipient_read (recipient_user_id, read_at),
    KEY idx_sender_user_id (sender_user_id),
    KEY idx_send_batch_id (send_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM站内信表';

-- =====================================================
-- 6. 内置角色、权限与部门
-- =====================================================

INSERT INTO iam_role (code, name, description, built_in) VALUES
    ('iam_admin', 'IAM管理员', 'IAM系统内置管理员角色，拥有全部权限', 1),
    ('iam_user', '普通用户', 'IAM系统内置普通用户角色，无管理权限，供业务账号分配基础身份', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), built_in = 1;

INSERT INTO iam_department (code, name, parent_id, sort_order, status) VALUES
    ('root', '默认根部门', NULL, 0, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = NULL, sort_order = 0, status = 1;

INSERT INTO iam_permission (code, name, description, type, built_in) VALUES
    ('iam:user:page', '用户管理页面', '查看IAM用户管理菜单和页面', 'page', 1),
    ('iam:role:page', '角色管理页面', '查看IAM角色管理菜单和页面', 'page', 1),
    ('iam:message:page', '站内信页面', '查看IAM站内信菜单和页面', 'page', 1),
    ('iam:trusted-application:page', '可信应用页面', '查看可信应用管理菜单和页面', 'page', 1),
    ('iam:department:page', '部门管理页面', '查看IAM部门管理菜单和页面', 'page', 1),
    ('iam:user-group:page', '协作组管理页面', '查看IAM协作组管理菜单和页面', 'page', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), type = VALUES(type), built_in = 1;

INSERT INTO iam_permission (code, name, description, type, built_in) VALUES
    ('iam:user:api', '用户管理接口', '调用IAM用户管理相关接口', 'api', 1),
    ('iam:role:api', '角色管理接口', '调用IAM角色管理相关接口', 'api', 1),
    ('iam:permission:api', '权限管理接口', '调用IAM权限管理相关接口', 'api', 1),
    ('iam:message:api', '站内信接口', '调用IAM站内信相关接口', 'api', 1),
    ('iam:trusted-application:api', '可信应用接口', '调用可信应用管理相关接口', 'api', 1),
    ('iam:department:api', '部门管理接口', '调用IAM部门管理相关接口', 'api', 1),
    ('iam:user-group:api', '协作组管理接口', '调用IAM协作组管理相关接口', 'api', 1),
    ('iam:dashboard:api', '仪表盘统计接口', '调用IAM仪表盘统计相关接口', 'api', 1),
    ('iam:session:api', '会话管理接口', '调用IAM会话管理相关接口', 'api', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), type = VALUES(type), built_in = 1;

INSERT INTO iam_permission (code, name, description, type, built_in) VALUES
    ('iam:data:all', 'IAM全量数据', '可访问IAM全部业务数据范围', 'data', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), type = VALUES(type), built_in = 1;

INSERT INTO iam_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM iam_role r
JOIN iam_permission p
WHERE r.code = 'iam_admin'
  AND r.built_in = 1
  AND p.built_in = 1
  AND NOT EXISTS (
      SELECT 1 FROM iam_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET FOREIGN_KEY_CHECKS = 1;
SELECT 'Simple IAM Server 1.0.0 schema initialization completed!' AS status;
