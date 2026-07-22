-- smart_redis_limiter 策略持久化 Schema
--
-- 表关系：smart_redis_limiter_policy （策略主表）
--              ↓ 一对多，policy_id 外键，CASCADE DELETE
--         smart_redis_limiter_policy_limit（窗口配置表）
--
--         smart_redis_limiter_policy_revision（服务级版本表，独立）
--
-- 设计要点：
--   - service_code / resource_code / subject 均使用 utf8mb4_bin（区分大小写，保证三元组精确匹配）
--   - row_version 乐观锁：所有写操作须通过 CAS 校验，防止并发修改静默覆盖
--   - revision 表由管理模块在每次策略变更后递增，limiter 引擎轮询该表实现增量感知
--   - window_seconds 由服务层在写入前计算（limit_window × limit_unit），
--     用于同策略内多窗口唯一约束，不冗余存储任何业务逻辑

CREATE TABLE IF NOT EXISTS `smart_redis_limiter_policy` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `service_code`  VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                                 COMMENT '服务编码：限流策略的业务归属，区分大小写',
    `resource_code` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                                 COMMENT '资源编码：同一服务内的细粒度保护对象（接口名 / 方法名等），区分大小写',
    `subject`       VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                                 COMMENT '限流主体：被限流的具体对象标识（用户ID / IP / 固定值 * 等），区分大小写',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1
                                 COMMENT '启停状态：0-停用（不纳入快照下发），1-启用',
    `row_version`   BIGINT       NOT NULL DEFAULT 0
                                 COMMENT '行级乐观锁版本号：每次写操作须 CAS 校验，防止并发覆盖',
    `created_at`    DATETIME(3)  NOT NULL COMMENT '创建时间，精确到毫秒（UTC）',
    `updated_at`    DATETIME(3)  NOT NULL COMMENT '最后修改时间，精确到毫秒（UTC）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_limiter_policy_identity` (`service_code`, `resource_code`, `subject`)
        COMMENT '三元组唯一：同一 service_code + resource_code + subject 只允许一条策略',
    KEY `idx_smart_limiter_policy_snapshot` (`service_code`, `enabled`, `resource_code`, `subject`)
        COMMENT '快照查询索引：按 service_code + enabled 过滤后按 resource_code/subject 有序返回'
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='限流精确策略表：三元组（service_code, resource_code, subject）唯一标识一条策略';

CREATE TABLE IF NOT EXISTS `smart_redis_limiter_policy_limit` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `policy_id`      BIGINT      NOT NULL
                                 COMMENT '所属策略主键，外键关联 smart_redis_limiter_policy，策略删除时级联删除',
    `sort_order`     INT         NOT NULL
                                 COMMENT '策略内 limit 展示排列序号，同策略内唯一，决定多窗口的显示顺序',
    `limit_count`    BIGINT      NOT NULL
                                 COMMENT '滑动窗口内允许通过的最大请求数（阈值）',
    `limit_window`   BIGINT      NOT NULL
                                 COMMENT '时间窗口时长数值，与 limit_unit 配合表示完整窗口（如 5 MINUTE）',
    `limit_unit`     VARCHAR(32) NOT NULL
                                 COMMENT '时间单位枚举编码（SECOND / MINUTE / HOUR / DAY），须与引擎枚举一致',
    `window_seconds` BIGINT      NOT NULL
                                 COMMENT 'limit_window × limit_unit 换算后的标准化秒数，服务层写入前计算；同策略内唯一，防止重复窗口',
    `created_at`     DATETIME(3) NOT NULL COMMENT '创建时间，精确到毫秒（UTC）',
    `updated_at`     DATETIME(3) NOT NULL COMMENT '最后修改时间，精确到毫秒（UTC）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_limiter_policy_limit_order` (`policy_id`, `sort_order`)
        COMMENT '策略内 sort_order 唯一约束',
    UNIQUE KEY `uk_smart_limiter_policy_limit_window` (`policy_id`, `window_seconds`)
        COMMENT '策略内 window_seconds 唯一约束：同一策略不允许两个等效时间窗口',
    KEY `idx_smart_limiter_policy_limit_policy` (`policy_id`)
        COMMENT '按策略查询 limit 列表的覆盖索引',
    CONSTRAINT `fk_smart_limiter_policy_limit_policy`
        FOREIGN KEY (`policy_id`) REFERENCES `smart_redis_limiter_policy` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='限流策略窗口配置表：一条策略对应一到多个滑动窗口，引擎取所有窗口中最严格的约束执行';

CREATE TABLE IF NOT EXISTS `smart_redis_limiter_policy_revision` (
    `service_code`  VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                                 COMMENT '服务编码，主键，与策略表保持一致',
    `revision`      BIGINT       NOT NULL
                                 COMMENT '服务级策略单调版本号，每次策略变更后由管理模块递增；limiter 引擎对比本地版本发现变更后拉取最新快照',
    `published_at`  DATETIME(3)  NOT NULL
                                 COMMENT '当前版本最后一次策略变更时间，精确到毫秒（UTC）',
    PRIMARY KEY (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='限流服务级策略版本表：每个服务一行，revision 单调递增，limiter 引擎通过轮询该表实现增量感知';
