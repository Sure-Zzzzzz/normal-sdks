-- =====================================================================
-- License Server schema 2.0（新领域模型：license_* 表族）
-- 事实源 = starter 实体注解（entity/ 包 23 张表）；本脚本由人工逐列对齐，
-- 变更实体时必须同步本文件（KMS 2.0.2 schema 维护同律）。
-- 与 1.0（smart_license_* 五表+tenant_id）不兼容：新库直建；旧库处置见 docs/02_migrate_from_1_0.md。
-- 字符集 utf8mb4；时间列 DATETIME(6)（与 JPA Instant 微秒精度一致）。
-- =====================================================================

DROP TABLE IF EXISTS license_plan_version;
DROP TABLE IF EXISTS license_model_version;
DROP TABLE IF EXISTS license_revocation_package_part;
DROP TABLE IF EXISTS license_revocation_package;
DROP TABLE IF EXISTS license_revocation_scope;
DROP TABLE IF EXISTS license_revocation;
DROP TABLE IF EXISTS license_activation_quota_allocation;
DROP TABLE IF EXISTS license_activation;
DROP TABLE IF EXISTS license_subject_assignment;
DROP TABLE IF EXISTS license_signing_operation;
DROP TABLE IF EXISTS license_signing_profile;
DROP TABLE IF EXISTS license_fulfillment_grant;
DROP TABLE IF EXISTS license_limiter_policy_binding;
DROP TABLE IF EXISTS license_quota_reservation;
DROP TABLE IF EXISTS license_grant;
DROP TABLE IF EXISTS license_product;
DROP TABLE IF EXISTS license_customer;
DROP TABLE IF EXISTS license_integration_plan;
DROP TABLE IF EXISTS license_role_projection;
DROP TABLE IF EXISTS license_integration_request;
DROP TABLE IF EXISTS license_quota_balance;
DROP TABLE IF EXISTS license_organization_binding;
DROP TABLE IF EXISTS license_issuer;

-- ===== 身份域 =====

CREATE TABLE license_issuer (
    issuer_id    VARCHAR(64)  NOT NULL COMMENT '签发方编号（iss-<uuid>）',
    code         VARCHAR(64)  NOT NULL COMMENT '业务编码（唯一）',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    status       INT          NOT NULL COMMENT '1=生效',
    row_version  BIGINT       NOT NULL COMMENT '乐观锁版本',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (issuer_id),
    UNIQUE KEY uk_license_issuer_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '签发方（产品线与信任域归属主体）';

CREATE TABLE license_customer (
    customer_id  VARCHAR(64)  NOT NULL COMMENT '客户编号（cu-<uuid>）',
    code         VARCHAR(64)  NOT NULL COMMENT '业务编码（唯一）',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    status       INT          NOT NULL COMMENT '1=生效',
    row_version  BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (customer_id),
    UNIQUE KEY uk_license_customer_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户（买方企业；主数据归 CRM，此处仅编号与最小展示资料）';

CREATE TABLE license_product (
    product_id             VARCHAR(64)   NOT NULL COMMENT '产品编号',
    issuer_id              VARCHAR(64)   NOT NULL COMMENT '归属签发方',
    code                   VARCHAR(64)   NOT NULL COMMENT '产品编码',
    display_name           VARCHAR(128)  NOT NULL,
    declared_actions       VARCHAR(8192) NOT NULL COMMENT '声明动作总集（JSON 数组文本；模型动作与 DATA 校验的事实源）',
    supported_capabilities VARCHAR(4096) NOT NULL COMMENT '支持能力（JSON 数组文本）',
    application_bindings   TEXT          NULL COMMENT '门户应用及权限快照绑定项（JSON 数组文本）',
    status                 INT           NOT NULL,
    row_version            BIGINT        NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_license_product_issuer_code (issuer_id, code),
    KEY idx_license_product_issuer (issuer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品（声明动作总集与门户应用绑定主体）';

-- ===== 授权域 =====

CREATE TABLE license_grant (
    grant_id              VARCHAR(128) NOT NULL COMMENT '授权编号（gr-<uuid>）',
    customer_id           VARCHAR(128) NOT NULL,
    product_id            VARCHAR(128) NOT NULL,
    subject_kind          VARCHAR(16)  NOT NULL COMMENT 'DEPLOYMENT/PERSON/SERVICE',
    eligibility_kind      VARCHAR(32)  NOT NULL COMMENT 'ALLOCATED_DEPLOYMENTS/CUSTOMER_MEMBERS/ASSIGNED_SUBJECTS',
    offline_proof_profile VARCHAR(96)  NULL COMMENT '受控离线身份能力版本（可空）',
    mode                  VARCHAR(16)  NOT NULL COMMENT 'OFFLINE/ONLINE/HYBRID',
    state                 VARCHAR(16)  NOT NULL COMMENT 'DRAFT/ENABLED/SUSPENDED/REVOKED',
    grant_revision        BIGINT       NOT NULL COMMENT '商业修订号',
    grant_state_version   BIGINT       NOT NULL COMMENT '管理状态版本',
    business_order_ref    VARCHAR(128) NULL COMMENT '业务订单编号（与 CRM 对账钩子，可空）',
    model_version_id      VARCHAR(128) NULL COMMENT '引用的已发布模型版本（可空：无模型授权）',
    rights_json           TEXT         NULL COMMENT '权益值快照（JSON 对象；键集为模型 rights 声明子集）',
    limiter_policies_json TEXT         NULL COMMENT '客户 OpenAPI 限频授权快照（由 License 投影到 limiter Management）',
    validity_kind         VARCHAR(16)  NULL COMMENT '期限形态：FIXED/PERPETUAL（在线判定与离线签发共用同一事实源）',
    starts_at             BIGINT       NULL COMMENT '期限开始（epoch 秒）',
    service_until         BIGINT       NULL COMMENT '服务截止（FIXED）',
    grace_until           BIGINT       NULL COMMENT '宽限截止（FIXED）',
    row_version           BIGINT       NOT NULL,
    PRIMARY KEY (grant_id),
    KEY idx_license_grant_customer (customer_id),
    KEY idx_license_grant_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户授权（客户×产品×对象×模式；管理状态与商业修订同行）';

CREATE TABLE license_limiter_policy_binding (
    binding_id       VARCHAR(128) NOT NULL COMMENT 'License 本地限频投影绑定编号',
    grant_id         VARCHAR(128) NOT NULL COMMENT '授权编号',
    grant_revision   BIGINT       NOT NULL COMMENT '本次投影使用的商业修订',
    customer_id      VARCHAR(128) NOT NULL COMMENT '客户编号',
    service_code     VARCHAR(128) NOT NULL COMMENT '限频服务编码',
    resource_code    VARCHAR(128) NOT NULL COMMENT '限频资源编码',
    namespace        VARCHAR(128) NULL COMMENT 'Management 返回的服务声明命名空间',
    desired_policy   TEXT         NOT NULL COMMENT '期望窗口快照（JSON）',
    rule_id          VARCHAR(64)  NULL COMMENT 'Management 规则编号',
    rule_row_version BIGINT       NULL COMMENT 'Management 规则 CAS 版本',
    service_revision BIGINT       NULL COMMENT 'Management 服务快照修订',
    state            VARCHAR(24)  NOT NULL COMMENT 'PENDING_SYNC/SYNCED/DISABLE_PENDING/DISABLED/FAILED/UNCERTAIN/SUPERSEDED',
    operation_id     VARCHAR(128) NOT NULL COMMENT 'License 幂等操作编号',
    failure_reason   VARCHAR(128) NULL COMMENT '固定失败分类，不保存上游原文',
    updated_at       DATETIME(6)  NOT NULL,
    row_version      BIGINT       NOT NULL COMMENT 'License 本地乐观锁版本',
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_license_limiter_binding_target
        (grant_id, customer_id, service_code, resource_code),
    KEY idx_license_limiter_binding_grant (grant_id, state),
    KEY idx_license_limiter_binding_reconcile (state, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'License 授权到 limiter Management 的策略投影台账';

CREATE TABLE license_fulfillment_grant (
    fulfillment_id VARCHAR(128) NOT NULL COMMENT '外部履约编号（主键：一条履约只建一个授权）',
    grant_id       VARCHAR(128) NOT NULL COMMENT 'License 授权编号（唯一：一条授权只承接一条履约）',
    created_at     DATETIME(6)  NOT NULL COMMENT '关联建立时间',
    row_version    BIGINT       NOT NULL COMMENT '乐观锁版本',
    PRIMARY KEY (fulfillment_id),
    UNIQUE KEY uk_license_fulfillment_grant (grant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '履约授权关联台账（与草稿同事务提交，跨实例唯一去重）';

-- ===== 签发域 =====

CREATE TABLE license_signing_profile (
    profile_id                VARCHAR(64)    NOT NULL COMMENT '档案编号',
    product_id                VARCHAR(64)    NOT NULL,
    purpose                   VARCHAR(32)    NOT NULL COMMENT 'CREDENTIAL/REVOCATION/STATE_RECOVERY',
    kid                       VARCHAR(128)   NOT NULL COMMENT '协议密钥标识（对外暴露）',
    key_ref                   VARCHAR(256)   NOT NULL COMMENT 'KMS 逻辑钥引用（只存服务端）',
    key_version               INT            NOT NULL COMMENT '精确钥版本',
    registered_public_key_hex VARCHAR(1024)  NOT NULL COMMENT '登记时固化的 SPKI 公钥（hex）',
    status                    VARCHAR(16)    NOT NULL COMMENT 'ACTIVE/RETIRED（轮换=旧档案退役留验）',
    active_marker             INT            GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT '在用兜底唯一列（RETIRED 为 NULL 不占唯一）',
    row_version               BIGINT         NOT NULL,
    created_at                DATETIME(6)    NOT NULL,
    updated_at                DATETIME(6)    NOT NULL,
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_license_signing_profile_active (product_id, purpose, active_marker),
    KEY idx_license_signing_profile_lookup (product_id, purpose, status),
    KEY idx_license_signing_profile_kid (kid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '签名档案（产品×用途唯一；档案不可改绑，轮换走新档案）';

CREATE TABLE license_signing_operation (
    operation_id         VARCHAR(128)  NOT NULL COMMENT '签发任务编号',
    credential_id        VARCHAR(128)  NOT NULL COMMENT '凭据编号（JTI）',
    grant_id             VARCHAR(128)  NOT NULL,
    activation_id        VARCHAR(128)  NULL COMMENT '冻结载荷派生的部署激活编号（人员/服务凭据为空）',
    signing_profile_id   VARCHAR(128)  NOT NULL,
    state                VARCHAR(16)   NOT NULL COMMENT 'PENDING_SIGN/SIGNING/READY/UNCERTAIN/REJECTED/ABANDONED',
    execution_generation BIGINT        NOT NULL COMMENT '执行代数（恢复提升代数使旧工作者无法提交）',
    frozen_header        VARCHAR(8192) NOT NULL COMMENT '冻结的 JWS 头（受理期落库）',
    frozen_payload       LONGTEXT      NOT NULL COMMENT '冻结的 JWS 载荷段（无 padding Base64URL）',
    committed_signature  LONGTEXT      NULL COMMENT '提交的签名段（READY 后非空）',
    idempotency_digest   VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '幂等摘要（同请求唯一重放识别）',
    reason_category      VARCHAR(64)   NULL COMMENT '拒绝/失败原因分类',
    last_kms_call_ref    VARCHAR(64)   NULL COMMENT '最近签名服务调用追踪号（License 侧生成，对账锚）',
    last_kms_call_at     DATETIME(6)   NULL COMMENT '最近签名服务调用时刻（KMS 审计按主体+时间窗对账）',
    row_version          BIGINT        NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_license_signing_operation_credential (credential_id),
    KEY idx_license_signing_operation_grant (grant_id),
    KEY idx_license_signing_operation_activation (activation_id, grant_id, operation_id),
    UNIQUE KEY uk_license_signing_operation_idempotency (idempotency_digest)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '签发任务（CAS 状态机；受理先持久化再事务外签名，UNCERTAIN 不自动重签）';

-- ===== 对象与激活域 =====

CREATE TABLE license_subject_assignment (
    assignment_id         VARCHAR(64)   NOT NULL,
    grant_id              VARCHAR(64)   NOT NULL,
    grant_revision        BIGINT        NOT NULL COMMENT '建行时的授权修订号',
    subject_kind          VARCHAR(16)   NOT NULL COMMENT 'PERSON/SERVICE',
    source_id             VARCHAR(128)  NOT NULL COMMENT '身份来源',
    subject_id            VARCHAR(128)  NOT NULL COMMENT '主体编号',
    assignment_generation BIGINT        NOT NULL COMMENT '分配代数（换人=撤旧建新）',
    state                 VARCHAR(16)   NOT NULL COMMENT 'ACTIVE/SUSPENDED（暂停仍占席）',
    metrics               VARCHAR(2048) NOT NULL COMMENT '实名指标（JSON 数组文本）',
    row_version           BIGINT        NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (assignment_id),
    KEY idx_license_assignment_grant (grant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '固定资格名单（ASSIGNED_SUBJECTS 形态的人员/服务资格）';

CREATE TABLE license_activation (
    activation_id         VARCHAR(64)   NOT NULL,
    grant_id              VARCHAR(64)   NOT NULL,
    deployment_code       VARCHAR(128)  NOT NULL COMMENT '部署编码',
    activation_generation BIGINT        NOT NULL COMMENT '激活代数（换机=新激活记录替代关系）',
    state                 VARCHAR(16)   NOT NULL COMMENT 'ACTIVE/SUSPENDED/REPLACED/REVOKED（替换或吊销由对应操作产生）',
    component_digests     VARCHAR(4096) NOT NULL COMMENT '设备分量摘要（当前NONE必须为空对象）',
    caller_source_id     VARCHAR(128)  NULL COMMENT '管理端登记的获准调用服务来源（存量未绑在线拒绝）',
    caller_subject_id    VARCHAR(128)  NULL COMMENT '管理端登记的获准调用服务编号',
    row_version           BIGINT        NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (activation_id),
    KEY idx_license_activation_grant (grant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署激活（DEPLOYMENT 形态的部署实例与激活代数）';

CREATE TABLE license_activation_quota_allocation (
    allocation_id     VARCHAR(64)  NOT NULL COMMENT '部署额度分配编号',
    grant_id          VARCHAR(128) NOT NULL COMMENT '授权编号',
    activation_id     VARCHAR(64)  NOT NULL COMMENT '部署激活编号',
    grant_revision    BIGINT       NOT NULL COMMENT '分配时固化的商业修订号',
    metric            VARCHAR(128) NOT NULL COMMENT '模型声明的LOCAL额度指标',
    quantity          BIGINT       NOT NULL COMMENT '本激活持有的额度数量',
    retention         VARCHAR(16)  NOT NULL COMMENT 'FIXED/PERPETUAL',
    retain_until      BIGINT       NULL COMMENT '固定保留截止（epoch秒）',
    window_anchor_at  BIGINT       NULL COMMENT '固定窗口授权级锚（TOTAL为空）',
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (allocation_id),
    UNIQUE KEY uk_license_activation_quota_metric (activation_id, metric),
    KEY idx_license_activation_quota_grant (grant_id, metric),
    KEY idx_license_activation_quota_activation (activation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '部署激活的LOCAL额度分配事实';

-- ===== 吊销域 =====

CREATE TABLE license_revocation (
    revocation_id   VARCHAR(64)  NOT NULL,
    target_kind     VARCHAR(32)  NOT NULL COMMENT 'CREDENTIAL/GRANT/ACTIVATION/ASSIGNMENT/SIGNING_KEY',
    target_id       VARCHAR(128) NOT NULL,
    reason_category VARCHAR(64)  NOT NULL COMMENT 'PIRACY/CONTRACT_END/SECURITY/OTHER',
    revoked_at      DATETIME(6)  NOT NULL,
    revoked_by      VARCHAR(128) NOT NULL COMMENT '操作者主体（iam:/aksk: 归一）',
    row_version     BIGINT       NOT NULL,
    PRIMARY KEY (revocation_id),
    UNIQUE KEY uk_license_revocation_target (target_kind, target_id),
    KEY idx_license_revocation_target_id (target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '吊销台账（只增不减；重复吊销同目标幂等 409）';

-- ===== 吊销包导出域 =====

CREATE TABLE license_revocation_scope (
    scope_id          VARCHAR(128) NOT NULL COMMENT 'issuer/product 吊销导出范围编号',
    issuer_id         VARCHAR(64)  NOT NULL COMMENT '协议 scope.issuerId',
    product_id        VARCHAR(64)  NOT NULL COMMENT '协议 scope.productId',
    next_sequence     BIGINT       NOT NULL COMMENT '已分配的下一个导出序号；失败序号不复用',
    installed_sequence BIGINT      NOT NULL COMMENT '当前服务端已确认导出的最高序号',
    trust_version     BIGINT       NOT NULL COMMENT 'REVOCATION 信任档案版本水位',
    row_version       BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (scope_id),
    UNIQUE KEY uk_license_revocation_scope_product (issuer_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品级吊销导出水位（序号只增不复用）';

CREATE TABLE license_revocation_package (
    package_id          VARCHAR(128) NOT NULL COMMENT '吊销包编号',
    operation_id        VARCHAR(128) NOT NULL COMMENT '独立产品级操作编号，不复用签发任务',
    issuer_id           VARCHAR(64)  NOT NULL,
    product_id          VARCHAR(64)  NOT NULL,
    sequence            BIGINT       NOT NULL COMMENT '产品 scope 导出序号',
    snapshot_id         VARCHAR(128) NOT NULL,
    signing_profile_id  VARCHAR(128) NOT NULL COMMENT 'REVOCATION 用途档案',
    kid                 VARCHAR(128) NOT NULL,
    key_version         INT          NOT NULL,
    state               VARCHAR(16)  NOT NULL COMMENT 'PENDING/SIGNING/READY/FAILED/UNCERTAIN',
    execution_generation BIGINT      NOT NULL,
    manifest_jws        LONGTEXT     NULL COMMENT '已签 manifest 原文',
    part_count          INT          NOT NULL DEFAULT 0,
    manifest_sha256     CHAR(64)     NULL,
    reason_category     VARCHAR(64)  NULL,
    idempotency_digest  VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    row_version         BIGINT       NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (package_id),
    UNIQUE KEY uk_license_revocation_package_operation (operation_id),
    UNIQUE KEY uk_license_revocation_package_idempotency (idempotency_digest),
    UNIQUE KEY uk_license_revocation_package_sequence (issuer_id, product_id, sequence),
    KEY idx_license_revocation_package_scope (issuer_id, product_id, sequence)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品级吊销包元数据（独立于 grant 签发任务）';

CREATE TABLE license_revocation_package_part (
    package_id      VARCHAR(128) NOT NULL,
    part_index      INT          NOT NULL COMMENT '从 0 连续编号',
    part_jws        LONGTEXT     NOT NULL COMMENT '原始吊销分片 Compact JWS',
    part_sha256     CHAR(64)     NOT NULL COMMENT '原始 JWS UTF-8 字节 SHA-256',
    entry_count     INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (package_id, part_index),
    KEY idx_license_revocation_package_part_package (package_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '吊销包签名分片（按包与序号唯一）';

-- ===== 模型与方案域 =====

CREATE TABLE license_model_version (
    model_version_id VARCHAR(64) NOT NULL,
    model_id         VARCHAR(64) NOT NULL COMMENT '模型族编号（同族多版本）',
    product_id       VARCHAR(64) NOT NULL,
    version          BIGINT      NOT NULL COMMENT '族内版本号',
    status           VARCHAR(16) NOT NULL COMMENT 'DRAFT/PUBLISHED/RETIRED（发布不可变）',
    snapshot         LONGTEXT   NOT NULL COMMENT '条件模型快照（fields/actions 可选 quota；发布级校验）',
    row_version      BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (model_version_id),
    KEY idx_license_model_product (product_id),
    KEY idx_license_model_family (model_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '条件模型版本（字段声明+动作条件树+可选额度定义）';

CREATE TABLE license_plan_version (
    plan_version_id  VARCHAR(64) NOT NULL,
    plan_id          VARCHAR(64) NOT NULL COMMENT '方案族编号',
    product_id       VARCHAR(64) NOT NULL,
    model_version_id VARCHAR(64) NOT NULL COMMENT '引用的已发布模型版本（精确版本）',
    version          BIGINT      NOT NULL,
    status           VARCHAR(16) NOT NULL COMMENT 'DRAFT/PUBLISHED（发布预填固化）',
    prefill_rights   LONGTEXT   NOT NULL COMMENT '权益预填（键集为模型 rights 声明子集）',
    row_version      BIGINT      NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (plan_version_id),
    KEY idx_license_plan_product (product_id),
    KEY idx_license_plan_family (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '授权方案（引用已发布模型+权益预填，开授权默认权益来源）';

-- ===== 组织绑定域 =====

CREATE TABLE license_organization_binding (
    customer_id             VARCHAR(64)  NOT NULL COMMENT '客户编号（主键=一对一绑定）',
    organization_instance_id VARCHAR(128) NOT NULL COMMENT 'IAM 组织实例',
    root_department_id      BIGINT       NOT NULL COMMENT 'IAM 授权根部门（部门准入子树根）',
    binding_generation      INT          NOT NULL COMMENT '绑定代数（重绑递增，联动清单按代数识别过期）',
    created_at              DATETIME(6)  NOT NULL,
    row_version             BIGINT       NOT NULL,
    PRIMARY KEY (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户组织绑定（IAM 部门准入事实源；绑定存在即启用目录准入）';

-- ===== 额度域 =====

CREATE TABLE license_quota_balance (
    balance_id   VARCHAR(64)  NOT NULL COMMENT '余额行编号',
    grant_id     VARCHAR(128) NOT NULL COMMENT '授权编号',
    metric       VARCHAR(128) NOT NULL COMMENT '指标名',
    quota_limit  BIGINT       NOT NULL COMMENT '限额（扩容按差额追加保留已消费）',
    remaining    BIGINT       NOT NULL COMMENT '剩余',
    window_until BIGINT       NOT NULL DEFAULT 0 COMMENT '固定消费窗口截止；其他指标为0',
    window_anchor_at BIGINT   NULL COMMENT '首次引入指标的商业开始锚；后续修订不可改',
    window_period_seconds BIGINT NULL COMMENT '已引入固定消费指标的周期；不可重置累计水位',
    fence_sequence BIGINT    NOT NULL DEFAULT 0 COMMENT '余额和续期共用的持久围栏序号',
    created_at   DATETIME(6)  NOT NULL,
    row_version  BIGINT       NOT NULL,
    PRIMARY KEY (balance_id),
    UNIQUE KEY uk_license_quota_grant_metric (grant_id, metric)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度余额行（多实例原子预留走悲观行锁围栏）';

CREATE TABLE license_quota_reservation (
    reservation_id        VARCHAR(128) NOT NULL COMMENT '额度操作编号',
    grant_id              VARCHAR(128) NOT NULL COMMENT '授权编号',
    metric                VARCHAR(128) NOT NULL COMMENT '指标名',
    quantity              BIGINT       NOT NULL COMMENT '本次数量',
    kind                  VARCHAR(16)  NOT NULL COMMENT 'CONCURRENT或CONSUMPTION',
    action                VARCHAR(128) NOT NULL COMMENT '模型动作；重放不能换动作',
    grant_revision        BIGINT       NOT NULL COMMENT '本次操作核验的商业修订',
    business_operation_id VARCHAR(160) NOT NULL COMMENT '调用方业务操作幂等号',
    operation_key         VARCHAR(64)  NOT NULL COMMENT '授权指标真实认证主体业务号的长度分帧SHA256',
    assignment_id         VARCHAR(128) NULL COMMENT '真实固定资格索引',
    activation_id         VARCHAR(128) NULL COMMENT '真实部署实例索引',
    state                 VARCHAR(16)  NOT NULL COMMENT 'ACTIVE、RELEASED或EXPIRED',
    fence                 BIGINT       NOT NULL COMMENT '本次操作围栏',
    valid_until           BIGINT       NOT NULL COMMENT '本次操作有效截止（epoch秒）',
    requester_subject     VARCHAR(160) NOT NULL COMMENT '真实认证主体',
    authentication_source VARCHAR(160) NOT NULL COMMENT '真实认证来源',
    created_at            DATETIME(6) NOT NULL,
    row_version           BIGINT       NOT NULL,
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uk_license_quota_reservation_operation
        (operation_key),
    KEY idx_license_quota_reservation_grant (grant_id, metric, kind, state, valid_until)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度预占与累计扣减持久操作台账';

-- ===== 协作联动域 =====

CREATE TABLE license_integration_plan (
    plan_id          VARCHAR(64) NOT NULL COMMENT '联动计划编号',
    command_id       VARCHAR(64) NOT NULL COMMENT '协作命令编号（对外命令视图主键）',
    customer_id      VARCHAR(64) NOT NULL,
    product_id       VARCHAR(64) NOT NULL,
    application_id   VARCHAR(64) NOT NULL COMMENT '目标 IAM 应用编号',
    desired_rules    TEXT        NOT NULL COMMENT '期望角色规则快照（PAGE/API/DATA 批准子集）',
    department_diff  TEXT        NOT NULL COMMENT '部门差异（挂载/卸载列表）',
    projection_snapshot TEXT     NULL COMMENT '环境绑定、专用角色、权限清单与真实目录快照；旧空行不可执行',
    execution_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL或显式AUTO',
    failure_reason VARCHAR(64) NULL COMMENT '固定故障原因，不保存原始上游报文',
    desired_revision BIGINT      NOT NULL COMMENT '期望修订号（同目标单调递增）',
    state            VARCHAR(40) NOT NULL COMMENT '待执行、人工确认、核验、替代、漂移或未知',
    confirmed_revision BIGINT    NULL COMMENT '人工确认回执修订号',
    valid_until      BIGINT      NOT NULL COMMENT '有效截止（epoch 秒）',
    created_at       DATETIME(6) NOT NULL,
    row_version      BIGINT      NOT NULL,
    PRIMARY KEY (plan_id),
    UNIQUE KEY uk_license_integration_command (command_id),
    KEY idx_license_integration_target (customer_id, product_id, application_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'IAM 受委托角色联动计划（MANUAL 清单+命令状态；确认=人员回执非服务端签收）';

CREATE TABLE license_role_projection (
    projection_id VARCHAR(64) NOT NULL COMMENT '稳定外部幂等UUID与本地台账编号',
    external_id VARCHAR(64) NOT NULL COMMENT '当前环境的外部幂等UUID，重确认时重新生成',
    customer_id VARCHAR(64) NOT NULL COMMENT '客户编号',
    product_id VARCHAR(64) NOT NULL COMMENT '产品编号',
    application_id BIGINT NOT NULL COMMENT '批准目标IAM应用编号',
    organization_instance_id VARCHAR(128) NOT NULL COMMENT '管理员确认的环境编号，非IAM目录纪元',
    binding_generation INT NOT NULL COMMENT '客户组织绑定代数',
    root_department_id BIGINT NOT NULL COMMENT '批准客户组织根',
    open_role_id VARCHAR(64) NULL COMMENT '已核验实际受委托角色UUID',
    role_code VARCHAR(128) NULL COMMENT '远端回读的实际角色编码（稳定引用；不证明所有权）',
    desired_revision BIGINT NOT NULL COMMENT '同目标期望修订',
    acknowledged_rules TEXT NULL COMMENT '最后确认实际应用规则，空表示无规则',
    acknowledged_departments TEXT NULL COMMENT '最后确认完整部门挂载JSON数组',
    remote_revision BIGINT NULL COMMENT '最后确认的IAM实际修订围栏',
    state VARCHAR(40) NOT NULL COMMENT '协作安全状态，未知与漂移阻止自动写入',
    updated_at DATETIME(6) NOT NULL COMMENT '最近协调时间',
    row_version BIGINT NOT NULL COMMENT '本地乐观锁修订',
    PRIMARY KEY (projection_id),
    UNIQUE KEY uk_license_projection_external (external_id),
    UNIQUE KEY uk_license_projection_target (customer_id, product_id, application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='License专用角色协调与真实观察台账';

CREATE TABLE license_integration_request (
    request_digest VARCHAR(64) NOT NULL COMMENT '已验证主体、HTTP目标和幂等键的SHA256摘要',
    input_digest VARCHAR(64) NOT NULL COMMENT '规范化请求JSON的SHA256摘要',
    plan_id VARCHAR(64) NOT NULL COMMENT '实际返回的结果计划编号',
    PRIMARY KEY (request_digest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='License角色协作请求幂等台账';
