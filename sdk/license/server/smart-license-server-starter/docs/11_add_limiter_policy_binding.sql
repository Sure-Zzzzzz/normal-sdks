-- License 2.0 存量库增量迁移：补齐客户 OpenAPI 限频授权快照列及本地投影台账。
-- 执行前停止 License 写入并完成备份；本文件不包含 DROP，也不覆盖已有表。
-- 先执行只读预检。若列或表已存在但结构不一致，停止并人工核对，不能用 IF NOT EXISTS 掩盖漂移。
SELECT column_name, column_type, is_nullable, column_default, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'license_grant'
  AND column_name = 'limiter_policies_json';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('license_grant', 'license_limiter_policy_binding');

-- 仅当 license_grant 已存在且该列缺失、备份完成后，单独执行下列语句。
-- 已有列必须与 01_schema.sql 的 TEXT NULL 定义核对；不覆盖或改写任何存量值。
-- 新增可空列使旧授权保持 NULL，不能把 NULL 猜作历史上已授权的限频政策。
-- ALTER TABLE license_grant
--     ADD COLUMN limiter_policies_json TEXT NULL
--         COMMENT '客户 OpenAPI 限频授权快照（由 License 投影到 limiter Management）';

-- 确认表不存在、完成备份并核对同一客户/服务/资源的历史远端归属后，
-- 逐条执行下面的建表语句；存量授权中的 limiterPoliciesJson 必须按真实远端策略补建或标记未确认，不能猜造。
-- CREATE TABLE license_limiter_policy_binding (
--     binding_id       VARCHAR(128) NOT NULL COMMENT 'License 本地限频投影绑定编号',
--     grant_id         VARCHAR(128) NOT NULL COMMENT '授权编号',
--     grant_revision   BIGINT       NOT NULL COMMENT '本次投影使用的商业修订',
--     customer_id      VARCHAR(128) NOT NULL COMMENT '客户编号',
--     service_code     VARCHAR(128) NOT NULL COMMENT '限频服务编码',
--     resource_code    VARCHAR(128) NOT NULL COMMENT '限频资源编码',
--     namespace        VARCHAR(128) NULL COMMENT 'Management 返回的服务声明命名空间',
--     desired_policy   TEXT         NOT NULL COMMENT '期望窗口快照（JSON）',
--     rule_id          VARCHAR(64)  NULL COMMENT 'Management 规则编号',
--     rule_row_version BIGINT       NULL COMMENT 'Management 规则 CAS 版本',
--     service_revision BIGINT       NULL COMMENT 'Management 服务快照修订',
--     state            VARCHAR(24)  NOT NULL COMMENT 'PENDING_SYNC/SYNCED/DISABLE_PENDING/DISABLED/FAILED/UNCERTAIN/SUPERSEDED',
--     operation_id     VARCHAR(128) NOT NULL COMMENT 'License 幂等操作编号',
--     failure_reason   VARCHAR(128) NULL COMMENT '固定失败分类，不保存上游原文',
--     updated_at       DATETIME(6) NOT NULL,
--     row_version      BIGINT       NOT NULL COMMENT 'License 本地乐观锁版本',
--     PRIMARY KEY (binding_id),
--     UNIQUE KEY uk_license_limiter_binding_target
--         (grant_id, customer_id, service_code, resource_code),
--     KEY idx_license_limiter_binding_grant (grant_id, state),
--     KEY idx_license_limiter_binding_reconcile (state, updated_at)
-- ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'License 授权到 limiter Management 的策略投影台账';

-- 增量迁移后回读授权快照列、绑定表列/索引/字符集及存量绑定事实，再启动 ddl-auto=validate 的服务。
