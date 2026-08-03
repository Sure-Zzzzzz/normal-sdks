# Simple AKSK 用户手册（2.x 封版）

> **封版说明**：本文档是 Simple AKSK 2.x 的冻结快照，仅覆盖已发布的 2.x artifact 最终版本。2.x 不再接受功能、缺陷修复或常规维护发布。
>
> 1.x 用户请查看 [USER_MANUAL.1.x.md](USER_MANUAL.1.x.md)。后续 IAM/AKSK 协作能力将以独立的 3.x 规划推进；3.x 尚未发布，不改变 2.x 的封版状态。

## 最终已发布坐标

| 模块 | 最终版本 | 用途 |
|------|----------|------|
| `simple-aksk-core` | 2.0.0 | 公共常量、模型和工具 |
| `simple-aksk-server-core` | 2.0.3 | Server 公共配置、Redis Key 工具和 Token 审计事件 |
| `simple-aksk-server-starter` | 2.0.3 | AKSK 认证服务器、JWE Token、Redis 缓存和 OAuth2 限流 |
| `simple-aksk-client-core` | 2.0.0 | Token 管理抽象 |
| `simple-aksk-redis-token-manager` | 2.0.1 | Redis Token 缓存、分布式锁和预刷新 |
| `simple-aksk-feign-redis-client-starter` | 2.0.1 | Feign Bearer Token 自动携带 |
| `simple-aksk-resttemplate-redis-client-starter` | 2.0.1 | RestTemplate Bearer Token 自动携带 |
| `simple-aksk-resource-core` | 2.0.0 | 安全上下文和权限注解 |
| `simple-aksk-resource-server-starter` | 2.0.1 | INTROSPECT 资源保护和路径归一化 |
| `simple-aksk-resource-audit-listener-starter` | 2.0.0 | 资源访问审计事件监听 |
| `simple-aksk-server-audit-listener-starter` | 2.0.1 | Server Token 生命周期审计事件监听 |

以上坐标均已在 Maven Central 验证。以下内容不属于 2.x 发布矩阵：HTTP Session Client（1.x）、`simple-aksk-security-context-starter`（1.x）、metrics starter 的未发布 2.0 设计稿，以及 demo 模块。

## 架构与边界

```text
Feign / RestTemplate Client
          ↓ 获取 Bearer Token
AKSK Server (/oauth2/token, /oauth2/revoke, /oauth2/introspect)
          ↓ JWE Bearer Token
Resource Server（INTROSPECT）
```

- AKSK Server 基于 OAuth2 Client Credentials，签发 JWE（`A256GCMKW` + `A256GCM`）Token。
- Resource Server 仅使用 INTROSPECT，能够感知撤销；本地缓存默认启用，兜底缓存默认关闭。
- 平台级 AKSK 使用 `AKP` 前缀，用户级 AKSK 使用 `AKU` 前缀；Secret 使用 `SK` 前缀。
- scope 是签发时固定的能力边界；`security_context` 仅承载动态业务上下文，不能扩张 scope 权限。
- 2.0.3 起 AKSK Server 必须接入 Redis，用于 Token 缓存、撤销同步、L1 失效广播和 OAuth2 端点限流。

## Server 接入

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:2.0.3'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
runtimeOnly 'mysql:mysql-connector-java:8.0.33'
```

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
                token:
                  me: my-aksk-server
              limiter:
                oauth2:
                  enable: true
              admin:
                enabled: true
                username: admin
        limiter:
          redis:
            smart:
              enable: true
              me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}
              mode: annotation
        cache:
          key-prefix: sure-auth-aksk
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}
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

同一 AKSK Server 集群中，`redis.token.me`、`limiter.redis.smart.me` 和 `cache.me` 必须一致。

### OAuth2 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/oauth2/token` | POST | 签发 Access Token |
| `/oauth2/revoke` | POST | 撤销 Token |
| `/oauth2/introspect` | POST | 查询 Token 状态 |
| `/oauth2/jwks` | GET | JWK Set |
| `/.well-known/oauth-authorization-server` | GET | Server 元数据 |

```bash
curl -X POST http://localhost:8080/oauth2/token \
  -u "AKP...:SK..." \
  -d "grant_type=client_credentials&scope=read write"
```

响应中的 `access_token` 是五段 Base64URL JWE，客户端不应直接解析；通过 `/oauth2/introspect` 获取 Token 状态与 claims。

## 客户端接入

### Feign

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:2.0.1'
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

```java
@AkskClientFeignClient(name = "example-service", url = "http://example-service:8080")
public interface ExampleServiceClient {

    @GetMapping("/api/resource")
    String getResource();
}
```

### RestTemplate

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:2.0.1'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

开启 `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.enable=true` 后，可注入名为 `akskClientRestTemplate` 的 `RestTemplate`。Feign 与 RestTemplate starter 可共同使用同一个 `TokenManager`。

`simple-aksk-redis-token-manager:2.0.1` 使用 SHA-256 截断 128-bit hex 生成多上下文缓存 Key，避免 2.0.0 的 `hashCode()` 碰撞串号问题。

## 资源服务保护

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:2.0.1'
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
                  client-id: AKP...
                  client-secret: SK...
                security:
                  protected-paths:
                    - /api/**
```

- `introspect.local-cache.enabled` 默认 `true`，默认 TTL 为 3 秒。
- `introspect.local-cache.fallback.enabled` 默认 `false`；启用前应评估撤销即时性和可用性取舍。
- 2.0.1 默认启用 `security.context-path-aware`。当设置 `server.servlet.context-path=/api` 时，外部 `/api/**` 会归一化为应用内匹配路径；`spring.mvc.servlet.path` 不参与该归一化。
- 资源验证成功后可使用 `SimpleAkskSecurityContextHelper` 读取上下文，并会发布 `AkskAccessEvent`。

## 审计

- `simple-aksk-server-audit-listener-starter:2.0.1` 监听 `TokenIssuedEvent`、`TokenRevokedEvent`、`TokenRemovedEvent`、`TokenIntrospectedEvent`，并转为 `ServerTokenAuditRecord`。
- `simple-aksk-resource-audit-listener-starter:2.0.0` 监听 `AkskAccessEvent`，并转为 `AkskAuditRecord`。
- 业务可实现相应 Handler 接口接收审计记录；多个 Handler 可同时注册。

## 2.x 最终版本记录

| 模块 | 版本 | 说明 |
|------|------|------|
| Server Core / Starter | 2.0.3 | OAuth2 端点限流与 Redis 必需化 |
| Server Core / Starter | 2.0.2 | Client Entity 两级缓存和依赖升级 |
| Server Core / Starter | 2.0.1 | raw JWT Token 反序列化兼容修复 |
| Core | 2.0.0 | JWE Token 基础模型与常量 |
| Client Core | 2.0.0 | 移除客户端 JWT 解析，改由缓存 TTL 管理有效性 |
| Redis Token Manager | 2.0.1 | 缓存 Key 改为 SHA-256 截断 128-bit hex |
| Feign / RestTemplate Client | 2.0.1 | 跟进 Redis Token Manager 安全加固 |
| Resource Server Starter | 2.0.1 | context-path-aware 路径归一化与高风险配置 fail fast |
| Resource Audit Listener | 2.0.0 | 仅保留 INTROSPECT 审计链路 |
| Server Audit Listener | 2.0.1 | 跟进 Server Core / Starter 2.0.3 |

## 封版后的使用建议

已接入 2.x 的系统应继续使用本手册列出的最终已发布坐标，并依据自身安全要求维护基础设施和应用配置。2.x 不再发放空的“最终版”包，也不会将未发布的 3.x 设计内容回填到 2.x 文档或依赖关系中。
