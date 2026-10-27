-- 工单平台 1.0.0 DDL（MySQL 8，simple_ticket_test 库；家族交付形态=starter docs/schema.sql）
-- 时间列 TIMESTAMP 存 UTC（领域 Instant 直转）；展示墙钟 Asia/Shanghai 在应用层换算。

DROP TABLE IF EXISTS ticket_evaluation;
DROP TABLE IF EXISTS ticket_attachment;
DROP TABLE IF EXISTS ticket_comment;
DROP TABLE IF EXISTS ticket_transition;
DROP TABLE IF EXISTS ticket;
DROP TABLE IF EXISTS ticket_group;
DROP TABLE IF EXISTS ticket_type;
DROP TABLE IF EXISTS ticket_no_sequence;
DROP TABLE IF EXISTS ticket_portal_idempotency;

CREATE TABLE IF NOT EXISTS ticket_type (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                  VARCHAR(64)  NOT NULL COMMENT '类型编码',
    name                  VARCHAR(120) NOT NULL COMMENT '类型名称',
    form_template         TEXT         NULL COMMENT '表单模板 JSON',
    default_group         VARCHAR(64)  NOT NULL COMMENT '默认处理组编码',
    sla_response_minutes  BIGINT       NOT NULL COMMENT '响应时限分钟',
    sla_resolution_minutes BIGINT      NOT NULL COMMENT '办结时限分钟',
    enabled               TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用',
    created_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_type_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单类型';

CREATE TABLE IF NOT EXISTS ticket_group (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    code               VARCHAR(64)  NOT NULL COMMENT '组编码',
    name               VARCHAR(120) NOT NULL COMMENT '组名称',
    member_subject_ids TEXT         NULL COMMENT '组员标识 JSON 数组',
    leader_subject_id  VARCHAR(128) NULL COMMENT '组长标识',
    enabled            TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用（停用不接新单路由，存量不碍）',
    created_at         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_group_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '处理组';

CREATE TABLE IF NOT EXISTS ticket (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_no             VARCHAR(20)  NOT NULL COMMENT '单号 TK+yyMMdd+6位日序列',
    type_code             VARCHAR(64)  NOT NULL COMMENT '类型编码',
    subject               VARCHAR(120) NOT NULL COMMENT '标题',
    description           TEXT         NULL COMMENT '描述 Markdown',
    form_answers          TEXT         NULL COMMENT '模板答案 JSON 快照',
    priority              VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH/URGENT',
    status                VARCHAR(20)  NOT NULL COMMENT 'SUBMITTED/PROCESSING/PENDING_INFO/RESOLVED/CLOSED/WITHDRAWN',
    owner_subject_id      VARCHAR(128) NOT NULL COMMENT '提单人标识（DATA owner 维度）',
    owner_name            VARCHAR(120) NULL COMMENT '提单人姓名快照',
    assignee_group        VARCHAR(64)  NULL COMMENT '当前处理组编码',
    assignee_subject_id   VARCHAR(128) NULL COMMENT '当前处理人标识',
    assignee_name         VARCHAR(120) NULL COMMENT '处理人姓名快照',
    response_deadline     TIMESTAMP(3) NOT NULL COMMENT '响应时限（提交固化）',
    resolution_deadline   TIMESTAMP(3) NOT NULL COMMENT '办结时限基线（提交固化；到期判定加暂停累计）',
    confirm_deadline      TIMESTAMP(3) NULL COMMENT '确认期限（RESOLVED 生成，REJECT 清除）',
    pending_accumulated_ms BIGINT      NOT NULL DEFAULT 0 COMMENT '暂停累计毫秒',
    pause_started_at      TIMESTAMP(3) NULL COMMENT '本次暂停起点（NULL=未暂停）',
    overdue_response      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '响应超时标记（扫描原子置位）',
    overdue_resolution    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '办结超时标记（扫描原子置位）',
    source                VARCHAR(10)  NOT NULL DEFAULT 'PORTAL' COMMENT 'PORTAL/OPENAPI',
    creator_credential_id VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '创建凭据标识（openapi 审计定位）',
    idempotency_key       VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '幂等键（openapi 提交）',
    request_fingerprint   CHAR(64)     NULL COMMENT '规范化机器开单请求 SHA-256 摘要',
    related_ticket_no     VARCHAR(20)  NULL COMMENT '关联原单号（重提场景）',
    last_activity_at      TIMESTAMP(3) NOT NULL COMMENT '最近活跃（我的工单排序锚）',
    accepted_at           TIMESTAMP(3) NULL COMMENT '受理时刻（响应时效实际值）',
    resolved_at           TIMESTAMP(3) NULL COMMENT '办结时刻（办结时效实际值）',
    closed_at             TIMESTAMP(3) NULL COMMENT '关闭时刻',
    created_at            TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version               BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁',
    UNIQUE KEY uk_ticket_no (ticket_no),
    UNIQUE KEY uk_source_credential_idem (source, creator_credential_id, idempotency_key),
    KEY idx_owner_status (owner_subject_id, status),
    KEY idx_group_status (assignee_group, status),
    KEY idx_status_resolution (status, overdue_resolution, resolution_deadline),
    KEY idx_status_confirm (status, confirm_deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单';

CREATE TABLE IF NOT EXISTS ticket_transition (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id        BIGINT       NOT NULL COMMENT '工单 ID',
    from_status      VARCHAR(20)  NOT NULL,
    to_status        VARCHAR(20)  NOT NULL,
    action           VARCHAR(20)  NOT NULL COMMENT '动作（与 Ticket 领域方法对应）',
    actor_subject_id VARCHAR(128) NULL COMMENT '动作者（系统动作为 NULL）',
    actor_role       VARCHAR(10)  NOT NULL COMMENT 'OWNER/ASSIGNEE/ADMIN/SYSTEM',
    reason_code      VARCHAR(30)  NULL COMMENT '待补充原因（仅 REQUEST_INFO）',
    created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_transition_ticket (ticket_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '状态转移记录（不可变只增）';

CREATE TABLE IF NOT EXISTS ticket_comment (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id        BIGINT       NOT NULL COMMENT '工单 ID',
    author_subject_id VARCHAR(128) NOT NULL,
    author_role      VARCHAR(10)  NOT NULL COMMENT 'OWNER/ASSIGNEE/ADMIN/SYSTEM',
    author_name      VARCHAR(120) NULL COMMENT '作者姓名快照',
    body             TEXT         NOT NULL COMMENT '正文 Markdown',
    internal         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '处理人内部备注（提单人不可见）',
    created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_comment_ticket (ticket_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单留言（不可变只增）';

CREATE TABLE IF NOT EXISTS ticket_attachment (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL COMMENT '工单 ID',
    comment_id   BIGINT       NULL COMMENT '关联留言（NULL=提交期附件）',
    object_key   VARCHAR(255) NOT NULL COMMENT '对象键（前缀 ticket/{ticketNo}/）',
    file_name    VARCHAR(255) NOT NULL,
    size         BIGINT       NOT NULL COMMENT '字节',
    content_type VARCHAR(100) NOT NULL,
    uploaded_by  VARCHAR(128) NOT NULL,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_attachment_ticket (ticket_id),
    UNIQUE KEY uk_attachment_object (object_key),
    KEY idx_attachment_object (object_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单附件';

CREATE TABLE IF NOT EXISTS ticket_evaluation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id           BIGINT       NOT NULL COMMENT '工单 ID（唯一，一次不可改）',
    evaluator_subject_id VARCHAR(128) NOT NULL,
    star                TINYINT      NOT NULL COMMENT '1-5',
    comment             VARCHAR(1000) NULL,
    evaluated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_evaluation_ticket (ticket_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工单评价（confirm 关闭可评，超时关闭不可评）';

CREATE TABLE IF NOT EXISTS ticket_no_sequence (
    date_key CHAR(6) NOT NULL COMMENT '北京日期 yyMMdd',
    seq      BIGINT  NOT NULL DEFAULT 0 COMMENT '当日序列',
    PRIMARY KEY (date_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '单号日序列（原子 UPDATE 递增，丢号不回补）';

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
