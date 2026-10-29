-- CRM 1.0.0 -> 1.1.0 结构升级。
-- 前提：目标库使用 1.0.0 的 schema.sql，且已完成备份；脚本不删除业务数据。
-- 执行后由部署流程重新启动 1.1.0 服务。

CREATE TABLE IF NOT EXISTS crm_opportunity (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    opportunity_id        VARCHAR(64)  NOT NULL COMMENT '商机唯一标识',
    customer_id           VARCHAR(64)  NOT NULL COMMENT '客户唯一标识',
    owner                 VARCHAR(128) NOT NULL COMMENT '归属主体（iam:/aksk: 前缀）',
    title                 VARCHAR(256) NOT NULL COMMENT '商机标题',
    primary_offering_id   VARCHAR(64)  DEFAULT NULL COMMENT '主推目录项标识',
    contact_id            VARCHAR(64)  DEFAULT NULL COMMENT '主要联系人标识',
    expected_close_date   DATE         DEFAULT NULL COMMENT '预计成交日期',
    estimated_amount      DECIMAL(20, 6) DEFAULT NULL COMMENT '预计金额',
    currency              VARCHAR(8)   DEFAULT NULL COMMENT '预计金额币种',
    stage                 VARCHAR(16)  NOT NULL COMMENT '销售阶段',
    lost_reason           VARCHAR(512) DEFAULT NULL COMMENT '输单原因',
    aggregate_version     BIGINT       NOT NULL COMMENT '聚合版本',
    created_at            TIMESTAMP(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at            TIMESTAMP(3) NOT NULL COMMENT '更新时间（UTC）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_opportunity_id (opportunity_id),
    KEY idx_opportunity_owner (owner, id),
    KEY idx_opportunity_customer (customer_id, id),
    KEY idx_opportunity_stage (stage, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CRM 销售商机';

SET @crm_database = DATABASE();
SELECT COUNT(*) INTO @crm_quotation_opportunity_column
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @crm_database
  AND TABLE_NAME = 'crm_quotation'
  AND COLUMN_NAME = 'opportunity_id';
SET @crm_quotation_opportunity_sql = IF(
    @crm_quotation_opportunity_column = 0,
    'ALTER TABLE crm_quotation ADD COLUMN opportunity_id VARCHAR(64) DEFAULT NULL COMMENT ''关联商机标识''',
    'SELECT 1');
PREPARE crm_quotation_opportunity_statement FROM @crm_quotation_opportunity_sql;
EXECUTE crm_quotation_opportunity_statement;
DEALLOCATE PREPARE crm_quotation_opportunity_statement;

SELECT COUNT(*) INTO @crm_quotation_opportunity_index
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @crm_database
  AND TABLE_NAME = 'crm_quotation'
  AND INDEX_NAME = 'idx_quotation_opportunity';
SET @crm_quotation_opportunity_index_sql = IF(
    @crm_quotation_opportunity_index = 0,
    'ALTER TABLE crm_quotation ADD KEY idx_quotation_opportunity (opportunity_id)',
    'SELECT 1');
PREPARE crm_quotation_opportunity_index_statement FROM @crm_quotation_opportunity_index_sql;
EXECUTE crm_quotation_opportunity_index_statement;
DEALLOCATE PREPARE crm_quotation_opportunity_index_statement;
