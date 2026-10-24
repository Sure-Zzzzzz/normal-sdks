# 1.3.8

## 发布信息

- 版本：`1.3.8`
- 日期：`2026-10-24`
- 类型：Feature + Bug 修复（Portal 路由投影、登录要求门禁、授权投影启动协调）
- 基线版本：`1.3.7`
- 关联制品：`simple-iam-server-core:1.3.6`、`simple-iam-core:1.1.0`
- 运行基线：Spring Boot `2.7.9`、Java 8；Gradle 构建进程使用 Java 11 不改变制品基线

## 依赖变更

| 依赖 | 版本 | 说明 |
|---|---|---|
| `simple-iam-server-core` | `1.3.6` | 已发布的 Server 常量与错误文案 |
| `simple-iam-core` | `1.1.0` | 身份协议基座，保持不变 |
| `simple-data-permission-core` | 现有已发布版本 | Starter 只依赖 Core |
| `simple-data-permission-spring-mvc-starter` | 由部署 runtime 显式装配 | 不作为 Starter 生产传递依赖 |

## 变更

- 新增 `GET /iam/api/portal/applications` Portal 可信应用路由投影，供已授权 AKP 读取启用 Portal 的可信应用路由。
- Portal 机器路由固定投影 `forwardAuthorization=REQUEST`，由 Gateway 原样转发浏览器请求头给业务自有 Nginx/API；IAM 不读取、保存或记录凭据值，Cookie 始终移除。
- 明确 IAM 与 AKSK 属于平台内置应用：两者保留在用户态 Portal 授权模型中，但从动态机器目录排除，由 IAM Nginx 固定入口承载；部署配置不得移除这两个内置编码。
- 目录采用 API + DATA 双重校验，使用 `catalogRevision` 保证分页快照一致性；读取期间目录变化返回 `409`，不返回跨版本的部分结果。
- 新增 `iam_portal_catalog_revision` 单例表及对应迁移脚本；全新数据库使用 `docs/schema.sql`。
- Portal 路由配置、可信应用生命周期和应用排序的有效变更推进目录 revision；旧 `GET /iam/api/applications` 的路径、响应和权限保持不变。
- Server 主体依赖 `simple-data-permission-core`；Spring MVC 参数解析与强制执行由部署宿主显式装配 `simple-data-permission-spring-mvc-starter`，避免把 MVC 运行时耦合进业务 starter。
- 新增登录要求文档版本表 `iam_login_requirement_document`，登录接口返回数据库中的正文和版本信息；密码、短信和外部登录在成功前必须确认服务条款与隐私政策版本。登录要求默认启用，只有显式配置 `io.github.surezzzzzz.sdk.auth.iam.server.login.requirements.enabled=false` 才返回 `DISABLED` 并关闭校验；启用时两份文档都必须存在且标记为必选，缺失或配置不一致会在启动/读取时失败关闭。当前 SQL 内置两条明确标记的必选联调占位文本，部署方可按业务要求替换。
- 登录要求修订号和登录方式编码分别按数据库字段长度在启动期/请求期校验；`docs/schema.sql` 仅用于全新数据库初始化，已有数据库按版本迁移脚本升级。
- 存量 IAM 应用在 bootstrap 关闭时也会只增量补齐 Portal 路由投影的内置权限清单与 `iam_admin` API/DATA 授权，不覆盖既有自定义授权。
- 启动期授权投影重算增加 fencing 屏障及目标应用级协作事件：先关闭 AKSK 本地投影总闸，所有有效主体追平后再恢复；失败或租约丢失时保持关闭。
- 修复可信应用异步删除调度器的停机竞态：收到上下文关闭事件后停止领取新任务，并等待在途任务完成后再释放依赖资源。

## 向后兼容性

- 既有 IAM Web 与 Open API 路径保持兼容；新增登录要求接口和登录请求确认字段要求 Login Web/Contract 使用 `1.3.8+`。
- 默认行为变化：登录要求默认启用。存量部署必须执行 1.3.7→1.3.8 数据迁移并提供两条必选文档；明确配置 `io.github.surezzzzzz.sdk.auth.iam.server.login.requirements.enabled=false` 才保持无门禁行为。
- 内置应用 `iam`、`aksk` 仍由 IAM 固定入口承载，不进入动态 Portal 机器目录；业务可信应用继续使用自身 HTTP origin。

## 测试

- Java 8 `BeanPresenceTest`：通过，验证三张新增 1.3.8 表可完成 Hibernate `validate`。
- 兼容回归：在测试 profile 显式关闭登录要求后，按 `LOCAL_TEST_COMMANDS.md` 的 7 批完整 Starter 矩阵执行；该模式只验证既有登录 API，不替代 REQUIRED 门禁验收。
- REQUIRED 门禁：`IamLoginRequirementsServiceTest` 覆盖默认启用、成对文档、版本确认、显式关闭和异常失败关闭；前端 REQUIRED/DISABLED/ERROR 走查另按 Login Web 文档执行。

## 发布前置

- `docs/schema.sql` 与迁移脚本内的服务条款、隐私政策目前仍是明确标记的联调占位文本；未替换为业务批准正文前，不得宣称生产法律文案已完成。

## 升级

1. 暂停 IAM Server 写请求并完成可恢复备份。
2. 已运行 1.3.7 的数据库执行一次 `docs/migration/V1.3.7__to__V1.3.8__portal_route_projection.sql`；新库直接执行 `docs/schema.sql`。
3. 部署 1.3.8 runtime，并显式引入 `simple-data-permission-spring-mvc-starter`。
4. 在 AKSK 管理侧为 Gateway AKP 显式授予 `iam:portal-application:read:api` 与 `iam:portal-application` 的 `read` DATA 范围；升级不会自动扩权旧凭证。
5. 如替换联调占位文本，更新 `iam_login_requirement_document` 的正文、修订号和版本标识，并保持 `terms-of-service`、`privacy-policy` 两条记录为必选；前端从 requirements 接口读取正文并在三种登录请求中回传确认信息。关闭门禁时仅使用 `io.github.surezzzzzz.sdk.auth.iam.server.login.requirements.enabled=false`，不要删除文档记录。
