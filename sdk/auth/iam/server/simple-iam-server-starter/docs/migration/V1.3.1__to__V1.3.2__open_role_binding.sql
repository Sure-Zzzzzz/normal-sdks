-- V1.3.1 -> V1.3.2：受委托角色边界表（纯 DDL，只 CREATE 新表，不动旧角色与授权）
-- 不按名称接管人工角色、不删除任何授权；执行前备份，同一数据库仅执行一次。

CREATE TABLE iam_open_role_binding (
    open_role_id CHAR(36) NOT NULL COMMENT '对外稳定角色UUID',
    role_id BIGINT NOT NULL COMMENT '绑定的既有普通角色ID',
    owner_source_id VARCHAR(128) NOT NULL COMMENT '可信主体来源',
    owner_subject_type VARCHAR(16) NOT NULL COMMENT '可信主体类型',
    owner_subject_id VARCHAR(256) NOT NULL COMMENT '可信主体标识',
    external_id CHAR(36) NOT NULL COMMENT '调用方创建幂等UUID',
    application_id BIGINT NOT NULL COMMENT '固定非内置目标应用ID',
    root_department_id BIGINT NOT NULL COMMENT '固定授权部门根ID',
    creation_digest CHAR(64) NOT NULL COMMENT '规范化创建内容摘要',
    revision BIGINT NOT NULL DEFAULT 1 COMMENT '单调修改版本，初始为1',
    state VARCHAR(16) NOT NULL COMMENT '状态：ACTIVE有效，DELETED已删除',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近变化时间',
    PRIMARY KEY (open_role_id),
    UNIQUE KEY uk_open_role_role (role_id),
    UNIQUE KEY uk_open_role_external (owner_source_id, owner_subject_type, owner_subject_id, external_id),
    KEY idx_open_role_scope (application_id, root_department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部主体角色管理委托边界表';
