# Simple AKSK Server Starter

> 当前版本 **3.1.0**。版本沿革见各 `CHANGELOG.*.md`。  
> 2.x 冻结快照见 [README.2.x.md](README.2.x.md)。  
> 1.x 冻结快照见 [README.1.x.md](README.1.x.md)。

[![Version](https://img.shields.io/badge/version-3.1.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Authorization Server](https://img.shields.io/badge/Spring%20Authorization%20Server-0.4.1-brightgreen.svg)](https://spring.io/projects/spring-authorization-server)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)


基于 Spring Authorization Server 的 AKSK（Access Key / Secret Key）认证服务器 Starter，支持平台级和用户级 AKSK 管理，提供完整的
OAuth2 Client Credentials 授权流程、JWE Token 签发与验证、Token 全生命周期管理与审计。

---

## 特性

- ✅ **双层级 AKSK 管理**：平台级（AKP）和用户级（AKU）两种类型
- ✅ **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.1，完全符合 OAuth2 规范
- ✅ **JWE Token 签发**：JWE（A256GCMKW）加密，payload 密文不可读
- ✅ **Token 即时撤销**：`/oauth2/revoke` 撤销后 introspect 立即返回 `active=false`
- ✅ **Token 审计事件**：颁发、撤销、删除、introspect 全生命周期事件；可选 3.0 审计 listener 在事务提交后投递不含 Token 原文的记录
- ✅ **L1+L2 两级缓存**：Caffeine 本地缓存 + Redis 分布式缓存，introspect 热路径命中 L1 无需访问 Redis
- ✅ **多实例缓存一致性**：Redis Pub/Sub 广播缓存失效，多副本间 L1 缓存强一致
- ✅ **Admin 管理界面**：AKSK 和 Token 的创建、查询、启用/禁用、撤销、删除
- ✅ **Client 管理 API**：内网 REST API，支持创建、查询、删除、权限同步
- ✅ **Token 管理 API**：内网 REST API，支持查询、撤销、删除、统计
- ✅ **公共资源层鉴权（3.1.0）**：`/api/**` 由 `simple-resource-server-starter` 统一鉴权（kid 路由、API permission、DATA plan），配置错误启动即拦；部署方可选外插 IAM 资源层让同一 `/api/**` 接受 IAM 人员 Token
- ✅ **安全上下文传递**：Token 中携带自定义安全上下文信息

---

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    // AKSK Server 本体。simple-aksk-core / simple-aksk-server-core /
    // simple-application-authorization-core / simple-data-permission-core、
    // spring-boot-starter-data-redis 与 Spring Authorization Server 以 api 传递；
    // smart-cache、smart-redis-limiter、公共资源层、route 等实现细节以 implementation /
    // runtimeOnly 传递运行时，使用方无需重复声明
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:3.1.0'

    // 必需：宿主 Web / Security / JPA（starter 以 compileOnly 口径声明，使用方自备）
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Admin 管理页面模板引擎（admin.enabled 默认开启；显式关闭后可省略）
    runtimeOnly 'org.springframework.boot:spring-boot-starter-thymeleaf'

    // 数据库驱动（MySQL 必需；无论直连 spring.datasource.* 还是 mysql-route 接管均由使用方提供）
    runtimeOnly 'mysql:mysql-connector-java:8.0.33'
}
```

### 2. 初始化数据库

```bash
# 新部署或允许重建时：执行 3.0.0 完整初始化脚本
mysql -u <database-user> -p <database-name> < docs/01_schema_3.0.0.sql

# 从 2.x 升级：先备份并停写，再仅执行一次升级脚本
mysql -u <database-user> -p <database-name> < docs/02_upgrade_3.0.0.sql
```

> `01_schema_3.0.0.sql` 会重建 AKSK 相关表，仅用于新部署或确认允许清空数据的环境。`02_upgrade_3.0.0.sql` 仅用于 2.x 升级：它保留现有 Client 与 Token 数据、新建应用授权投影表，但不会从旧 Scope 自动推导或自动准入任何授权；每个 Client 在首次 3.0 准入前必须先处理其 2.x 存量活跃 Token，再配置完整授权并显式准入。同一数据库不要重复执行。进入 3.0 后，完整替换或撤销应用授权会事务性撤销该 Client 的活跃 Token，历史 Token 不会因当前投影更新而获得新授权。

### 3. 配置应用

下面是一份**完整配置**（键值即默认值，`<...>` 为必填项）。每行注释解释该配置的作用，按需精简：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              jwt:
                key-id: sure-auth-aksk-2026        # JWT Key ID。签发侧自动包装 aksk/ 路由前缀，配置值不得重复携带前缀或非法字符（启动校验拦截）
                expires-in: 3600                   # Token 有效期（秒）
                public-key: classpath:keys/public.pem    # RSA 公钥，支持 classpath 路径 / PEM 内容 / Base64 三种形态
                private-key: classpath:keys/private.pem  # RSA 私钥，形态同上
                encryption-key: <Base64 AES-256 密钥>    # JWE 加密密钥（必填），openssl rand -base64 32 生成，32 字节
                security-context-max-size: 4096    # security_context claim 最大字节数，超出拒绝签发
              redis:
                token:
                  me: my-aksk-server               # 应用标识：Token 缓存的 Redis 命名空间。同一集群所有实例必须一致
              limiter:
                oauth2:
                  enable: true                     # OAuth2 端点限流总开关，覆盖 /oauth2/token、/oauth2/introspect、/oauth2/revoke
                  token:
                    algorithm: sliding             # token 端点限流算法
                    fallback: deny                 # token 端点限流执行异常时的策略：deny 拒绝 / allow 放行
                    key-strategy: ip               # 取不到 clientId 时的回退限流维度（默认按 clientId 限流）
                  introspect:
                    fallback: allow                # introspect 端点限流异常策略
                  revoke:
                    fallback: allow                # revoke 端点限流异常策略
              admin:
                enabled: true                      # Admin 管理台开关。false 时 /admin 整链不装配（门户形态），启动日志提示"管理台已停用"
                username: admin                    # Admin 登录用户名
                password: <管理员密码>              # Admin 登录密码（必填），走受保护的部署配置
                session-timeout-minutes: 30        # Admin 会话超时（分钟）
          resource:
            server:
              enabled: true                      # 公共资源层开关。显式 false 会被启动校验拒绝（/api 裸奔守护），保持默认即可
              # protected-paths: [/api/**]       # 资源层保护路径。缺省自动补全 /api/**，显式配置不得摘除（启动校验拦截）
        limiter:
          redis:
            smart:
              enable: true                         # smart-redis-limiter 自动配置开关，OAuth2 限流复用其算法与 Redis Bean
              me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}  # 限流命名空间，必须与 redis.token.me 一致
              mode: annotation                     # annotation / interceptor / both。AKSK 自身用 annotation；需要 MVC 接口限流时改 interceptor/both
              interceptor:
                enabled: false                     # MVC 拦截器开关（mode 含 interceptor 时生效）
                exclude-patterns: [/actuator/**]   # 拦截器排除路径
              # rules:                             # MVC 限流规则（可选），每条含 path-pattern / key-strategy / algorithm / fallback / limits
              #   - path-pattern: /api/**
              #     key-strategy: ip
              #     algorithm: sliding
              #     fallback: allow
              #     limits:
              #       - count: 600
              #         window: 1
              #         unit: MINUTES
        cache:
          key-prefix: sure-auth-aksk               # Redis key 前缀
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}        # 缓存命名空间，必须与 redis.token.me 一致
          l1:
            enabled: true                          # Caffeine 本地一级缓存（introspect 热路径免 Redis）
            expire-seconds: 10                     # L1 TTL（秒），越短撤销感知越快、Redis 压力越大
            max-size: 10000                        # L1 最大条目数
          l2:
            enabled: true                          # Redis 分布式二级缓存
            expire-seconds: ${io.github.surezzzzzz.sdk.auth.aksk.server.jwt.expires-in:3600}  # 建议与 Token 有效期一致
            key-format: "{keyPrefix}:{me}:{cacheName}::{key}"   # Redis key 格式，需与 AKSK key 格式一致
          consistency:
            mode: strong                           # strong（Pub/Sub 广播失效，多实例强一致）/ eventual（不广播）
```

> **三个 `me` 必须同值**：`redis.token.me`、`limiter.redis.smart.me`、`cache.me` 是同一个应用标识。同集群不同实例配不同 `me`，Token 缓存、L1 失效广播和 OAuth2 限流会落到不同 Redis 命名空间。
>
> **启动期 fail-fast 三项**（配错直接拒绝启动）：`jwt.key-id` 含路由前缀或非法字符；`auth.resource.server.enabled=false`；protected-paths 摘掉 `/api/**`。

### 4. 访问 Admin 管理页面

启动后访问部署地址的 `/admin`，使用部署侧配置的管理员账号登录。

### 可选：接入 Token 审计 listener

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:3.0.0'
```

该扩展不由 Starter 自动引入。应用授权完整替换或撤销会为每个实际失效 Token 产生 `REVOKED` 事件，`cause` 分别为 `APPLICATION_AUTHORIZATION_REPLACED` 或 `APPLICATION_AUTHORIZATION_REVOKED`；详见审计 listener 的 README。Handler 接收的审计 record 不含 Token 原文。

---

## 使用指南

### 创建 AKSK

**通过 Admin 页面**：登录后点击"创建平台级AKSK"或"创建用户级AKSK"，保存生成的 Client ID 和 Client Secret（仅显示一次）。

**通过代码**：

```java

@Autowired
private ClientManagementService clientManagementService;

// 平台级
ClientInfo client = clientManagementService.createPlatformClient("My Service");

// 用户级
ClientInfo client = clientManagementService.createUserClient("user123", "张三", "My Client");
```

### 获取 Access Token

使用标准 OAuth2 Client Credentials 请求 `/oauth2/token`。Client ID 和 Client Secret 仅通过受保护的部署配置提供给调用方；不要把认证信息、Access Token 或完整响应写入文档、日志和测试输出。

请求的 `scope` 只能在 Client 注册范围内缩小，且不参与 API permission 或 DATA grant 推导。

> JWE Token 结构为 5 段（`Header.EncryptedKey.IV.Ciphertext.Tag`），payload 已加密，无法在客户端直接解析。

### 撤销 Token

调用标准 OAuth2 `/oauth2/revoke` 可撤销当前 Client 的 Token。撤销后 introspect 返回 `active=false`，并同步清除 L1、L2 缓存。认证材料、Token 值和 introspection 完整响应均不应记录或展示。

### 监听审计事件

```java

@EventListener
public void onTokenIssued(TokenIssuedEvent event) {
    log.info("Token issued: clientId={}, scopes={}", event.getClientId(), event.getScopes());
}

@EventListener
public void onTokenRevoked(TokenRevokedEvent event) {
    log.info("Token revoked: clientId={}, eventTime={}", event.getClientId(), event.getEventTime());
}

@EventListener
public void onTokenIntrospected(TokenIntrospectedEvent event) {
    log.info("Token introspected: clientId={}, active={}", event.getClientId(), event.isActive());
}
```

所有事件通过 `event.getEventType()` 获取 `TokenEventType` 枚举，无需硬编码字符串。

### introspect 调用边界

`/oauth2/introspect` 必须使用已认证的 Client 调用。资源服务应通过受保护配置保存内省客户端认证材料；不要启用匿名内省，也不要记录认证材料、Token 或完整响应。

### 3.0 权限模型：OAuth Scope、security_context 与 aksk_authorization

3.0 将 OAuth 请求范围、运行时业务上下文和可信应用授权快照严格分离：

| 载体 | 作用 | 来源 | 是否可信授权来源 |
|------|------|------|------------------|
| `scope` | OAuth2 请求范围，决定本次令牌请求是否在 Client 注册范围内 | OAuth Client 配置与 token 请求 | 否，不推导 API permission 或 DATA grant |
| `security_context` | 业务运行时上下文，例如租户、用户或请求来源 | 每次 token 请求由调用方传入 | 否，不得作为 API/DATA 授权来源 |
| `aksk_authorization` | 服务端保存的应用授权快照，包含角色、页面权限、精确 API permission 和 `DataGrantDocument` | Admin 页面或应用授权管理 REST | 是，签发时从当前投影生成 |

Client 创建只注册 OAuth Client，不自动创建应用授权。没有启用且已准入的应用授权投影时，Server 默认拒绝签发带授权快照的 Token；因此接入顺序必须是：

1. 创建 Client，并安全保存一次性 Client Secret；
2. 在 Admin 的“应用授权管理”页面，或 `/api/application-authorization` REST 资源中完整配置应用编码、准入状态、角色、页面权限、精确 API permission 和 DATA 文档；
3. 显式准入后再调用 `/oauth2/token` 获取 Token；
4. 资源服务按 Token 中的 `aksk_authorization` 快照执行 API 与 DATA 校验。

#### OAuth Scope 请求方式

| 请求方式 | 说明 |
|---------|------|
| 不传 `scope` | 使用 Client 注册的默认 OAuth Scope |
| 指定 `scope` | 只能在 Client 注册范围内缩小请求范围，不能扩大权限 |

Scope 不是 API permission，也不是 DATA grant。不要使用 `/api/*`、`data:*` 或 `data:dept:*` 这类 Scope 文本代替应用授权；应用授权中的 permission 和 DATA grant 必须由服务端明确配置并严格校验。

#### security_context 使用边界

`security_context` 适合携带每次请求变化的业务上下文：

```bash
curl -X POST https://<aksk-server>/oauth2/token \
  -u "<client-id>:<client-secret>" \
  -d "grant_type=client_credentials" \
  -d 'security_context={"tenant_id":"<tenant-id>","request_source":"<request-source>"}'
```

资源侧可通过 `SimpleAkskSecurityContextProvider` 读取它，但必须把它当作不可信输入；租户范围、API permission 和 DATA grant 仍以 `aksk_authorization` 为准。

---

## Spring Authorization Server 默认端点

| 端点                                        | 方法   | 说明              | 鉴权                |
|-------------------------------------------|------|-----------------|-------------------|
| `/oauth2/token`                           | POST | 颁发 Access Token | Basic Auth (AKSK) |
| `/oauth2/revoke`                          | POST | 撤销 token        | Basic Auth (AKSK) |
| `/oauth2/introspect`                      | POST | 查询 token 状态     | Basic Auth (AKSK) |
| `/oauth2/jwks`                            | GET  | 公钥集合（JWK Set）   | 公开                |
| `/.well-known/oauth-authorization-server` | GET  | 服务器元数据          | 公开                |

### introspect 响应

introspect 用于确认 Token 是否有效及读取经过服务端校验的 claims。生产调用方仅应消费自身所需字段；不要记录或对外暴露完整 introspection 响应。

### APISIX 集成建议

| 方式       | 插件/实现方式             | 优点          | 缺点                        |
|-----------|----------------------|-------------|---------------------------|
| introspect 验证 | 调用 `/oauth2/introspect` | 可即时感知撤销，L1 缓存加速 | 每次请求多一次 HTTP 调用（命中 L1 时极低延迟） |
| 本地验证     | 自定义插件/业务自行实现 JWE 解密与 claims 校验 | 无额外 introspect HTTP 调用 | 需要安全分发 AES-256 密钥，且必须自行处理撤销状态一致性 |

> **推荐方式**：AKSK Token 为 JWE 格式，APISIX 内置 `jwt-auth` 不能直接验证该 JWE。生产环境建议通过 introspect 验证，由 AKSK Server 统一处理 JWE 解密、撤销状态和缓存一致性。

---

## 管理 API

`/api/**` 是机器管理控制面（3.1.0 起走公共资源层鉴权）：调用 Token 必须包含对应端点的精确 API permission；涉及读取、更新、删除或批量操作时，还必须通过目标资源的 `DataAccessPlan` 校验。Scope 与 `security_context` 均不能替代这两项校验。Admin 页面使用本地 session + form login + CSRF，与机器 REST 是不同的控制面。

### 应用授权管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/application-authorization` | 为已有 Client 创建首次授权投影，重复创建返回 409 |
| `GET` | `/api/application-authorization/{clientId}` | 查询指定 Client 授权 |
| `GET` | `/api/application-authorization` | 按 `DataAccessPlan` 过滤后再统计和分页 |
| `PUT` | `/api/application-authorization/{clientId}` | 完整替换授权，递增授权版本并撤销该 Client 活跃 Token |
| `POST` | `/api/application-authorization/{clientId}/revoke` | 撤销授权并撤销该 Client 活跃 Token |

### Client 管理

| 端点                                   | 方法     | 说明                  |
|--------------------------------------|--------|---------------------|
| `/api/client`                        | POST   | 创建 Client           |
| `/api/client`                        | GET    | 查询 Client 列表（分页/批量） |
| `/api/client/{clientId}`             | GET    | 查询 Client 详情        |
| `/api/client/{clientId}`             | DELETE | 删除 Client           |
| `/api/client/{clientId}`             | PATCH  | 更新 Client（enabled、OAuth Scope、名称或归属） |
| `/api/client?owner_user_id={userId}` | PATCH  | 批量同步用户 OAuth Scope |
| `/api/client/{clientId}/secret`      | PUT    | 重置 Client Secret    |

### Token 管理

| 端点                       | 方法     | 说明                         |
|--------------------------|--------|----------------------------|
| `/api/token`             | GET    | 查询 Token 列表（MySQL）         |
| `/api/token`             | DELETE | 批量撤销指定 Client 下所有活跃 Token  |
| `/api/token/redis`       | GET    | 查询 Redis 缓存中的 Token 列表     |
| `/api/token/{id}`        | GET    | 查询 Token 详情                |
| `/api/token/{id}/revoke` | POST   | 撤销 Token（同步清除 L1+L2 缓存并广播） |
| `/api/token/{id}`        | DELETE | 删除 Token（先撤销再删除）           |
| `/api/token/expired`     | DELETE | 清理过期 Token                 |
| `/api/token/statistics`  | GET    | 获取 Token 统计信息              |

---

## 与 IAM 协作（可选）

### 业务资源服务：双身份接入

AKSK Server 可以独立部署和使用。若同一业务 API 还需要接受 IAM 人员身份，应由业务资源服务组合公共资源层、IAM Provider 与 AKSK Provider；认证源二选一，不共享数据库、不回退认证，也不合并权限。接入方式见 [IAM 与 AKSK 协作接入](../../../README.IAM-AKSK协作.md)。

### 本 Server：让 /api 接受 IAM 人员 Token

默认形态下 `/api/**` 只认本 Server 签发的 JWE Token（AKP / AKU 机器调用）。部署方若需要让门户或自建前端以 IAM 人员身份调这些管理 API，只需在**部署工程**（不是给本 starter 加依赖）引入 IAM 资源层并配置回源校验：

```gradle
implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          iam:
            resource:
              server:
                verification-endpoint: http://<iam-host>/iam/resource/tokens/verify
                client-id: <IAM 侧签发的 verification client id>
                client-secret: <凭据，走受保护的部署配置，不要写进代码仓库>
```

配置生效后，同一个 `/api/**` 端点接受两种身份：AKSK Token（client credentials 换取）与 IAM Token（门户登录换取），均按各自授权通过 API permission 与 DATA 过滤。

#### 原理：两种 Token 各自怎么验

- **AKSK 半边不需要引 aksk-resource**。`/api` 收到的 AKSK Token 是本 Server 自己签发的 JWE：本 Server 持有解密密钥，自身就是权威源，内置的 `JweResourceAuthenticationAdapter` 直接本地解密验真。此时若引入 `simple-aksk-resource-server-starter`，它的校验方式是把每个请求经 HTTP 打回本 Server 的 `/oauth2/introspect`——自己调自己、校验自己刚签的 token，凭据配置、进程内 HTTP 往返、OAuth2 端点限流挤占全是白付。
- **IAM 半边必须外插 iam-resource**。IAM Token 不是本 Server 签发的，本 Server 没有校验它的任何材料。`simple-iam-resource-server-starter` 的做法是把 Token 回源到 IAM 的 verify 端点，由签发方给结论，本 Server 只消费结果——谁签发，谁校验。
- **两个来源如何并存**。Token header 里的 `kid` 带来源前缀（`aksk/xxx`、`iam/xxx`），公共资源层按前缀路由到对应 Provider，两套身份互不干扰。

一句话：验外来身份回源，验自家身份本地解。业务服务两个都外插（都不持有密钥）；本 Server 作为 AKSK 签发者，AKSK 半边天然本地解，只外插 IAM 半边。

授权语义不变：IAM 主体调 `/api/**` 同样要过精确 API permission 与 `DataAccessPlan` 过滤，权限码（`akskClient:*`、`akskToken:*`、`akskApplicationAuthorization:*` 等）与数据范围在 IAM 侧为该人员配置；本 Server 零代码改动，不引入 iam-resource 时行为与默认形态完全一致。

---

## AKSK 类型说明

| 类型         | 前缀    | 用途                           |
|------------|-------|------------------------------|
| 平台级（AKP）   | `AKP` | 服务间调用、后台任务、系统级操作             |
| 用户级（AKU）   | `AKU` | 用户 API 调用、移动端、第三方集成          |
| Secret Key | `SK`  | 与 AK 配对，BCrypt 加密存储，仅创建时返回明文 |

---

## 文档导航

- [3.0.0 新装手册](docs/03_install_3.0.0.md)
- [2.x 升级手册](docs/04_upgrade_2.x_to_3.0.0.md)
- [运维手册](docs/05_operations_3.0.0.md)
- [发布验收清单](docs/06_release_acceptance_3.0.0.md)
- [依赖解析验证](docs/07_dependency_resolution_3.0.0.md)

## 常见问题

### 1. 如何生成 RSA 密钥对？

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

### 2. 如何生成 AES-256 密钥？

```bash
openssl rand -base64 32
```

### 3. 如何禁用 Admin 管理页面？

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server.admin.enabled: false
```

### 4. Redis 是否必需？

是。3.0.0 起 Redis 为必需基础设施，用于 Token 缓存、撤销同步、多实例 L1 缓存失效广播和 OAuth2 端点限流。

### 5. 多实例部署时缓存如何保持一致？

`consistency.mode=strong` 时，任意实例 revoke token 后会通过 Redis Pub/Sub 广播缓存失效消息，其他实例的 L1
缓存立即清除。每个实例启动时生成唯一 UUID 作为实例标识，不会误忽略其他实例的消息。

### 6. Token 撤销后 introspect 仍返回 active=true？

检查以下几点：

- `redis.token.me` 与 `cache.me` 是否一致
- `cache.consistency.mode` 是否为 `strong`
- Redis Pub/Sub 连接是否正常（查看启动日志中 `Cache invalidation listener initialized` 是否出现）

### 7. 启动时出现 `Cannot load module CasJackson2Module` 警告

无害警告，如需屏蔽：

```yaml
logging:
  level:
    org.springframework.security.jackson2: ERROR
```

---

## 版本历史

### 3.1.0 (2026-09-01)

Management API 授权重构——`/api/**` 鉴权链移交公共资源层（`simple-resource-server-starter` 1.1.1）；启动期 fail-fast 校验（keyId 路由前缀 / 资源层开关 / protected-paths 守护）；中间件依赖对齐 route 1.2.2 版本线（cache 2.2.0、limiter 2.1.0）与依赖口径收紧；测试配置 MySQL 连接切 mysql-route 接管；README 新增 iam-resource 外插接入教程。详见 [CHANGELOG.3.1.0.md](CHANGELOG.3.1.0.md)。

### 3.0.0 (2026-08-19)

应用授权自闭环版本。新增 AKSK Server 自主维护的 `aksk_application_authorization` 投影与 Admin / REST 管理入口；Token 签发和内省按已启用、已准入的授权快照 fail-close。管理 REST 从 Scope 模型迁移至精确 API permission + `DataAccessPlan`；Admin Secret 改为认证会话一次性交付。详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.3 (2026-06-22)

OAuth2 端点限流 + Redis 必需化。新增 `/oauth2/token`、`/oauth2/introspect`、`/oauth2/revoke` Security Filter 限流，复用 `smart-redis-limiter-starter:1.1.4`，默认按 clientId provider 维度计数。详见 [CHANGELOG.2.0.3.md](CHANGELOG.2.0.3.md)。

### 2.0.2 (2026-06-15)

性能优化 + 依赖升级，向后兼容。

**新增：Client Entity 两级缓存**。`/oauth2/token` 请求中 `OAuth2RegisteredClientEntity.findByClientId` 的 JPA 查询次数从 2~3 次降至 0~1 次（首次 1 次，后续命中 L1）。详见 [CHANGELOG.2.0.2.md](CHANGELOG.2.0.2.md)。

### 1.1.3 (2026-05-01)

新增功能：

- **AKU 归属信息可修改**：新增 `updateOwnerInfo()` 接口，支持修改用户级 AKSK 的 ownerUserId/ownerUsername
- **Admin 编辑归属信息**：详情页新增"编辑归属信息"按钮（仅 AKU 显示）
- **`PATCH /api/client/{clientId}`**：新增内网 API，支持更新 enabled/scopes/name/ownerUserId

Bug 修复：

- **Admin 详情页编辑操作无响应**：修复 JS 通过 `data.success` 判断成功导致操作永远失败的问题，改用 `response.ok`

详见 [CHANGELOG.1.1.3.md](CHANGELOG.1.1.3.md)

### 1.1.2 (2026-04-28)

Bug 修复：

- **删除 Client 时不撤销关联 Token**：`deleteClient()` 现在先撤销所有活跃 Token 再删除记录
- **重置 Secret 后页面数据不回填**：`createSuccess()` 控制器读取 URL 参数并填充到页面
- **Token 列表页 REVOKED 过滤结果未验证**：补充验证逻辑

优化：

- Token 详情页 REVOKED 状态撤销按钮禁用
- Admin 首页和详情页断言增强
- Client 列表页 enabled 过滤增强
- create-success 页面动态标题（Secret 重置 / AKSK 创建）

测试覆盖：

- 全项目审计 27 个测试文件，修复 50+ 处断言问题（7 CRITICAL、8 HIGH、15+ MEDIUM、5 LOW）

详见 [CHANGELOG.1.1.2.md](CHANGELOG.1.1.2.md)

### 1.1.1 (2026-04-27)

新增功能：

- **重置 Client Secret**：支持单独调用接口重置 Secret，可选是否同时撤销所有 Token
- **批量撤销 Token**：支持通过 clientId 批量撤销该 Client 下所有活跃 Token
- **过期 Token 处理优化**：已过期的 Token 不能再被撤销，只允许通过 `deleteExpiredTokens()` 清理
- **Admin 界面增强**：详情页新增"撤销所有Token"和"重置Secret"按钮，Token列表页EXPIRED状态撤销按钮禁用

Bug 修复：

- 删除 Token 时若 MySQL 中已不存在不再抛出异常
- 只在 Redis 中存在的 Token 撤销时正确发布 `TokenRevokedEvent`
- Redis 列表页撤销 Token 后刷新状态正确更新
- Token 详情页补充 REVOKED 状态显示，REVOKED 状态下隐藏剩余时间行

### 1.1.0 (2026-04-17)

引入 SmartCache L1+L2 两级缓存，Redis Pub/Sub 多实例强一致。详见 [CHANGELOG.1.1.0.md](CHANGELOG.1.1.0.md)

### 1.0.7 (2026-04-15)

Admin 清理过期 Token 接口路径修复。详见 [CHANGELOG.1.0.7.md](CHANGELOG.1.0.7.md)

### 1.0.6 (2026-04-13)

Introspect 端点匿名访问支持、Admin session 超时白屏修复。详见 [CHANGELOG.1.0.6.md](CHANGELOG.1.0.6.md)

### 1.0.5 (2026-04-10)

Token 全生命周期审计事件、Token 撤销能力、Admin 撤销操作。详见 [CHANGELOG.1.0.5.md](CHANGELOG.1.0.5.md)

### 1.0.4 (2026-xx-xx)

Admin 编辑权限页面 UX 优化。详见 [CHANGELOG.1.0.4.md](CHANGELOG.1.0.4.md)

### 1.0.3 (2026-xx-xx)

OAuth2 scope 权限控制、JWT 安全上下文提取、统一异常处理。详见 [CHANGELOG.1.0.3.md](CHANGELOG.1.0.3.md)

### 1.0.2 (2026-02-04)

JWT 新增 `auth_server_id` claim。详见 [CHANGELOG.1.0.2.md](CHANGELOG.1.0.2.md)

### 1.0.1 (2026-01-31)

scope 参数可选、Admin 页面增强。详见 [CHANGELOG.1.0.1.md](CHANGELOG.1.0.1.md)

### 1.0.0 (2026-01-19)

初始版本发布。

---

## 技术栈

- Spring Boot 2.7.x
- Spring Authorization Server 0.4.1
- Spring Data JPA + MySQL
- Spring Data Redis
- SmartCache（Caffeine L1 + Redis L2）
- Thymeleaf
- Lombok / JUnit 5

## 许可证

Apache License 2.0
