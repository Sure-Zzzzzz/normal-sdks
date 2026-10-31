-- 存量库追加迁移；执行前备份并停止本域写入。旧计划快照保持NULL，必须重新生成，不伪造历史事实。
-- 本文件先只读预检；不要直接执行下方 DDL。按实际缺失列、表和索引逐条人工复制，
-- 每条执行后重新回读 information_schema，避免部分迁移重复失败。
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'license_integration_plan'
  AND column_name IN ('projection_snapshot', 'execution_mode', 'failure_reason', 'state')
ORDER BY ordinal_position;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('license_role_projection', 'license_integration_request');

-- 表已存在时逐列与下方定义核对，不能把“已存在”误当完整结构；缺列或定义冲突停止。
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('license_role_projection', 'license_integration_request')
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique, seq_in_index, column_name, sub_part
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('license_role_projection', 'license_integration_request')
ORDER BY table_name, index_name, seq_in_index;

-- 仅在对应列缺失且定义已人工核对后，分别复制执行；state 仅在需补充注释/长度时调整。
-- ALTER TABLE license_integration_plan
--     ADD COLUMN projection_snapshot TEXT NULL COMMENT '环境绑定、专用角色、权限清单与真实目录快照';
-- ALTER TABLE license_integration_plan
--     ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL或显式AUTO';
-- ALTER TABLE license_integration_plan
--     ADD COLUMN failure_reason VARCHAR(64) NULL COMMENT '固定故障原因，不含上游报文';
-- ALTER TABLE license_integration_plan
--     MODIFY COLUMN state VARCHAR(40) NOT NULL COMMENT '待执行、人工确认、核验、替代、漂移或未知';

-- 仅在表不存在且列/索引预检已确认缺失时，复制执行下列建表语句。
-- CREATE TABLE license_role_projection (
--     projection_id VARCHAR(64) NOT NULL COMMENT '稳定外部幂等UUID与本地台账编号',
--     external_id VARCHAR(64) NOT NULL COMMENT '当前环境外部幂等UUID，重确认时重新生成',
--     customer_id VARCHAR(64) NOT NULL COMMENT '客户编号',
--     product_id VARCHAR(64) NOT NULL COMMENT '产品编号',
--     application_id BIGINT NOT NULL COMMENT '批准目标IAM应用编号',
--     organization_instance_id VARCHAR(128) NOT NULL COMMENT '管理员确认的环境编号，非IAM目录纪元',
--     binding_generation INT NOT NULL COMMENT '客户组织绑定代数',
--     root_department_id BIGINT NOT NULL COMMENT '批准客户组织根',
--     open_role_id VARCHAR(64) NULL COMMENT '已核验实际受委托角色UUID',
--     role_code VARCHAR(128) NULL COMMENT '实际角色编码，不证明所有权',
--     desired_revision BIGINT NOT NULL COMMENT '同目标期望修订',
--     acknowledged_rules TEXT NULL COMMENT '最后确认实际应用规则，空表示无规则',
--     acknowledged_departments TEXT NULL COMMENT '最后确认完整部门挂载JSON数组',
--     remote_revision BIGINT NULL COMMENT '最后确认的IAM实际修订围栏',
--     state VARCHAR(40) NOT NULL COMMENT '协作安全状态，未知与漂移阻止自动写入',
--     updated_at DATETIME(6) NOT NULL COMMENT '最近协调时间',
--     row_version BIGINT NOT NULL COMMENT '本地乐观锁修订',
--     PRIMARY KEY (projection_id),
--     UNIQUE KEY uk_license_projection_external (external_id),
--     UNIQUE KEY uk_license_projection_target (customer_id, product_id, application_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='License专用角色协调与真实观察台账';

-- CREATE TABLE license_integration_request (
--     request_digest VARCHAR(64) NOT NULL COMMENT '已验证主体、HTTP目标和幂等键的SHA256摘要',
--     input_digest VARCHAR(64) NOT NULL COMMENT '规范化请求JSON的SHA256摘要',
--     plan_id VARCHAR(64) NOT NULL COMMENT '实际返回的结果计划编号',
--     PRIMARY KEY (request_digest)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='License角色协作请求幂等台账';
