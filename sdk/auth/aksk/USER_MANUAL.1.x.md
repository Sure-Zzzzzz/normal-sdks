# Simple AKSK 用户手册（1.x 封版）

> **说明**：此文档为 1.x 版本的冻结快照。2.0.0 用户手册请查看 [USER_MANUAL.md](USER_MANUAL.md)。

## 目录

- [1. 概述](#1-概述)
- [2. 架构设计](#2-架构设计)
- [3. 模块总览](#3-模块总览)
- [4. 服务端模块](#4-服务端模块)
- [5. 客户端模块](#5-客户端模块)
- [6. 资源服务端模块](#6-资源服务端模块)
- [7. 审计模块](#7-审计模块)
- [8. 快速开始](#8-快速开始)
- [9. 常见问题](#9-常见问题)
- [10. 版本历史](#10-版本历史)

---

## 1. 概述

### 1.1 什么是 Simple AKSK？

Simple AKSK 是一套基于 OAuth2 Client Credentials Grant 的 API 认证解决方案，为服务间调用和外部系统提供统一的 OpenAPI 认证机制，类似 AWS/阿里云的 AKSK（AccessKey/SecretKey）认证模式。

### 1.2 核心特性

- **双层级 AKSK 管理**：平台级（AKP）和用户级（AKU）两种类型
- **Token 即时撤销**：撤销后 introspect 立即返回 `active=false`
- **L1+L2 两级缓存**：Caffeine 本地缓存 + Redis 分布式缓存，introspect 热路径命中 L1 无需访问 Redis（可选）
- **多实例缓存一致性**：Redis Pub/Sub 广播缓存失效，多副本间 L1 缓存强一致（可选）
- **资源保护**：Introspect 验证（推荐，即时感知撤销）和 HTTP Header 解析（已有网关场景）
- **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.1，完全符合 OAuth2 规范
- **JWT Token 签发**：RSA 算法签发，支持自定义公私钥和 `auth_server_id`
- **Token 审计事件**：颁发、撤销、删除、introspect 全生命周期事件
- **多种客户端实现**：Feign、RestTemplate，支持 Redis 和 HttpSession 两种 Token 缓存策略
- **权限注解**：4 种权限校验注解，支持 SpEL 表达式
- **Admin 管理界面**：AKSK 和 Token 的完整管理操作

### 1.3 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.7.x | 基础框架 |
| Spring Authorization Server | 0.4.1 | OAuth2 授权服务器 |
| Spring Security | 5.7.x | 安全框架 |
| Java | 8+ | 运行环境 |
| MySQL | 5.7+ / 8.0+ | Token 持久化 |
| Redis | - | Token 缓存（可选） |
| Caffeine | 2.9.x | L1 本地缓存（可选，需引入 Redis） |
| APISIX | 3.x | API 网关（可选，已有网关基础设施时使用） |

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        客户端层 (Client)                         │
│  Feign Client Starter  │  RestTemplate Client Starter           │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 换取 Token
┌─────────────────────────────────────────────────────────────────┐
│                        服务端层 (Server)                         │
│  simple-aksk-server-starter  │  Admin 管理界面                   │
│  /oauth2/token  /oauth2/revoke  /oauth2/introspect  /oauth2/jwks │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 携带 Token
┌─────────────────────────────────────────────────────────────────┐
│                     资源服务层 (Resource)                        │
│  Introspect 验证（resource-server-starter，推荐）                │
│  Header 解析（security-context-starter，已有网关场景）           │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 两种部署架构

#### 架构 A：Introspect 验证（推荐）

```
客户端 → AKSK Server 换 Token → 携带 Token → 业务服务
                                                    ↑
                                        resource-server-starter
                                        （INTROSPECT 模式，调用 /oauth2/introspect）
```

- 业务服务引入 `simple-aksk-resource-server-starter`，配置 `verification-mode: INTROSPECT`
- 启用 L1+L2 缓存后，introspect 热路径命中 L1 缓存，无 Redis IO，性能与本地验签相当
- 即时感知 token 撤销，撤销后下次请求立即返回 `active=false`
- 无需网关，部署简单

#### 架构 B：API 网关代理（已有 APISIX 等网关时）

```
客户端 → AKSK Server 换 Token → 携带 JWT → APISIX 验签 → 注入 Header → 业务服务
                                                                          ↑
                                                          security-context-starter
                                                          （从 Header 读用户信息）
```

- 适合已有 API 网关的基础设施，网关负责验签并注入用户信息到请求头
- 业务服务引入 `simple-aksk-security-context-starter`，从请求头读取用户信息
- 注意：jwt-auth 插件本地验签无法感知 token 撤销，如需即时感知需改用 openid-connect 插件

---

## 3. 模块总览

| 模块 | 版本 | 类型 | 说明 |
|------|------|------|------|
| `simple-aksk-core` | 1.0.2 | 核心 | 公共常量、模型、工具类 |
| `simple-aksk-server-core` | 1.0.4 | 核心 | Server 公共配置、审计事件定义 |
| `simple-aksk-server-starter` | 1.1.3 | 服务端 | AKSK 认证服务器，L1+L2 两级缓存 |
| `simple-aksk-client-core` | 1.0.1 | 客户端核心 | Token 管理抽象接口 |
| `simple-aksk-redis-token-manager` | 1.1.0 | 客户端 | Redis Token 缓存管理 |
| `simple-aksk-httpsession-token-manager` | 1.0.1 | 客户端 | HttpSession Token 缓存管理 |
| `simple-aksk-feign-redis-client-starter` | 1.1.0 | 客户端 | Feign + Redis Token 管理 |
| `simple-aksk-resttemplate-redis-client-starter` | 1.1.0 | 客户端 | RestTemplate + Redis Token 管理 |
| `simple-aksk-feign-httpsession-client-starter` | 1.0.1 | 客户端 | Feign + HttpSession Token 管理 |
| `simple-aksk-resttemplate-httpsession-client-starter` | 1.0.1 | 客户端 | RestTemplate + HttpSession Token 管理 |
| `simple-aksk-resource-core` | 1.0.3 | 资源端核心 | 权限注解、安全上下文工具 |
| `simple-aksk-resource-server-starter` | 1.0.6 | 资源端 | Introspect/JWT 验证（默认 Introspect，支持兜底降级） |
| `simple-aksk-security-context-starter` | 1.0.3 | 资源端 | Header 解析（已有 API 网关场景） |
| `simple-aksk-resource-audit-listener-starter` | 1.0.0 | 审计 | 监听资源端访问事件，生成审计记录 |
| `simple-aksk-server-audit-listener-starter` | 1.0.0 | 审计 | 监听 Server Token 生命周期事件，生成审计记录 |

---

## 4. 服务端模块

### 4.1 simple-aksk-server-core

Server 端公共模块，提供 `simple-aksk-server-starter` 和扩展模块共享的内容。

**包含内容**：
- `SimpleAkskServerProperties` — 服务端配置类
- `SimpleAkskServerConstant` — 服务端常量
- `RedisKeyHelper` — Redis Key 构建工具
- 审计事件类（见 [7. 审计模块](#7-审计模块)）

### 4.2 simple-aksk-server-starter

AKSK 认证服务器，提供完整的 OAuth2 授权流程和管理能力。

**核心能力**：

1. **AKSK 管理**：平台级（AKP）和用户级（AKU）的创建、查询、启用/禁用、删除
2. **Token 颁发**：`/oauth2/token`，支持 scope 指定
3. **Token 撤销**：`/oauth2/revoke`，撤销后 introspect 立即返回 `active=false`，同步清除 L1+L2 缓存并广播
4. **Token 自省**：`/oauth2/introspect`，查询 token 实时状态，命中 L1 缓存时无 Redis IO
5. **Token 管理**：查询、撤销、删除、统计，支持 MySQL 和 Redis 双视图
6. **Admin 管理界面**：`/admin`，Web 管理页面，支持所有管理操作
7. **L1+L2 两级缓存**：可选，Caffeine 本地缓存 + Redis 分布式缓存，多实例 Pub/Sub 强一致
8. **审计事件**：Token 全生命周期事件发布（见 [7. 审计模块](#7-审计模块)）

**标准端点**：

| 端点 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/oauth2/token` | POST | 颁发 Access Token | Basic Auth (AKSK) |
| `/oauth2/revoke` | POST | 撤销 token | Basic Auth (AKSK) |
| `/oauth2/introspect` | POST | 查询 token 状态 | Basic Auth (AKSK) |
| `/oauth2/jwks` | GET | 公钥集合（JWK Set） | 公开 |
| `/.well-known/oauth-authorization-server` | GET | 服务器元数据 | 公开 |

**管理 API**（需要 token 且包含对应 scope）：

| 端点 | 方法 | 说明 | 所需 scope |
|------|------|------|------|
| `/api/client` | POST | 创建 Client | `/api/client` |
| `/api/client` | GET | 查询 Client 列表 | `/api/client` |
| `/api/client` | PATCH | 同步用户 Scopes | `/api/client` |
| `/api/client/{clientId}` | GET/DELETE | 详情/删除 | `/api/client` |
| `/api/client/{clientId}` | PATCH | 更新 Client（enabled/scopes/name/ownerUserId） | `/api/client` |
| `/api/token` | GET | 查询 Token 列表（MySQL） | `/api/token` |
| `/api/token/redis` | GET | 查询 Token 列表（Redis） | `/api/token` |
| `/api/token/{id}` | GET/DELETE | 详情/删除 | `/api/token` |
| `/api/token/{id}/revoke` | POST | 撤销 Token | `/api/token` |
| `/api/token/expired` | DELETE | 清理过期 Token | `/api/token` |
| `/api/token/statistics` | GET | 统计信息 | `/api/token` |

**配置示例**：

无 Redis（最小配置）：

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
              admin:
                enabled: true
                username: admin
```

启用 L1+L2 两级缓存（推荐）：

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
              redis:
                enabled: true
                token:
                  me: my-app
              admin:
                enabled: true
                username: admin
        cache:
          key-prefix: sure-auth-aksk
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me:my-app}
          l1:
            enabled: true
            expire-seconds: 10
            max-size: 10000
          l2:
            enabled: true
            expire-seconds: ${io.github.surezzzzzz.sdk.auth.aksk.server.jwt.expires-in:3600}
            key-format: "{keyPrefix}:{me}:{cacheName}::{key}"
          consistency:
            mode: strong
```

**配置项**：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `jwt.key-id` | `sure-auth-aksk-2026` | JWT Key ID，同时作为 `auth_server_id` claim |
| `jwt.expires-in` | `3600` | JWT 有效期（秒） |
| `jwt.public-key` | - | RSA 公钥（classpath/file 路径、PEM 或 Base64） |
| `jwt.private-key` | - | RSA 私钥（classpath/file 路径、PEM 或 Base64） |
| `jwt.security-context-max-size` | `4096` | Token 请求中 `security_context` 参数最大字节数 |
| `redis.enabled` | `false` | 启用 Redis 缓存层 |
| `redis.token.me` | `default` | 应用标识，多实例必须一致 |
| `admin.enabled` | `true` | 启用 Admin 管理页面 |
| `admin.username` | `admin` | Admin 登录用户名 |
| `admin.password` | - | Admin 登录密码（启用 admin 时必填） |
| `admin.session-timeout-minutes` | `30` | Admin 会话超时时间（分钟） |
| `introspect.require-authentication` | `true` | `false` 时 `/oauth2/introspect` 接受无鉴权请求（仅限内网/测试环境） |

**JWT Token 内容**：

```json
{
  "sub": "AKP1234567890abcdefgh",
  "client_id": "AKP1234567890abcdefgh",
  "client_type": "platform",
  "user_id": "user123",
  "username": "张三",
  "scope": "read write",
  "auth_server_id": "sure-auth-aksk-2026",
  "security_context": "{...}",
  "iss": "http://your-server:8080",
  "exp": 1775638763,
  "iat": 1775635163
}
```

---

## 5. 客户端模块

### 5.1 simple-aksk-client-core

客户端核心模块，提供 Token 管理的抽象接口。

**核心接口**：
- `TokenManager` — Token 管理器，提供 `getToken()`、`clearToken()` 等方法
- `TokenCacheStrategy` — Token 缓存策略接口
- `TokenRefreshExecutor` — 向 AKSK Server 换取 Token 的执行器

### 5.2 Token 管理器

| 模块 | 缓存方式 | 适用场景 |
|------|----------|----------|
| `simple-aksk-redis-token-manager` | Redis | 多实例部署，Token 跨实例共享 |
| `simple-aksk-httpsession-token-manager` | HttpSession | 单实例，无 Redis 依赖 |

**配置示例**（Redis Token Manager）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              client-id: AKP1234567890abcdefgh
              client-secret: SK1234567890abcdefghijklmnopqrstuvwxyz1234
              server-url: http://localhost:8080
              token-endpoint: /oauth2/token
              token:
                refresh-before-expire: 300
              redis:
                token:
                  me: my-app
```

### 5.3 simple-aksk-feign-redis-client-starter

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:1.1.0'
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

```java
// 推荐：使用 @AkskClientFeignClient，自动携带 AKSK token
@AkskClientFeignClient(name = "my-service", url = "http://my-service:8080")
public interface MyServiceClient {
    @GetMapping("/api/resource")
    String getResource();
}

// 其他 Feign 客户端不受影响，不会携带 AKSK token
@FeignClient(name = "other-service", url = "http://other-service:8080")
public interface OtherServiceClient {
    @GetMapping("/public/data")
    String getData();
}
```

> **注意**：1.0.1 修复了 `AkskFeignConfiguration` 污染全局 Feign 上下文的问题，升级后普通 `@FeignClient` 不再自动携带 AKSK token。

### 5.4 simple-aksk-resttemplate-redis-client-starter

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:1.1.0'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

```java
@Autowired
@Qualifier("akskClientRestTemplate")
private RestTemplate akskClientRestTemplate;
```

**连接池配置**：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.enable` | `false` | 是否自动创建 `akskClientRestTemplate` bean |
| `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.max-total` | `100` | 连接池最大连接数 |
| `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.max-per-route` | `20` | 每个路由最大连接数 |
| `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.connect-timeout` | `5000` | 连接超时（ms） |
| `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.read-timeout` | `30000` | 读取超时（ms） |

### 5.5 HttpSession 客户端

适用于单实例、无 Redis 依赖的场景：

```gradle
// Feign
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-httpsession-client-starter:1.0.1'

// RestTemplate
implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-httpsession-client-starter:1.0.1'
```

### 5.6 两种客户端共存

Feign 和 RestTemplate 两个 starter 可以同时引用，共享同一个 `TokenManager`，token 只取一次，互不干扰。

---

## 6. 资源服务端模块

### 6.1 simple-aksk-resource-core

**权限注解**：

| 注解 | 说明 |
|------|------|
| `@RequireContext` | 要求请求携带 AKSK 安全上下文 |
| `@RequireField` | 要求上下文中某字段不为空 |
| `@RequireFieldValue` | 要求上下文中某字段等于指定值 |
| `@RequireExpression` | SpEL 表达式校验 |

### 6.2 simple-aksk-resource-server-starter

支持 INTROSPECT（默认）和 JWT 两种模式。

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.6'
```

**INTROSPECT 模式配置**（默认，推荐）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                enabled: true
                # verification-mode 默认为 INTROSPECT，可省略
                introspect:
                  endpoint: http://localhost:8080/oauth2/introspect
                  client-id: AKP1234567890abcdefgh
                  client-secret: SK...
                  local-cache:
                    enabled: true          # 默认开启，TTL 3s
                    expire-seconds: 3
                    fallback:
                      enabled: false       # 兜底降级，默认关闭
                      stale-ttl-multiplier: 10
                security:
                  protected-paths:
                    - /api/**
```

**JWT 模式配置**（不推荐，无法感知撤销）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                enabled: true
                verification-mode: JWT
                jwt:
                  issuer-uri: http://localhost:8080
                security:
                  protected-paths:
                    - /api/**
```

**配置项**：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `true` | 是否启用 |
| `verification-mode` | `INTROSPECT` | 验证模式：`INTROSPECT`（推荐）或 `JWT` |
| `jwt.issuer-uri` | - | 授权服务器地址（自动发现 JWKS） |
| `jwt.public-key` | - | RSA 公钥（PEM 或 Base64） |
| `introspect.endpoint` | - | Introspect 端点 URL |
| `introspect.client-id` | - | Introspect 调用的客户端 ID |
| `introspect.client-secret` | - | Introspect 调用的客户端 Secret |
| `introspect.local-cache.enabled` | `true` | 是否启用本地缓存 |
| `introspect.local-cache.expire-seconds` | `3` | 本地缓存 TTL（秒），撤销感知延迟 = TTL |
| `introspect.local-cache.fallback.enabled` | `false` | 是否启用兜底降级 |
| `security.protected-paths` | `/api/**` | 需要认证的路径 |
| `security.permit-all-paths` | - | 白名单路径 |

验证通过后发布 `AkskAccessEvent` 事件（`source` 为 `"jwt"` 或 `"introspect"`）。

### 6.3 simple-aksk-security-context-starter

适用于已有 API 网关（APISIX 等）的场景。

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-security-context-starter:1.0.3'
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              security-context:
                enable: true
                header-prefix: x-sure-auth-aksk-
```

从以下请求头中解析用户信息：

| 请求头 | 说明 |
|--------|------|
| `x-sure-auth-aksk-client-id` | 客户端 ID |
| `x-sure-auth-aksk-client-type` | 客户端类型 |
| `x-sure-auth-aksk-user-id` | 用户 ID |
| `x-sure-auth-aksk-username` | 用户名 |
| `x-sure-auth-aksk-roles` | 角色 |
| `x-sure-auth-aksk-scope` | 权限范围 |

解析后发布 `AkskAccessEvent` 事件（`source` 为 `"header"`），可通过 `SimpleAkskSecurityContextHelper` 读取。

---

## 7. 审计模块

### 7.1 Token 审计事件（simple-aksk-server-core）

Server 端在 token 生命周期的关键节点发布 Spring 事件，业务方监听即可实现审计。

| 事件 | 触发时机 | 额外字段 |
|------|----------|----------|
| `TokenIssuedEvent` | `/oauth2/token` 颁发新 token | - |
| `TokenRevokedEvent` | token 被撤销 | - |
| `TokenRemovedEvent` | Spring Authorization Server 内部删除 | - |
| `TokenIntrospectedEvent` | `/oauth2/introspect` 查询 | `active: boolean` |

所有事件继承 `AbstractTokenEvent`，包含：`clientId`、`clientType`、`userId`、`username`、`tokenValue`、`scopes`、`issuedAt`、`expiresAt`、`eventTime`。

```java
@EventListener
public void onTokenIssued(TokenIssuedEvent event) {
    log.info("Token issued: clientId={}, scopes={}", event.getClientId(), event.getScopes());
}

@EventListener
public void onAnyTokenEvent(AbstractTokenEvent event) {
    log.info("Token event: type={}, clientId={}", event.getEventType(), event.getClientId());
}
```

### 7.2 AKSK 访问事件（simple-aksk-resource-core）

资源端认证成功后发布 `AkskAccessEvent`，包含完整的访问信息。

```java
@EventListener
public void onAkskAccess(AkskAccessEvent event) {
    log.info("AKSK access: clientId={}, uri={}, source={}",
        event.getClientId(), event.getRequestUri(), event.getSource());
    // source: "introspect"、"jwt" 或 "header"
}
```

### 7.3 simple-aksk-server-audit-listener-starter

开箱即用的 Server 端审计 starter。

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:1.0.0'
```

```java
@Component
public class MyServerTokenAuditHandler implements ServerTokenAuditHandler {
    @Override
    public void handle(ServerTokenAuditRecord record) {
        log.info("Token audit: type={}, client={}", record.getEventType(), record.getClientId());
    }
}
```

### 7.4 simple-aksk-resource-audit-listener-starter

开箱即用的 Resource 端审计 starter，支持 Header 认证和 JWT 认证两种来源。

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-audit-listener-starter:1.0.0'
```

```java
@Component
public class MyAkskAuditHandler implements AkskAuditHandler {
    @Override
    public void handle(AkskAuditRecord record) {
        log.info("AKSK audit: clientId={}, uri={}, source={}",
            record.getClientId(), record.getRequestUri(), record.getSource());
        // source: "introspect"、"jwt" 或 "header"
    }
}
```

---

## 8. 快速开始

### 8.1 搭建 AKSK Server

**Step 1：添加依赖**

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.1.3'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'mysql:mysql-connector-java:8.0.33'

// 可选：启用 L1+L2 两级缓存
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
```

**Step 2：初始化数据库**

```bash
mysql -u root -p < docs/00_database.sql
mysql -u root -p sure_auth_aksk < docs/01_schema.sql
```

**Step 3：生成 RSA 密钥对**

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

**Step 4：配置**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sure_auth_aksk?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              jwt:
                public-key: classpath:keys/public.pem
                private-key: classpath:keys/private.pem
              admin:
                username: admin
                password: your_admin_password
```

**Step 5：访问 Admin 页面**

`http://localhost:8080/admin`，创建 AKSK，换取 Token。

---

### 8.2 接入客户端（Feign）

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:1.1.0'
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              client-id: AKP1234567890abcdefgh
              client-secret: SK...
              server-url: http://localhost:8080
```

```java
@AkskClientFeignClient(name = "my-service", url = "http://my-service:8080")
public interface MyServiceClient {
    @GetMapping("/api/resource")
    String getResource();
}
```

---

### 8.3 保护资源服务（推荐 Introspect 模式）

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.6'
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: http://localhost:8080/oauth2/introspect
                  client-id: AKP1234567890abcdefgh
                  client-secret: SK...
                security:
                  protected-paths:
                    - /api/**
```

```java
@RestController
public class MyController {

    @GetMapping("/api/resource")
    @RequireExpression(
        value = "#context['scope'] != null && #context['scope'].contains('read')",
        message = "需要 read 权限"
    )
    public String getResource() {
        String clientId = SimpleAkskSecurityContextHelper.getClientId();
        return "Hello, " + clientId;
    }
}
```

---

### 8.4 保护资源服务（已有 API 网关）

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-security-context-starter:1.0.3'
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              security-context:
                enable: true
```

适用于已有 APISIX 等网关基础设施的场景，网关负责验签并注入用户信息到请求头，业务服务通过 `SimpleAkskSecurityContextHelper` 读取。

---

## 9. 常见问题

**Q1：平台级和用户级 AKSK 有什么区别？**

平台级（AKP）不绑定用户，适合服务间调用；用户级（AKU）绑定具体用户，JWT 中包含 `user_id` 和 `username`，适合代表用户发起的调用。

**Q2：token 撤销后为什么 introspect 还返回 active=true？**

检查以下几点：
- `redis.token.me` 与 `cache.me` 是否一致
- `cache.consistency.mode` 是否为 `strong`
- Redis Pub/Sub 连接是否正常（查看启动日志中 `Cache invalidation listener initialized` 是否出现）
- 如果未启用 Redis，撤销后 MySQL 已更新，introspect 应立即返回 `active=false`

**Q3：JWT 模式和 Introspect 模式怎么选？**

推荐 Introspect 模式。服务端启用 L1+L2 两级缓存后，introspect 热路径命中 L1 本地缓存，性能与 JWT 本地验签相当，且支持即时撤销感知。JWT 模式无法感知 token 撤销，仅在不需要撤销能力的场景下适用。

**Q4：Feign 和 RestTemplate 两个 starter 能同时用吗？**

可以，共享同一个 `TokenManager`，token 只取一次，互不干扰。

**Q5：resource-server-starter 和 security-context-starter 有什么区别？**

- `resource-server-starter`：自行验证 token，支持 Introspect（推荐）和 JWT 两种模式，无需网关
- `security-context-starter`：从请求头解析用户信息，适合已有 API 网关（APISIX 等）的场景，网关已验签

两者都会发布 `AkskAccessEvent`，都支持 `SimpleAkskSecurityContextHelper` 和权限注解。

**Q6：如何生成 RSA 密钥对？**

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

**Q7：启动时出现 `Cannot load module CasJackson2Module` 警告怎么处理？**

无害警告，屏蔽方式：

```yaml
logging:
  level:
    org.springframework.security.jackson2: ERROR
```

**Q8：没有 Redis 时如何配置？**

不需要任何 SmartCache 配置，只需确保 `redis.enabled` 为 `false`（默认值），服务自动降级为纯 MySQL 模式。

**Q9：多实例部署时缓存如何保持一致？**

`consistency.mode=strong` 时，任意实例 revoke token 后通过 Redis Pub/Sub 广播缓存失效消息，其他实例的 L1 缓存立即清除。每个实例启动时生成唯一 UUID 作为实例标识，不会误忽略其他实例的消息。

---

## 10. 版本历史

### simple-aksk-server-starter

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.1.3 | 2026-05-01 | AKU 归属信息可修改、新增 `PATCH /api/client/{clientId}`、修复 Admin 编辑操作无响应 |
| 1.1.2 | 2026-04-28 | 删除 Client 时撤销关联 Token、重置 Secret 后页面数据回填修复 |
| 1.1.1 | 2026-04-27 | 重置 Client Secret、批量撤销 Token、过期 Token 处理优化 |
| 1.1.0 | 2026-04-17 | 引入 SmartCache L1+L2 两级缓存，Redis Pub/Sub 多实例强一致 |
| 1.0.7 | 2026-04-15 | Admin 清理过期 Token 接口路径修复 |
| 1.0.6 | 2026-04-13 | Introspect 端点匿名访问支持、Admin session 超时白屏修复 |
| 1.0.5 | 2026-04-10 | Token 全生命周期审计事件、Token 撤销能力、Admin 撤销操作 |
| 1.0.0 | 2026-01-19 | 初始版本 |

### simple-aksk-resource-server-starter

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.6 | 2026-05-06 | oauth2-oidc-sdk 依赖范围改为 api |
| 1.0.5 | 2026-05-02 | 升级 core 至 1.0.3，常量和工具方法迁移到 core |
| 1.0.4 | 2026-05-02 | 兜底缓存降级策略、缓存统计日志、默认模式改为 INTROSPECT |
| 1.0.3 | 2026-04-25 | Introspect 本地缓存（默认开启，TTL 3s） |
| 1.0.2 | 2026-04-13 | 新增 Introspect 验证模式，修复 scope claim 解析 |
| 1.0.0 | - | 初始版本 |

### simple-aksk-feign-redis-client-starter / simple-aksk-resttemplate-redis-client-starter

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.1.0 | 2026-05-06 | 升级依赖，适配 client-core 1.0.1 |
| 1.0.1 | - | 修复 AkskFeignConfiguration 污染全局 Feign 上下文 |
| 1.0.0 | - | 初始版本 |

### simple-aksk-feign-httpsession-client-starter / simple-aksk-resttemplate-httpsession-client-starter

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.1 | 2026-05-07 | 初始版本 |

### simple-aksk-resource-audit-listener-starter / simple-aksk-server-audit-listener-starter

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | - | 初始版本 |
