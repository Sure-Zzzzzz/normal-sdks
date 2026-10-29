-- =====================================================
-- Simple CRM Server 1.1.0 数据库初始化脚本
-- 依赖：Spring Boot 2.7.9、MySQL 5.7+ / MySQL 8.0+、InnoDB、utf8mb4
-- 身份协作：resource-server + 双 Provider；不在 CRM 表中保存 tenant_id
-- 说明：仅用于全新初始化，会删除同名既有表。
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 执行前请先创建并选择目标 CRM 数据库。

-- =====================================================
-- 1. 客户、联系人与可销售目录表
-- =====================================================

DROP TABLE IF EXISTS crm_customer;
CREATE TABLE crm_customer (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    customer_id           VARCHAR(64)  NOT NULL COMMENT '客户唯一标识',
    display_name          VARCHAR(256) NOT NULL COMMENT '客户展示名称',
    state                 VARCHAR(16)  NOT NULL COMMENT '状态：active/inactive/merged',
    owner                 VARCHAR(128) NOT NULL COMMENT '归属主体（iam:/aksk: 前缀）',
    merged_into_customer_id VARCHAR(64) DEFAULT NULL COMMENT '并入目标客户标识（仅 merged 态非空）',
    aggregate_version     BIGINT       NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at            TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at            TIMESTAMP(3) NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_id (customer_id),
    KEY idx_customer_owner (owner),
    KEY idx_customer_state (state, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 客户档案';

DROP TABLE IF EXISTS crm_contact;
CREATE TABLE crm_contact (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    contact_id   VARCHAR(64)  NOT NULL COMMENT '联系人唯一标识',
    customer_id  VARCHAR(64)  NOT NULL COMMENT '归属客户唯一标识（合并时同事务改挂）',
    display_name VARCHAR(256) NOT NULL COMMENT '联系人展示名称',
    title        VARCHAR(128) DEFAULT NULL COMMENT '职务（可空）',
    state        VARCHAR(16)  NOT NULL COMMENT '状态：active/inactive',
    aggregate_version BIGINT  NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at   TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at   TIMESTAMP(3) NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contact_id (contact_id),
    KEY idx_contact_customer (customer_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 客户联系人';

DROP TABLE IF EXISTS crm_opportunity;
CREATE TABLE crm_opportunity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    opportunity_id VARCHAR(64) NOT NULL COMMENT '商机唯一标识',
    customer_id VARCHAR(64) NOT NULL COMMENT '客户唯一标识',
    owner VARCHAR(128) NOT NULL COMMENT '归属主体（iam:/aksk: 前缀）',
    title VARCHAR(256) NOT NULL COMMENT '商机标题',
    primary_offering_id VARCHAR(64) DEFAULT NULL COMMENT '主推目录项标识（可空）',
    contact_id VARCHAR(64) DEFAULT NULL COMMENT '主要联系人标识（可空）',
    expected_close_date DATE DEFAULT NULL COMMENT '预计成交日期（可空）',
    estimated_amount DECIMAL(20,6) DEFAULT NULL COMMENT '预计金额（可空）',
    currency VARCHAR(8) DEFAULT NULL COMMENT '预计金额币种（金额存在时必填）',
    stage VARCHAR(16) NOT NULL COMMENT '阶段：new/qualified/proposal/negotiation/won/lost',
    lost_reason VARCHAR(512) DEFAULT NULL COMMENT '输单原因（lost 时必填）',
    aggregate_version BIGINT NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at TIMESTAMP(3) NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id), UNIQUE KEY uk_opportunity_id (opportunity_id),
    KEY idx_opportunity_owner (owner, id), KEY idx_opportunity_customer (customer_id, id),
    KEY idx_opportunity_stage (stage, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 销售商机';

DROP TABLE IF EXISTS crm_offering;
CREATE TABLE crm_offering (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    offering_id               VARCHAR(64)  NOT NULL COMMENT '商品或服务唯一标识',
    offering_reference        VARCHAR(128) NOT NULL COMMENT '稳定业务引用',
    display_name              VARCHAR(256) NOT NULL COMMENT '展示名称',
    state                     VARCHAR(16)  NOT NULL COMMENT '状态：draft/published/disabled（下架可重新上架）',
    capability_type           VARCHAR(64)  NOT NULL COMMENT '商业能力类型稳定 code',
    required_consumer_capability VARCHAR(128) NOT NULL COMMENT '所需消费者能力',
    owner                     VARCHAR(128) NOT NULL COMMENT '归属主体（iam:/aksk: 前缀）',
    aggregate_version         BIGINT       NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at                TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at                TIMESTAMP(3) NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_offering_id (offering_id),
    KEY idx_offering_state (state, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 可销售目录（三态）';

DROP TABLE IF EXISTS crm_quotation;
CREATE TABLE crm_quotation (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    quotation_id              VARCHAR(64)  NOT NULL COMMENT '报价唯一标识',
    customer_id               VARCHAR(64)  NOT NULL COMMENT '客户唯一标识（合并时同事务改挂）',
    opportunity_id            VARCHAR(64)  DEFAULT NULL COMMENT '关联商机标识（可空）',
    owner                     VARCHAR(128) NOT NULL COMMENT '归属主体（iam:/aksk: 前缀；转移时改指）',
    aggregate_version         BIGINT       NOT NULL COMMENT '聚合版本（乐观锁）',
    current_version           INT          NOT NULL COMMENT '当前版本号（编辑草稿不变，重签递增）',
    current_confirmable_version INT         DEFAULT NULL COMMENT '当前可确认版本号（非空=已签发待确认）',
    confirmed_order_id        VARCHAR(64)  DEFAULT NULL COMMENT '确认生成订单标识（终态锚点）',
    supersedes_quotation_id   VARCHAR(64)  DEFAULT NULL COMMENT '重议来源报价标识（本报价确认时其自动 SUPERSEDED）',
    created_at                TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at                TIMESTAMP(3) NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quotation_id (quotation_id),
    KEY idx_quotation_owner (owner),
    KEY idx_quotation_customer (customer_id),
    KEY idx_quotation_opportunity (opportunity_id),
    KEY idx_quotation_supersedes (supersedes_quotation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 报价聚合';

-- =====================================================
-- 2. 报价、订单与履约事实表
-- =====================================================

DROP TABLE IF EXISTS crm_quotation_version;
CREATE TABLE crm_quotation_version (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    quotation_id      VARCHAR(64)   NOT NULL COMMENT '报价唯一标识',
    version           INT           NOT NULL COMMENT '报价版本号',
    state             VARCHAR(16)   NOT NULL COMMENT '状态：draft/issued/confirmed/withdrawn/superseded（expired 为惰性派生态不落库）',
    settlement_currency VARCHAR(8)  NOT NULL COMMENT '结算币种（ISO 4217）',
    valid_until       TIMESTAMP(3)  NOT NULL COMMENT '有效截止时间（UTC；惰性过期判定依据）',
    total_amount      DECIMAL(20, 6) NOT NULL COMMENT '总价（冻结）',
    issued_at         TIMESTAMP(3)  DEFAULT NULL COMMENT '签发时间（终态共用槽位之一起点）',
    issued_by         VARCHAR(128)  DEFAULT NULL COMMENT '签发人 owner（iam:/aksk: 前缀）',
    terminal_at       TIMESTAMP(3)  DEFAULT NULL COMMENT '终态时间（确认/撤回/取代共用槽位）',
    terminal_by       VARCHAR(128)  DEFAULT NULL COMMENT '终态操作人 owner（确认/撤回/取代共用槽位；取代态可空）',
    aggregate_version BIGINT        NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at        TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at        TIMESTAMP(3)  NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quotation_version (quotation_id, version),
    KEY idx_qv_state (state, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 报价版本（行项冻结见 crm_quotation_line）';

DROP TABLE IF EXISTS crm_quotation_line;
CREATE TABLE crm_quotation_line (
    id                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    quotation_line_id       VARCHAR(64)   NOT NULL COMMENT '报价行唯一标识',
    quotation_id            VARCHAR(64)   NOT NULL COMMENT '报价唯一标识',
    version                 INT           NOT NULL COMMENT '报价版本号',
    offering_id             VARCHAR(64)   NOT NULL COMMENT '商品或服务唯一标识（快照锚点）',
    offering_reference      VARCHAR(128)  NOT NULL COMMENT '商品业务引用快照',
    quantity                DECIMAL(20, 6) NOT NULL COMMENT '数量（冻结）',
    unit                    VARCHAR(32)   NOT NULL COMMENT '单位（冻结）',
    unit_price              DECIMAL(20, 6) NOT NULL COMMENT '单价金额（冻结）',
    currency                VARCHAR(8)    NOT NULL COMMENT '币种（ISO 4217）',
    line_total              DECIMAL(20, 6) NOT NULL COMMENT '行总价（冻结）',
    commercial_terms_json   MEDIUMTEXT    NOT NULL COMMENT '商业条款快照 JSON',
    obligation_template_json MEDIUMTEXT   NOT NULL COMMENT '履约义务模板快照 JSON',
    created_at              TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quotation_line_id (quotation_line_id),
    KEY idx_ql_version (quotation_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 报价行快照';

DROP TABLE IF EXISTS crm_order;
CREATE TABLE crm_order (
    id                         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id                   VARCHAR(64)   NOT NULL COMMENT '订单唯一标识（仅由报价确认生成）',
    source_quotation_id        VARCHAR(64)   NOT NULL COMMENT '来源报价唯一标识（合并时同事务改挂）',
    source_quotation_version   INT           NOT NULL COMMENT '来源报价版本',
    customer_id                VARCHAR(64)   NOT NULL COMMENT '客户唯一标识',
    settlement_currency        VARCHAR(8)    NOT NULL COMMENT '结算币种（ISO 4217）',
    total_amount               DECIMAL(20, 6) NOT NULL COMMENT '总价（冻结）',
    owner                      VARCHAR(128)  NOT NULL COMMENT '归属主体（继承报价 owner）',
    display_state              VARCHAR(32)   NOT NULL COMMENT '展示状态稳定 code',
    aggregate_version          BIGINT        NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at                 TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at                 TIMESTAMP(3)  NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    UNIQUE KEY uk_order_source (source_quotation_id, source_quotation_version),
    KEY idx_order_owner (owner),
    KEY idx_order_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 订单';

DROP TABLE IF EXISTS crm_order_line;
CREATE TABLE crm_order_line (
    id                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_line_id            VARCHAR(64)   NOT NULL COMMENT '订单行唯一标识',
    order_id                 VARCHAR(64)   NOT NULL COMMENT '订单唯一标识',
    source_quotation_line_id VARCHAR(64)   NOT NULL COMMENT '来源报价行唯一标识',
    offering_reference       VARCHAR(128)  NOT NULL COMMENT '商品业务引用快照',
    quantity                 DECIMAL(20, 6) NOT NULL COMMENT '数量（冻结）',
    unit                     VARCHAR(32)   NOT NULL COMMENT '单位（冻结）',
    unit_price               DECIMAL(20, 6) NOT NULL COMMENT '单价金额（冻结）',
    currency                 VARCHAR(8)    NOT NULL COMMENT '币种（ISO 4217）',
    line_total               DECIMAL(20, 6) NOT NULL COMMENT '行总价（冻结）',
    commercial_terms_json    MEDIUMTEXT    NOT NULL COMMENT '商业条款快照 JSON',
    obligation_template_json MEDIUMTEXT    NOT NULL COMMENT '履约义务模板快照 JSON',
    created_at               TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_line_id (order_line_id),
    KEY idx_ol_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 订单行';

DROP TABLE IF EXISTS crm_fulfillment_item;
CREATE TABLE crm_fulfillment_item (
    id                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    fulfillment_id           VARCHAR(64)   NOT NULL COMMENT '履约项唯一标识',
    order_id                 VARCHAR(64)   NOT NULL COMMENT '订单唯一标识',
    order_line_id            VARCHAR(64)   NOT NULL COMMENT '订单行唯一标识',
    owner                    VARCHAR(128)  NOT NULL COMMENT '归属主体（继承报价 owner，人员查询维度）',
    assigned_consumer_id     VARCHAR(128)  NOT NULL COMMENT '冻结的消费者标识（须绑定其 AKP clientId；机器回写授权维度）',
    consumer_protocol_version INT          NOT NULL COMMENT '消费者协议版本（冻结）',
    state                    VARCHAR(24)   NOT NULL COMMENT '状态：pending_dispatch/in_progress/completed/failed',
    report_sequence          INT           NOT NULL DEFAULT 0 COMMENT '已回写序号水位（只增不减，重置保留）',
    obligation_template_json MEDIUMTEXT    NOT NULL COMMENT '履约义务模板快照 JSON',
    result_data              MEDIUMTEXT    DEFAULT NULL COMMENT '最近回写结果数据（重置清空）',
    result_note              VARCHAR(512)  DEFAULT NULL COMMENT '最近回写备注（可空）',
    reported_by              VARCHAR(128)  DEFAULT NULL COMMENT '最近回写人 owner（iam:/aksk: 前缀）',
    reported_at              TIMESTAMP(3)  DEFAULT NULL COMMENT '最近回写时间（UTC）',
    aggregate_version        BIGINT        NOT NULL COMMENT '聚合版本（乐观锁；状态条件写实现原子认领）',
    created_at               TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at               TIMESTAMP(3)  NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fulfillment_id (fulfillment_id),
    KEY idx_fi_consumer_state (assigned_consumer_id, state, id),
    KEY idx_fi_owner (owner),
    KEY idx_fi_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 履约项（消费者拉取认领回写）';

DROP TABLE IF EXISTS crm_fulfillment_consumer;
CREATE TABLE crm_fulfillment_consumer (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    consumer_id       VARCHAR(128) NOT NULL COMMENT '消费者标识（必须绑定其 AKP clientId，登记时核验一致）',
    capability        VARCHAR(128) NOT NULL COMMENT '满足的消费者能力',
    protocol_version  INT          NOT NULL COMMENT '协议版本',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用（停用不参与新履约选择，历史不受影响）',
    aggregate_version BIGINT       NOT NULL COMMENT '聚合版本（乐观锁）',
    created_at        TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at        TIMESTAMP(3) NOT NULL COMMENT '变更时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumer_capability (consumer_id, capability),
    KEY idx_consumer_enabled (enabled, capability)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 履约消费者注册表（选择与回写授权依据；无端点无凭据）';

-- =====================================================
-- 3. 幂等与业务审计表
-- =====================================================

DROP TABLE IF EXISTS crm_idempotency;
CREATE TABLE crm_idempotency (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope_key           VARCHAR(512) NOT NULL COMMENT '稳定范围键（owner|command|resourceType[|targetId]|idempotencyKey）',
    request_digest      CHAR(64)     NOT NULL COMMENT '规范化请求摘要 SHA-256',
    first_resource_id   VARCHAR(64)  DEFAULT NULL COMMENT '创建命令首成功分配的顶级资源 ID（可空）',
    first_result_json   MEDIUMTEXT   DEFAULT NULL COMMENT '已知目标命令首成功声明结果 JSON（可空）',
    created_at          TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_scope (scope_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 命令幂等记录（终身保留，重放依据）';

DROP TABLE IF EXISTS crm_audit;
CREATE TABLE crm_audit (
    id                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    owner                    VARCHAR(128) NOT NULL COMMENT '操作归属主体（iam:/aksk: 前缀）',
    action                   VARCHAR(48)  NOT NULL COMMENT 'CRM 操作稳定 code',
    target_resource_type     VARCHAR(32)  NOT NULL COMMENT '被操作资源类型稳定 code',
    target_resource_id       VARCHAR(64)  NOT NULL COMMENT '被操作资源唯一标识',
    target_aggregate_version BIGINT       NOT NULL COMMENT '被操作聚合版本',
    correlation_id           VARCHAR(128) NOT NULL COMMENT '命令关联标识',
    result_code              VARCHAR(64)  DEFAULT NULL COMMENT '命令执行结果错误码（NULL=成功）',
    occurred_at              TIMESTAMP(3) NOT NULL COMMENT '审计事实发生时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_audit_target (target_resource_type, target_resource_id, id),
    KEY idx_audit_owner (owner, id),
    KEY idx_audit_time (occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 业务事实审计（写命令事实，永久保留）';

SET FOREIGN_KEY_CHECKS = 1;
SELECT 'Simple CRM Server 1.1.0 schema initialization completed!' AS status;


