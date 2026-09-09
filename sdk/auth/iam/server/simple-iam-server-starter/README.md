# simple-iam-server-starter

统一身份认证与授权服务（IAM Server）。一个可独立部署的 Spring Boot 应用模块：承载本地账号体系、浏览器登录会话、OAuth 2.1 / OIDC 授权协议、RBAC、可信应用、Portal 数据、站内信与审计事件，为业务系统提供"一次登录、处处可用"的身份底座。

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
- **claims 注入**：`sub`（稳定的 IAM 用户 ID）、`sid`（IAM 会话）、`auth_time`；Access Token 附带 `roles` / `permissions` 与应用授权投影
- **Consent 授权确认**：应用可要求用户确认授权范围，确认结果落库为投影
- **Refresh Token 主动防线**：客户端显式配置 `refresh_token` 授权类型时按标准流程签发（默认不配置即不签发）；全局一次性使用（`reuseRefreshTokens=false`）——refresh grant 必轮换签发新值；旧值二次使用即重放：整族吊销（当前 token 同步失效，失败关闭）+ 发布 `RefreshTokenReuseDetectedEvent`；登出 / 禁用 / 删除 / 改密联动族失效；TTL 取 `token.access-expires-in` / `token.refresh-expires-in`（默认 30 分钟 / 10 小时）
- **资源端 Token 验证端点**：供无法本地验签的资源系统远程校验，验证客户端以 Basic 独立认证
- **平台管理员特权**：挂内置 `iam_admin` 的用户在授权解析时特权合并（admitted + 各应用清单申报全量，含 DATA all=true），不落库、挂 / 摘角色即时生效、摘除即降权（机制见 [权限与授权投影](docs/领域文档/权限与授权投影.md)）
- **开放 API**：`/iam/api/**` 供 AKSK 凭证做组织与人员同步（码 + DATA 双闸，公共资源层链接管，默认失败关闭）

### 管理面

- 用户 / 部门 / 协作组 / 角色 / 权限 CRUD 与关系分配（含外部身份预绑定）
- 最后管理员保护（最后一个可用管理员不可删除 / 禁用 / 撤销角色）、内置角色与权限保护
- 可信应用管理：OAuth 客户端（原始 secret 仅创建时返回一次，后续查询仅 `secretPresent`）、Portal 集成、应用菜单
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
    implementation "io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0"
    implementation "org.springframework.boot:spring-boot-starter-web"
    implementation "org.springframework.boot:spring-boot-starter-security"
    implementation "org.springframework.boot:spring-boot-starter-data-jpa"
}
```

- Spring Boot **2.7.x**（验证档 2.7.9）、Spring Security 5.7.x、Spring Authorization Server 0.4.1；源码兼容 Java 8；不支持 Spring Boot 3.x
- 独立 MySQL 数据库（`mysql-connector-java` 已随模块 runtimeOnly 引入），字符集 `utf8mb4`
- Redis 6+（会话 / 缓存 / 限流 / 锁 / SSE 广播 / OAuth2 授权缓存共用）
- RSA 公私钥对；JWE 模式另需 AES-256 加密密钥
- 启动引导管理员密码（见 [配置与多实例部署](docs/领域文档/配置与多实例部署.md) 的 `bootstrap` 配置组）

数据库初始化：在**全新环境**执行 [schema.sql](docs/schema.sql)。该脚本包含建表前置清理，不能直接用于已有数据环境。

### 数据表清单（25 张 = 3 张 SAS 标准表 + 22 张 `iam_*` 业务表）

| 表 | 用途 |
|---|---|
| `oauth2_registered_client` | SAS 标准：OAuth 客户端注册 |
| `oauth2_authorization` | SAS 标准：授权码 / token 状态 |
| `oauth2_authorization_consent` | SAS 标准：scope 确认记录 |
| `iam_user` | 用户（含 `identity_source` / `external_id` 外部身份绑定、`must_change_password` 须改密标记） |
| `iam_role` / `iam_permission` | 角色 / 权限（内置项受保护） |
| `iam_user_role` / `iam_role_permission` | 用户-角色、角色-权限关系 |
| `iam_department` / `iam_user_group` / `iam_user_group_member` | 部门、协作组及成员 |
| `iam_session` | IAM 会话（双时钟、状态） |
| `iam_refresh_token_family` | Refresh Token 族（轮换链条、重放检测、族吊销、过期清理） |
| `iam_password_reset` | 密码重置凭证（配套预留） |
| `iam_authorize_context` | 授权交易状态机（state / PKCE 哈希、会话绑定） |
| `iam_consent` | Consent 投影 |
| `iam_trusted_application` | 可信应用 |
| `iam_application_permission_manifest` | 可信应用权限清单（申报制，投影与规则的码空间事实源） |
| `iam_role_authorization_rule` | 角色-应用授权规则（投影的计算源） |
| `iam_application_authorization` | 用户-应用授权投影 |
| `iam_resource_verification_client` | 资源验证客户端 |
| `iam_trusted_application_portal` / `iam_trusted_application_menu` | Portal 集成与应用菜单 |
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
| `/iam/web/portal/accessible-applications` | GET | 已认证 | 当前用户可访问且已启用 Portal 集成的应用及菜单；平台管理员直通全部已启用应用 |
| `/iam/web/branding` | GET | 匿名 | 品牌配置 |
| `/iam/web/messages` | GET | 已认证 | 站内信列表 |
| `/iam/web/messages/page` | GET | 已认证 | 站内信分页 |
| `/iam/web/messages/unread-count` | GET | 已认证 | 未读数 |
| `/iam/web/messages/read-all` | PUT | 已认证 | 全部已读 |
| `/iam/web/messages/{messageId}/read` | PUT | 已认证 | 单条已读 |
| `/iam/web/messages/events` | GET (SSE) | 已认证 | 站内信实时推送流 |
| `/iam/web/theme-preference` | GET / PUT | 已认证 | 用户主题偏好 |

### 管理端点（`/iam/admin/**`，进门为 `ROLE_iam_admin` 或任一页面权限码）

组织与用户：

| 端点 | 说明 |
|---|---|
| `GET /iam/admin/organizations/tree` | 组织树（部门 + 协作组） |
| `GET /iam/admin/organizations/departments/{id}/workspace` | 部门工作台视图 |
| `GET /iam/admin/organizations/users/{id}/profile` | 用户组织画像 |
| `GET / POST /iam/admin/users`、`GET / PUT / DELETE /iam/admin/users/{userId}` | 用户 CRUD 与分页（删除级联清理角色绑定、组成员与应用授权投影） |
| `PUT /iam/admin/users/{userId}/enable` / `disable` | 启用 / 禁用（禁用即全端吊销） |
| `PUT /iam/admin/users/{userId}/unlock` | 手动解锁（清除登录失败锁定与失败计数，不等 15 分钟自动过期） |
| `PUT /iam/admin/users/{userId}/reset-password` | 管理员重置密码（即全端吊销） |
| `POST / DELETE /iam/admin/users/{userId}/external-identity` | 外部身份预绑定 / 解绑 |
| `GET /iam/admin/users/{userId}/roles`、`POST / DELETE /iam/admin/users/{userId}/roles/{roleId}` | 用户角色查询与分配 |

部门与协作组：

| 端点 | 说明 |
|---|---|
| `GET / POST /iam/admin/departments`、`GET / PUT / DELETE /iam/admin/departments/{id}`、`GET /iam/admin/departments/page` | 部门 CRUD 与分页 |
| `GET /iam/admin/departments/{departmentId}/roles`、`POST / DELETE /iam/admin/departments/{departmentId}/roles/{roleId}` | 部门挂载角色查询与分配——部门下全体成员自动继承（个人直接角色之外的并集，转部门实时生效）；撤销 `iam_admin` 命中部门级最后管理员保护时 409 |
| `GET / POST /iam/admin/user-groups`、`GET / PUT / DELETE /iam/admin/user-groups/{id}`、`GET /iam/admin/user-groups/page` | 协作组 CRUD 与分页 |
| `GET / POST / DELETE /iam/admin/user-groups/{groupId}/users/{userId}`、`GET /iam/admin/user-groups/{groupId}/users` | 协作组成员管理 |

角色与权限（内置项受保护不可改删）：

| 端点 | 说明 |
|---|---|
| `GET / POST /iam/admin/roles`、`GET / PUT / DELETE /iam/admin/roles/{roleId}`、`GET /iam/admin/roles/page` | 角色 CRUD 与分页（删除级联清理角色-权限关系、成员绑定与授权规则） |
| `GET / POST / DELETE /iam/admin/roles/{roleId}/permissions/{permissionId}`、`GET /iam/admin/roles/{roleId}/permissions` | 角色-权限关系 |
| `GET /iam/admin/roles/{roleId}/departments` | 反查挂载了该角色的部门（成员继承来源，只读展示，不含子部门递归） |
| `GET / PUT / DELETE /iam/admin/roles/{roleId}/authorization-rules/{applicationId}` | 角色应用授权规则（PUT 固定地址 upsert 返 200，无规则 GET 返 404，变更触发投影重算） |
| `GET /iam/admin/permissions`、`GET /iam/admin/permissions/{permissionId}`、`GET /iam/admin/permissions/page` | 权限列表 / 详情 / 分页（只读——权限码为系统内置 seed，由安全链与 `@PreAuthorize` 在代码中消费，运行期不提供增删改） |

可信应用（应用 + OAuth 客户端）：

| 端点 | 说明 |
|---|---|
| `GET / POST /iam/admin/trusted-applications`、`GET / PUT / DELETE /iam/admin/trusted-applications/{id}`、`GET /iam/admin/trusted-applications/page` | 可信应用 CRUD 与分页（创建必须带初始客户端） |
| `GET / POST /iam/admin/trusted-applications/{id}/clients`、`GET / PUT / DELETE …/clients/{clientId}` | OAuth 客户端管理（原始 secret 仅创建时返回一次） |

用户应用授权与验证客户端：

| 端点 | 说明 |
|---|---|
| `GET / PUT / DELETE /iam/admin/users/{userId}/application-authorizations/{applicationId}`、`GET /iam/admin/users/{userId}/application-authorizations` | 用户应用授权（投影进 Access Token）；摘要与详情按用户是否平台管理员下发 `platformAdmin` 标记（特权不受单应用撤销影响） |
| `GET / POST /iam/admin/trusted-applications/{id}/resource-verification-clients`、`GET / DELETE …/{clientId}`、`POST …/{clientId}/secret` | 验证客户端管理（含密钥轮换） |

站内信：

| 端点 | 说明 |
|---|---|
| `POST /iam/admin/messages` | 管理端发送站内信 |

管理面通用约定：创建主资源返回 `201 Created`，删除返回 `204 No Content`；认证、授权和业务错误均以标准 HTTP 状态表达，不依赖自定义业务状态码字段。

### 开放 API 端点（`/iam/api/**`，公共资源层链）

主体为 AKSK 凭证（外部业务系统组织与人员同步），Bearer 鉴权 + 端点级 API 码 + DATA 范围双闸。接入契约与 AKSK 侧三权配置见 [开放 API 契约](../contract/openapi/simple-iam-open-api.openapi.yaml)。

用户族（API 码 `iam:user:api`，DATA resource=`iam:user`——读=部门范围与请求条件求交、写=目标部门完整落在授权范围，失败关闭 403）：

| 端点 | 说明 |
|---|---|
| `GET /iam/api/users`、`GET /iam/api/users/{userId}` | 用户分页 / 详情（DATA 部门范围求交，越权数据不出库） |
| `GET /iam/api/users/{userId}/roles` | 用户角色列表 |
| `POST /iam/api/users`、`PUT / DELETE /iam/api/users/{userId}` | 用户创建 / 更新 / 删除（目标部门须在 DATA 范围内） |
| `PUT /iam/api/users/{userId}/enable` / `disable` / `reset-password` | 启用 / 禁用（全端吊销）/ 重置密码（审计 operator 为 AKSK 主体标识） |
| `POST / DELETE /iam/api/users/{userId}/roles/{roleId}` | 角色绑定 / 解绑 |

部门族（API 码 `iam:department:api`，第一版纯码控不评估 DATA）：

| 端点 | 说明 |
|---|---|
| `GET /iam/api/departments` | 全量平铺列表（含 `fullPath`，外部自行重建树） |
| `GET / POST /iam/api/departments`、`GET / PUT / DELETE /iam/api/departments/{departmentId}` | 部门详情 / CRUD（删除走既有级联规则） |

### 资源验证端点（Order 2 链，验证客户端 Basic）

| 端点 | 说明 |
|---|---|
| `POST /iam/resource/tokens/verify` | 远程 token 验证：请求体携带 token，校验通过返回 `sub` 与 `iamAuthorization` 投影 |

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
                    enabled: true          # 同步场景可开，吊销延迟=缓存 TTL 窗口
                    fallback:
                      enabled: false       # 用户与组织管理属高敏：禁 stale fallback
```

部署注意：凭据走 yml `${ENV:}` 占位符（OS 环境变量 relaxed binding 映射不到 `client-id`）；`surezzzzzz` 前缀 6 个 z（少 z 适配器静默不装配、症状 401）。

## 业务应用接入（挂门户）

业务系统以可信应用身份接入统一应用门户，四步（机制详见 [权限与授权投影](docs/领域文档/权限与授权投影.md)）：

| # | 步骤 | 操作 | 说明 |
|---|---|---|---|
| 1 | 建可信应用 | `POST /iam/admin/trusted-applications`（必须带初始 OAuth 客户端） | entry 与 routePrefix 决定门户挂载位置 |
| 2 | 申报权限清单 | manifest：`roles` / `pagePermissions` / `apiPermissions` / `dataResources` | 该应用权限码空间的事实源；未申报的应用无法投影 |
| 3 | 配角色应用授权规则 | `PUT /iam/admin/roles/{roleId}/authorization-rules/{applicationId}` | 角色 × 应用的码集合，投影的计算源；变更即触发投影重算 |
| 4 | 给用户准入 | 用户应用授权 admitted（已申报清单的应用走准入 + 角色规则自动投影；特殊需要可手工授权行微调） | 决定用户 Token 中的投影内容与门户可见性 |

验证：用户登录门户核对应用可见与菜单，解出的 Access Token 中核对 `iamAuthorization` 投影内容。

## 当前交付边界

后端能力已完成实现与真实 MySQL/Redis 模块测试验证。正式发布前仍需完成 Vue 登录页、Consent 页、代理路由、浏览器回调、Token 换取和 `/userinfo` 的端到端联调验证。

**预留骨架（开关或表结构在、实现未完全接线）**：

| 项 | 现状 |
|---|---|
| MFA | `mfa.enabled` 开关 + SPI 预留，默认关闭，无实现 |
| 开放注册 | `registration.open` 占位；开户走管理员建账 |
| 自助密码重置 | `password-reset.self-service` 占位；`PasswordResetService` 的 token 凭证机制已实现但未暴露 HTTP 端点，`iam_password_reset` 表为配套预留 |

**明确不做（一期排除）**：机器对机器凭证（凭证签发走 aksk-server，本模块只作被调方）、多因素认证、账号密码找回邮件链路；组织架构与人员同步经开放 API（`/iam/api/**`，AKSK 凭证）。

## 文档地图

| 文档 | 内容 |
|---|---|
| [登录认证与会话](docs/领域文档/登录认证与会话.md) | 本地登录、验证码与失败锁定、LDAP / OIDC 外部身份、身份归一、会话生命周期、登出与全端吊销 |
| [OAuth2与令牌验证](docs/领域文档/OAuth2与令牌验证.md) | 授权码 + PKCE + Consent 完整流程、Token 签发与 claims、资源端验证、业务应用接入规则 |
| [权限与授权投影](docs/领域文档/权限与授权投影.md) | 权限清单 → 角色规则 → 用户投影三层结构、触发事件、三种 JSON 形态、级联与手工授权语义 |
| [管理台与站内信](docs/领域文档/管理台与站内信.md) | 管理面统一操作模式、可信应用族特殊点、SSE 站内信实时推送 |
| [配置与多实例部署](docs/领域文档/配置与多实例部署.md) | 全量配置参考、配套组件接线、多实例部署、启动引导恢复码、审计事件流、敏感信息边界 |
| [schema.sql](docs/schema.sql) | 全新环境数据库初始化脚本 |
