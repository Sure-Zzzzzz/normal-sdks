-- =====================================================================
-- simple-feedback-server-starter 1.0.0 schema 基线
-- 依赖：MySQL 5.7+/8.x，utf8mb4；数据源经 simple-mysql-route-starter 接管接入。
-- 警告：包含 DROP TABLE IF EXISTS，仅用于全新库初始化与受控测试；
--       生产升级只执行 docs/migration/ 下的追加式迁移，禁止重放本文件。
-- 升级指引：见 docs/migration/README.md。
-- =====================================================================

DROP TABLE IF EXISTS `feedback_submission`;

CREATE TABLE `feedback_submission` (
    `id` CHAR(36) NOT NULL COMMENT '反馈ID（服务端生成UUID）',
    `application_code` VARCHAR(64) NOT NULL COMMENT '目标应用编码（命中启用中注册项）',
    `kind` VARCHAR(16) NOT NULL COMMENT '发起形态：proactive-主动发起，intercepted-错误场景发起',
    `type` VARCHAR(16) NOT NULL COMMENT '反馈类型：bug-缺陷报告，feature-功能建议，experience-体验问题，other-其他',
    `score` INT NOT NULL COMMENT '满意度打分（1-10）',
    `content` VARCHAR(2000) NOT NULL COMMENT '反馈文本（1-2000字符，原文存储）',
    `contact_email` VARCHAR(254) DEFAULT NULL COMMENT '联系邮箱（选填，自由输入不关联账号）',
    `submitter_subject_id` VARCHAR(128) NOT NULL COMMENT '提交人subjectId快照（不作鉴权依据）',
    `submitter_display_name` VARCHAR(64) DEFAULT NULL COMMENT '提交人显示名快照（可空，展示回退subjectId）',
    `owner` VARCHAR(160) NOT NULL COMMENT '归属owner（iam:{subjectId}，只从认证上下文派生）',
    `context_path` VARCHAR(500) DEFAULT NULL COMMENT '发起页面路径（深链预填，可改可清）',
    `environment` VARCHAR(2000) DEFAULT NULL COMMENT '环境快照JSON原文（键白名单ua/viewport/theme/locale）',
    `status` VARCHAR(16) NOT NULL COMMENT '状态：submitted-已提交，resolved-已解决，dismissed-不予处理',
    `handled_by` VARCHAR(128) DEFAULT NULL COMMENT '处理人subjectId（最近一次处理覆盖）',
    `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间（UTC可逆）',
    `handler_note` VARCHAR(1000) DEFAULT NULL COMMENT '内部处理备注（每次处理整体覆盖）',
    `reply` VARCHAR(1000) DEFAULT NULL COMMENT '给提交人的答复（提交人在我的反馈可见）',
    `replied_by` VARCHAR(128) DEFAULT NULL COMMENT '答复人subjectId',
    `replied_at` DATETIME DEFAULT NULL COMMENT '答复时间（UTC可逆）',
    `row_version` INT NOT NULL DEFAULT 0 COMMENT '行版本（乐观锁）',
    `idempotency_key` VARCHAR(64) NOT NULL COMMENT '幂等键（浏览器表单会话UUID）',
    `idempotency_digest` VARCHAR(128) NOT NULL COMMENT '幂等摘要（命令内容摘要）',
    `first_response` TEXT COMMENT '首成功响应快照（重放依据，不受后续处理影响）',
    `created_time` DATETIME NOT NULL COMMENT '创建时间（UTC可逆）',
    `updated_time` DATETIME NOT NULL COMMENT '更新时间（UTC可逆）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_idempotency` (`owner`, `idempotency_key`),
    KEY `idx_owner_created` (`owner`, `created_time`),
    KEY `idx_application_status_created` (`application_code`, `status`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品反馈提交表';

DROP TABLE IF EXISTS `feedback_application`;

CREATE TABLE `feedback_application` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `application_code` VARCHAR(64) NOT NULL COMMENT '应用编码（全局唯一，创建后不可变）',
    `display_name` VARCHAR(64) NOT NULL COMMENT '门户展示名',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '应用说明（可选，给提交人看）',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-停用（拦新不拦旧），1-启用',
    `owner_group` VARCHAR(64) DEFAULT NULL COMMENT '期望处理组名（纯提示信息，不做权限计算）',
    `row_version` INT NOT NULL DEFAULT 0 COMMENT '行版本（乐观锁）',
    `created_by` VARCHAR(128) NOT NULL COMMENT '创建人subjectId',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_application_code` (`application_code`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品反馈应用注册表';

DROP TABLE IF EXISTS `feedback_attachment`;

CREATE TABLE `feedback_attachment` (
    `id` CHAR(36) NOT NULL COMMENT '附件ID（服务端生成UUID）',
    `submission_id` CHAR(36) NOT NULL COMMENT '所属反馈ID',
    `storage_key` VARCHAR(500) NOT NULL COMMENT '对象存储键（服务端生成，feedback/{submissionId}/{uuid}.{ext}）',
    `original_filename` VARCHAR(255) NOT NULL COMMENT '用户侧原始文件名（仅作下载展示）',
    `content_type` VARCHAR(100) NOT NULL COMMENT '媒体类型（白名单内）',
    `size_bytes` BIGINT NOT NULL COMMENT '文件大小（字节）',
    `created_time` DATETIME NOT NULL COMMENT '创建时间（UTC可逆）',
    PRIMARY KEY (`id`),
    KEY `idx_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品反馈附件表';
