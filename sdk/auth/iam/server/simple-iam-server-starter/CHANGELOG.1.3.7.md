# CHANGELOG - simple-iam-server-starter 1.3.7

## 发布信息

- 版本：`1.3.7`
- 类型：Bug 修复 + Feature（五件：特权短路修复 / JIT 冲突策略 / 页面准入详情端点 / 可信应用目录端点 / openapi 六族补全）
- 基线版本：`1.3.6`
- 关联制品：`simple-iam-server-core:1.3.6`（常量+文案，1.3.5 为废号跳号）、`simple-iam-client-core:1.0.3` 与四传输件（C/D/E 件契约镜像，1.0.2 为废号跳号）

## A 件：特权短路缺陷修复（集成环境缺陷报告）

- 症状：平台管理员对内置应用存在"仅准入空行"（投影合成产物）时，资源令牌验证返回空权限——特权兜底只在行缺失时触发，行有效即短路。
- 修复：`applyPrivilegeFloor` 保底并集——行有效且平台管理员+内置应用时 pages/apis=union(行投影, 清单全量)、DATA=清单全量合成、roles/authorizationVersion 保留行的；行可以更细但不劣化为空。非特权/非内置/无清单语义零变化。
- 堵增量：例行投影重算（场景 2/3/4）三集合全空且行不存在时不再新建空准入行；显式准入与既有行收缩语义不变。存量空行由保底并集无害化（数据侧可按清单核对清理）。

## B 件：JIT 用户名冲突策略（行为变更）

- 新配置 `iam.server.external-identity.username-conflict-strategy = auto-suffix（默认）| reject`（挂 externalIdentity 树；非法值启动拒绝）。
- auto-suffix：建议名撞已有 username 时按"建议名-{现存最大号+1}"续（连字符数字，GitHub 同款；中间空洞不复用，尾部号删除后回退复用的边角见 DESIGN）；**默认行为从"拒绝"变更为"自动后缀"**，治理型客户配置 reject 回到 1.3.6 行为。
- reject：撞名拒绝走既有管理员预绑定（1.3.6 语义原样保留）。
- 绝不自动并号防线不变；pre-bound-only 优先于策略；冲突处置审计留痕（建议名/终名/策略）。

## C 件：页面准入应用详情端点（feedback 消费）

- 新增 `GET /iam/api/users/{subjectId}/page-admitted-application-details`：与裸数组端点同判定同口径（共用唯一实现，永不分裂），差异仅携带 displayName/description（与门户侧边栏同源）；同码 `iam:portal:api`、无 DATA 面、顺序未定义。裸数组契约零变化。

## D 件：可信应用目录端点（limiter 消费）

- 新增 `GET /iam/api/applications`（分页，createdAt 倒序）：目录三字段 applicationCode/applicationName/status；API 码 `iam:application:api` + DATA `iam:application` read all（注册表无部门归属，IN 范围不适用）。
- client 侧配套：`IamApplicationClient`（1 方法）与 `IamUserClient` 第 13 方法 `listPageAdmittedApplicationDetails`（随 client-core 1.0.3 与四传输件）。

## E 件：openapi 面补全（六族，老大拍板"都补进去"同版并入）

- **messages 站内信**（`iam:message:api`）：POST /iam/api/messages 发信（收件人形态与管理台同构，发信者记录外部主体标识）/ GET 按收件人查信（subjectId 必填防遍历）/ GET 批次详情。
- **sessions 会话**（`iam:session:api`）：GET 按用户列在线会话（subjectId 必填）/ DELETE 踢指定会话（风控强制下线）。
- **permissions 权限清单**（`iam:permission:api`）：GET 分页只读目录（keyword 过滤）。
- **role-catalog 角色目录**（`iam:role:api`）：GET 分页只读目录（内置+自定义全量）——/iam/api/roles 为受委托角色族在册路径，普通角色以 role-catalog 区隔；写管理保留管理台。
- **statistics 统计聚合**（`iam:dashboard:api`）：GET 只读快照（与管理台同源口径；登录明细不开放）。
- **user-groups 协作组**（`iam:user-group:api`）：CRUD + 成员分页/加/移全管理面（组织同步场景）。
- 六族 REST 合规（资源名词/HTTP 动词/201/204/404），纯码控无 DATA 面（对齐管理台同族零 DATA 判断）；client 侧六新接口（15 方法）+ 模型随 client-core 跳号版一并发布。
- openapi yaml 32 路径（14 新端点 + 13 schema）。

## 测试

- 特权保底 4 用例（缺陷现场复刻/部分码并集/非特权不误放+无行兜底/非内置 1.3.1 回归）+ 投影堵空行 1 用例。
- JIT 策略 4 用例（of 解析/max+1 续号/并发窗口/REJECT 回归）+ 集成 18/18（同名自动后缀开号+不并号+cleanup 覆盖后缀号）。
- C/D 真 AKP HTTP 用例（details 同源断言+裸数组回归/目录分页三字段+无码 403+无 DATA 403）。
- client 契约（javax 17/17、jakarta 14/14）：details 对象数组解析、applications 分页解析、方法数 48（十族全量断言）。
- **端到端实测**（mavenLocal + local-runtime 1.3.7 部署，真 AKP 令牌过 iam.zs.com）：六族 14 端点全形态矩阵（无 token 401/假 token 401/真 token 200）、无码 403×8 端点、user-groups 全生命周期（建 201→读 200→改 200→成员 204/200→移 204→删 204→删后 404）、踢会话 204+列表消失实证、发信全链（201→批次 200→查信 200，senderUsername=aksk 主体）、空准入行缺陷现场复刻（受影响应用在列=保底并集生效）。
- E2E 抓出并修复三缺陷：iam_message.sender_user_id NOT NULL（外部主体发信 500，改 nullable+实体同步）、sender_username 64 溢出（resolveOperator 改短标识 aksk:{clientId}、列扩 128）、六族分页无上限（补 MAX_ADMIN_PAGE_SIZE）。

## 升级指引

- 执行数据库迁移 [V1.3.6__to__V1.3.7__openapi_expansion.sql](docs/migration/V1.3.6__to__V1.3.7__openapi_expansion.sql) 一次（iam_message 两列变更，见 SQL 头注释）；新初始化库直接用 schema.sql。
- core 坐标升至 1.3.6；行为变更两条（JIT 默认自动后缀；特权保底并集——空行管理员恢复全量）需知会存量部署。
- 存量"空准入行"无需清理（保底并集已无害化）；如需治理可按"清单全量 vs 行投影"差异清单人工核对。
- AKP 授权面新增 `iam:application:api`（bootstrap 升级重启自动登记）；六族码均已在册，消费方按最小码申领。
- feedback/limiter 消费方：引 `simple-iam-client-core:1.0.3` + 对应传输件 1.0.3。
