-- 仅用于从旧版 ticket 表升级的第一阶段；全新空库直接使用 schema.sql。
-- 执行门槛：先完成可恢复备份，并在该备份的克隆库上完整演练本脚本、应用启动和回滚。
-- 执行门槛：核对当前库为目标库，旧 uk_source_idem 存在，新列与新索引均不存在。
-- 执行门槛：确认 OPENAPI 存量为 0；若不为 0，旧请求无法无损推导指纹，必须先另定兼容方案。
-- ALTER 可能重建表或阻塞写入；按克隆库实测耗时安排维护窗口，暂停 Ticket 写请求。
-- 本阶段不删除旧索引；旧服务仍可读取旧结构，但跨凭据复用同一幂等键仍会被旧索引拒绝。
-- 在同一 MySQL 会话中，先按真实核验结果设置：
-- @ticket_target_database、@ticket_backup_verified、@ticket_clone_rehearsal_verified、@ticket_writes_paused。
-- 其中三个 *_verified / *_paused 标志均须为 1；不得使用 mysql --force 绕过失败门槛。

SELECT DATABASE() AS target_database;
SELECT COUNT(*) AS openapi_rows
FROM ticket
WHERE source = 'OPENAPI';
SELECT COLUMN_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND COLUMN_NAME IN ('creator_credential_id', 'idempotency_key', 'request_fingerprint')
ORDER BY ORDINAL_POSITION;
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND INDEX_NAME IN ('uk_source_idem', 'uk_source_credential_idem')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

CREATE TEMPORARY TABLE ticket_idempotency_expand_gate (
    ready TINYINT NOT NULL,
    CONSTRAINT chk_ticket_idempotency_expand_ready CHECK (ready = 1)
);
INSERT INTO ticket_idempotency_expand_gate (ready)
SELECT CASE
           WHEN DATABASE() = @ticket_target_database
               AND @ticket_backup_verified = 1
               AND @ticket_clone_rehearsal_verified = 1
               AND @ticket_writes_paused = 1
               AND (SELECT COUNT(*) FROM ticket WHERE source = 'OPENAPI') = 0
               AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND COLUMN_NAME = 'request_fingerprint') = 0
               AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND COLUMN_NAME IN ('creator_credential_id', 'idempotency_key')) = 2
               AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_idem') = 'source,idempotency_key'
               AND (SELECT MAX(NON_UNIQUE) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_idem') = 0
               AND (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_credential_idem') = 0
           THEN 1 ELSE 0 END;

ALTER TABLE ticket
    ADD COLUMN request_fingerprint CHAR(64) NULL COMMENT '规范化机器开单请求 SHA-256 摘要' AFTER idempotency_key,
    MODIFY COLUMN creator_credential_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
        COMMENT '创建凭据标识（openapi 审计定位）',
    MODIFY COLUMN idempotency_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
        COMMENT '幂等键（openapi 提交）',
    ADD UNIQUE KEY uk_source_credential_idem (source, creator_credential_id, idempotency_key);

INSERT INTO ticket_idempotency_expand_gate (ready)
SELECT CASE
           WHEN (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                   AND COLUMN_NAME = 'request_fingerprint') = 'char(64)'
               AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND COLUMN_NAME IN ('creator_credential_id', 'idempotency_key')
                      AND COLLATION_NAME = 'utf8mb4_bin') = 2
               AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_credential_idem')
                   = 'source,creator_credential_id,idempotency_key'
               AND (SELECT MAX(NON_UNIQUE) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_credential_idem') = 0
               AND (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_idem') = 2
           THEN 1 ELSE 0 END;

DROP TEMPORARY TABLE ticket_idempotency_expand_gate;

SELECT COLUMN_NAME, COLUMN_TYPE, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND COLUMN_NAME IN ('creator_credential_id', 'idempotency_key', 'request_fingerprint')
ORDER BY ORDINAL_POSITION;
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND INDEX_NAME IN ('uk_source_idem', 'uk_source_credential_idem')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
