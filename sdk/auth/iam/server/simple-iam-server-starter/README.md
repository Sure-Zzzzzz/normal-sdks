# simple-iam-server-starter

统一身份认证与授权服务（IAM Server）。一个可独立部署的 Spring Boot 应用模块：承载本地账号体系、浏览器登录会话、OAuth 2.1 / OIDC 授权协议、RBAC、可信应用、Portal 数据、站内信与审计事件，为业务系统提供"一次登录、处处可用"的身份底座。

当前版本为 `1.3.2`，变更与升级要求见 [CHANGELOG.1.3.2.md](CHANGELOG.1.3.2.md)。

`1.3.2` 增加服务主体受委托角色 API：外部系统通过 AKSK 身份维护自己创建的普通角色、固定应用规则和批准根子树内的部门挂载；商业授权、期限和产品资格仍由外部系统管理。既有身份协议、事件类型和普通角色行为保持兼容。

> **1.3.1 要点**：平台管理员特权域收窄至内置应用（模块默认仅 `iam`，部署方可经引导配置追加）——非内置（业务）应用不再走 iam_admin 特权兜底（门户可见性、令牌投影、资源校验一律要求真实授权行）；无清单应用的"仅准入"隐性通道同步收窄；内置应用行为不变。详见 [CHANGELOG.1.3.1.md](CHANGELOG.1.3.1.md)。

> **1.3.0 要点**：对外身份口径全面切换为稳定公开主体 `subjectId`（管理面/开放 API/URI/claim/事件载荷，数字 userId 退出对外）；新增手机号绑定与短信登录、忘记密码、Excel 用户导入、B2M 短信投递适配器；可信应用内置标记管理面可调。升级与破坏性说明见 [CHANGELOG.1.3.0.md](CHANGELOG.1.3.0.md)。

本模块**不提供浏览器页面**：登录页、授权确认页、Portal 和管理界面统一由 Vue Web 承载，IAM Server 只提供 OAuth2/OIDC 协议端点和 JSON API。

## 契约链路

| 段 | 角色 | 说明 |
|---|---|---|
| 1 | **IAM Server（本模块）** | 身份提供方（IdP）：用户库、登录会话、授权码、Token 签发与验证 |
| 2 | **Vue Web** | 登录页 / Consent 页 / Portal / 管理界面的渲染方；服务端供数，无独立后端 |
| 3 | **业务系统** | 持 Token 访问的资源应用；本地验签或调本服务的验证端点 |

模块分层：`simple-iam-core`（身份协议契约基座）→ `simple-iam-server-core`（IAM Server 域契约：事件、错误码、常量、异常）→ **`simple-iam-server-starter`（本模块：Web API、服务、装配、实体与仓储）**。只需订阅 IAM 事件或引用错误码契约的集成方，可以只依赖 `simple-iam-server-core`。

权威 API Contract 位于 `sdk/auth/iam/server/contract/`；各前端仓库的 release 必须声明兼容的 Server 与 Contract 版本范围。

## 特性总览

### 受委托角色接入

适用于外部服务自动维护某个产品应用的部门角色。受委托角色仍是普通 IAM 角色，但管理权固定授予创建它的服务主体，并限定在一个非内置应用和一个部门根内；IAM 不存放商业授权、有效期或机器绑定规则，也不直接判断产品资格。

调用方使用可信 AKSK `SERVICE` 主体（推荐 AKP），由宿主在公共资源链接管 `/iam/api/**` 并在线回源验证。管理员在 AKSK 配置该主体对 `applicationCode=iam` 的准入、下表 API 及完整 DATA 授权；升级不会自动给旧 AKP 新权限。新端点不接收管理员 Cookie、IAM 登录令牌或 HUMAN 主体。

| API 码 | DATA resource / actions / dimensions |
| --- | --- |
| `iam:open-role:create:api`、`iam:open-role:read:api` | `iam:open-role` / `create,read` / `applicationId,rootDepartmentId` |
| `iam:open-role-rule:read:api`、`iam:open-role-rule:write:api` | `iam:open-role-rule` / `read,write` / `applicationId,rootDepartmentId,openRoleId` |
| `iam:open-department-role:read:api`、`iam:open-department-role:write:api` | `iam:open-department-role` / `read,write` / `applicationId,rootDepartmentId,openRoleId,departmentId` |
| `iam:open-directory:read:api` | `iam:open-directory` / `read` / `rootDepartmentId` |
| `iam:open-application:read:api` | `iam:open-application` / `read` / `applicationId` |

按批准应用和根配置 `IN` 条件。同一 grant 内所有条件同时满足，不同 grant 为或；未指定合法维度表示该项不额外约束，`all=true` 也不绕过所有权、非内置应用或固定范围。规则写是批准范围内的权限治理权，不会自动限制为调用方运行权限的子集。这里的 DATA 授权决定服务可以管理哪些 IAM 对象；规则中的 `dataGrantTemplate` 则是目标产品应用授予用户的数据权限，两者不能互相替代。

1. GET `/iam/api/target-applications/{applicationId}` 与 `/permission-manifest` 核对非内置目标及当前清单；GET `/iam/api/organization-directories/{rootDepartmentId}/departments` 获取完整根子树。目录最大 4096 节点、64 层，超过预算 422，不返回截断成功。
2. POST `/iam/api/roles` 提交 `externalId` UUID、正数 JSON 整数 `applicationId/rootDepartmentId`、1 至 128 字符 `name`、最大 255 字符 `description`。服务生成 `openRoleId` 和角色 code，固定主体、应用和根；首次 201，同键同内容回读当前角色 200，不重置规则。不同内容或删除墓碑 409。
3. GET `/iam/api/roles`（可按应用、根或 externalId 分页）或 `/{openRoleId}` 回读事实；详情提供强 ETag（HTTP 版本标记），例如 `"open-role:20000000-0000-4000-8000-000000000001:2"`。
4. PUT `/iam/api/roles/{openRoleId}/authorization-rules/{applicationId}` 携带 If-Match 及 `manifestVersion/manifestDigest/pagePermissions/apiPermissions/dataGrantTemplate`。DELETE 同地址清规则。数组必传，可为空；DATA null 表示无数据授权。
5. PUT/DELETE `/iam/api/departments/{departmentId}/roles/{openRoleId}` 携带 If-Match 操作单条关系（PUT 空请求体）；GET `/iam/api/roles/{openRoleId}/departments` 查询关系和同事务 revision，响应为 `Cache-Control: no-store`、不提供 ETag，每次返回当前完整分页，不按 If-None-Match 返回 304。写入版本标记从角色详情读取。成员仅继承直属部门角色，整棵子树需要逐部门挂载。移出或消失的部门仍可清理本角色残留关系。
6. GET `/iam/api/organization-directories/{rootDepartmentId}/members/{subjectId}` 查询最小成员事实。不存在或不在批准根内统一 404，应视为没有资格证据。

创建编号及 `manifestVersion` 只接受正数 JSON 整数，范围为有符号 64 位整数；小数、指数形式、数字字符串、布尔值、null 和溢出均返回 400，不做截断或类型转换，也不改变宿主其他接口的 JSON 解析规则。

修改缺 If-Match 返回 428，旧版本 412，非法标记 400；不接受通配符、弱标记或列表。清单变化返回 409。发生超时先回读效果并重新计算差异，不能换新 ETag 后盲重发旧内容。无实际变化不递增 revision、不重复投影或发变更事件。角色版本用于防止并发写覆盖，不代表组织或调用方 DATA 授权的版本；部门关系需要读取当前完整响应，不能仅凭角色版本认定组织范围未变化。新 JSON 请求预算 1 MiB；拒绝以 HTTP 状态表达，错误体只有安全 `message/timestamp/requestId`。

管理角色详情、总列表、分页、部门角色及用户有效角色均包含只读 `openRoleBinding`（UUID、固定应用/根、revision、state），普通角色为 null。列表用于识别委托边界，修改前读取当前角色详情取得版本。管理台修改这类角色也必须携带同一强 If-Match，并遵守固定范围；禁止个人直授、传统全局权限及跨应用规则。删除由管理台完成并保留委托墓碑，机器不提供角色硬删除。应用/根仍有 ACTIVE 委托引用时删除返回 409；有 ACTIVE 引用的目标应用也不能改为内置应用。

归属、revision、目录、成员资格及分页不使用缓存。既有 OAuth2 缓存不改变；在线治理宿主关闭 AKSK introspect 本地缓存及 fallback，资源端不读缓存不代表 AKSK Server 本身零撤权延迟。角色收缩不保证关闭用户 admitted，产品资格和商业有效期仍由消费方实时判断。数据库清理、恢复或环境替换后，消费方须暂停自动联动，重新核对主体、应用、根及三权，不能依据数字编号重绑。

### 受委托角色审计

本版复用 `AdminActionEvent` 与既有动作类型，不新增事件族。角色创建、改名、规则替换或清除、部门挂载或撤销、管理员删除复用已有审计发布入口。`detail` 为 JSON，按操作携带 `operation/requestId/openRoleId/applicationId/rootDepartmentId/revision/actorSourceId/actorSubjectType/actorSubjectId`，部门关系变化另带 `departmentId`；不含角色展示资料、完整权限文档或认证材料。操作者来自已验证服务身份或管理员会话，不以角色所有者冒充实际操作者。

宿主需要消费审计时，显式引入 `simple-iam-server-audit-listener-starter:1.0.0`。该已发布监听器可继续接收事件并把 `detail` 交给 `ServerIamAuditHandler`，无需为本版升级监听器；它不是版本检测或自动升级机制。事务内的变更在提交后消费，回滚、创建重放及无变化写入不产生新的成功变更审计。

默认日志不输出 `detail`，因此默认日志不等于完整审计留存。需要落库或转发时，由宿主提供 Handler；提交后消费为尽力执行，不承诺可靠投递、失败重试或恰好一次处理。

### 身份认证

- **本地账号密码登录**：委托编码器（BCrypt 优先）校验；登录失败计数、渐进人机验证、账号锁定
- **自助修改密码**：`PUT /iam/web/auth/password`（会话 + CSRF）；错旧密码计入凭据失败计数（与登录同锁定策略）；改密成功踢其他终端、保留当前会话，旧密码签发的全部 OAuth token 与 refresh 族同步失效
- **首登 / 重置后强制改密**：管理员建号与重置密码置 `must_change_password` 标记，登录响应携带 `mustChangePassword`；标记未清期间 Web / 管理链非白名单端点一律 403 + `AUTH_009`（改密、登出等白名单放行）；外部身份源用户改密 / 重置均拒绝（`AUTH_010`，防置标记后锁死）
- **渐进人机验证**：失败达到阈值后要求验证码；验证码组件缺失时自动降级放行（不阻断登录）；验证码校验先于凭据校验，验证码失败不计入密码失败次数
- **外部身份源登录**：凭证型（LDAP bind）与跳转型（OIDC 单点登录）两种形态，经 SPI 接入，本模块不含具体协议实现（适配器为独立模块）
- **外部身份归一**：已绑定身份直接复用；同名本地账号绝不自动并号（防接管）；未绑定身份按配置开号（JIT）或拒绝（pre-bound-only）
- **IAM 会话**：滑动空闲超时 + 绝对上限双时钟；续期节流（空闲用户零写库）；Redis 存储，多实例互认；支持单端登出与用户级全端吊销
- **部署级管理员恢复**：恢复码在启动期消费，永不进入 HTTP 边界

### 授权协议

- **OAuth 2.1 授权码 + PKCE**：公共客户端强制 S256；授权码一次性消费
- **OIDC**：ID Token、userinfo 按 scope 暴露身份字段
- **Token 格式双模式**：`jwt`（JWS RS256，默认）或 `jwe`（Access Token = `JWE(JWS(payload))`，ID Token 仍为标准 JWS）
- **claims 注入**：`sub`（稳定公开主体 `subjectId`）、`sid`（IAM 会话）、`auth_time`；Access Token 附带 `roles` / `permissions` 与应用授权投影
- **Consent 授权确认**：应用可要求用户确认授权范围，确认结果落库为投影
- **Refresh Token 主动防线**：客户端显式配置 `refresh_token` 授权类型时按标准流程签发（默认不配置即不签发）；全局一次性使用（`reuseRefreshTokens=false`）——refresh grant 必轮换签发新值；旧值二次使用即重放：整族吊销（当前 token 同步失效，失败关闭）+ 发布 `RefreshTokenReuseDetectedEvent`；登出 / 禁用 / 删除 / 改密联动族失效；TTL 取 `token.access-expires-in` / `token.refresh-expires-in`（默认 30 分钟 / 10 小时）
- **资源端 Token 验证端点**：供无法本地验签的资源系统远程校验，验证客户端以 Basic 独立认证
- **平台管理员特权（1.3.1 起特权域=内置应用）**：挂内置 `iam_admin` 的用户在授权解析时对**内置应用**特权合并（admitted + 清单申报全量，含 DATA all=true），不落库、挂 / 摘角色即时生效、摘除即降权；**非内置（业务）应用不走特权**——访问须有真实授权行（显式授予或业务自宣告角色投影）（机制见 [权限与授权投影](docs/领域文档/权限与授权投影.md)）
- **开放 API**：`/iam/api/**` 供 AKSK 凭证做组织与人员同步（码 + DATA 双闸，公共资源层链接管，默认失败关闭）

### 管理面

- 用户 / 部门 / 协作组 / 角色 / 权限 CRUD 与关系分配（含外部身份预绑定）
- 最后管理员保护（最后一个可用管理员不可删除 / 禁用 / 撤销角色）、内置角色与权限保护
- 可信应用管理：OAuth 客户端（原始 secret 仅创建时返回一次，后续查询仅 `secretPresent`）、Portal 集成、应用菜单树与默认入口；PAGE 可选择标准布局或隐藏 Portal 顶栏和侧栏的沉浸展示，平台管理员可指定无深链登录首页；可信应用可标记为平台内置（管理面可调，禁删除/禁停用，下线走关闭门户集成，判定口径 = `built_in` 列优先、引导配置清单兜底）
- 用户应用授权管理（决定 Access Token 中的应用授权投影内容）
- 角色应用授权规则：角色 × 应用的权限集合定义，配合应用权限清单与授权投影联动（见 [权限与授权投影](docs/领域文档/权限与授权投影.md)）
- 资源验证客户端管理（独立于 OAuth 客户端的验证凭证，支持密钥轮换）
- 站内信：管理端发送、Web 端读取与 SSE 实时推送
- 管理台品牌（名称 / Logo / CSS / JS）与用户级主题偏好

### 运行时能力

- 审计事件四族（Token / 认证 / 会话 / 管理面）统一发布，监听器 SPI 落地；refresh token 重放事件独立可订阅
- 过期 token 定时清理：refresh 族表 + 授权表三路（refresh / access / code）、分批删除、多实例分布式锁互斥（默认每天凌晨 2 点，`cleanup.*` 配置组）
- 匿名端点 IP 限流消除会话膨胀放大，429 出口统一
- 七条安全过滤链按路径精确分工
- spring-session Redis 会话、SSE 跨实例 Pub/Sub 广播
- 启动引导：内置角色 / 权限 / 管理员账号幂等初始化
- 安全依赖守门：Redis / 缓存 / 限流 / 锁四个配套组件缺失时按配置启动失败

## 依赖与运行基线

在 Spring Boot 应用中引入依赖：

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:simple-iam-server-starter:1.3.2"
    implementation "org.springframework.boot:spring-boot-starter-web"
    implementation "org.springframework.boot:spring-boot-starter-security"
    implementation "org.springframework.boot:spring-boot-starter-data-jpa"
}
```

- 固定运行时基线为 Spring Boot **2.7.9**、Spring Authorization Server **0.4.1**、Java **8**；不支持 Spring Boot 3.x，不声明其他 Spring Boot 版本的 Server 运行时兼容性
- Starter 通过 `api` 传递正式制品 `simple-iam-server-core:1.3.2`，身份协议基座仍为 `simple-iam-core:1.1.0`；不要将 Server Core 强制降回旧版
- 独立 MySQL 数据库（`mysql-connector-java` 已随模块 runtimeOnly 引入），字符集 `utf8mb4`
- Redis 6+（会话 / 缓存 / 限流 / 锁 / SSE 广播 / OAuth2 授权缓存共用）
- RSA 公私钥对；JWE 模式另需 AES-256 加密密钥
- 启动引导管理员密码（见 [配置与多实例部署](docs/领域文档/配置与多实例部署.md) 的 `bootstrap` 配置组）

数据库初始化：在**全新环境**执行 [schema.sql](docs/schema.sql)。该脚本包含建表前置清理，不能直接用于已有数据环境。

### 从 1.3.1 升级

1. 暂停写请求并完成可恢复备份，确认目标 InnoDB 支持 3072 字节索引预算及 `DYNAMIC` 行格式。
2. 在目标库执行一次 [V1.3.1__to__V1.3.2__open_role_binding.sql](docs/migration/V1.3.1__to__V1.3.2__open_role_binding.sql)。脚本仅创建 `iam_open_role_binding`，不修改旧角色或授权，不按名称接管人工角色；不是可反复执行的初始化脚本。已有数据环境不得执行 `schema.sql`。
3. 将 Starter 升至 `1.3.2`，由其消费 Server Core `1.3.2`。开启启动引导时，增量合并 IAM 应用清单中的八个新 API 码和五个 DATA 资源，并补齐受管内置 `iam_admin` 的已有规则；数据库锁协调多实例，保留定制声明，重复核对不持续递增版本。同名 DATA 动作或维度不兼容时启动失败，不覆盖。
4. 引导关闭时，先确认内置 `iam` 应用、权限清单和管理员规则已存在，再由宿主显式调用 `IamOpenRoleManifestUpgradeService.upgrade()`；该服务不会创建缺失的应用或清单。无论采用哪种方式，都须核对实际清单及规则后再配置调用方。
5. 在 AKSK 管理侧显式授予服务主体准入、精确 API 与 DATA 范围。原 `iam:user:api`、`iam:department:api` 不包含新管理权，旧 AKP 不自动扩权。受委托角色治理宿主关闭 introspect 本地缓存和 fallback（旧结果回退）。AKSK 验证组件默认自动装配且三件套缺一即启动失败；暂不接管的宿主须显式 `io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled: false`。
6. 管理端消费 `openRoleBinding`，对受委托角色改名、改规则、挂载或删除提交强 If-Match；普通角色仍按原方式调用。消费方回读角色、规则、目录和成员事实后再开启自动联动。

角色删除保留 `DELETED` 墓碑，防止同一创建键重新生成授权；不能通过删除委托表进行降版。需要回退时，停写并恢复完整升级前备份。数据库清理、恢复或环境替换后，消费方重新确认身份、目标应用、部门根和三权，不凭复用的数字编号自动续接旧状态。

### 数据表清单（31 张 = 3 张 SAS 标准表 + 28 张 `iam_*` 业务表）

| 表 | 用途 |
|---|---|
| `oauth2_registered_client` | SAS 标准：OAuth 客户端注册 |
| `oauth2_authorization` | SAS 标准：授权码 / token 状态 |
| `oauth2_authorization_consent` | SAS 标准：scope 确认记录 |
| `iam_user` | 用户（含 `identity_source` / `external_id` 外部身份绑定、`must_change_password` 须改密标记） |
| `iam_role` / `iam_permission` | 角色 / 权限（内置项受保护） |
| `iam_open_role_binding` | 服务主体的角色委托边界：稳定角色 UUID、归属三元组、创建幂等键、固定应用与部门根、修改版本和删除墓碑；不是商业授权表 |
| `iam_user_role` / `iam_role_permission` | 用户-角色、角色-权限关系 |
| `iam_department` / `iam_department_role` / `iam_user_group` / `iam_user_group_member` | 部门、部门-角色、协作组及成员 |
| `iam_session` | IAM 会话（双时钟、状态） |
| `iam_refresh_token_family` | Refresh Token 族（轮换链条、重放检测、族吊销、过期清理） |
| `iam_password_reset` | 密码重置凭证（配套预留） |
| `iam_authorize_context` | 授权交易状态机（state / PKCE 哈希、会话绑定） |
| `iam_consent` | Consent 投影 |
| `iam_trusted_application` | 可信应用 |
| `iam_trusted_application_cleanup_operation` | 可信应用异步删除操作、租约、清理进度及失败状态 |
| `iam_application_permission_manifest` | 可信应用权限清单（申报制，投影与规则的码空间事实源） |
| `iam_role_authorization_rule` | 角色-应用授权规则（投影的计算源） |
| `iam_application_authorization` | 用户-应用授权投影 |
| `iam_application_authorization_state` | 应用授权纪元、所属人授权继承状态及投影重算屏障 |
| `iam_owner_authorization_change_log` | 所属人授权变更顺序及最终状态快照，供协作方增量读取 |
| `iam_resource_verification_client` | 资源验证客户端 |
| `iam_trusted_application_portal` / `iam_trusted_application_menu` / `iam_portal_setting` | Portal 集成、应用菜单与全局登录首页单例 |
| `iam_message` | 站内信 |
| `iam_user_theme_preference` | 用户主题偏好 |

## HTTP 端点总览

### 授权协议端点（SAS 原生，Order 1 链）

| 端点 | 说明 |
|---|---|
| `GET /oauth2/authorize` | 授权端点（未认证 302 跳 `/login?redirect=…`；Consent 确认也提交到本端点） |
| `POST /oauth2/token` | 令牌端点（机密客户端 Basic 认证 + PKCE `code_verifier`） |
| `GET /oauth2/jwks` | JWKS 公钥集 |
| `GET /userinfo` | OIDC userinfo（按 scope 返回身份字段） |
| `GET /.well-known/openid-configuration` | OIDC discovery |

### Web 端点（`/iam/web/**`）

| 端点 | 方法 | 认证 | 说明 |
|---|---|---|---|
| `/iam/web/auth/csrf` | GET | 匿名 | 取 CSRF token（登录页用） |
| `/iam/web/auth/providers` | GET | 匿名 | 登录方式列表（含展示文案） |
| `/iam/web/auth/captcha` | GET | 匿名 | 验证码挑战 |
| `/iam/web/auth/login` | POST | 匿名 | 登录（本地或外部凭证型）；响应携带 `mustChangePassword`——管理员建号 / 重置后首登为 `true` |
| `/iam/web/auth/authorize/{providerCode}` | GET | 匿名 | 外部跳转型登录发起（返回上游跳转地址，由前端跳转） |
| `/iam/web/auth/callback/{providerCode}` | GET | 匿名 | 外部跳转型登录回调 |
| `/iam/web/auth/logout` | POST | 已认证 | 登出 |
| `/iam/web/auth/me` | GET | 已认证 | 当前用户信息 |
| `/iam/web/auth/password` | PUT | 已认证 | 自助修改密码（body `oldPassword` / `newPassword`；成功 204，踢其他终端保留当前会话；须改密拦截期在白名单内放行） |
| `/iam/web/oauth2/consent-info` | GET | 已认证 | Consent 页供数（state 换授权请求详情；授权码流程中用户已登录） |
| `/iam/web/portal/accessible-applications` | GET | 已认证 | 当前用户可访问且已启用 Portal 集成的应用及菜单；平台管理员直通**内置**应用（1.3.1 起非内置应用同样要求授权行） |
| `/iam/web/portal/navigation-context` | GET | 已认证 | 当前用户的可访问应用、应用默认入口与无深链登录首页候选；Portal 仅在根路由时使用 |
| `/iam/web/branding` | GET | 匿名 | 品牌配置 |
| `/iam/web/messages` | GET | 已认证 | 站内信列表 |
| `/iam/web/messages/page` | GET | 已认证 | 站内信分页 |
| `/iam/web/messages/unread-count` | GET | 已认证 | 未读数 |
| `/iam/web/messages/read-all` | PUT | 已认证 | 全部已读 |
| `/iam/web/messages/{messageId}/read` | PUT | 已认证 | 单条已读 |
| `/iam/web/messages/events` | GET (SSE) | 已认证 | 站内信实时推送流 |
| `/iam/web/theme-preference` | GET / PUT | 已认证 | 用户主题偏好 |

### 管理端点（`/iam/admin/**`，进门为 `ROLE_iam_admin` 或任一页面权限码）

仪表盘与会话：

| 端点 | 说明 |
|---|---|
| `GET /iam/admin/dashboard` | 仪表盘聚合（用户 / 部门 / 协作组 / 角色 / 权限 / 可信应用计数 + 在期会话 / 今日登录人数 / 锁定 / 禁用 / 无部门用户运行态，`iam:dashboard:api`） |
| `GET /iam/admin/dashboard/recent-logins` | 最近登录记录分页（用户 + 部门 + 时间） |
| `GET /iam/admin/sessions` | 在期会话分页（可按 `subjectId` 过滤；会话 / 用户 / 客户端 / IP / UA / 认证与最后活跃时间，`iam:session:api`） |
| `PUT /iam/admin/sessions/users/{subjectId}/revoke` | 强制下线：吊销该用户全部会话（等价用户级全端吊销，返回吊销数，发 `REVOKED` 审计） |

组织与用户：

| 端点 | 说明 |
|---|---|
| `GET /iam/admin/organizations/tree` | 组织树（部门 + 协作组） |
| `GET /iam/admin/organizations/departments/{id}/workspace` | 部门工作台视图 |
| `GET /iam/admin/organizations/users/{id}/profile` | 用户组织画像（有效角色与权限各带 `source` 来源标记：`direct` 个人直接 / `department_inherited` 部门继承） |
| `GET / POST /iam/admin/users`、`GET / PUT / DELETE /iam/admin/users/{subjectId}` | 用户 CRUD 与分页（分页过滤：status / departmentId / keyword / lastLoginAfter / lockedUntilAfter / noDepartment——未挂部门筛选，仪表盘下钻用；删除级联清理角色绑定、组成员与应用授权投影） |
| `PUT /iam/admin/users/{subjectId}/enable` / `disable` | 启用 / 禁用（禁用即全端吊销） |
| `PUT /iam/admin/users/{subjectId}/unlock` | 手动解锁（清除登录失败锁定与失败计数，不等自动过期） |
| `PUT /iam/admin/users/{subjectId}/reset-password` | 管理员重置密码（即全端吊销） |
| `POST / DELETE /iam/admin/users/{subjectId}/external-identity` | 外部身份预绑定 / 解绑 |
| `GET /iam/admin/users/{subjectId}/roles`、`POST / DELETE /iam/admin/users/{subjectId}/roles/{roleId}` | 用户角色查询与分配 |

部门与协作组：

| 端点 | 说明 |
|---|---|
| `GET / POST /iam/admin/departments`、`GET / PUT / DELETE /iam/admin/departments/{id}`、`GET /iam/admin/departments/page` | 部门 CRUD 与分页 |
| `GET /iam/admin/departments/{departmentId}/roles`、`POST / DELETE /iam/admin/departments/{departmentId}/roles/{roleId}` | 部门挂载角色查询与分配——部门下全体成员自动继承（个人直接角色之外的并集，转部门实时生效）；撤销 `iam_admin` 命中部门级最后管理员保护时 409 |
| `GET / POST /iam/admin/user-groups`、`GET / PUT / DELETE /iam/admin/user-groups/{id}`、`GET /iam/admin/user-groups/page` | 协作组 CRUD 与分页 |
| `GET / POST / DELETE /iam/admin/user-groups/{groupId}/users/{subjectId}`、`GET /iam/admin/user-groups/{groupId}/users` | 协作组成员管理 |

角色与权限（内置项受保护不可改删）：

| 端点 | 说明 |
|---|---|
| `GET / POST /iam/admin/roles`、`GET / PUT / DELETE /iam/admin/roles/{roleId}`、`GET /iam/admin/roles/page` | 角色 CRUD 与分页（删除级联清理角色-权限关系、成员绑定与授权规则） |
| `GET / POST / DELETE /iam/admin/roles/{roleId}/permissions/{permissionId}`、`GET /iam/admin/roles/{roleId}/permissions` | 角色-权限关系 |
| `GET /iam/admin/roles/{roleId}/departments` | 反查挂载了该角色的部门（成员继承来源，只读展示，不含子部门递归） |
| `GET /iam/admin/roles/{roleId}/users/page` | 角色成员分页（个人直挂成员，不含部门继承） |
| `GET / PUT / DELETE /iam/admin/roles/{roleId}/authorization-rules/{applicationId}` | 角色应用授权规则（PUT 固定地址 upsert 返 200，无规则 GET 返 404，变更触发投影重算） |
| `GET /iam/admin/permissions`、`GET /iam/admin/permissions/{permissionId}`、`GET /iam/admin/permissions/page` | 权限列表 / 详情 / 分页（只读——权限码为系统内置 seed，由安全链与 `@PreAuthorize` 在代码中消费，运行期不提供增删改） |

可信应用（应用 + OAuth 客户端）：

| 端点 | 说明 |
|---|---|
| `GET / POST /iam/admin/trusted-applications`、`GET / PUT / DELETE /iam/admin/trusted-applications/{id}`、`GET /iam/admin/trusted-applications/page` | 可信应用 CRUD 与分页（创建必须带初始客户端；`builtIn` 可在创建与更新时标记，内置应用禁删除/禁停用，其 OAuth 客户端强制免授权确认；引导配置清单内的编码不可摘除内置标记） |
| `GET / POST /iam/admin/trusted-applications/{id}/clients`、`GET / PUT / DELETE …/clients/{clientId}` | OAuth 客户端管理（原始 secret 仅创建时返回一次） |
| `PUT /iam/admin/trusted-applications/{id}/portal/configuration` | 原子更新 Portal 集成、菜单树与默认入口（`configVersion` 乐观锁） |
| `GET / PUT /iam/admin/portal/login-landing` | 查询或更新无深链登录首页单例（仅 `iam_admin`） |

用户应用授权与验证客户端：

| 端点 | 说明 |
|---|---|
| `GET / PUT / DELETE /iam/admin/users/{subjectId}/application-authorizations/{applicationId}`、`GET /iam/admin/users/{subjectId}/application-authorizations` | 用户应用授权（投影进 Access Token）；摘要与详情按用户是否平台管理员下发 `platformAdmin` 标记（纯展示字段；特权不受单应用撤销影响——1.3.1 起该语义仅对内置应用成立） |
| `GET / POST /iam/admin/trusted-applications/{id}/resource-verification-clients`、`GET / DELETE …/{clientId}`、`POST …/{clientId}/secret` | 验证客户端管理（含密钥轮换） |

站内信：

| 端点 | 说明 |
|---|---|
| `POST /iam/admin/messages` | 管理端发送站内信 |
| `GET /iam/admin/messages/page` | 发送批次分页（标题 / 发送人 / 目标构成 / 收件与已读计数） |
| `GET /iam/admin/messages/{sendBatchId}` | 批次详情（含正文） |
| `GET /iam/admin/messages/{sendBatchId}/recipients` | 批次收件人分页（含已读时间） |

管理面通用约定：创建主资源返回 `201 Created`，删除返回 `204 No Content`；认证、授权和业务错误均以标准 HTTP 状态表达，不依赖自定义业务状态码字段。

### 开放 API 端点（`/iam/api/**`，公共资源层链）

主体为 AKSK 凭证（外部业务系统组织与人员同步、受委托角色治理），Bearer 鉴权 + 端点级 API 码 + 适用的数据权限检查。新受委托角色端点额外要求来源为 AKSK 的 `SERVICE` 主体并完整评估 DATA；既有用户、部门接口保持原行为。接入契约与 AKSK 侧三权配置见 [开放 API 契约](../contract/openapi/simple-iam-open-api.openapi.yaml)。

用户族（API 码 `iam:user:api`，DATA resource=`iam:user`——读=部门范围与请求条件求交、写=目标部门完整落在授权范围，失败关闭 403）：

| 端点 | 说明 |
|---|---|
| `GET /iam/api/users`、`GET /iam/api/users/{subjectId}` | 用户分页 / 详情（DATA 部门范围求交，越权数据不出库） |
| `GET /iam/api/users/{subjectId}/roles` | 用户角色列表 |
| `POST /iam/api/users`、`PUT / DELETE /iam/api/users/{subjectId}` | 用户创建 / 更新 / 删除（目标部门须在 DATA 范围内） |
| `PUT /iam/api/users/{subjectId}/enable` / `disable` / `reset-password` | 启用 / 禁用（全端吊销）/ 重置密码（审计 operator 为 AKSK 主体标识） |
| `POST / DELETE /iam/api/users/{subjectId}/roles/{roleId}` | 角色绑定 / 解绑 |

部门族（API 码 `iam:department:api`，第一版纯码控不评估 DATA）：

| 端点 | 说明 |
|---|---|
| `GET /iam/api/departments` | 全量平铺列表（含 `fullPath`，外部自行重建树） |
| `GET / POST /iam/api/departments`、`GET / PUT / DELETE /iam/api/departments/{departmentId}` | 部门详情 / CRUD（删除走既有级联规则） |

受委托角色与只读事实（八个精确 API 码、五个 DATA 资源，权限表见上文「受委托角色接入」）：

| 端点 | 说明 |
|---|---|
| `POST /iam/api/roles` | 创建自有受委托角色；首次 201，同键同内容重放 200，均返回 Location 与 ETag |
| `GET /iam/api/roles`、`GET /iam/api/roles/{openRoleId}` | 自有且在 DATA 范围内的角色分页 / 详情；详情返回 ETag |
| `GET / PUT / DELETE /iam/api/roles/{openRoleId}/authorization-rules/{applicationId}` | 固定应用规则读取 / 整体替换 / 清除；PUT 返回 200，DELETE 返回 204，写操作须带 If-Match |
| `GET /iam/api/roles/{openRoleId}/departments` | 当前批准范围内的角色-部门关系分页及角色版本 |
| `PUT / DELETE /iam/api/departments/{departmentId}/roles/{openRoleId}` | 单条部门挂载 / 撤销，返回 204；须带 If-Match，PUT 请求体必须为空 |
| `GET /iam/api/organization-directories/{rootDepartmentId}/departments` | 完整部门根子树，超过节点 / 深度预算返回 422，环或根的悬空父链等结构冲突返回 409，不返回截断成功 |
| `GET /iam/api/organization-directories/{rootDepartmentId}/members/{subjectId}` | 最小成员资格事实；不存在或不在根内统一 404 |
| `GET /iam/api/target-applications/{applicationId}`、`GET /iam/api/target-applications/{applicationId}/permission-manifest` | 批准的非内置应用事实及当前权限清单版本 / 摘要 |

服务主体没有角色删除、改名、个人直授或传统全局权限管理入口。管理员仍在管理面处理受委托角色，但必须遵守同一固定范围和条件版本，不能借旧接口跨应用授权。

### 资源验证端点（Order 2 链，验证客户端 Basic）

| 端点 | 说明 |
|---|---|
| `POST /iam/resource/tokens/verify` | 远程 token 验证：请求体携带 token，校验通过返回 `sub` 与 `iam_authorization` 投影 |

### 与 AKSK 的接入口径

AKSK 管理台作为统一应用门户中的业务应用时，IAM 侧登记一个可信应用，并分别维护两类客户端：

- **应用登录客户端（PKCE）**：只负责浏览器登录和授权码交换；
- **资源校验客户端**：只负责 AKSK Server 回源调用 `/iam/resource/tokens/verify`，验证 IAM 人员 Token。

资源校验客户端不是 AKU，也不能创建或替代 AK/SK。AKU、AKSK Token、应用授权和业务数据权限仍由 AKSK Server 管理。AKU 所属人授权继承还使用 IAM 与 AKSK 之间固定的内部 reader SERVICE，不能通过浏览器、普通 OAuth2 客户端或资源校验 Basic 客户端代替。

因此，PKCE 解决“用户如何登录”，资源校验客户端解决“资源服务如何验证用户 Token”，授权投影解决“AKSK 如何得到人员当前三权结论”。三者职责不同，不能互相替代。

## 安全过滤链

七条链按 Order 精确分工，先匹配先赢；开放 API `/iam/api/**` 由公共资源层链（`HIGHEST_PRECEDENCE`，simple-resource-server-starter）在其前接管——宿主配置 `protected-paths` 覆盖该路径后启用（STATELESS、排斥 Cookie、无 CSRF、失败统一 401/403），未配置时该路径落 Order(5) 会话链拒绝（失败关闭，启动期软提示），矛盾配置 fail-fast：

| Order | 路径 | 认证方式 | 要点 |
|---|---|---|---|
| 0 | `/error` | permitAll | 容器错误分派放行给 BasicErrorController——安全链上 `sendError`（如开放 API 的 401/403）会以 ERROR dispatch 再过过滤链，若落 Order(6) denyAll 会被覆盖成 403 空 body |
| 1 | `/oauth2/**`、`/userinfo`、`/.well-known/**` 等授权端点 | 授权服务器自身 | OIDC userinfo mapper；PKCE 公共客户端强制 S256；未认证 302 `/login?redirect=`；CSRF 用 HttpSession 仓库 |
| 2 | `/iam/resource/**` | 验证客户端 Basic | `NullSecurityContextRepository`（完全无状态，不碰会话） |
| 3 | `/iam/web/**` | 匿名七端点 permitAll + 其余已认证 | csrf / login / providers / captcha / authorize / callback / branding 匿名放行 |
| 4 | `/iam/admin/**` | `hasAnyAuthority(ROLE_iam_admin 或任一页面权限码)` | 管理台入口门（页面权限用户可进）；端点级仍由 `@PreAuthorize` 逐个强制 |
| 5 | `/iam/**` | 已认证 | IAM 其余路径兜底（未配置开放 API 链时 `/iam/api/**` 在此被拒） |
| 6 | 其余全部 | `denyAll` | 显式拒绝 |

密码编码为委托编码器（BCrypt 优先，兼容历史格式）；Servlet 容器会话超时由 `iamSessionTimeoutInitializer` 对齐配置。

## 错误处理约定

- **Web 匿名端点**：业务错误统一 401 JSON；登录失败响应携带 `captchaRequired=true` 提示前端补验证码；限流触发 429 并携带 `Retry-After`（统一由 IAM 异常处理出口返回）
- **须改密拦截与改密**：标记未清期间 Web / 管理链非白名单端点 403 JSON 携带 `code=AUTH_009`（前端据此引导改密）；改密端点错旧密码 401（计入锁定计数）、新密码违反策略 400 `AUTH_011`、外部身份源用户改密 / 被重置 400 `AUTH_010`
- **外部登录回调**：失败 302 回登录页并附 `error` 查询参数（错误码默认透传前端引导），仅账号锁定降级为 `login-failed`（锁定事实不向未认证方泄露，防回调爆破探测）；上游不可用的 503、登录方式不存在的 404 只发生在凭证型 login 端点（见 [登录认证与会话](docs/领域文档/登录认证与会话.md)）
- **管理面**：创建主资源 201、删除 204；认证、授权和业务错误以标准 HTTP 状态表达；最后管理员保护返回 `409 Conflict`；内置 `iam_admin` 角色不可删除、编码不可变更
- **资源验证端点**：校验不过返回带错误码的 401/403，验证客户端认证失败为 401
- 日志不落 token 原文、密码、恢复码

## 开放 API 部署（AKSK 接入）

开放 API `/iam/api/**` 默认失败关闭（未配置即落会话链拒绝）。接入共六步，分布在两侧，联调前逐项核对（完整版含各步语义见[开放 API 契约](../contract/openapi/simple-iam-open-api.openapi.yaml)的「接入检查清单」）：

| # | 配置点 | 归属侧 | 漏配症状 |
|---|---|---|---|
| 1 | 创建 AKSK 客户端（AKP / AKU） | AKSK | 换不出 token |
| 2 | 应用授权 admitted=true（applicationCode=iam） | AKSK | 换不出 token |
| 3 | API 码 `iam:user:api` / `iam:department:api` | AKSK | 403（未勾的族整族拒绝） |
| 4 | DATA grants `iam:user`（all 或 departmentId 受限） | AKSK | 403（失败关闭） |
| 5 | 资源层 protected-paths 覆盖 `/iam/api/**` | IAM 宿主 | 403（落会话链被拒） |
| 6 | introspect 回源 AKSK Server + 验证客户端凭据 | IAM 宿主 | 401 |

上述表中的旧 API 码及 `iam:user` DATA 用于组织与人员同步。受委托角色治理须改为逐项配置上文的八个精确 API 码及五个 DATA 资源，不可用旧码替代；`SERVICE` 主体的身份和目标授权范围均须核对。

IAM 宿主侧两步的配置：

1. **配置公共资源层接管**（宿主 application.yml）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          resource:
            server:
              security:
                protected-paths: /iam/api/**
```

2. **外插 AKSK Provider**：宿主自行引入 `simple-aksk-resource-server-starter`（版本与配置见其 README），token 验证走 introspect 回源 AKSK Server：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                introspect:
                  endpoint: https://aksk.example.com/oauth2/introspect
                  client-id: ${AKSK_INTROSPECT_CLIENT_ID:}      # 普通AKSK客户端的 AKP/AKS
                  client-secret: ${AKSK_INTROSPECT_CLIENT_SECRET:}
                  local-cache:
                    enabled: false         # 受委托角色治理关闭；仅普通同步场景可按吊销延迟要求开启
                    fallback:
                      enabled: false       # 用户与组织管理属高敏：禁 stale fallback
```

部署注意：凭据走 yml `${ENV:}` 占位符（OS 环境变量 relaxed binding 映射不到 `client-id`）；`surezzzzzz` 前缀 6 个 z（少 z 适配器静默不装配、症状 401）。

## 业务应用接入（挂门户）

业务系统以可信应用身份接入统一应用门户，按以下四步完成：

| # | 步骤 | 操作 | 说明 |
|---|---|---|---|
| 1 | 建可信应用 | `POST /iam/admin/trusted-applications`（必须带初始 OAuth 客户端） | entry 与 routePrefix 决定门户挂载位置；首次配置 Portal 集成自动追加到全局根节点顺序末尾 |
| 2 | 申报权限清单 | manifest：`roles` / `pagePermissions` / `apiPermissions` / `dataResources` | 该应用权限码空间的事实源；未申报的应用无法投影 |
| 3 | 配角色应用授权规则 | `PUT /iam/admin/roles/{roleId}/authorization-rules/{applicationId}` | 角色 × 应用的码集合，投影的计算源；变更即触发投影重算 |
| 4 | 给用户准入 | 用户应用授权 admitted（已申报清单的应用走准入 + 角色规则自动投影；特殊需要可手工授权行微调） | 决定用户 Token 中的投影内容与门户可见性 |

验证：用户登录门户后核对应用可见性与菜单；解出 Access Token，核对 `iam_authorization` 投影内容与已申报的清单、角色规则和准入状态一致。

Portal 根节点顺序由平台管理员通过 `GET/PUT /iam/admin/portal/application-order` 维护。更新必须提交读取时的 `version` 和全部 Portal 集成 ID；冲突返回 `409` 后重读再调整。服务端按该顺序返回当前用户可访问的应用，前端不得按编码或名称二次排序。OAuth2 Consent 页面会同时展示可信应用名称与内置图标；没有归属的历史客户端回退为客户端名称和默认图标。

## 能力边界

本模块提供身份、角色、授权投影及 JSON API；浏览器页面和代理由 Web 与宿主承载，业务应用负责消费授权结果。受委托角色 API 不替代产品资格判断、商业有效期检查或离线授权验证，模块测试也不替代消费方的真实 AKSK 通信与部署联调。

**预留骨架（开关或表结构在、实现未完全接线）**：

| 项 | 现状 |
|---|---|
| MFA | `mfa.enabled` 开关 + SPI 预留，默认关闭，无实现 |
| 开放注册 | `registration.open` 占位；开户走管理员建账 |
| 自助密码重置 | `password-reset.self-service` 默认关闭；开启且装配短信投递能力后，通过手机号挑战和 `POST /iam/web/auth/password-reset` 完成重置，成功即吊销全部会话 |

**明确不做（一期排除）**：机器对机器凭证（凭证签发走 aksk-server，本模块只作被调方）、多因素认证、账号密码找回邮件链路；组织架构与人员同步经开放 API（`/iam/api/**`，AKSK 凭证）。

## 文档地图

| 文档 | 内容 |
|---|---|
| [登录认证与会话](docs/领域文档/登录认证与会话.md) | 人员 / 服务认证边界、本地与外部登录、会话生命周期、登出与全端吊销 |
| [OAuth2与令牌验证](docs/领域文档/OAuth2与令牌验证.md) | 授权码与 Token 签发、人员资源验证、AKSK 服务治理及内部 reader 职责区分 |
| [权限与授权投影](docs/领域文档/权限与授权投影.md) | 清单 → 规则 → 投影、受委托角色归属与版本、直属部门继承、完整 DATA 及商业资格边界 |
| [管理台与站内信](docs/领域文档/管理台与站内信.md) | 委托摘要与共同条件写、应用异步删除、管理审计及 SSE 站内信 |
| [配置与多实例部署](docs/领域文档/配置与多实例部署.md) | 服务治理部署、增量引导核验、缓存边界、全量配置、多实例与审计消费 |
| [Portal默认入口与沉浸页路由](docs/领域文档/Portal默认入口与沉浸页路由.md) | 默认入口、登录首页、服务端应用顺序、沉浸展示与权限边界 |
| [升级说明](docs/migration/README.md) | 各版本数据库前提、增量迁移及回退要求 |
| [schema.sql](docs/schema.sql) | 全新环境数据库初始化脚本 |
| [V1.3.1__to__V1.3.2__open_role_binding.sql](docs/migration/V1.3.1__to__V1.3.2__open_role_binding.sql) | 从 1.3.1 升级的增量建表脚本，不重置旧数据 |
| [CHANGELOG.1.3.2.md](CHANGELOG.1.3.2.md) | 受委托角色能力、审计兼容性及升级要求 |
