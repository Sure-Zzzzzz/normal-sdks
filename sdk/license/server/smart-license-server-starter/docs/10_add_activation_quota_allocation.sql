-- License 2.0 存量库增量迁移：补齐部署激活的 LOCAL 额度分配事实。
-- 执行前停止 License 写入并完成备份；已有激活必须由业务方按授权事实补录，不能用权益限额批量猜造。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'license_activation_quota_allocation';

-- 仅在上面的查询确认表不存在后执行下列建表语句；已有表不得覆盖。
-- CREATE TABLE license_activation_quota_allocation (
--     allocation_id     VARCHAR(64)  NOT NULL,
--     grant_id          VARCHAR(128) NOT NULL,
--     activation_id     VARCHAR(64)  NOT NULL,
--     grant_revision    BIGINT       NOT NULL,
--     metric            VARCHAR(128) NOT NULL,
--     quantity          BIGINT       NOT NULL,
--     retention         VARCHAR(16)  NOT NULL,
--     retain_until      BIGINT       NULL,
--     window_anchor_at  BIGINT       NULL,
--     created_at        DATETIME(6)  NOT NULL,
--     PRIMARY KEY (allocation_id),
--     UNIQUE KEY uk_license_activation_quota_metric (activation_id, metric),
--     KEY idx_license_activation_quota_grant (grant_id, metric),
--     KEY idx_license_activation_quota_activation (activation_id)
-- ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署激活的LOCAL额度分配事实';

-- 存量激活若引用含 LOCAL 指标的模型，必须按授权修订、部署分配和窗口锚逐行补录后才允许离线签发。
