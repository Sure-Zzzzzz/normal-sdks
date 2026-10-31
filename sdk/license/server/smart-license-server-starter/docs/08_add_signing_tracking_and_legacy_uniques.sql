-- 既有 license_* 数据库的追加迁移：补齐签发追踪列与历史事实唯一屏障。
--
-- 本脚本只允许追加列和唯一索引，不删除、合并或改写任何签发、冻结输入、签名、交付或吊销事实。
-- 先执行全部“只读预检”，把结果保存到受控变更记录；出现重复组、列定义不一致或等价索引已存在时停止，
-- 由人工核对后逐条跳过已具备结构的 ALTER。不要直接执行 01_schema.sql，也不要用清理重复数据的方案“修复”唯一键。
-- 目标为 MySQL 8；索引名和列定义必须以 information_schema 的实际回读为准。

-- 一、列预检：确认最近一次 KMS 调用追踪列是否缺失。查询不输出任何凭据或签名内容。
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'license_signing_operation'
  AND column_name IN ('last_kms_call_ref', 'last_kms_call_at')
ORDER BY ordinal_position;

-- 目标追踪列缺失时才追加；credential_id/target_id 应为 VARCHAR(128)、target_kind 应为 VARCHAR(32)，
-- 三列必须 NOT NULL；字符集/排序规则应与当前数据库建表基线一致。NULL 计数必须为0。
-- 定义、表或列缺失及 NULL 计数不符合时停止，不在本迁移中改写已有列定义。
SELECT table_name, column_name, data_type, character_maximum_length, collation_name, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'license_signing_operation' AND column_name = 'credential_id')
       OR (table_name = 'license_revocation' AND column_name IN ('target_kind', 'target_id')))
ORDER BY table_name, ordinal_position;

SELECT
    (SELECT COUNT(*) FROM license_signing_operation WHERE credential_id IS NULL) AS signing_credential_nulls,
    (SELECT COUNT(*) FROM license_revocation WHERE target_kind IS NULL OR target_id IS NULL) AS revocation_target_nulls;

-- 预期：已存在的列保持原定义；缺少的列才分别执行下方对应 ALTER。
-- 以下两条只在对应列缺失且类型/可空性已由上面的回读确认后，分别人工复制执行。
-- ALTER TABLE license_signing_operation
--     ADD COLUMN last_kms_call_ref VARCHAR(64) NULL
--         COMMENT '最近签名服务调用追踪号（License 侧生成，对账锚）';
-- ALTER TABLE license_signing_operation
--     ADD COLUMN last_kms_call_at DATETIME(6) NULL
--         COMMENT '最近签名服务调用时刻（按主体与时间窗对账）';

-- 二、签发凭据编号重复预检：只返回重复组数量和行数，不返回 credential_id。
SELECT COUNT(*) AS duplicate_groups,
       COALESCE(SUM(group_count), 0) AS duplicate_rows
FROM (
    SELECT COUNT(*) AS group_count
    FROM license_signing_operation
    GROUP BY credential_id
    HAVING COUNT(*) > 1
) AS duplicate_credentials;

-- 三、签发凭据唯一索引预检：已有等价完整唯一索引时不要重复 ADD；非唯一/前缀索引不算通过。
SELECT table_name,
       index_name,
       non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns,
       MAX(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS has_prefix_column
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'license_signing_operation'
GROUP BY table_name, index_name, non_unique
HAVING non_unique = 0
   AND COUNT(*) = 1
   AND SUM(CASE WHEN column_name = 'credential_id' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1;

-- 同名但不同定义的索引属于冲突，不能重复 ADD，也不能在本迁移中删除旧索引。
SELECT table_name, index_name, non_unique, seq_in_index, column_name, sub_part
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'license_signing_operation'
  AND index_name = 'uk_license_signing_operation_credential'
ORDER BY seq_in_index;

-- 仅在签发凭据重复预检的 duplicate_groups=0 且上一步没有等价唯一索引时执行。
-- ALTER TABLE license_signing_operation
--     ADD UNIQUE KEY uk_license_signing_operation_credential (credential_id);

-- 四、吊销目标重复预检：不返回 target_kind/target_id，也不删除旧吊销事实。
SELECT COUNT(*) AS duplicate_groups,
       COALESCE(SUM(group_count), 0) AS duplicate_rows
FROM (
    SELECT COUNT(*) AS group_count
    FROM license_revocation
    GROUP BY target_kind, target_id
    HAVING COUNT(*) > 1
) AS duplicate_revocation_targets;

-- 五、吊销目标唯一索引预检：已有等价唯一索引时不要重复 ADD；非唯一索引不算通过。
SELECT table_name,
       index_name,
       non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns,
       MAX(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS has_prefix_column
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'license_revocation'
GROUP BY table_name, index_name, non_unique
HAVING non_unique = 0
   AND COUNT(*) = 2
   AND COUNT(DISTINCT column_name) = 2
   AND SUM(CASE WHEN column_name IN ('target_kind', 'target_id') AND sub_part IS NULL THEN 1 ELSE 0 END) = 2;

SELECT table_name, index_name, non_unique, seq_in_index, column_name, sub_part
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'license_revocation'
  AND index_name = 'uk_license_revocation_target'
ORDER BY seq_in_index;

-- 仅在吊销目标重复预检的 duplicate_groups=0 且上一步没有等价唯一索引时执行。
-- ALTER TABLE license_revocation
--     ADD UNIQUE KEY uk_license_revocation_target (target_kind, target_id);

-- 六、完成后只读回读：确认列和等价唯一索引存在；不以 ALTER 成功或服务健康替代业务回读。
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'license_signing_operation'
        AND column_name IN ('last_kms_call_ref', 'last_kms_call_at'))
       OR (table_name = 'license_signing_operation'
        AND column_name = 'credential_id')
       OR (table_name = 'license_revocation'
        AND column_name IN ('target_kind', 'target_id')))
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique, seq_in_index, column_name, sub_part
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('license_signing_operation', 'license_revocation')
ORDER BY table_name, index_name, seq_in_index;
