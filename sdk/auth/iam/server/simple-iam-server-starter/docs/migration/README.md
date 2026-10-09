# IAM Server 升级

## 1.3.6

从已运行的 1.3.5 升级只需执行同目录 [V1.3.5__to__V1.3.6__password_max_age.sql](V1.3.5__to__V1.3.6__password_max_age.sql) 一次：`iam_user` 加 `must_change_password_reason`（须改密原因，与须改密标记同写同清）与 `password_updated_at`（口令生存期基线）两个 nullable 列，[schema.sql](../schema.sql) 已同步含新列。**无数据回填**——存量行两列为 null 属预期：原因 null 时前端兜底通用改密文案；`password_updated_at` null 时生存期策略开启后由用户首次登录回填激活基线（不做迁移期批量回填，避免一刀切全员判过期）。核查查询应返回空。全新库直接用 `schema.sql` 初始化。

默认策略关闭（`max-age-days=0`），升级后行为与 1.3.5 一致；行为收紧项（三处改密点拒绝新密码=当前密码）与数据库无关，见 CHANGELOG.1.3.6 升级指引。

## 1.3.3

从已运行的 1.3.2 升级只需执行同目录 [V1.3.2__to__V1.3.3__phone_profile_cleanup.sql](V1.3.2__to__V1.3.3__phone_profile_cleanup.sql) 一次：纯数据修复，无 DDL，[schema.sql](../schema.sql) 与 1.3.2 完全一致、无需变更。该脚本把修复前经管理台落库的空串/纯空白手机号行连同 `phone_bound_at` 双置 NULL（对齐解绑语义），脚本内置的核查查询必须返回空集。升级前或升级后执行均可：1.3.3 代码只堵新入口，不清历史。

存量未规范化号码（裸号/非法格式资料值）不影响 `uk_phone` 正确性，不做批量改写，登记方按需人工修正。全新库直接用 `schema.sql` 初始化，无需本脚本。

## 1.3.2

从已运行的 1.3.1 升级，暂停写请求、完成可恢复备份后，只执行同目录 [V1.3.1__to__V1.3.2__open_role_binding.sql](V1.3.1__to__V1.3.2__open_role_binding.sql) 一次。它仅创建 `iam_open_role_binding`，不修改旧角色或授权表，不按名称接管人工角色，不删除任何授权。目标 InnoDB 须支持 3072 字节索引预算及 DYNAMIC 行格式；初始化 [schema.sql](../schema.sql) 含 DROP，只用于全新库。

启动 1.3.2 后，开启的启动引导增量补齐 IAM 应用的八个精确 API 与五个 DATA 资源，并补受管内置 `iam_admin` 的已有规则。数据库应用锁协调多实例；定制声明保留，同名 DATA 动作/维度不兼容时失败，不覆盖。重复核对不持续递增版本。

引导关闭的部署必须先确认内置 IAM 应用、权限清单、内置 `iam_admin` 及其规则存在，再显式调用 `IamOpenRoleManifestUpgradeService.upgrade()`。该方法不负责创建这些前置对象：缺应用或清单会直接返回，缺受管管理员角色或规则也不会补建。不能以方法调用结束代替升级核验；必须核对清单新增八个 API 码、五个 DATA 资源与管理员规则后，才配置服务授权。精确声明及各自范围见 [权限与授权投影](../领域文档/权限与授权投影.md)。

新机器权限在 AKSK 管理侧显式批准，旧 AKP 不自动扩权。治理宿主使用原资源层路径接管和 AKSK 适配器，关闭 introspect 本地缓存及 fallback；AKSK 验证组件默认自动装配，暂不接管的升级宿主显式置 `io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled: false`，避免缺 introspect 配置在启动期失败。角色归属/版本、组织事实不新增缓存。部署先 Core 1.3.2、Starter 1.3.2，再核对清单及服务三权，最后验收消费方。

管理角色详情提供只读 `openRoleBinding`（`openRoleId/applicationId/rootDepartmentId/revision/state`），普通角色为 null。管理端数字 `roleId`、开放端 UUID `openRoleId`、创建幂等键 `externalId` 各自独立，不能混用。受委托角色改名、规则、挂载、删除都提交强 If-Match `"open-role:<openRoleId>:<revision>"`；旧普通角色不新增该要求。缺条件 428、旧版本 412、非法标记 400；冲突后重读并重算差异，不盲重发。

应用或根存在 ACTIVE 委托引用时删除返回 409，有 ACTIVE 引用的目标应用也不能改为内置应用，须先清理角色；角色删除保留墓碑，禁止通过 DROP 委托表降版或复活幂等键。降版不能抹去这些治理约束，需要停写并恢复完整升级前备份。数据库重建或恢复后，消费方暂停联动并重新确认主体、目标和三权，不能凭数字编号自动续接旧状态。

## 1.3.0

`1.3.0` 将对外用户标识切换为稳定的 `subjectId`，新增手机号能力，并把可信应用的内置属性和所属人授权变更日志纳入数据库结构。新部署直接执行上级 `schema.sql`；已运行 `1.2.0` 的数据库只执行一次 `V1.2.0__to__V1.3.0__subject_and_phone.sql`。

执行前必须暂停 IAM Server 的写请求并完成可恢复的逻辑备份。该脚本依赖 `1.2.0` 的表和索引命名，不能重复执行，也不能跳过 `1.2.0` 直接用于更早版本。脚本完成后启动 `1.3.0` 服务：启动引导会通过配置的主体 ID 生成器为存量用户幂等回填 `subject_id`；回填完成前，所有以 `subjectId` 定位用户的对外接口应视为未完成升级。

升级后应核对以下结果：

1. `iam_user` 存在 `subject_id` 唯一索引、`phone_bound_at`，手机号唯一索引为 `uk_phone`；未绑定手机号保留 `NULL`，解绑后号码可重新绑定。
2. `iam_trusted_application` 存在 `built_in`，既有 `iam` 和 `aksk` 记录已被标记；引导配置清单中的编码始终按内置应用保护，即使数据库列被手工改为 `0`。
3. `iam_aksk_authorization_change` 已更名为 `iam_owner_authorization_change_log`，事件唯一索引和发生时间索引均为新表名；迁移保留原有自增序列。

数据库结构已变更时不支持手写反向 DDL。升级失败或验收不通过时，停止 `1.3.0` 服务，从升级前备份恢复，并启动与该备份对应的历史版本。

## 1.2.0

`1.2.0` 为可信应用增加全局安全停用、OAuth 安全纪元和可恢复的异步删除操作。新部署使用上级
`schema.sql`；已运行 `1.1.1` 的数据库只执行
`V1.1.1__to__V1.2.0__trusted_application_lifecycle.sql`。该脚本不可重复执行，执行前后检结果必须保留。

升级后所有既有可信应用与 consent 均初始化为安全纪元 `1`、应用状态为可用，不会强制用户重新登录。既有
用户应用授权的 `owner_security_epoch` 按该用户当前 `permission_version + 1` 回填，避免 AKU 在线 reader 因
DDL 默认值滞后而失败关闭；该回填不改授权内容、准入状态或业务授权版本。首次停用或恢复才推进应用安全
纪元，使旧授权和 consent 永久失效。升级脚本不创建删除任务、不变更 Portal 启用状态、不修改 OAuth client secret。

## 1.1.1

`1.1.1` 在 `1.1.0` 的菜单树和默认入口能力上，新增 Portal 可信应用根节点的全局排序。排序由服务端维护，浏览器只提交完整快照与版本号；停用的 Portal 集成仍保留位置，重新启用后回到原位。

按数据库现状选择且只选择一条路径：

| 数据库现状 | 执行文件 | 兼容性 |
| --- | --- | --- |
| 新部署 | 上级 `schema.sql` | 新库直接具备 1.1.1 全量结构 |
| 已运行 1.0.0，尚未执行任一 1.1 升级 | `V1.0.0__to__V1.1.1__portal_menu_tree_and_application_order.sql` | MySQL 8.0+ |
| 已运行 1.1.0 菜单树 | `V1.1.0__to__V1.1.1__portal_application_order.sql` | MySQL 5.7+ / 8.0+ |

`V1.0.0__to__V1.1.0__portal_menu_tree.sql` 是既有 1.1.0 历史升级脚本，不能与本版 1.0.0 直升脚本连续执行。所有升级脚本均不可重复执行；已完成升级的数据库只能执行后续版本提供的增量脚本。

### 1.1.1 升级步骤

1. 在维护窗口暂停 IAM Server 的写请求，先对目标数据库完成可恢复的完整逻辑备份，并单独备份 `iam_trusted_application_menu`。该升级前备份是回退的唯一依据，必须保留到发布验收完成。
2. 按上表选择一个脚本并执行一次。文件内依次输出前检、DDL/DML 和后检结果；前检不符合目标版本时立即停止，不能尝试补跑或改写脚本。
3. 核对后检结果：菜单树路径应存在 `parent_id`、`node_type`、`icon`、`required_page_permission`、`presentation_mode`；根节点排序路径应存在 `sort_order`、`uk_sort_order` 和 `idx_enabled_sort_order`，且 `sort_order` 无空值、无重复值。
4. 启动 1.1.1 实例，先读取可信应用详情、Portal 可访问应用和管理端应用顺序接口。再在管理端保存包含 GROUP 与 PAGE 的菜单树，调整两个 Portal 应用根节点顺序，并以普通用户验证仅保留获准应用的相对顺序。服务首次启动时，仅当内置 IAM 菜单仍精确等于 1.0.0 官方七项扁平默认菜单，才会自动升级为“身份目录 / 访问控制”分组树；任意手工增删、改名、改路由、图标、排序调整或既有 GROUP 均视为自定义配置并保持不变。

### 1.1.1 回退原则

数据库结构变更后，不支持通过手写反向 DDL 回退。若升级失败或验收不通过：停止 1.1.1 实例，从第 1 步的升级前备份恢复数据库，再启动与该备份结构对应的历史版本。不要在已写入 1.1.x 菜单树或根节点排序数据的数据库上直接降级服务。

### 1.1.1 数据语义

升级会把 1.0.0 历史菜单保留为根级 `PAGE`：原 `route` 保持不变，新增 `parent_id`、`icon` 与 `required_page_permission` 为空，`node_type` 为 `PAGE`，`presentation_mode` 为 `STANDARD`。因此这些菜单在 1.1.1 中仍可读、可导航，并按节点类型显示稳定的默认图标和原有 Portal 布局；除未定制的内置 IAM 默认菜单会在首次启动时自动分组外，只有管理端明确保存 `menuTree` 后才会出现 GROUP 节点。需要无顶栏、无侧栏的展示页时，只能在 PAGE 上设置 `IMMERSIVE`；它不启用浏览器全屏，也不改变登录会话、路由和页面权限判断。

根节点排序回填以可信应用创建时间、应用 ID 为稳定初始顺序。上线后通过管理端的完整快照接口调整；不应直接修改 `sort_order`，也不应让客户端按应用编码、名称重新排序。
