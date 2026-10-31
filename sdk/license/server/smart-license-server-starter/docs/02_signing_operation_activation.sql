-- 存量 License 库的激活索引迁移（MySQL 8）。新库只执行 01_schema.sql，不重复执行本脚本。
-- 执行前停止 License 写入并备份 license_signing_operation；本脚本不修改冻结签名字节或签名结果。
-- 先用 SHOW COLUMNS FROM license_signing_operation LIKE 'activation_id' 确认列不存在。
-- 若宿主此前已自动加列，跳过下面 ALTER 中 ADD COLUMN，仍须回填旧行并补索引。
ALTER TABLE license_signing_operation
    ADD COLUMN activation_id VARCHAR(128) NULL
        COMMENT '冻结载荷派生的部署激活编号（人员/服务凭据为空）' AFTER grant_id,
    ADD KEY idx_license_signing_operation_activation (activation_id, grant_id, operation_id);

-- 冻结的是无 padding Base64URL 段，先替换 URL 字符并补齐四字节组后解码。
-- 非协议占位输入、坏 Base64URL 或坏 JSON 不伪造归属；人员/服务凭据保持 NULL。
CREATE TEMPORARY TABLE license_activation_backfill AS
SELECT operation_id,
       CASE WHEN frozen_payload REGEXP '^[A-Za-z0-9_-]+$'
           THEN CONVERT(FROM_BASE64(RPAD(REPLACE(REPLACE(frozen_payload, '-', '+'), '_', '/'),
                                        4 * CEIL(CHAR_LENGTH(frozen_payload) / 4), '=')) USING utf8mb4)
           ELSE NULL END AS decoded_payload
FROM license_signing_operation;

-- 把不可解析输入转成空对象，只用于安全提取元数据；原表 frozen_payload 不变。
UPDATE license_activation_backfill
SET decoded_payload = '{}'
WHERE decoded_payload IS NULL OR NOT JSON_VALID(decoded_payload);

UPDATE license_signing_operation AS operation
JOIN license_activation_backfill AS frozen ON operation.operation_id = frozen.operation_id
SET operation.activation_id = JSON_UNQUOTE(JSON_EXTRACT(frozen.decoded_payload, '$.subjectBinding.activationId'))
WHERE JSON_UNQUOTE(JSON_EXTRACT(frozen.decoded_payload, '$.subjectBinding.kind')) = 'DEPLOYMENT'
  AND JSON_TYPE(JSON_EXTRACT(frozen.decoded_payload, '$.subjectBinding.activationId')) = 'STRING'
  AND CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(frozen.decoded_payload, '$.subjectBinding.activationId')))) > 0
  AND CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(frozen.decoded_payload, '$.subjectBinding.activationId'))) <= 128;

-- 校验只返回异常任务编号，不输出载荷。结果必须为空；存在部署缺编号时先人工核实，不伪造编号。
SELECT operation.operation_id
FROM license_signing_operation AS operation
JOIN license_activation_backfill AS frozen ON operation.operation_id = frozen.operation_id
WHERE JSON_UNQUOTE(JSON_EXTRACT(frozen.decoded_payload, '$.subjectBinding.kind')) = 'DEPLOYMENT'
  AND operation.activation_id IS NULL;

DROP TEMPORARY TABLE license_activation_backfill;
