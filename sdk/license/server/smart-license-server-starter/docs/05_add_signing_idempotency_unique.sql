-- 既有 license_* 数据库增量迁移：为签发受理增加精确幂等键唯一屏障。
-- 本文件先只读预检；不要直接执行文件中的 ALTER。确认定义、重复组和索引状态后，
-- 只复制执行实际缺失且已人工批准的追加语句。重复执行不会删除或改绑历史任务。
SELECT column_name, column_type, collation_name, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'license_signing_operation'
  AND column_name = 'idempotency_digest';

-- 幂等列应为 VARCHAR(128)、utf8mb4_bin、NOT NULL；定义不一致时停止，先人工评估，
-- 不隐式改变历史摘要的比较语义。仅排序规则缺失时可按下面受控 MODIFY 语句修正。
-- 本查询只输出重复组数量与涉及任务数量，不输出原幂等键。
SELECT COUNT(*) AS duplicate_groups, COALESCE(SUM(group_count), 0) AS duplicate_rows
FROM (
    SELECT COUNT(*) AS group_count
    FROM license_signing_operation
    GROUP BY idempotency_digest COLLATE utf8mb4_bin
    HAVING COUNT(*) > 1
) AS duplicate_tasks;

SELECT index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns,
       MAX(CASE WHEN sub_part IS NULL THEN 0 ELSE 1 END) AS has_prefix_column
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'license_signing_operation'
GROUP BY index_name, non_unique
HAVING non_unique = 0
   AND COUNT(*) = 1
   AND SUM(CASE WHEN column_name = 'idempotency_digest' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1;

SELECT index_name, non_unique, seq_in_index, column_name, sub_part
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'license_signing_operation'
  AND index_name = 'uk_license_signing_operation_idempotency'
ORDER BY seq_in_index;

SELECT COUNT(*) AS null_rows
FROM license_signing_operation
WHERE idempotency_digest IS NULL;

SELECT COUNT(*) AS oversized_rows
FROM license_signing_operation
WHERE CHAR_LENGTH(idempotency_digest) > 128;

-- 仅在列定义已核对为预期、duplicate_groups=0、且上一步没有等价完整唯一索引时，
-- 手工复制执行下一条。已有索引、前缀索引或同名非唯一索引都必须先人工处理。
-- 不运行01_schema.sql的DROP段；保留历史冻结输入、任务编号与原摘要，兼容既有重放。
-- 仅在列类型/字符集已人工核对、null_rows=0、oversized_rows=0、duplicate_groups=0，
-- 且批准将幂等比较规则统一为二进制精确比较时，才复制以下定义修正；已有正确定义时跳过。
-- ALTER TABLE license_signing_operation
--     MODIFY COLUMN idempotency_digest VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
--         COMMENT '幂等摘要（同请求唯一重放识别）';
-- 修正后重新回读列定义和重复组，再按前述条件复制唯一键追加。
-- ALTER TABLE license_signing_operation
--     ADD UNIQUE KEY uk_license_signing_operation_idempotency (idempotency_digest);
