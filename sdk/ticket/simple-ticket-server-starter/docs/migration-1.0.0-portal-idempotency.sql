-- 已有库增量迁移：Portal 人员创建增加主体隔离的幂等记录。
CREATE TABLE IF NOT EXISTS ticket_portal_idempotency (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_subject_id    VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    ticket_id           BIGINT NOT NULL,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_portal_owner_idem (owner_subject_id, idempotency_key),
    KEY idx_portal_idem_ticket (ticket_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Portal 创建幂等记录';
