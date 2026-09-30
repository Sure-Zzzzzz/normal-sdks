-- =====================================================
-- Simple AKSK Server 2.x -> 3.0.0 数据库升级脚本
-- 适用前提：已使用 simple-aksk-server-starter 2.x，且保留
-- oauth2_registered_client 与 oauth2_authorization 两张历史表。
--
-- 执行要求：
-- 1. 先完成完整数据库备份，并在维护窗口停止 2.x 服务写入；
-- 2. 确认当前库不存在 aksk_application_authorization；
-- 3. 对同一数据库仅执行一次。本脚本不支持重复执行；
-- 4. 每个 Client 重新准入前，必须先撤销其全部 2.x 存量活跃 Token；
--    内省会按当前投影重建授权，不能让旧 Token 因重新准入而获得新权限。
-- 5. 执行后必须逐 Client 配置应用授权并显式准入，才可由 3.0.0 签发
--    或内省有效 Token。禁止从旧 scope 自动推导或自动授予应用权限。
-- =====================================================

SET NAMES utf8mb4;

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

ALTER TABLE oauth2_authorization
    ADD KEY idx_oauth2_authorization_registered_client_id (registered_client_id);

SELECT 'AKSK Server 2.x to 3.0.0 database schema upgrade completed.' AS status;
