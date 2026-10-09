-- V1.3.5 -> V1.3.6：口令最长生存期与须改密原因（两列新增，均 nullable，无数据回填）
-- docs/schema.sql 已含新列（新初始化库直接建表，勿执行本脚本）；同一数据库仅执行一次。
-- 语义：must_change_password_reason 与 must_change_password 同写同清（存量行 null=历史置位无原因，
--       前端兜底通用文案）；password_updated_at 存量行 null=策略未激活，开启 max-age-days 后
--       用户首次登录回填为登录时刻（激活基线），不做迁移期批量回填（避免一刀切全员判过期）。

ALTER TABLE iam_user ADD COLUMN must_change_password_reason VARCHAR(32) NULL
    COMMENT '须改密原因：FIRST_LOGIN/PASSWORD_RESET/PASSWORD_EXPIRED，与须改密标记同写同清（1.3.6）';
ALTER TABLE iam_user ADD COLUMN password_updated_at TIMESTAMP NULL
    COMMENT '密码最近一次设置时刻：建号 null，策略开启后首登回填激活基线；改密/重置刷新（1.3.6）';

-- 核查：应返回 0（列已就位）
SELECT COUNT(*) AS missing_columns
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'iam_user'
   AND column_name IN ('must_change_password_reason', 'password_updated_at')
HAVING COUNT(*) <> 2;
