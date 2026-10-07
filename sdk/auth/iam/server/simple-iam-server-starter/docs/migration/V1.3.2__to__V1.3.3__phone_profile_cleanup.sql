-- V1.3.2 -> V1.3.3：admin 手机号资料面清理（纯数据修复，无 DDL；docs/schema.sql 无需变更）
-- 修复前管理台创建/更新用户会把空串手机号原样落库，第二个空串用户撞 uk_phone 唯一索引。
-- phone 与 phone_bound_at 双置 NULL（对齐解绑语义）；同一数据库仅执行一次，执行前备份。

-- 1. 清理空串/纯空白手机号存量行（含修复前已撞索引环境中的幸存脏行）
UPDATE iam_user SET phone = NULL, phone_bound_at = NULL
 WHERE phone IS NOT NULL AND TRIM(phone) = '';

-- 2. 清理后核查：本查询必须返回空集；任何残留行按人工逐条处理，不得盲目批量改写
SELECT id, username
  FROM iam_user
 WHERE (phone IS NOT NULL AND TRIM(phone) = '')
    OR (phone IS NULL AND phone_bound_at IS NOT NULL);

-- 说明：
-- a) 存量未规范化号码（裸号/非法格式，修复前经 admin 路径落库的资料值）不影响 uk_phone
--    正确性，不提供批量改写（避免误改真实号码），登记方按需人工修正；
-- b) 投影表 iam_application_authorization 无手机号字段，无需投影清理；
-- c) 升级 1.3.3 前或后执行本脚本均可（新代码只堵入口，不清历史）。
