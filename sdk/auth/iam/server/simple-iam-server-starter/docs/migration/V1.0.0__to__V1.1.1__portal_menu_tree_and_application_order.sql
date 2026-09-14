-- IAM Server 1.0.0 -> 1.1.1：可信应用 Portal 菜单树、默认入口和应用根节点全局排序。
-- 仅适用于已由 1.0.0 schema.sql 初始化且尚未执行 1.1.x 升级的 MySQL 8+ 数据库。
-- 本文件按前检、升级、后检顺序一次执行；不支持重复执行。

-- ===== 前检（只读）=====
SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_menu'
  AND column_name IN ('route', 'parent_id', 'node_type', 'icon', 'required_page_permission', 'presentation_mode')
ORDER BY field(column_name, 'route', 'parent_id', 'node_type', 'icon', 'required_page_permission', 'presentation_mode');

SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND column_name = 'sort_order';

-- ===== 升级 =====
ALTER TABLE iam_trusted_application_portal
    ADD COLUMN default_page_menu_code VARCHAR(64) NULL COMMENT '应用默认入口PAGE菜单编码' AFTER api_base,
    ADD COLUMN default_entry_path VARCHAR(255) NULL COMMENT '应用默认入口相对路径' AFTER default_page_menu_code,
    ADD COLUMN config_version BIGINT NOT NULL DEFAULT 0 COMMENT 'Portal配置乐观锁版本' AFTER default_entry_path,
    ADD COLUMN sort_order BIGINT NULL COMMENT 'Portal应用根节点全局排序值' AFTER api_base;

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

UPDATE iam_trusted_application_menu
SET parent_id = NULL,
    node_type = 'PAGE',
    icon = NULL,
    required_page_permission = NULL,
    presentation_mode = 'STANDARD';

DROP TEMPORARY TABLE IF EXISTS tmp_iam_portal_application_order;
CREATE TEMPORARY TABLE tmp_iam_portal_application_order (
    sequence_no BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    PRIMARY KEY (sequence_no),
    UNIQUE KEY uk_application_id (application_id)
) ENGINE=InnoDB;

INSERT INTO tmp_iam_portal_application_order (application_id)
SELECT portal.application_id
FROM iam_trusted_application_portal portal
INNER JOIN iam_trusted_application application ON application.id = portal.application_id
ORDER BY application.created_at ASC, portal.application_id ASC;

UPDATE iam_trusted_application_portal portal
INNER JOIN tmp_iam_portal_application_order ordered ON ordered.application_id = portal.application_id
SET portal.sort_order = ordered.sequence_no * 10;

SELECT
    SUM(CASE WHEN sort_order IS NULL THEN 1 ELSE 0 END) AS missing_sort_order_count,
    COUNT(*) - COUNT(DISTINCT sort_order) AS duplicate_sort_order_count,
    COUNT(*) AS portal_integration_count
FROM iam_trusted_application_portal;

ALTER TABLE iam_trusted_application_portal
    MODIFY COLUMN sort_order BIGINT NOT NULL COMMENT 'Portal应用根节点全局排序值',
    ADD UNIQUE KEY uk_sort_order (sort_order),
    ADD KEY idx_enabled_sort_order (enabled, sort_order, application_id);

DROP TEMPORARY TABLE IF EXISTS tmp_iam_portal_application_order;

-- ===== 后检（只读）=====
SELECT column_name, is_nullable, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND column_name IN ('default_page_menu_code', 'default_entry_path', 'config_version', 'sort_order')
ORDER BY field(column_name, 'default_page_menu_code', 'default_entry_path', 'config_version', 'sort_order');

SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'iam_trusted_application_portal'
  AND index_name IN ('uk_sort_order', 'idx_enabled_sort_order')
ORDER BY index_name, seq_in_index;

SELECT
    SUM(CASE WHEN sort_order IS NULL THEN 1 ELSE 0 END) AS missing_sort_order_count,
    COUNT(*) - COUNT(DISTINCT sort_order) AS duplicate_sort_order_count,
    COUNT(*) AS portal_integration_count
FROM iam_trusted_application_portal;
