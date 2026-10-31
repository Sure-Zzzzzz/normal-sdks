-- 既有 license_* 数据库增量迁移，仅新增 License 自有履约关联台账。
-- 不执行 01_schema.sql 的 DROP/重建段，不改业务订单引用的通用非唯一语义。
-- 存量唯一关联在下一轮同步中事务内补齐；多授权歧义或断裂关联停止并交人工核对。
CREATE TABLE IF NOT EXISTS license_fulfillment_grant (
    fulfillment_id VARCHAR(128) NOT NULL COMMENT '外部履约编号（主键：一条履约只建一个授权）',
    grant_id       VARCHAR(128) NOT NULL COMMENT 'License 授权编号（唯一：一条授权只承接一条履约）',
    created_at     DATETIME(6)  NOT NULL COMMENT '关联建立时间',
    row_version    BIGINT       NOT NULL COMMENT '乐观锁版本',
    PRIMARY KEY (fulfillment_id),
    UNIQUE KEY uk_license_fulfillment_grant (grant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '履约授权关联台账（与草稿同事务提交，跨实例唯一去重）';
