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

跨模块联调阶段可使用相同的 Gradle `project(...)` 依赖；发布坐标以实际已发布版本为准，不能把本地候选版本当成已发布版本。

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
    implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.0.1' // AKSK Provider
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
