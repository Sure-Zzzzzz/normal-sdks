-- 部署激活增量迁移：登记获准调用服务；不猜测存量记录绑定，不回写历史签名材料。
-- 本文件先只读核对列存在性；不要直接执行下方 ALTER。已有列不重复追加，缺哪列才人工复制哪一条。
SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'license_activation'
  AND column_name IN ('caller_source_id', 'caller_subject_id');

-- 以下两条只在对应列缺失且类型/可空性已由 information_schema 回读确认后，分别人工复制执行。
-- ALTER TABLE license_activation
--     ADD COLUMN caller_source_id VARCHAR(128) NULL COMMENT '管理端登记的获准调用服务来源';
-- ALTER TABLE license_activation
--     ADD COLUMN caller_subject_id VARCHAR(128) NULL COMMENT '管理端登记的获准调用服务编号';

-- 无默认值、无基于客户/管理员/调用者猜绑定的UPDATE。
-- 存量NULL绑定在在线判定时拒绝，经具备activation.manage及grant.manage DATA的管理API明确补齐。
