-- 已有库增量迁移：附件对象键必须唯一，避免同一对象被重复挂靠。
-- 执行门槛：目标表存在、历史 object_key 无重复、唯一索引尚不存在；不满足时先停下处理，不能使用 mysql --force 绕过。
SELECT TABLE_NAME, COUNT(*) AS table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket_attachment'
GROUP BY TABLE_NAME;
SELECT object_key, COUNT(*) AS duplicate_count
FROM ticket_attachment
GROUP BY object_key
HAVING COUNT(*) > 1;
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket_attachment'
  AND INDEX_NAME = 'uk_attachment_object'
GROUP BY INDEX_NAME, NON_UNIQUE;

CREATE TEMPORARY TABLE ticket_attachment_owner_gate (
    ready TINYINT NOT NULL,
    CONSTRAINT chk_ticket_attachment_owner_ready CHECK (ready = 1)
);
INSERT INTO ticket_attachment_owner_gate (ready)
SELECT CASE
           WHEN (SELECT COUNT(*)
                 FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'ticket_attachment') = 1
               AND (SELECT COUNT(*)
                    FROM (SELECT object_key
                          FROM ticket_attachment
                          GROUP BY object_key
                          HAVING COUNT(*) > 1) duplicates) = 0
               AND (SELECT COUNT(*)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'ticket_attachment'
                      AND INDEX_NAME = 'uk_attachment_object') = 0
           THEN 1 ELSE 0 END;

ALTER TABLE ticket_attachment
    ADD UNIQUE KEY uk_attachment_object (object_key);

DROP TEMPORARY TABLE ticket_attachment_owner_gate;
