-- IAM Server 1.0.0 -> 1.1.0：可信应用 Portal 菜单树升级。
-- 仅适用于已由 1.0.0 schema.sql 初始化且尚未执行本脚本的 MySQL 8+ 数据库。
-- 本文件按前检、升级、后检顺序一次执行；不支持重复执行。

-- ===== 前检（只读）=====
-- 目标表必须存在。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_menu';

-- 升级前预期仅返回 route；升级后预期返回六个菜单树字段，route 的 is_nullable 为 YES。
SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_menu'
  AND column_name IN ('route', 'parent_id', 'node_type', 'icon', 'required_page_permission', 'presentation_mode')
ORDER BY field(column_name, 'route', 'parent_id', 'node_type', 'icon', 'required_page_permission', 'presentation_mode');

-- 升级前不应返回该索引。
SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_menu'
  AND index_name = 'idx_application_id_parent_sort'
ORDER BY seq_in_index;

-- ===== 升级 =====

ALTER TABLE iam_trusted_application_portal
    ADD COLUMN default_page_menu_code VARCHAR(64) NULL COMMENT '应用默认入口PAGE菜单编码' AFTER api_base,
    ADD COLUMN default_entry_path VARCHAR(255) NULL COMMENT '应用默认入口相对路径' AFTER default_page_menu_code,
    ADD COLUMN config_version BIGINT NOT NULL DEFAULT 0 COMMENT 'Portal配置乐观锁版本' AFTER default_entry_path;

CREATE TABLE iam_portal_setting (
    id INT NOT NULL COMMENT '单例主键，固定为1',
    login_landing_application_id BIGINT NULL COMMENT '无深链登录首页应用ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '全局设置乐观锁版本',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM Portal全局设置表';

INSERT INTO iam_portal_setting (id, login_landing_application_id, version) VALUES (1, NULL, 0);

ALTER TABLE iam_trusted_application_menu
    MODIFY COLUMN route VARCHAR(255) NULL COMMENT 'PAGE 相对路由，GROUP 为空',
    ADD COLUMN parent_id BIGINT NULL COMMENT '父菜单ID，根菜单为空' AFTER application_id,
    ADD COLUMN node_type VARCHAR(16) NOT NULL DEFAULT 'PAGE' COMMENT '节点类型：GROUP/PAGE' AFTER name,
    ADD COLUMN icon VARCHAR(64) NULL COMMENT '菜单节点图标编码，空时按节点类型回退' AFTER node_type,
    ADD COLUMN required_page_permission VARCHAR(256) NULL COMMENT 'PAGE 页面权限码，空表示继承应用准入' AFTER route,
    ADD COLUMN presentation_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT 'PAGE Portal展示模式：STANDARD/IMMERSIVE，GROUP固定STANDARD' AFTER required_page_permission,
    ADD KEY idx_application_id_parent_sort (application_id, parent_id, sort_order, id);

-- 旧菜单全部是根级页面，默认值已完成 node_type 回填；显式更新便于审计升级结果。
UPDATE iam_trusted_application_menu
SET parent_id = NULL,
    node_type = 'PAGE',
    icon = NULL,
    required_page_permission = NULL,
    presentation_mode = 'STANDARD';

-- ===== 后检（只读）=====
SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_menu'
  AND column_name IN ('route', 'parent_id', 'node_type', 'icon', 'required_page_permission', 'presentation_mode')
ORDER BY field(column_name, 'route', 'parent_id', 'node_type', 'icon', 'required_page_permission', 'presentation_mode');

SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_menu'
  AND index_name = 'idx_application_id_parent_sort'
ORDER BY seq_in_index;

-- 升级后的历史数据预期均为 0；后续由管理端新建的层级菜单不适用该判断。
SELECT
    SUM(CASE WHEN parent_id IS NOT NULL THEN 1 ELSE 0 END) AS non_root_count,
    SUM(CASE WHEN node_type <> 'PAGE' THEN 1 ELSE 0 END) AS non_page_count,
    SUM(CASE WHEN icon IS NOT NULL THEN 1 ELSE 0 END) AS configured_icon_count,
    SUM(CASE WHEN required_page_permission IS NOT NULL THEN 1 ELSE 0 END) AS bound_permission_count,
    SUM(CASE WHEN presentation_mode <> 'STANDARD' THEN 1 ELSE 0 END) AS non_standard_presentation_count
FROM iam_trusted_application_menu;

SELECT id, login_landing_application_id, version
FROM iam_portal_setting
WHERE id = 1;
