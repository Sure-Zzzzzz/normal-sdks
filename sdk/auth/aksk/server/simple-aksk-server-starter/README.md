# Simple AKSK Server Starter

[![Version](https://img.shields.io/badge/version-2.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Authorization Server](https://img.shields.io/badge/Spring%20Authorization%20Server-0.4.1-brightgreen.svg)](https://spring.io/projects/spring-authorization-server)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> **1.x 封版文档**：如果你使用的是 1.x 版本，请查看 [README.1.x.md](README.1.x.md)。

基于 Spring Authorization Server 的 AKSK（Access Key / Secret Key）认证服务器 Starter，支持平台级和用户级 AKSK 管理，提供完整的
OAuth2 Client Credentials 授权流程、JWE Token 签发与验证、Token 全生命周期管理与审计。

---

## 特性

- ✅ **双层级 AKSK 管理**：平台级（AKP）和用户级（AKU）两种类型
- ✅ **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.1，完全符合 OAuth2 规范
- ✅ **JWE Token 签发**：JWE（A256GCMKW）加密，payload 密文不可读
- ✅ **Token 即时撤销**：`/oauth2/revoke` 撤销后 introspect 立即返回 `active=false`
- ✅ **Token 审计事件**：颁发、撤销、删除、introspect 全生命周期事件，无论是否启用 Redis 均发布
- ✅ **L1+L2 两级缓存**：Caffeine 本地缓存 + Redis 分布式缓存，introspect 热路径命中 L1 无需访问 Redis
- ✅ **多实例缓存一致性**：Redis Pub/Sub 广播缓存失效，多副本间 L1 缓存强一致
- ✅ **Admin 管理界面**：AKSK 和 Token 的创建、查询、启用/禁用、撤销、删除
- ✅ **Client 管理 API**：内网 REST API，支持创建、查询、删除、权限同步
- ✅ **Token 管理 API**：内网 REST API，支持查询、撤销、删除、统计
- ✅ **安全上下文传递**：Token 中携带自定义安全上下文信息

---

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:2.0.0'

    // 必需运行时依赖
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'mysql:mysql-connector-java:8.0.33'

    // 可选：Redis 两级缓存（推荐）
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
}
```

### 2. 初始化数据库

```bash
mysql -u root -p < docs/00_database.sql
mysql -u root -p sure_auth_aksk < docs/01_schema.sql
```

### 3. 配置应用

**最小配置（无 Redis）**：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              jwt:
                key-id: sure-auth-aksk-2026
                expires-in: 3600
                public-key: classpath:keys/public.pem
                private-key: classpath:keys/private.pem
                encryption-key: <Base64 AES-256 密钥>
              admin:
                enabled: true
                username: admin
                # password: 请在 application-local.yml 中配置
```

**推荐配置（启用 L1+L2 两级缓存）**：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              jwt:
                key-id: sure-auth-aksk-2026
                expires-in: 3600
                public-key: classpath:keys/public.pem
                private-key: classpath:keys/private.pem
                encryption-key: <Base64 AES-256 密钥>
              redis:
                enabled: true
                token:
                  me: my-app          # 应用标识，多实例需保持一致
              admin:
                enabled: true
                username: admin
        cache:
          key-prefix: sure-auth-aksk
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me:my-app}
          l1:
            enabled: true
            expire-seconds: 10        # L1 本地缓存 TTL（秒）
            max-size: 10000
          l2:
            enabled: true
            expire-seconds: ${io.github.surezzzzzz.sdk.auth.aksk.server.jwt.expires-in:3600}
            key-format: "{keyPrefix}:{me}:{cacheName}::{key}"
          consistency:
            mode: strong              # 多实例 Pub/Sub 强一致
```

### 4. 访问 Admin 管理页面

启动后访问 `http://localhost:8080/admin`，使用配置的用户名密码登录。

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

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP1234567890abcdefgh:SK1234567890abcdefghijklmnopqrstuvwxyz1234" \
  -d "grant_type=client_credentials&scope=read write"
```

响应：

```json
{
  "access_token": "<JWE 5段 Base64URL 字符串>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

> JWE Token 结构为 5 段（`Header.EncryptedKey.IV.Ciphertext.Tag`），payload 已加密，无法在客户端直接解析。

### 权限模型：Scope 与 Security Context

AKSK 的权限体系由两层组成：

| 层级 | 载体 | 生命周期 | 谁决定 | 不可覆盖 |
|------|------|---------|--------|---------|
| AKSK 权限 | **scope** | AKSK 创建时 | 签发方 | ✅ 不可被运行时覆盖 |
| 业务上下文 | **security_context** | 每次请求 token 时 | 使用方 | ❌ 可动态传入 |

**Scope**：AKSK 签发时确定的所有权限集合，包括接口权限和数据权限。写入后不可被运行时参数覆盖，是 AKSK 的能力边界。

**Security Context**：业务方自定义的运行时上下文，补充 scope 之外的动态信息（如当前租户、请求来源等）。由使用方在请求 token 时传入，不属于 AKSK 的固有能力。

> **设计原则**：scope 和 security_context 职责不重叠。如果出现字段冲突，说明权限设计有问题，应调整 scope 或 security_context 的字段划分。

#### Scope 请求方式

| 请求方式     | 说明                        |
|----------|---------------------------|
| 不传 scope | 使用 Client 注册时配置的所有 scope  |
| 指定 scope | 必须在 Client 授权范围内，可缩小但不能扩大 |

#### 最佳实践

**1. Scope 承载固定权限**

接口权限和数据权限统一写在 scope 中，AKSK 创建时确定，运行时不可变：

```
scope: "read:order write:order region:华东 level:3"
```

资源侧通过 `@RequireExpression` 鉴权表达式判断：

```java
@RequireExpression("#context['scope'] != null && (' ' + #context['scope'] + ' ').contains(' region:华东 ')")
public Order getOrder(String orderId) { ... }
```

**2. Security Context 承载动态上下文**

运行时变化的业务信息放在 security_context 中，每次请求 token 时传入：

```bash
curl -X POST /oauth2/token \
  -u "AKP...:SK..." \
  -d "grant_type=client_credentials&security_context={\"tenant_id\":\"t123\",\"request_source\":\"mobile\"}"
```

资源侧通过 `SimpleAkskSecurityContextProvider` 读取：

```java
@Autowired
private SimpleAkskSecurityContextProvider contextProvider;

String tenantId = contextProvider.get("tenant_id");
```

**3. 不要在 Security Context 中重复 Scope 已有的字段**

```
❌ scope 包含 region:华东，security_context 又传 region:华南
   → 字段冲突，说明权限设计有问题

✅ scope 包含 region:华东（数据权限，固定），security_context 传 request_source:mobile（动态上下文）
   → 职责清晰，互不重叠
```

### 撤销 Token

```bash
# 标准 OAuth2 撤销端点（client 自己撤销自己的 token）
curl -X POST http://localhost:8080/oauth2/revoke \
  -u "AKP1234567890abcdefgh:SK..." \
  -d "token=eyJraWQi..."

# 撤销后 introspect 返回 active=false（L1+L2 缓存同步清除）
curl -X POST http://localhost:8080/oauth2/introspect \
  -u "AKP1234567890abcdefgh:SK..." \
  -d "token=eyJraWQi..."
# {"active": false}
```

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

### 携带自定义安全上下文

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP...:SK..." \
  -d "grant_type=client_credentials&security_context={\"tenant_id\":\"t123\"}"
```

生成的 JWE Token 中会包含 `security_context` claim（introspect 返回的 claims 中可见）。

### introspect 端点匿名访问（可选）

适用于内网/测试环境，允许无需认证调用 introspect：

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server:
  introspect:
    require-authentication: false  # 默认 true，生产环境请勿修改
```

---

## Spring Authorization Server 默认端点

| 端点                                        | 方法   | 说明              | 鉴权                |
|-------------------------------------------|------|-----------------|-------------------|
| `/oauth2/token`                           | POST | 颁发 Access Token | Basic Auth (AKSK) |
| `/oauth2/revoke`                          | POST | 撤销 token        | Basic Auth (AKSK) |
| `/oauth2/introspect`                      | POST | 查询 token 状态     | Basic Auth (AKSK) |
| `/oauth2/jwks`                            | GET  | 公钥集合（JWK Set）   | 公开                |
| `/.well-known/oauth-authorization-server` | GET  | 服务器元数据          | 公开                |

### introspect 响应示例

```json
{
  "active": true,
  "sub": "AKP1234567890abcdefgh",
  "client_id": "AKP1234567890abcdefgh",
  "client_type": "platform",
  "scope": "read write",
  "auth_server_id": "sure-auth-aksk-2026",
  "iss": "http://your-server:8080",
  "exp": 1775638763,
  "iat": 1775635163,
  "token_type": "Bearer"
}
```

### APISIX 集成建议

| 方式       | 插件             | 优点          | 缺点                        |
|-----------|----------------|-------------|---------------------------|
| 本地验签     | jwt-auth       | 性能最好，无额外 IO | 需配置 AES-256 密钥才能解密 JWE payload |
| introspect 验证 | openid-connect | 可即时感知撤销，L1 缓存加速 | 每次请求多一次 HTTP 调用（命中 L1 时极低延迟） |

> **本地验签说明**：2.0.0 Token 为 JWE 格式，需要 APISIX 配置 AES-256 密钥才能解密 payload、提取 claims。建议生产环境优先使用 introspect 模式。

---

## Client 管理 API

| 端点                                   | 方法     | 说明                  |
|--------------------------------------|--------|---------------------|
| `/api/client`                        | POST   | 创建 Client           |
| `/api/client`                        | GET    | 查询 Client 列表（分页/批量） |
| `/api/client/{clientId}`             | GET    | 查询 Client 详情        |
| `/api/client/{clientId}`             | DELETE | 删除 Client           |
| `/api/client/{clientId}`             | PATCH  | 更新 Client（enabled/scopes/name/ownerUserId） |
| `/api/client?owner_user_id={userId}` | PATCH  | 批量同步用户权限            |
| `/api/client/{clientId}/secret`      | PUT    | 重置 Client Secret    |

以上接口需要携带有效 token，且 token 的 scope 中包含 `/api/client`。

## Token 管理 API

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

以上接口需要携带有效 token，且 token 的 scope 中包含 `/api/token`。

---

## 配置说明

### JWT 配置

| 配置项                             | 说明                          | 默认值                 |
|---------------------------------|-----------------------------|---------------------|
| `jwt.key-id`                    | JWT Key ID                  | sure-auth-aksk-2026 |
| `jwt.expires-in`                | Token 过期时间（秒）               | 3600                |
| `jwt.public-key`                | RSA 公钥（支持文件路径/PEM内容/Base64） | -                   |
| `jwt.private-key`               | RSA 私钥                      | -                   |
| `jwt.encryption-key`             | AES-256 密钥（Base64 编码，32 字节）  | - **必填**           |
| `jwt.security-context-max-size` | Security Context 最大大小（字节）   | 4096                |

### Redis 配置

| 配置项              | 说明            | 默认值     |
|------------------|---------------|---------|
| `redis.enabled`  | 是否启用 Redis 缓存 | false   |
| `redis.token.me` | 应用标识，多实例需保持一致 | default |

### SmartCache 配置（启用 Redis 时推荐配置）

| 配置项                       | 说明                                  | 默认值                                   |
|---------------------------|-------------------------------------|---------------------------------------|
| `cache.key-prefix`        | Redis key 前缀                        | sure-auth-aksk                        |
| `cache.me`                | 实例标识，建议与 `redis.token.me` 保持一致      | -                                     |
| `cache.l1.enabled`        | 是否启用 L1 本地缓存                        | true                                  |
| `cache.l1.expire-seconds` | L1 缓存 TTL（秒）                        | 10                                    |
| `cache.l1.max-size`       | L1 最大条目数                            | 10000                                 |
| `cache.l2.enabled`        | 是否启用 L2 Redis 缓存                    | true                                  |
| `cache.l2.expire-seconds` | L2 缓存 TTL（秒），建议与 JWT 过期时间一致         | 3600                                  |
| `cache.l2.key-format`     | Redis key 格式，需与 AKSK key 格式一致       | `{keyPrefix}:{me}:{cacheName}::{key}` |
| `cache.consistency.mode`  | 一致性模式：`strong`（Pub/Sub）或 `eventual` | strong                                |

### Admin 配置

| 配置项                             | 说明               | 默认值   |
|---------------------------------|------------------|-------|
| `admin.enabled`                 | 是否启用 Admin 管理页面  | true  |
| `admin.username`                | Admin 用户名        | admin |
| `admin.password`                | Admin 密码         | -     |
| `admin.session-timeout-minutes` | Session 超时时间（分钟） | 30    |

### Introspect 配置

| 配置项                                 | 说明                     | 默认值  |
|-------------------------------------|------------------------|------|
| `introspect.require-authentication` | introspect 端点是否需要客户端认证 | true |

---

## AKSK 类型说明

| 类型         | 前缀    | 用途                           |
|------------|-------|------------------------------|
| 平台级（AKP）   | `AKP` | 服务间调用、后台任务、系统级操作             |
| 用户级（AKU）   | `AKU` | 用户 API 调用、移动端、第三方集成          |
| Secret Key | `SK`  | 与 AK 配对，BCrypt 加密存储，仅创建时返回明文 |

---

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

不是。Redis 仅用于缓存提升性能，默认使用 MySQL 存储。不配置 Redis 时自动降级为纯数据库模式。

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
- Spring Data Redis（可选）
- SmartCache（Caffeine L1 + Redis L2）
- Thymeleaf
- Lombok / JUnit 5

## 许可证

Apache License 2.0
