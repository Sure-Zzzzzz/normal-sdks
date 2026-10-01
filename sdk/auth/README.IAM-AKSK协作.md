# IAM 与 AKSK 协作接入

IAM 与 AKSK 都能独立使用。需要让人员身份和服务身份访问同一业务 API 时，在**资源服务**组合两个 Provider；不要把 IAM Server 嵌入 AKSK Server，也不要把 AKSK Server 嵌入 IAM Server。

```text
IAM Server ──受控验证──┐
                       ├── 资源服务：同一 API / 同一业务链
AKSK Server ──认证内省──┘
```

- IAM Server 与 AKSK Server 独立部署、独立运行，不共享数据库。
- IAM 不是 AKSK 签发 Token 的运行前提；AKSK 也不是 IAM 登录或签发 Token 的运行前提。
- 业务服务只维护一套 Controller、应用服务与领域逻辑。认证完成后，两个来源都使用同一 API 权限与数据权限边界。

## 聚散原则（总纲）

**聚是一团火，散是满天星。** 聚，是把公共能力拧成一股完整的力量——统一认证入口、统一权限判定、统一 UI 契约，火在一起烧才旺；散，是各产品在各自的位置独立发光——领域逻辑、凭据、权限码各归各家，星在满天各有轨道。判据始终一条：这个改动有没有让某个产品"知道了不该知道的另一个产品"。

上面三条是聚散原则在部署层的体现。原则全文：**产品之间只经中立协议与公共机制协作，任何一方不得在代码、依赖、配置、命名上认知另一方的存在**。它是分层的，每层有独立判据，破任何一层都算破原则：

| 层 | 原则 | 判据（怎么算没破） |
| --- | --- | --- |
| 部署聚散 | 独立部署运行，不共享库/缓存/密钥；互不为运行前提 | 任一产品单独起服即完整可用 |
| 代码聚散 | IAM 不认知 AKSK（及任何具体业务应用），反之亦然：无 import、无特判分支、无对方领域的概念 | 源码 grep 不到对方产品名/编码；协作只依赖**中立协议包**（如 `simple-owner-authorization-collaboration-core`，协议模型是双方之间的合同，只含 owner / authorization 等中性概念） |
| 依赖聚散 | IAM Server 与 AKSK Server 的正式依赖只包含各自领域和中立协议；需要 `OWNER_INHERITED` 时，由部署 AKSK 的业务应用显式组合 AKSK Server Starter 与 IAM collaboration Starter | AKSK Server 的 `build.gradle` 不依赖 IAM；部署应用可按需声明 `simple-iam-aksk-collaboration-starter`，适配器通过中立 owner-authorization SPI 接入，不把 IAM 领域代码带进 AKSK Server |
| 配置聚散 | 凭据**三处归属**：客户端注册的事实源在 IAM 库；浏览器侧 client-id 构建期注入 web 产物；回源验证凭据在资源服务侧受保护配置（env） | 凭据不出现在命令行回显、日志、仓库；每处凭据有且只有一个家 |
| 权限聚散 | 权限码**申报制**：各应用清单自申报页面/接口/数据资源，IAM 只提供通用机制（清单、角色规则、投影、准入），不内置任何应用的权限码 | IAM 源码与库表 grep 不到业务权限码（如 `akskClient:read`）；新应用接入零 IAM 代码改动 |
| 命名聚散 | 跨产品契约用中性名：`owner-authorization` 而非 `aksk-*`、`ownerUsername` 而非领域私有称谓——**命名即边界，命名泄漏就是认知泄漏** | 契约路径/scope/表名/DTO 字段逐个过中性筛 |

反例存档（均已收口，引以为戒）：IAM 内部协作端点曾名 `/iam/internal/aksk/**`、表名 `iam_aksk_authorization_change`——路径与表名直接认知对方产品，1.3.0 统一中性化为 `owner-authorization` 系列；IAM 侧曾手写协作 DTO 与协议模型漂移，已删并收编进中立协议包单一事实源。

聚散同样适用于前端：共享组件与样式进 theme-contract 契约仓统一发版，各业务 web 引用而不复制——契约是"聚"，业务实现是"散"。

## 选择接入方式

| 场景 | 资源服务依赖 | 适用身份 |
| --- | --- | --- |
| 仅 IAM | 公共资源层 + IAM Provider | 人员、管理员、门户用户 |
| 仅 AKSK | 公共资源层 + AKSK Provider | 服务、脚本、第三方系统 |
| IAM 与 AKSK 协作 | 公共资源层 + 两个 Provider | 同一业务 API 同时接受人员与服务身份 |

仅引入实际需要的 Provider。即使两个 Provider 都接入，单个请求也只会选择其中一个认证源。

## 组合资源服务

协作资源服务使用公共资源层建立唯一的 Bearer 入口，再由 IAM 与 AKSK Provider 提供各自的验证能力：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:<resource-version>'
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:<iam-version>'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:<aksk-version>'
}
```

**Spring Boot 3 宿主**：资源层提供等价的 jakarta 系列模块（Servlet 6 / Jakarta EE 命名空间），坐标把 `jakarta` 插在 `-starter` 前且不改变路径形态——`simple-resource-server-jakarta-starter` / `simple-iam-resource-server-jakarta-starter` / `simple-aksk-resource-server-jakarta-starter`（含配套 `simple-data-permission-spring-mvc-jakarta-starter`）。SB2 宿主用上表经典坐标，SB3 宿主整组换 jakarta 坐标，两组不混用；Provider 组合方式、配置键与本文全部流程在两种宿主下一致。Spring Boot 3 支持目前覆盖 resource 侧（资源服务组合层），IAM/AKSK Server 本体仍是 Servlet 5 形态。

跨模块联调阶段可使用相同的 Gradle `project(...)` 依赖；发布坐标以实际已发布版本为准，不能把本地候选版本当成已发布版本。

**AKSK Server 自身作为宿主组合时**（门户形态、或宿主直接内嵌 AKSK Server）：除 `simple-aksk-server-starter` 外，要接受 IAM 人员 Token 必须再组合 `simple-iam-resource-server-starter`（IAM 验证适配器，kid=`iam/*` 的装配源）——它与公共层 `simple-resource-server-starter` 是**两个坐标，极易混淆**：只装公共层时 IAM Token 一律 401 且无任何装配日志（症状=浏览器 PKCE 循环熔断）。同时宿主必须显式开启 `io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true`（该 starter 默认关闭），否则 OAuth2 端点限流 filter 装配失败、服务起不来。验证组合是否正确：解包 fat jar 确认 `simple-iam-resource-server-starter` 在 `BOOT-INF/lib` 中，且带 IAM Token 请求 `/api/**` 返回非 401。

公共资源层只负责统一的请求认证、应用准入和精确 API permission 校验。每个 Provider 注册自己的 `ResourceAuthenticationAdapter`，不创建竞争的最终业务 `SecurityFilterChain`。详细契约见 [公共资源层](resource/simple-resource-server-starter/README.md)。

## 分别配置两个验证渠道

两个 Provider 的验证材料相互独立，由资源服务在受保护配置中提供：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          resource:
            server:
              security:
                protected-paths:
                  - /api/**
                permit-all-paths:
                  - /public/**
          iam:
            resource:
              server:
                verification-endpoint: https://<iam-server>/iam/resource/tokens/verify
                client-id: <iam-resource-verification-client-id>
                client-secret: <iam-resource-verification-client-secret>
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: https://<aksk-server>/oauth2/introspect
                  client-id: <aksk-introspection-client-id>
                  client-secret: <aksk-introspection-client-secret>
```

- IAM 验证客户端只验证其受控范围内的 IAM Token。
- AKSK 内省客户端必须使用已认证的内省契约；AKSK 3.0 不支持匿名内省。
- 凭据应由部署侧的受保护配置或密钥管理提供，不能写入源码、文档、日志或测试输出。
- 生产环境应保持验证失败即拒绝；不要用过期授权快照放行请求。

Provider 的单独配置与能力说明见 [IAM Resource Server Starter](iam/resource/simple-iam-resource-server-starter/README.md) 和 [AKSK Resource Server Starter](aksk/resource/simple-aksk-resource-server-starter/README.md)。

## 同一业务 API

业务接口不读取原始 Token、身份来源、角色、OAuth scope 或 `security_context` 来决定权限。它声明稳定、精确的 API permission：

```java
@RestController
public class OrderController {

    @GetMapping("/api/orders")
    @RequireApiPermission("order.read")
    public List<OrderResponse> list() {
        return orderApplicationService.list();
    }
}
```

也可以在公共资源层配置“路径模式 + 精确 HTTP 方法 → 精确 API permission”。两种方式均要求一个接口最终只有一个精确权限；若 IAM 人员和 AKSK 服务都要访问该接口，两个经过验证的应用授权上下文都必须包含 `order.read`。

角色、PAGE 权限、OAuth scope、URL、HTTP method、Controller 名称和 `security_context` 均不授予 API 或 DATA 权限。它们不能替代应用准入、精确 API permission 或数据授权文档。

## 认证、授权与数据权限边界

公共资源层只读取紧凑 Token 外层 JOSE protected header 的唯一 `kid`，并按命名空间路由：

```text
kid = <source-id>/<key-id>
iam/...  → IAM Provider
aksk/... → AKSK Provider
```

`kid` 仅用于路由，不是信任证明。被选中的 Provider 仍必须完整验证令牌、签发方、受众、时效、撤销状态和授权上下文。

| 场景 | 结果 |
| --- | --- |
| 缺失、畸形、未知或多个身份载体 | `401` |
| 选中的 Provider 拒绝凭据 | `401`，不尝试另一 Provider |
| 认证成功但未准入或缺少精确 API permission | `403` |
| `DataAccessPlan` 无法在真实数据操作处完整执行 | `403` |
| 显式公开路径 | 不读取 Bearer 凭据 |

协作是认证源的 **OR**，不是 IAM 与 AKSK 同时认证的 **AND**。公共层不会跨 Provider 回退、不会合并两个身份的权限，也不会基于未验证 claim 推断来源。

认证与授权顺序固定如下：

```text
Bearer / Provider 认证（401）
→ 应用准入与精确 API permission（403）
→ DATA 访问计划评估与完整范围执行（403）
→ 业务领域约束
```

公共资源层不替业务执行 SQL、JPA、MyBatis 或 Elasticsearch 过滤。业务服务必须在实际数据操作边界完整消费 `DataAccessPlan`：同一 grant 内约束保持 AND，不同 grant 保持 OR；无法完整执行时必须拒绝，不能退化为全量数据访问。

## 协作接入操作序

上节是协作的机制边界，本节给出从零到联调通过的可跟跑操作序。每步的机制细节链对应手册章节。

### 前置

- IAM Server `1.0.x` 独立部署就绪（部署步骤见 [IAM 用户手册](iam/USER_MANUAL.md) 第 4 章）
- AKSK Server `3.x` 独立部署就绪（部署步骤见 [AKSK 用户手册](aksk/USER_MANUAL.md) 第 4 章）
- 两侧不共享数据库、Redis 或密钥材料
- 一个 Spring Boot 2.7.x 业务资源服务工程

### 第一步：资源服务组合依赖与配置

依赖三行缺一不可（Provider 不传递公共层，公共 Starter 必须宿主自己声明）：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'      // 公共资源层
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'  // IAM Provider
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.1.0' // AKSK Provider（3.1.0 起可配 inherited AKU 接收）
}
```

配置整抄（凭据占位符替换为部署环境注入的环境变量）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          resource:
            server:
              security:
                protected-paths:
                  - /api/**                 # 你的业务 API 前缀，按实际改
          iam:
            resource:
              server:
                verification-endpoint: https://<iam-server>/iam/resource/tokens/verify
                client-id: ${IAM_RESOURCE_CLIENT_ID}          # 第二步在 IAM 管理台取得
                client-secret: ${IAM_RESOURCE_CLIENT_SECRET}
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: https://<aksk-server>/oauth2/introspect
                  client-id: ${AKSK_INTROSPECT_CLIENT_ID}     # 第三步在 AKSK 管理台取得
                  client-secret: ${AKSK_INTROSPECT_CLIENT_SECRET}
```

此时还启动不了（凭据没填）——先做第二、三步拿到两对凭据再启动。启动即自检：IAM 侧三件缺任一或超时非正数、AKSK 侧三件缺任一，均在自动配置阶段直接失败，不会带病上线。

### 第二步：IAM 侧准备（人员身份半边）

按顺序做三件事（IAM 管理台或管理 API 均可）：

1. 按四步建业务可信应用：建可信应用（带初始 OAuth 客户端）→ manifest 申报 `apiPermissions` / `dataResources` → 配角色应用授权规则 → 用户准入（每步的请求体示例直接抄 [IAM 用户手册](iam/USER_MANUAL.md) 第 7 章）
2. 在该可信应用下创建**资源验证客户端**，一次性保存弹出的 `clientId` / `clientSecret`（**只显示这一次**）——这就是上面配置里的 `IAM_RESOURCE_CLIENT_ID/SECRET`
3. 核对一遍码：manifest 申报的 API 码 = 资源服务接口上 `@RequireApiPermission("...")` 的码，逐字一致（如 `order.read`）

### 第三步：AKSK 侧准备（服务身份半边）

按顺序做三件事（AKSK 管理台 `/admin`）：

1. 创建**内省客户端**：Admin 首页点「创建平台级 Client」，保存凭据——这就是上面配置里的 `AKSK_INTROSPECT_CLIENT_ID/SECRET`（AKSK 3.0 不支持匿名内省，introspect 必须用已认证客户端；这个 Client 只做内省时的 Basic 认证，下面第 3 条的应用授权不是给它配的）
2. 为调用方创建 AKP / AKU 客户端，保存一次性展示的 Secret（这是第四步换 Token 用的 AK/SK，与内省客户端是两回事）
3. 为该客户端配完整应用授权（应用编码、精确 API permission、`DataGrantDocument`），API 码与 IAM manifest 申报的码**逐字一致**，然后勾选**「允许签发应用授权快照」开关**并点**「保存完整授权」**——这就是"准入"，不勾换不出 Token（详细点击路径见 [AKSK 用户手册](aksk/USER_MANUAL.md) 第 4.5 节）

### 第四步：联调验证

三个用例覆盖协作主干：

| # | 用例 | 操作 | 预期 |
|---|---|---|---|
| 1 | 人员身份 | 用户经门户登录（OAuth 2.1 授权码 + PKCE）取得 Access Token，`Authorization: Bearer <iam-token>` 调业务 API | `200`，返回该用户 DATA 授权范围内的数据 |
| 2 | 服务身份 | `POST /oauth2/token`（`client_credentials`，Basic 为 AK/SK）取得 Token，`Bearer <aksk-token>` 调**同一** API | `200`，返回应用授权 DATA 范围内的数据 |
| 3 | 边界负例 | 无凭据直调；有 Token 但调未授权接口；DATA 越界写 | 依次 `401` / `403` / `403`（按「认证、授权与数据权限边界」的结果表） |

用例 2 的两条 curl（换 Token + 打业务 API）：

```bash
# 换 Token（Basic 为第三步第 2 条的 AK/SK）
curl -s -u "<AK>:<SK>" -d "grant_type=client_credentials" \
  https://<aksk-server>/oauth2/token
# 抄下 access_token

# 打业务 API
curl -s -H "Authorization: Bearer <access_token>" \
  https://<resource-server>/api/orders
```

用例 1 的 Access Token 从浏览器走门户登录链取得（login-web 完整流程见 [IAM 用户手册](iam/USER_MANUAL.md) 第 5.3 节登录序）；联调期也可用 IAM 手册 4.6 的 curl 登录链拿会话后走授权码流程。真实三进程 E2E 的完整参考（orders 表双授权维度、Mock 层测试分层、外部编排验收任务）见 [协作 Demo](resource/simple-iam-aksk-collaboration-demo/README.md)。

### 排障：401 / 403 分层定位

先看状态码分层（401=认证层，403=授权层），再按来源缩小：

| 症状 | 定位方向 |
|---|---|
| `401` 无 Bearer 或 kid 畸形 / 未知 / 多载体 | 调用方未挂 Token 或 Token 损坏；检查请求头 |
| `401` IAM Provider 拒绝 | Token 不活跃（过期 / 会话失效 / 已吊销）→ 重新走登录链；或验证客户端凭据错误 → 核对 IAM 管理台凭据与资源服务配置 |
| `401` AKSK Provider 拒绝 | introspect 返回非 active：Token 已撤销 / 过期 → 重新换取；或内省客户端在 AKSK 侧未被授权 |
| `401` 但两侧凭据都对 | IAM 侧日志查 `VERIFICATION_001`（协议不符）/ `VERIFICATION_002`（网络不可用）；AKSK 侧查 introspect 回源地址是否指向正确的 AKSK Server |
| `403` 认证已过但接口被拒 | API 码缺配：IAM 侧查角色应用授权规则是否含该码、用户是否准入；AKSK 侧查应用授权是否配置且**已显式准入** |
| `403` 能读不能写 / 部分数据不可见 | DATA 层：核对两侧 DATA 授权文档是否覆盖目标维度；越界单条访问按业务设计也可能表现为 `404` |
| 资源服务启动失败 | 双 Provider 配置三件套是否齐全（见第一步的启动自检）；`surezzzzzz` 前缀 6 个 z，少 z 适配器静默不装配 |

## 联调环境重建手册（清库重来检查单）

本节是协作接入操作序的运维补篇：**任何可信应用**接入 IAM/门户的 IAM 侧配置共七件（下表），也是清库/换库后推倒重来的逐项检查单。全部配置走管理台页面或管理面 API（写操作带 CSRF 头），**禁止 DB 直写**——直写绕过校验与投影事件，且缓存不一致导致"改了不生效"的假象。

> 通俗解释：清库重建不是"把表灌回来"，而是把"注册→申报→绑角色→发凭据→配门户→建用户"这套链路重新走一遍。漏任何一件，症状都出现在离它最远的页面上（比如漏配角色规则，表现是"普通用户登录后应用消失"）。

### IAM 侧七件套（通用接入清单）

| # | 事项 | 要点与踩坑 |
|---|---|---|
| 1 | 建可信应用 | `applicationCode` 即门户路由前缀，建后不可改；平台内置应用勾选 built_in（禁删禁停、客户端强制免授权确认，走查不弹授权页） |
| 2 | 上传权限清单 | `pagePermissions` / `apiPermissions` / `dataResources` 三类。**dataResources 必须申报全部数据资源**——DATA 授权按清单校验，漏申报时授权写入直接 400「数据资源不在应用清单范围内」 |
| 3 | 配角色授权规则 | 普通用户页面可见性的**正规路径**：`iam_user` 绑自助面（如 `akskSelfCredential:page` + 自助 API）、`iam_admin` 绑管理面（全量页面/API + 数据授权模板）。持有角色的用户经投影并集自动获得权限。**清库后规则归零**——不配则所有普通用户（含 JIT 开号的 SSO/LDAP 用户）集体丢页面 |
| 4 | PKCE 公共客户端 | 门户形态页面登录用；redirect_uri 指向该应用在门户的回调地址 |
| 5 | 资源验证客户端（VC） | 供资源服务回源验证 IAM Token；secret 仅创建时展示一次，**立即写入受保护 env 文件**。清库后旧凭据全部失效，必须重建并同步 env |
| 6 | 门户集成 | 菜单树（页面节点路由以 `/` 开头）+ 每个页面节点绑定页面权限码 + 门户排序；菜单按用户 pagePermissions 过滤 |
| 7 | 用户与角色 | 本地密码用户走管理台建号；SSO/LDAP 用户首次登录 JIT 自动开号（当场分配 subjectId），但**默认无任何角色**——需显式分配 `iam_user` 才能经第 3 件获得页面 |

### 角色边界定案（2026-09-30）

**iam_admin 的特权域 = 内置应用集合**：platformAdmin 特权合并只覆盖内置应用（平台自身组件：iam、aksk、portal 等）；**业务应用（kms/license/crm 等非内置）一律不走平台特权**——iam_admin 对业务数据零默认可见，要治理业务应用必须由该应用**自宣告管理角色**（如 kms-admin）并显式分配。内置应用由 iam_admin 直接接管（全量 PAGE/API/DATA，禁删禁停），本质是"平台自身组件让渡治理权"；业务应用自宣告角色，本质是"业务数据治理权归业务"。注册可信应用时的 builtIn 勾选即此治理决策：勾=让渡给 iam_admin，不勾=自宣告角色族+自管授权。内置与非内置的完整选择指南（判定标准 / 生命周期的差异 / 产品售卖形态的说明）见 IAM 领域文档《权限与授权投影》"应用注册形态选择"一节。该收窄已落地 `simple-iam-server-starter` **1.3.1**（特权兜底仅内置应用、无清单"仅准入"隐性通道同收、门户直通限内置）；在此之前部署的版本仍覆盖全部应用——升级 1.3.1 后，依赖隐性特权访问业务应用的部署需补显式授权。注册配置口径不变：**非内置应用不给 iam_admin 配任何角色规则**。

### 用户授权三形态（何时用哪个）

| 形态 | 适用 | 说明 |
|---|---|---|
| platformAdmin 直通 | 平台管理员 | 绕过逐应用授权检查，仅限内置管理员 |
| 角色授权规则投影 | 绝大多数用户 | 授权随角色走，人员换部门/换角色后自动重算（撤权立即生效） |
| 逐人直授 | 特例（临时扩权、灰度） | 管理台用户详情「应用授权」全量替换式 PUT；与角色投影取并集 |

**撤销语义（1.3.0 起统一）**：管理面「撤销授权」= admitted 与 status **同时关闭**并留痕 revokedAt；例行投影重算（角色/规则/清单变更触发）只刷新权限内容、**不会复活已撤销的授权**——要恢复访问必须显式再授权（准入授予或 PUT admitted=true）。历史版本撤销只关 status 不关 admitted，曾产生"权限内容全新但生效位已死"的半开授权（页面表现为用户进不去应用），1.3.0 起服务端撤销已双关，旧存量半开行用一次显式再授权收口。

**菜单节点必须绑权限码**：`requiredPagePermission` 为空的 PAGE 对**所有已准入用户可见**——1.3.0 前内置仪表盘节点就是空权限，导致零权限用户被门户当作落地页送进管理台空壳页。1.3.0 起仪表盘节点要求 `iam:dashboard:page`（存量实例启动引导自动回填：清单先声明、iam_admin 规则后补、菜单节点最后落权限，三步原子完成并触发投影重算）。给可信应用配菜单树时，**任何页面节点都应绑定清单内权限码**，无权限节点等于全员可见的门户入口。

**无权限页必须有出路**：IMMERSIVE 展示模式下门户壳（侧栏/用户菜单/退出登录）整体隐藏，子应用是用户唯一可见界面——子应用自身的无权限态必须自带「返回门户 + 退出登录」出路（iam-admin-web 已内置该面板）；门户侧对未注册应用直链给「无法访问该应用」友好态。任何新子应用接入时同样遵守。

### AKSK 联调端起服三件套（缺一即熔断）

```bash
set -a; source /tmp/iam-aksk-collaboration.env; set +a   # ① VC 凭据 + reader 密钥（不入命令行）
# ② task：runAkskOwnerAuthorizationE2e（运行时外挂 iam-resource + collaboration adapter）
# ③ profile：local,e2e,owner-e2e（联调库 + 验证层 + 所属人投影层）
```

错误姿势症状对照（都实测踩过）：

| 错误姿势 | 症状 |
|---|---|
| 用 visual-acceptance task（无 adapter） | IAM Token 全部 401 → 页面反复重授权 → 前端熔断「授权验证持续失败，已暂停自动重试」 |
| 不传 profile（默认 local-test） | 联调流量打进开发库，数据牛头不对马嘴 |
| env 未 source（VC 凭据缺失） | adapter 静默不装配或验证 401，同第一种症状 |

熔断是前端会话存储里的 5 分钟保护标记（新令牌连续被拒两次触发，防 PKCE 跳转风暴）：修好服务端后清掉页面会话的 `aksk.*` 键再从门户重进即恢复。

### 重建后验证序列（必跑，全绿才算闭环）

1. `GET /iam/web/auth/providers` 探活 200（密码/手机号/域用户/单点四方式齐全）
2. 匿名 `GET /api/client` → `401`（资源链在位）
3. 门户登录 → 逐页打开该可信应用全部页面，无报错横幅（认证、准入、API、DATA 四层同验）
4. 仅持 `iam_user` 的普通用户：只见自助面菜单；SSO JIT 用户登录后同样成立（验证角色规则+JIT 角色分配两件事）
5. 自助建一枚 AKU 走通 OWNER_INHERITED 链路（验证所属人投影 + reader 流），验后清理

### 运维红线

- 服务以 gradle task 运行期间**禁用 `gradle --stop`**——它会连杀进程树下的两个 Server。
- 双端重启顺序：先 IAM 后 AKSK（依赖方先行）；Redis 会话跨重启存活，登录态不掉。
- 一切管理面写 API 带 CSRF 头；凭据只进受保护 env 文件，不进命令行回显、日志与仓库。

## 投产检查

- [ ] IAM Server、AKSK Server 和资源服务已作为独立进程部署，且 IAM 与 AKSK 不共享数据库。
- [ ] 资源服务只组合实际需要的 Provider，并配置至少一个受保护路径。
- [ ] 两个 Provider 分别使用部署侧提供的受保护验证材料。
- [ ] 每个受保护业务 API 均有一个精确、稳定的 API permission。
- [ ] IAM 与 AKSK 的应用授权上下文分别授予需要访问该 API 的同一个权限。
- [ ] `DataAccessPlan` 已在每个真实数据查询、更新或删除边界完整执行。
- [ ] 缺失、冲突、未知或 Provider 验证失败的凭据均返回 `401`，认证后的授权不足返回 `403`。
- [ ] 未启用跨 Provider fallback、权限合并、匿名 AKSK 内省或过期授权快照放行。

## 相关资料

- [IAM 用户手册](iam/USER_MANUAL.md)：IAM 从部署到接入的完整导引
- [AKSK 用户手册](aksk/USER_MANUAL.md)：AKSK 3.x 从部署到接入的完整导引
- [AKSK Server 3.0 独立应用授权闭环](aksk/server/simple-aksk-server-starter/README.md)
- [公共资源层](resource/simple-resource-server-starter/README.md)
- [IAM Resource Server Starter](iam/resource/simple-iam-resource-server-starter/README.md)
- [AKSK Resource Core](aksk/resource/simple-aksk-resource-core/README.md)
- [AKSK Resource Server Starter](aksk/resource/simple-aksk-resource-server-starter/README.md)
- [IAM / AKSK 协作 Demo](resource/simple-iam-aksk-collaboration-demo/README.md)：真实三进程 E2E 的验证参考，不是生产部署模板。
