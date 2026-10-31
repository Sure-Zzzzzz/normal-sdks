-- License 2.0 存量库增量迁移：补齐吊销包、额度操作与持久围栏。
-- 本文件不包含 DROP；执行前停止 License 写入并完成备份。
-- 先执行只读预检，确认目标表确实不存在；存在但结构不完整时停止并人工核对，不能覆盖。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'license_revocation_scope',
      'license_revocation_package',
      'license_revocation_package_part',
      'license_quota_reservation'
  )
ORDER BY table_name;

-- 仅在上面的查询确认对应表不存在，且数据库归属、备份和停写窗口均已确认后，
-- 逐条复制实际缺失的建表语句。已有表不能用 IF NOT EXISTS 掩盖结构漂移。
-- CREATE TABLE license_revocation_scope (
--     scope_id           VARCHAR(128) NOT NULL COMMENT 'issuer/product 吊销导出范围编号',
--     issuer_id          VARCHAR(64)  NOT NULL COMMENT '协议 scope.issuerId',
--     product_id         VARCHAR(64)  NOT NULL COMMENT '协议 scope.productId',
--     next_sequence      BIGINT       NOT NULL COMMENT '已分配的下一个导出序号；失败序号不复用',
--     installed_sequence BIGINT       NOT NULL COMMENT '当前服务端已确认导出的最高序号',
--     trust_version      BIGINT       NOT NULL COMMENT 'REVOCATION 信任档案版本水位',
--     row_version        BIGINT       NOT NULL,
--     created_at         DATETIME(6)  NOT NULL,
--     updated_at         DATETIME(6)  NOT NULL,
--     PRIMARY KEY (scope_id),
--     UNIQUE KEY uk_license_revocation_scope_product (issuer_id, product_id)
-- ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品级吊销导出水位（序号只增不复用）';

-- CREATE TABLE license_revocation_package (
--     package_id          VARCHAR(128) NOT NULL COMMENT '吊销包编号',
--     operation_id        VARCHAR(128) NOT NULL COMMENT '独立产品级操作编号，不复用签发任务',
--     issuer_id           VARCHAR(64)  NOT NULL,
--     product_id          VARCHAR(64)  NOT NULL,
--     sequence            BIGINT       NOT NULL COMMENT '产品 scope 导出序号',
--     snapshot_id         VARCHAR(128) NOT NULL,
--     signing_profile_id  VARCHAR(128) NOT NULL COMMENT 'REVOCATION 用途档案',
--     kid                 VARCHAR(128) NOT NULL,
--     key_version         INT          NOT NULL,
--     state               VARCHAR(16)  NOT NULL COMMENT 'PENDING/SIGNING/READY/FAILED/UNCERTAIN',
--     execution_generation BIGINT      NOT NULL,
--     manifest_jws        LONGTEXT     NULL COMMENT '已签 manifest 原文',
--     part_count          INT          NOT NULL DEFAULT 0,
--     manifest_sha256     CHAR(64)     NULL,
--     reason_category     VARCHAR(64)  NULL,
--     idempotency_digest  VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
--     row_version         BIGINT       NOT NULL,
--     created_at          DATETIME(6)  NOT NULL,
--     updated_at          DATETIME(6)  NOT NULL,
--     PRIMARY KEY (package_id),
--     UNIQUE KEY uk_license_revocation_package_operation (operation_id),
--     UNIQUE KEY uk_license_revocation_package_idempotency (idempotency_digest),
--     UNIQUE KEY uk_license_revocation_package_sequence (issuer_id, product_id, sequence),
--     KEY idx_license_revocation_package_scope (issuer_id, product_id, sequence)
-- ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品级吊销包元数据（独立于 grant 签发任务）';

-- CREATE TABLE license_revocation_package_part (
--     package_id      VARCHAR(128) NOT NULL,
--     part_index      INT          NOT NULL COMMENT '从 0 连续编号',
--     part_jws        LONGTEXT     NOT NULL COMMENT '原始吊销分片 Compact JWS',
--     part_sha256     CHAR(64)     NOT NULL COMMENT '原始 JWS UTF-8 字节 SHA-256',
--     entry_count     INT          NOT NULL,
--     created_at      DATETIME(6)  NOT NULL,
--     PRIMARY KEY (package_id, part_index),
--     KEY idx_license_revocation_package_part_package (package_id)
-- ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '吊销包签名分片（按包与序号唯一）';

-- CREATE TABLE license_quota_reservation (
--     reservation_id        VARCHAR(128) NOT NULL COMMENT '额度操作编号',
--     grant_id              VARCHAR(128) NOT NULL COMMENT '授权编号',
--     metric                VARCHAR(128) NOT NULL COMMENT '指标名',
--     quantity              BIGINT       NOT NULL COMMENT '本次数量',
--     kind                  VARCHAR(16)  NOT NULL COMMENT 'CONCURRENT或CONSUMPTION',
--     action                VARCHAR(128) NOT NULL COMMENT '模型动作',
--     grant_revision        BIGINT       NOT NULL COMMENT '首次落账商业修订',
--     business_operation_id VARCHAR(160) NOT NULL COMMENT '调用方业务操作幂等号',
--     operation_key         VARCHAR(64)  NOT NULL COMMENT '授权指标真实认证主体业务号的长度分帧SHA256',
--     assignment_id         VARCHAR(128) NULL COMMENT '真实固定资格索引',
--     activation_id         VARCHAR(128) NULL COMMENT '真实部署实例索引',
--     state                 VARCHAR(16)  NOT NULL COMMENT 'ACTIVE、RELEASED或EXPIRED',
--     fence                 BIGINT       NOT NULL COMMENT '本次操作围栏',
--     valid_until           BIGINT       NOT NULL COMMENT '本次操作有效截止（epoch秒）',
--     requester_subject     VARCHAR(160) NOT NULL COMMENT '真实认证主体',
--     authentication_source VARCHAR(160) NOT NULL COMMENT '真实认证来源',
--     created_at            DATETIME(6)  NOT NULL,
--     row_version           BIGINT       NOT NULL,
--     PRIMARY KEY (reservation_id),
--     UNIQUE KEY uk_license_quota_reservation_operation
--         (operation_key),
--     KEY idx_license_quota_reservation_grant (grant_id, metric, kind, state, valid_until)
-- ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度预占与累计扣减持久操作台账';

-- 已有余额表先只读检查；下列ALTER模板仅适用于旧余额结构且无固定窗口存量的库。
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'license_quota_balance'
ORDER BY ordinal_position;

-- 确认列缺失、完成备份并停止写入后，按实际缺失列逐条迁移，不能重复执行。
-- ALTER TABLE license_quota_balance MODIFY grant_id VARCHAR(128) NOT NULL;
-- ALTER TABLE license_quota_balance ADD window_until BIGINT NOT NULL DEFAULT 0;
-- ALTER TABLE license_quota_balance ADD window_anchor_at BIGINT NULL;
-- ALTER TABLE license_quota_balance ADD window_period_seconds BIGINT NULL;
-- ALTER TABLE license_quota_balance ADD fence_sequence BIGINT NOT NULL DEFAULT 0;
-- UPDATE license_quota_balance SET fence_sequence = row_version;
-- 固定窗口存量须先逐指标确认首次引入修订的startsAt、periodSeconds及当前窗口消费水位，
-- 再人工回填；不得用当前startsAt、当前时间或清零remaining替代已有商业事实。
-- 已有旧额度操作表缺action、grant_revision、真实认证来源或原对象索引时不能盲补默认值；
-- 应先核真实业务记录并设计受控回填，无法证明归属的记录保持不可消费。

-- 完成后重新回读表、列、索引、字符集和空数据事实，再启动 ddl-auto=validate 的服务。
