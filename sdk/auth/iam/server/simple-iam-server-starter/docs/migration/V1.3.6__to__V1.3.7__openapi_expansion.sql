-- V1.3.6 -> V1.3.7：openapi 扩面数据适配（外部主体发信 + 分页防护，纯列变更无数据回填）
-- docs/schema.sql 已含新形态（新初始化库直接建表）；同一数据库仅执行一次，执行前备份。
-- 背景：openapi 站内信族（POST /iam/api/messages）允许外部主体（AKP 服务凭据）发信——
--       外部主体无本地 userId，sender_user_id 必须可空；发送者标识记 sender_username
--       （aksk:{clientId} 形态，最长约 64 字符，扩容 128 防御余量）。

ALTER TABLE iam_message MODIFY COLUMN sender_user_id BIGINT NULL
    COMMENT '发送人用户ID（1.3.7 起可空：外部主体经 openapi 发信时无本地 userId，发送者标识记 sender_username）';
ALTER TABLE iam_message MODIFY COLUMN sender_username VARCHAR(128) NOT NULL
    COMMENT '发送人用户名（本地用户名或外部主体标识 aksk:{clientId}，1.3.7 扩容）';

-- 核查：应返回 2（两列均已就位）
SELECT COUNT(*) AS migrated_columns
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'iam_message'
   AND ((column_name = 'sender_user_id' AND is_nullable = 'YES')
     OR (column_name = 'sender_username' AND character_maximum_length = 128));
