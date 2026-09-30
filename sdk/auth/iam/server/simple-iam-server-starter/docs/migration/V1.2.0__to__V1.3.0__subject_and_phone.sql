-- V1.2.0 -> V1.3.0：公开主体 ID 与手机号绑定列（纯 DDL）
-- 存量 subject_id 回填不在此脚本：SQL 无法调用 SubjectIdGenerator SPI，
-- 生成算法会与代码不一致；回填一律走应用启动的幂等回填（IamSubjectIdBackfillService）。

ALTER TABLE iam_user
    ADD COLUMN subject_id VARCHAR(64) NULL COMMENT '对外用户主体，SPI 生成，写后不改；软删行永久占位永不复用' AFTER username,
    ADD COLUMN phone_bound_at TIMESTAMP NULL DEFAULT NULL COMMENT '手机号绑定时间（绑定/换绑验证通过时写入，登录不更新）；NULL=未绑定' AFTER phone,
    ADD UNIQUE KEY uk_subject_id (subject_id);

-- 手机号唯一化：删除普通索引 idx_phone，升级为唯一索引 uk_phone
-- （唯一索引对 NULL 不生效：解绑双置 NULL 后号码即释放）
ALTER TABLE iam_user
    DROP KEY idx_phone,
    ADD UNIQUE KEY uk_phone (phone);

-- 内置应用标记列：管理面可将可信应用标记为内置（禁删除/禁停用，客户端强制免授权确认）
ALTER TABLE iam_trusted_application
    ADD COLUMN built_in TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否平台内置应用；引导配置清单内的编码无论本列取值一律视为内置' AFTER application_security_epoch;

-- 存量平台子应用置为内置（iam 为引导配置兜底项，列置 1 保持口径一致；aksk 为访问凭证管理台）
UPDATE iam_trusted_application SET built_in = 1 WHERE application_code IN ('iam', 'aksk');

-- 聚散收口：专属命名中性化（RENAME 保留 AUTO_INCREMENT 序列，消费方游标无感；索引名与表注释同批刷新）
RENAME TABLE iam_aksk_authorization_change TO iam_owner_authorization_change_log;
ALTER TABLE iam_owner_authorization_change_log
    RENAME INDEX uk_iam_aksk_authorization_change_event TO uk_iam_owner_authorization_change_log_event,
    RENAME INDEX idx_iam_aksk_authorization_change_occurred TO idx_iam_owner_authorization_change_log_occurred,
    COMMENT='IAM所属人授权变更日志';
ALTER TABLE iam_application_authorization_state COMMENT='IAM所属人授权状态表';
