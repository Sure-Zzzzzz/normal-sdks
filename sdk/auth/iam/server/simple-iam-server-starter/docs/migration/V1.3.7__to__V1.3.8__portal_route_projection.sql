-- V1.3.7 -> V1.3.8：Portal 可信应用路由投影
-- 仅适用于已经完成 1.3.7 结构迁移的 MySQL 8+ 数据库；执行前按 migration/README.md 完成只读核验和可恢复备份。
-- 脚本是一次性升级 SQL，不自行执行版本判断；不满足前置条件时不得运行，也不使用 schema.sql 覆盖存量数据。

CREATE TABLE iam_user_document_consent (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT 'IAM用户ID',
    document_version_id BIGINT NOT NULL COMMENT '部署配置中的文档版本稳定标识',
    requirements_revision VARCHAR(128) NOT NULL COMMENT '登录要求集合版本',
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户确认时间',
    provider VARCHAR(64) NOT NULL COMMENT '完成确认的登录方式编码',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_document_consent (user_id, document_version_id),
    KEY idx_document_consent_user (user_id),
    CONSTRAINT fk_document_consent_user FOREIGN KEY (user_id) REFERENCES iam_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM用户登录要求文档同意记录';

CREATE TABLE iam_login_requirement_document (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    version_id BIGINT NOT NULL COMMENT '文档稳定版本标识',
    requirements_revision VARCHAR(128) NOT NULL COMMENT '登录要求集合版本',
    code VARCHAR(64) NOT NULL COMMENT '文档类型编码',
    title VARCHAR(128) NOT NULL COMMENT '文档展示标题',
    content LONGTEXT NOT NULL COMMENT '文档正文',
    url VARCHAR(512) DEFAULT NULL COMMENT '可选的文档访问地址',
    required TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否必须同意',
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_requirement_document_version (version_id),
    KEY idx_login_requirement_document_revision (requirements_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM登录前置文档版本';

INSERT INTO iam_login_requirement_document
    (version_id, requirements_revision, code, title, content, url, required)
VALUES
    (2026100101, 'placeholder-2026-10', 'terms-of-service', '服务条款',
     '本页面为 IAM 联调占位文本，不构成正式服务条款。正式发布前必须替换为经批准的服务条款正文。',
      '/legal/terms', 1),
    (2026100102, 'placeholder-2026-10', 'privacy-policy', '隐私政策',
     '本页面为 IAM 联调占位文本，不构成正式隐私政策。正式发布前必须替换为经批准的隐私政策正文。\n\nCookie 说明：IAM 使用必要的会话 Cookie（默认名称为 JSESSIONID）保存浏览器登录状态，并将 CSRF 防伪令牌绑定到同一服务端会话，用于防止跨站伪造请求。必要 Cookie 仅服务于登录、会话续期、退出和受保护的 IAM Web 请求，不用于广告、跨站跟踪或用户画像。Cookie 由统一入口接收并仅用于 IAM 会话；Portal Gateway 在转发请求到业务可信应用前会移除 IAM Cookie，业务应用不得将其作为自身身份凭据。禁用或拦截必要 Cookie 可能导致登录、协议确认、授权和退出流程无法完成。',
      '/legal/privacy', 1);

SELECT COUNT(*) AS existing_user_document_consent FROM iam_user_document_consent;

UPDATE iam_trusted_application_portal
SET config_version = 1
WHERE config_version = 0;

ALTER TABLE iam_trusted_application_portal
    MODIFY COLUMN route_prefix VARCHAR(128) NOT NULL COMMENT 'Portal路由前缀',
    ADD COLUMN target_origin VARCHAR(512) DEFAULT NULL COMMENT '业务自有Nginx HTTP origin，不含路径' AFTER api_base;

CREATE TABLE iam_portal_catalog_revision (
    id INT NOT NULL COMMENT '单例主键，固定为1',
    revision BIGINT NOT NULL COMMENT 'Portal 可信应用路由投影全局版本',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近变更时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM Portal 可信应用路由投影版本单例';

INSERT INTO iam_portal_catalog_revision (id, revision) VALUES (1, 1);

INSERT INTO iam_permission (code, name, description, type, built_in)
VALUES ('iam:portal-application:read:api', 'IAM Portal可信应用路由投影接口', '读取Portal可信应用路由投影', 'api', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), type = VALUES(type), built_in = 1;

SELECT id, revision FROM iam_portal_catalog_revision WHERE id = 1;
SELECT code, type, built_in FROM iam_permission WHERE code = 'iam:portal-application:read:api';
