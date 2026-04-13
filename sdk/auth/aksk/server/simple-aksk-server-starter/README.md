# Simple AKSK Server Starter

[![Version](https://img.shields.io/badge/version-1.0.6-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Authorization Server](https://img.shields.io/badge/Spring%20Authorization%20Server-0.4.1-brightgreen.svg)](https://spring.io/projects/spring-authorization-server)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 Spring Authorization Server 的 AKSK（Access Key / Secret Key）认证服务器 Starter，支持平台级和用户级 AKSK 管理，提供完整的 OAuth2 Client Credentials 授权流程、Token 全生命周期管理与审计。

---

## 特性

- ✅ **双层级 AKSK 管理**：平台级（AKP）和用户级（AKU）两种类型
- ✅ **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.1，完全符合 OAuth2 规范
- ✅ **JWT Token 签发**：RSA 算法签发，支持自定义公私钥和 `auth_server_id`
- ✅ **Token 即时撤销**：`/oauth2/revoke` 撤销后 introspect 立即返回 `active=false`
- ✅ **Token 审计事件**：颁发、撤销、删除、introspect 全生命周期事件，无论是否启用 Redis 均发布
- ✅ **Redis 缓存支持**：可选，提升 Token 验证性能
- ✅ **Admin 管理界面**：AKSK 和 Token 的创建、查询、启用/禁用、撤销、删除
- ✅ **Client 管理 API**：内网 REST API，支持创建、查询、删除、权限同步
- ✅ **Token 管理 API**：内网 REST API，支持查询、撤销、删除、统计
- ✅ **安全上下文传递**：Token 中携带自定义安全上下文信息

---

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.0.6'

    // 必需运行时依赖
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'mysql:mysql-connector-java:8.0.33'

    // 可选：Redis 缓存
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

### 2. 初始化数据库

```bash
mysql -u root -p < docs/00_database.sql
mysql -u root -p sure_auth_aksk < docs/01_schema.sql
```

### 3. 配置应用

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sure_auth_aksk?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    # password: 请在 application-local.yml 中配置

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
              redis:
                enabled: false
                token:
                  me: my-app
              admin:
                enabled: true
                username: admin
                # password: 请在 application-local.yml 中配置
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
  "access_token": "eyJraWQiOiJzdXJlLWF1dGgtYWtzay0yMDI2IiwiYWxnIjoiUlMyNTYifQ...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

### Scope 说明

| 请求方式 | 说明 |
|---------|------|
| 不传 scope | 使用 Client 注册时配置的所有 scope |
| 指定 scope | 必须在 Client 授权范围内，可缩小但不能扩大 |

### 撤销 Token

```bash
# 标准 OAuth2 撤销端点（client 自己撤销自己的 token）
curl -X POST http://localhost:8080/oauth2/revoke \
  -u "AKP1234567890abcdefgh:SK..." \
  -d "token=eyJraWQi..."

# 撤销后 introspect 返回 active=false
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

生成的 JWT 中会包含 `security_context` claim。

### introspect 端点匿名访问（可选）

适用于内网/测试环境，允许无需认证调用 introspect：

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server:
  introspect:
    require-authentication: false  # 默认 true，生产环境请勿修改
```

---

## Spring Authorization Server 默认端点

| 端点 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/oauth2/token` | POST | 颁发 Access Token | Basic Auth (AKSK) |
| `/oauth2/revoke` | POST | 撤销 token | Basic Auth (AKSK) |
| `/oauth2/introspect` | POST | 查询 token 状态 | Basic Auth (AKSK) |
| `/oauth2/jwks` | GET | 公钥集合（JWK Set） | 公开 |
| `/.well-known/oauth-authorization-server` | GET | 服务器元数据 | 公开 |

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

| 方式 | 插件 | 优点 | 缺点 |
|------|------|------|------|
| 本地验签 | jwt-auth | 性能最好，无额外 IO | 无法感知 token 撤销 |
| introspect 验证 | openid-connect | 可即时感知撤销 | 每次请求多一次 HTTP 调用 |

---

## Client 管理 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/client` | POST | 创建 Client |
| `/api/client` | GET | 查询 Client 列表（分页/批量） |
| `/api/client/{clientId}` | GET | 查询 Client 详情 |
| `/api/client/{clientId}` | DELETE | 删除 Client |
| `/api/client?owner_user_id={userId}` | PATCH | 批量同步用户权限 |

以上接口需要携带有效 token，且 token 的 scope 中包含 `/api/client`。

## Token 管理 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/token` | GET | 查询 Token 列表（MySQL） |
| `/api/token/redis` | GET | 查询 Redis 中的 Token 列表 |
| `/api/token/{id}` | GET | 查询 Token 详情 |
| `/api/token/{id}/revoke` | POST | 撤销 Token |
| `/api/token/{id}` | DELETE | 删除 Token（先撤销再删除） |
| `/api/token/expired` | DELETE | 清理过期 Token |
| `/api/token/statistics` | GET | 获取 Token 统计信息 |

以上接口需要携带有效 token，且 token 的 scope 中包含 `/api/token`。

---

## 配置说明

### JWT 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `jwt.key-id` | JWT Key ID | sure-auth-aksk-2026 |
| `jwt.expires-in` | Token 过期时间（秒） | 3600 |
| `jwt.public-key` | RSA 公钥（支持文件路径/PEM内容/Base64） | - |
| `jwt.private-key` | RSA 私钥 | - |
| `jwt.security-context-max-size` | Security Context 最大大小（字节） | 4096 |

### Redis 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `redis.enabled` | 是否启用 Redis 缓存 | false |
| `redis.token.me` | 应用标识，用于多实例隔离 | default |

### Admin 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `admin.enabled` | 是否启用 Admin 管理页面 | true |
| `admin.username` | Admin 用户名 | admin |
| `admin.password` | Admin 密码 | - |
| `admin.session-timeout-minutes` | Session 超时时间（分钟） | 30 |

### Introspect 配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `introspect.require-authentication` | introspect 端点是否需要客户端认证 | true |

---

## AKSK 类型说明

| 类型 | 前缀 | 用途 |
|------|------|------|
| 平台级（AKP） | `AKP` | 服务间调用、后台任务、系统级操作 |
| 用户级（AKU） | `AKU` | 用户 API 调用、移动端、第三方集成 |
| Secret Key | `SK` | 与 AK 配对，BCrypt 加密存储，仅创建时返回明文 |

---

## 常见问题

### 1. 如何生成 RSA 密钥对？

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

### 2. 如何禁用 Admin 管理页面？

```yaml
io.github.surezzzzzz.sdk.auth.aksk.server.admin.enabled: false
```

### 3. Redis 是否必需？

不是。Redis 仅用于缓存 OAuth2 授权信息提升性能，默认使用 MySQL 存储。

### 4. 启动时出现 `Cannot load module CasJackson2Module` 警告

无害警告，如需屏蔽：

```yaml
logging:
  level:
    org.springframework.security.jackson2: ERROR
```

### 5. token 撤销后 introspect 仍返回 active=true？

确认 Redis 缓存已正确清除。撤销操作会同时 evict Redis 缓存，如果仍有问题请检查 `redis.token.me` 配置是否与 server 一致。

---

## 版本历史

### 1.0.6 (2026-04-13)
Introspect 端点匿名访问支持、Admin session 超时白屏修复、换 Token 测试页面增强。详见 [CHANGELOG.1.0.6.md](CHANGELOG.1.0.6.md)

### 1.0.5 (2026-04-10)
Token 全生命周期审计事件、Token 撤销能力、Admin 撤销操作、品牌 Logo。详见 [CHANGELOG.1.0.5.md](CHANGELOG.1.0.5.md)

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
- Thymeleaf
- Lombok / JUnit 5

## 许可证

Apache License 2.0
