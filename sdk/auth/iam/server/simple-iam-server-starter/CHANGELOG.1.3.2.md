# CHANGELOG - simple-iam-server-starter 1.3.2

## 版本信息

- 版本：`1.3.2`
- 类型：Feature / 服务主体受委托角色治理
- 基线版本：`1.3.1`
- 固定运行时：Spring Boot `2.7.9`、Spring Authorization Server `0.4.1`、Java `8`

## 版本定位

外部服务可以通过认证的 HTTP API 管理自己创建的 IAM 普通角色，在批准的非内置应用和部门根范围内维护角色规则与部门挂载，不需要操作 IAM 数据库。

IAM 只承载身份、角色和授权投影，不持有产品商业授权、有效期、离线机器规则或业务资格。部门成员继承角色仍按既有直属部门机制执行；覆盖根及整棵子树时，由消费方读取完整目录并逐部门挂载。

## 依赖变化

| 依赖 | 1.3.1 | 1.3.2 | 说明 |
|---|---|---|---|
| `simple-iam-server-core` | `1.3.1` | `1.3.2` | `api` 消费正式制品，增加受委托角色常量与错误契约 |
| `simple-iam-core` | `1.1.0` | `1.1.0` | 身份协议基座不变 |
| `simple-iam-server-audit-listener-starter` | 无测试依赖 | `1.0.0`，仅测试 | 验证旧发布监听器可消费本版既有事件，不增加 Starter 的生产监听依赖 |

生产依赖使用 `io.github.sure-zzzzzz:simple-iam-server-core:1.3.2`，不依赖工作区 Core 源码。按需引入的审计监听器无需随本版升级，宿主仍须明确选择和部署依赖版本。

## 主要变更

### 服务身份与授权范围

新增端点沿用公共资源层的 `/iam/api/**` 接管与 AKSK 在线验证，只接受可信来源为 AKSK 的 `SERVICE` 主体；管理员 Cookie、IAM 登录令牌和 HUMAN 主体不能代替该身份。

每个动作独立检查应用准入、精确 API 码和完整 DATA 授权。同一 grant（一次数据授权）中的条件同时满足，不同 grant 为或；不能把不同 grant 的应用条件与部门根条件拼成新的管理权。即使 `all=true`，也不能绕过服务归属、固定应用、固定根或非内置应用限制。

| API 码 | DATA resource | actions | dimensions |
|---|---|---|---|
| `iam:open-role:create:api`、`iam:open-role:read:api` | `iam:open-role` | `create,read` | `applicationId,rootDepartmentId` |
| `iam:open-role-rule:read:api`、`iam:open-role-rule:write:api` | `iam:open-role-rule` | `read,write` | `applicationId,rootDepartmentId,openRoleId` |
| `iam:open-department-role:read:api`、`iam:open-department-role:write:api` | `iam:open-department-role` | `read,write` | `applicationId,rootDepartmentId,openRoleId,departmentId` |
| `iam:open-directory:read:api` | `iam:open-directory` | `read` | `rootDepartmentId` |
| `iam:open-application:read:api` | `iam:open-application` | `read` | `applicationId` |

规则写权限是批准范围内的治理权，不自动限制为调用方运行时权限的子集。上述 DATA 控制调用方能管理哪些 IAM 对象；目标应用规则的 `dataGrantTemplate` 控制最终用户能访问哪些业务数据。

### 角色创建、规则与部门挂载

| 端点 | 能力 |
|---|---|
| `POST /iam/api/roles` | 创建自有角色，固定主体、应用和部门根 |
| `GET /iam/api/roles`、`GET /iam/api/roles/{openRoleId}` | 按归属与 DATA 范围查询分页 / 详情 |
| `GET / PUT / DELETE /iam/api/roles/{openRoleId}/authorization-rules/{applicationId}` | 读取 / 整体替换 / 清除固定应用规则 |
| `GET /iam/api/roles/{openRoleId}/departments` | 读取当前范围内的角色-部门关系分页 |
| `PUT / DELETE /iam/api/departments/{departmentId}/roles/{openRoleId}` | 增加 / 撤销单条部门关系，PUT 请求体为空 |
| `GET /iam/api/organization-directories/{rootDepartmentId}/departments` | 读取完整根子树 |
| `GET /iam/api/organization-directories/{rootDepartmentId}/members/{subjectId}` | 读取最小成员资格事实 |
| `GET /iam/api/target-applications/{applicationId}`、`GET /iam/api/target-applications/{applicationId}/permission-manifest` | 读取批准的非内置应用及当前权限清单 |

创建使用调用方 `externalId` UUID 作为幂等键，由服务生成稳定 `openRoleId` UUID 和角色编码。相同主体、相同创建键、相同规范化内容首次返回 `201`，重放返回当前角色 `200`，不重置已有规则或部门关系；同键不同内容及已删除墓碑返回 `409`。

角色详情和规则响应携带强 ETag，即用于条件写入的版本标记。规则与部门关系写入必须提交 If-Match：缺少返回 `428`、旧版本返回 `412`、格式非法返回 `400`；弱标记、通配符及标记列表不被接受。规则整体替换同时核对 `manifestVersion` 与 `manifestDigest`，清单变化返回 `409`。无实际变化不递增角色 revision、不重复重算投影或发变更事件。

部门挂载只能新增到当前固定根子树内；部门已迁出或消失时仍允许清理本角色残留关系。完整目录包含根，最大 4096 节点、64 层；子树节点或深度、关系及成员祖先链深度超限返回 `422`，环或根的悬空父链等结构冲突返回 `409`，不把截断数据当作完整结果。成员不存在或不在批准根内统一返回 `404`。组织资格及 DATA 范围变化不由角色 revision 表示，消费方须读取当前事实。

新 JSON 请求预算为 1 MiB。错误由 HTTP 状态表达，响应体仅包含安全的 `message/timestamp/requestId`，不暴露内部错误码、异常链或认证材料。

部门关系 GET 返回 `Cache-Control: no-store`，不提供 ETag、不按 If-None-Match 返回 304；角色 revision 保留在响应体，写入标记从角色详情读取。组织迁移或调用方 DATA 收缩后，即使角色版本未变化，也返回当前完整分页。

创建的 `applicationId/rootDepartmentId` 和规则的 `manifestVersion` 使用局部严格整数解析，只接受正数 JSON 整数且不得超过 int64；小数、指数形式、数字字符串、布尔值、null 和溢出返回 400，不截断、不落库，不改变宿主其他接口的解析行为。

### 管理面共同约束

角色详情、总列表、分页、部门角色及用户有效角色响应增加只读 `openRoleBinding`：`openRoleId/applicationId/rootDepartmentId/revision/state`，普通角色为 `null`。列表批量读取委托摘要，不逐角色查询；列表用于识别边界，写入前读取当前详情版本。管理员对受委托角色改名、改规则、挂载及删除使用同一强 If-Match，并受同一应用与部门根约束；普通角色不新增条件写要求。

受委托角色禁止个人直接分配、传统全局权限及跨应用规则。机器不提供角色改名或删除入口；管理员删除时清理既有关系与投影贡献，并保留 `DELETED` 墓碑。固定应用或根仍有 ACTIVE 委托引用时禁止删除，有 ACTIVE 引用的应用也不能改为内置应用，返回 `409`。

### 数据结构与升级核对

新增 `iam_open_role_binding`，记录稳定角色标识、主体归属三元组、创建幂等键与摘要、固定应用及部门根、单调修改版本和 ACTIVE / DELETED 状态。它只表示角色管理的委托边界，不是商业授权或用户授权投影表。角色、规则和部门关系仍复用已有表，不另建一套权限体系。

升级服务在数据库锁下增量合并 IAM 清单中的八个 API 码与五个 DATA 资源，并补齐受管内置 `iam_admin` 的已有规则。定制声明及其他角色规则保持原样；同名 DATA 动作或维度不兼容时拒绝覆盖，重复执行不持续提高清单版本。新服务管理权限必须在 AKSK 显式批准，旧 AKP 和旧粗粒度 API 码不自动获得新权限。

归属、角色版本、目录、成员资格与分页不增加缓存，既有 OAuth2 缓存保持原行为。在线治理宿主关闭 AKSK introspect 本地缓存及 fallback（旧结果回退）；这不等于 AKSK Server 自身没有撤权延迟。商业有效期、人员产品资格及失效后的业务拒绝仍由消费方判断，角色收缩不保证把用户的 admitted 准入状态关闭。

### 审计与 Java 8 兼容性

复用 `AdminActionEvent` 及既有动作枚举，不改变 Core 事件类型和方法契约。角色变更的 `detail` 使用 JSON 携带最小元数据：`operation/requestId/openRoleId/applicationId/rootDepartmentId/revision/actorSourceId/actorSubjectType/actorSubjectId`；部门关系变更另带 `departmentId`。操作者取实际可信身份，不包含权限文档、个人展示资料、密码或 Token。

已发布审计监听器 `1.0.0` 可继续消费这些事件，并将 `detail` 交给 `ServerIamAuditHandler`；无需新增监听器版本，也没有自动检测或升级版本机制。事务内变更在提交后消费，回滚、创建重放及无变化写入不产生新的成功变更记录。默认日志不输出 `detail`，持久化或转发由宿主 Handler 实现；不承诺可靠投递、失败重试或恰好一次处理。

Refresh Token 族检查将 Java 11 的 `Optional.isEmpty()` 改为 Java 8 的 `!isPresent()`，保持原有无族记录即失效的语义。

## 新增或扩展测试

- `IamOpenRoleProtocolTest`：强 ETag、UUID 与规范化创建摘要、完整 DATA grant 不跨项拼权。
- `IamOpenRoleProtocolTest`：严格整数类型与 int64 精度边界，不改变全局解析规则。
- `IamOpenRoleContractTest`、`IamOpenRoleHttpTest`：部门关系不提供条件缓存标记；携带 If-None-Match 时，组织迁移与 DATA 收缩仍返回当前内容；非法创建及清单整数不留下角色或规则、不递增版本。
- `IamOpenRoleHttpTest`：真实管理 HTTP 角色目录、分页、部门挂载与用户继承响应的委托摘要一致，保留分页元数据、空页及普通角色 null 边界。
- `IamOpenRoleContractTest`：开放 API 引用及新增端点映射一致性、管理面条件写拒绝状态声明。
- `IamOpenRoleHttpTest`：真实 HTTP 生命周期与无变化写、认证和范围拒绝、并发创建及陈旧版本、迁出关系清理、管理员共同约束、删除墓碑、投影贡献收缩、目录预算与异常结构、增量清单升级，以及两个独立应用实例的共同创建与条件写约束。
- `IamOpenRoleHttpTest.publishedAuditConsumerObservesCommitButNotReplayOrRollback`：使用正式监听器 `1.0.0` 验证提交后消费、实际操作者与最小 detail；重放及事务回滚不产生新变更审计。

HTTP 测试宿主通过测试身份 Provider 提供已验证上下文，证明 IAM 入口后的认证类型、API / DATA 与领域约束；不替代消费方的真实 AKSK 身份交换、在线验证及部署联调验收。

## 向后兼容性

- 身份协议基座 `simple-iam-core:1.1.0`、既有事件族、事件方法和普通角色调用方式不变。
- 新能力使用独立精确 API 码和 DATA 资源；既有组织与人员同步接口不隐式扩权。
- 管理端须识别新增只读委托摘要；只有受委托角色新增条件写和固定范围约束。
- 新增表保留删除墓碑，不能通过删除该表降版或复活旧创建键。
- 不改变平台管理员仅对内置应用生效的特权边界，也不向 IAM 加入产品商业授权业务。

## 升级指南

1. 暂停写请求并完成可恢复备份。目标 InnoDB 必须支持 3072 字节索引预算及 `DYNAMIC` 行格式。
2. 从 `1.3.1` 升级仅执行一次 `docs/migration/V1.3.1__to__V1.3.2__open_role_binding.sql`，该脚本只 CREATE 新表，不修改既有角色和授权数据；`docs/schema.sql` 含 DROP，只供全新库初始化。
3. 将依赖升级为 `io.github.sure-zzzzzz:simple-iam-server-starter:1.3.2`，由其消费正式 Server Core `1.3.2`，不要强制降回旧版 Core。
4. 启动引导开启时自动执行清单增量核对；引导关闭时，先确认内置 `iam` 应用、清单及管理员规则已存在，再由宿主调用 `IamOpenRoleManifestUpgradeService.upgrade()`。该服务不创建缺失的应用或清单；配置调用方前必须核对实际八个新 API 码、五个 DATA 资源和受管管理员规则。
5. 在 AKSK 为服务主体显式配置 `applicationCode=iam` 准入、上述精确 API 与 DATA 范围。IAM 宿主由公共资源层接管 `/iam/api/**`，装配 AKSK 验证 Provider，并关闭 introspect 本地缓存及 fallback。
6. 管理端提交受委托角色的强 If-Match；外部服务回读目标应用、清单、角色、规则、完整目录及成员资格后再开启自动联动。超时或版本冲突先回读并重新计算差异，不能换新版本标记后盲重发旧内容。

数据库清理、恢复或环境替换后，消费方暂停自动联动，重新核对主体、应用、部门根及三权，不按名称或复用数字编号自动接管旧角色。需要回退时停写并恢复完整升级前备份，不能仅删除委托表后继续使用旧版本。
