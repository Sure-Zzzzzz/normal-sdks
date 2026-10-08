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
--   - revision 表由管理模块在每次策略变更后递增，运行端通过 HTTP 快照感知版本，不直接访问本表
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

-- ---------------------------------------------------------------------------
-- v2 类型化策略（TYPED_V2）：七字段身份 + 多维计数维度
--
-- 表关系：smart_redis_limiter_typed_rule （类型化规则主表）
--              ↓ 一对多，rule_id 外键，CASCADE DELETE
--         smart_redis_limiter_typed_rule_limit（窗口配置表）
--
-- 服务级版本复用 smart_redis_limiter_policy_revision（v1/v2 同服务编码共用一行，
-- 服务只能属于一种协议模式，由宿主目录声明，不在本表存储）。
--
-- 设计要点：
--   - 身份七字段（service_code/resource_code/dimension/selector/namespace/custom_type/object_id）
--     均为 NOT NULL DEFAULT ''：MySQL 唯一索引对 NULL 不判重，统一以空串表示"该字段不适用"
--   - dimension / selector / limit_unit 存枚举编码（与 smart-redis-limiter-core 2.2.0 枚举一致），
--     服务层校验组合约束（RESOURCE 仅 DEFAULT、DEFAULT 对象为空、CUSTOM 必带 custom_type 等）
--   - object_id 最多 256 码点：utf8mb4 下 VARCHAR(256) 即 1024 字节，与七字段唯一键 3072 字节上限相容，保留原值不 trim 不归一化
--   - 与 v1 表完全隔离：LEGACY_V1 服务只读写 policy 表，TYPED_V2 服务只读写本表

CREATE TABLE IF NOT EXISTS `smart_redis_limiter_typed_rule` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `service_code`  VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                                 COMMENT '服务编码：TYPED_V2 服务的业务归属，区分大小写',
    `resource_code` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                                 COMMENT '资源编码：同一服务内的保护对象，区分大小写',
    `dimension`     VARCHAR(32)  NOT NULL
                                 COMMENT '计数维度枚举编码（RESOURCE/IP/USER/SERVICE/CREDENTIAL/CUSTOMER/CUSTOM），与 core 枚举一致',
    `selector`      VARCHAR(16)  NOT NULL
                                 COMMENT '规则选择器枚举编码（DEFAULT/EXACT）：DEFAULT=各对象默认额度，EXACT=精确对象覆盖',
    `namespace`     VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT ''
                                 COMMENT '计数命名空间：宿主声明的稳定空间编码，DEFAULT ''（空串=宿主固定空间）',
    `custom_type`   VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT ''
                                 COMMENT '自定义业务类型：仅 CUSTOM 维度填写，其他维度空串',
    `object_id`     VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT ''
                                 COMMENT '精确对象标识：仅 EXACT 填写（IP 地址/主体稳定 ID/客户 ID/自定义对象），DEFAULT 空串；保留原值不 trim 不改大小写',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1
                                 COMMENT '启停状态：0-停用（不纳入快照下发），1-启用',
    `row_version`   BIGINT       NOT NULL DEFAULT 0
                                 COMMENT '行级乐观锁版本号：每次写操作须 CAS 校验，防止并发覆盖',
    `created_at`    DATETIME(3)  NOT NULL COMMENT '创建时间，精确到毫秒（UTC）',
    `updated_at`    DATETIME(3)  NOT NULL COMMENT '最后修改时间，精确到毫秒（UTC）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_limiter_typed_rule_identity`
        (`service_code`, `resource_code`, `dimension`, `selector`, `namespace`, `custom_type`, `object_id`)
        COMMENT '七字段身份唯一：同一服务+资源+维度+选择器+空间+自定义类型+对象只允许一条规则',
    KEY `idx_smart_limiter_typed_rule_snapshot` (`service_code`, `enabled`, `dimension`, `resource_code`)
        COMMENT '快照查询索引：按 service_code + enabled 过滤后按维度/资源有序返回'
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='v2 类型化限流规则表：七字段身份唯一，一条规则携带一至十六个窗口';

CREATE TABLE IF NOT EXISTS `smart_redis_limiter_typed_rule_limit` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_id`        BIGINT      NOT NULL
                                 COMMENT '所属规则主键，外键关联 smart_redis_limiter_typed_rule，规则删除时级联删除',
    `sort_order`     INT         NOT NULL
                                 COMMENT '规则内 limit 展示排列序号，同规则内唯一，决定多窗口的显示顺序',
    `limit_count`    BIGINT      NOT NULL
                                 COMMENT '滑动窗口内允许通过的最大请求数（阈值）',
    `limit_window`   BIGINT      NOT NULL
                                 COMMENT '时间窗口时长数值，与 limit_unit 配合表示完整窗口',
    `limit_unit`     VARCHAR(32) NOT NULL
                                 COMMENT '时间单位枚举编码（SECONDS/MINUTES/HOURS/DAYS），与 core 的 SmartRedisLimiterTimeUnit 一致',
    `window_seconds` BIGINT      NOT NULL
                                 COMMENT 'limit_window × limit_unit 换算后的标准化秒数，服务层写入前计算；同规则内唯一，防止重复窗口',
    `created_at`     DATETIME(3) NOT NULL COMMENT '创建时间，精确到毫秒（UTC）',
    `updated_at`     DATETIME(3) NOT NULL COMMENT '最后修改时间，精确到毫秒（UTC）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_smart_limiter_typed_rule_limit_order` (`rule_id`, `sort_order`)
        COMMENT '规则内 sort_order 唯一约束',
    UNIQUE KEY `uk_smart_limiter_typed_rule_limit_window` (`rule_id`, `window_seconds`)
        COMMENT '规则内 window_seconds 唯一约束：同一规则不允许两个等效时间窗口',
    KEY `idx_smart_limiter_typed_rule_limit_rule` (`rule_id`)
        COMMENT '按规则查询 limit 列表的覆盖索引',
    CONSTRAINT `fk_smart_limiter_typed_rule_limit_rule`
        FOREIGN KEY (`rule_id`) REFERENCES `smart_redis_limiter_typed_rule` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT='v2 类型化规则窗口配置表：一条规则对应一至十六个滑动窗口，单位规范化后不得重复';
