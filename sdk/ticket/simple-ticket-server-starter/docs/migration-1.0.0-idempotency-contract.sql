-- 仅在 expand 阶段完成、新版本所有实例已替换并通过真实幂等回归后执行。
-- 执行门槛：重新确认可恢复备份及克隆演练；停止并核对所有旧 Ticket 服务实例。
-- 旧代码按 (source, idempotency_key) 查询；删除旧索引后若出现跨凭据同键数据，旧代码可能查出多行。
-- 执行门槛：确认新三列唯一键存在、旧双列唯一键仍存在，且不再计划直接回滚到旧代码。
-- 删除旧键之后才能验证不同凭据复用同一幂等键；届时旧键不能无条件加回。
-- 在同一 MySQL 会话中，先按真实核验结果设置：
-- @ticket_target_database、@ticket_backup_verified、@ticket_clone_rehearsal_verified、
-- @ticket_writes_paused、@ticket_old_instances_stopped、@ticket_new_code_verified。
-- 所有确认标志均须为 1；不得使用 mysql --force 绕过失败门槛。

SELECT DATABASE() AS target_database;
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND INDEX_NAME IN ('uk_source_idem', 'uk_source_credential_idem')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

CREATE TEMPORARY TABLE ticket_idempotency_contract_gate (
    ready TINYINT NOT NULL,
    CONSTRAINT chk_ticket_idempotency_contract_ready CHECK (ready = 1)
);
INSERT INTO ticket_idempotency_contract_gate (ready)
SELECT CASE
           WHEN DATABASE() = @ticket_target_database
               AND @ticket_backup_verified = 1
               AND @ticket_clone_rehearsal_verified = 1
               AND @ticket_writes_paused = 1
               AND @ticket_old_instances_stopped = 1
               AND @ticket_new_code_verified = 1
               AND (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND COLUMN_NAME = 'request_fingerprint') = 'char(64)'
               AND (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND COLUMN_NAME IN ('creator_credential_id', 'idempotency_key')
                      AND COLLATION_NAME = 'utf8mb4_bin') = 2
               AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_idem') = 'source,idempotency_key'
               AND (SELECT MAX(NON_UNIQUE) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_idem') = 0
               AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_credential_idem')
                   = 'source,creator_credential_id,idempotency_key'
               AND (SELECT MAX(NON_UNIQUE) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_credential_idem') = 0
           THEN 1 ELSE 0 END;

ALTER TABLE ticket DROP INDEX uk_source_idem;

INSERT INTO ticket_idempotency_contract_gate (ready)
SELECT CASE
           WHEN (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                   AND INDEX_NAME = 'uk_source_idem') = 0
               AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket'
                      AND INDEX_NAME = 'uk_source_credential_idem')
                   = 'source,creator_credential_id,idempotency_key'
           THEN 1 ELSE 0 END;

DROP TEMPORARY TABLE ticket_idempotency_contract_gate;

SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket'
  AND INDEX_NAME IN ('uk_source_idem', 'uk_source_credential_idem')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
