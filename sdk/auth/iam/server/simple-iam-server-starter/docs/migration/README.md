# IAM Server 1.1.0 菜单树升级

本目录同时交付两种建库路径：

- 新部署：直接执行上级目录的 `schema.sql`，其中已包含 1.1.0 全量表结构；
- 已运行 1.0.0：按本文顺序一次性执行 `V1.0.0__to__V1.1.0__portal_menu_tree.sql`。

升级脚本不是可重复执行脚本。已完成升级的数据库不得再次执行，后续版本应提供各自的增量脚本。

## 升级步骤

1. 在维护窗口暂停 IAM Server 的写请求，先对目标数据库完成可恢复的完整逻辑备份，并单独备份 `iam_trusted_application_menu`。该升级前备份是回退到 1.0.0 的唯一依据，必须保留到发布验收完成。
2. 执行 `V1.0.0__to__V1.1.0__portal_menu_tree.sql` 一次。文件内依次输出前检、DDL/DML 和后检结果：只有目标表存在、`route` 仍为非空字段、且 `parent_id`、`node_type`、`icon`、`required_page_permission`、`presentation_mode` 与组合索引均尚不存在时才能继续。
3. 核对同一文件末尾的后检结果：`parent_id`、`node_type`、`icon`、`required_page_permission`、`presentation_mode` 和组合索引均存在；历史数据均为根级 `PAGE`，且 `parent_id`、`icon`、`required_page_permission` 为空、`presentation_mode` 为 `STANDARD`。需要保留验收快照时可在此时另行备份，但它不能替代第 1 步的回退备份。
4. 启动 1.1.0 实例，先读取可信应用详情和 Portal 可访问应用接口，再在管理端保存一棵包含 GROUP 与 PAGE 的菜单树。服务首次启动时，仅当内置 IAM 菜单仍精确等于 1.0.0 官方七项扁平默认菜单，才会自动升级为“身份目录 / 访问控制”分组树；任意手工增删、改名、改路由、图标、排序调整或既有 GROUP 均视为自定义配置并保持不变。

## 回退原则

数据库结构变更后，不支持通过手写反向 DDL 回退。若升级失败或验收不通过：停止 1.1.0 实例，从第 1 步的升级前备份恢复数据库，再启动仍兼容该备份结构的 1.0.0 实例。不要在已经写入 1.1 菜单树的数据上直接降级服务。

## 数据语义

升级会把 1.0.0 历史菜单保留为根级 `PAGE`：原 `route` 保持不变，新增 `parent_id`、`icon` 与 `required_page_permission` 为空，`node_type` 为 `PAGE`，`presentation_mode` 为 `STANDARD`。因此这些菜单在 1.1.0 中仍可读、可导航，并按节点类型显示稳定的默认图标和原有 Portal 布局；除未定制的内置 IAM 默认菜单会在首次启动时自动分组外，只有管理端明确保存 `menuTree` 后才会出现 GROUP 节点。需要无顶栏、无侧栏的展示页时，只能在 PAGE 上设置 `IMMERSIVE`；它不启用浏览器全屏，也不改变登录会话、路由和页面权限判断。
