-- IAM Server 1.1.0 -> 1.1.1：Portal 可信应用根节点全局排序。
-- 适用于已完成 1.1.0 菜单树升级且尚未执行本脚本的 MySQL 5.7+ / MySQL 8.0+ 数据库。
-- 本文件按前检、升级、后检顺序一次执行；不支持重复执行。

-- ===== 前检（只读）=====
SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND column_name = 'sort_order';

SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND index_name IN ('uk_sort_order', 'idx_enabled_sort_order')
ORDER BY index_name, seq_in_index;

-- ===== 升级 =====
-- 先允许空值，完成稳定回填和后检后再收紧为 NOT NULL，避免 DEFAULT 0 与唯一索引冲突。
ALTER TABLE iam_trusted_application_portal
    ADD COLUMN sort_order BIGINT NULL COMMENT 'Portal应用根节点全局排序值' AFTER api_base;

-- AUTO_INCREMENT 按 INSERT ... SELECT 的 ORDER BY 记录稳定序号；不依赖 MySQL 8 窗口函数或用户变量求值顺序。
DROP TEMPORARY TABLE IF EXISTS tmp_iam_portal_application_order;
CREATE TEMPORARY TABLE tmp_iam_portal_application_order (
    sequence_no BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    PRIMARY KEY (sequence_no),
    UNIQUE KEY uk_application_id (application_id)
) ENGINE=InnoDB;

INSERT INTO tmp_iam_portal_application_order (application_id)
SELECT portal.application_id
FROM iam_trusted_application_portal portal
INNER JOIN iam_trusted_application application ON application.id = portal.application_id
ORDER BY application.created_at ASC, portal.application_id ASC;

UPDATE iam_trusted_application_portal portal
INNER JOIN tmp_iam_portal_application_order ordered ON ordered.application_id = portal.application_id
SET portal.sort_order = ordered.sequence_no * 10;

-- 回填后必须无空值、无重复值，才允许创建唯一约束。
SELECT
    SUM(CASE WHEN sort_order IS NULL THEN 1 ELSE 0 END) AS missing_sort_order_count,
    COUNT(*) - COUNT(DISTINCT sort_order) AS duplicate_sort_order_count,
    COUNT(*) AS portal_integration_count
FROM iam_trusted_application_portal;

ALTER TABLE iam_trusted_application_portal
    MODIFY COLUMN sort_order BIGINT NOT NULL COMMENT 'Portal应用根节点全局排序值',
    ADD UNIQUE KEY uk_sort_order (sort_order),
    ADD KEY idx_enabled_sort_order (enabled, sort_order, application_id);

DROP TEMPORARY TABLE IF EXISTS tmp_iam_portal_application_order;

-- ===== 后检（只读）=====
SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND column_name = 'sort_order';

SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND index_name IN ('uk_sort_order', 'idx_enabled_sort_order')
ORDER BY index_name, seq_in_index;

SELECT
    SUM(CASE WHEN sort_order IS NULL THEN 1 ELSE 0 END) AS missing_sort_order_count,
    COUNT(*) - COUNT(DISTINCT sort_order) AS duplicate_sort_order_count,
    COUNT(*) AS portal_integration_count
FROM iam_trusted_application_portal;
