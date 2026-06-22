# CHANGELOG - simple-aksk-server-starter 2.0.3

## 发布日期

2026-06-22

## 版本类型

Patch Release - OAuth2 端点限流 + Redis 必需化 + smart-limiter 复用

## 变更概述

本版本在 AKSK Server OAuth2 Security Filter 链路中新增 `/oauth2/token`、`/oauth2/introspect`、`/oauth2/revoke` 端点限流。限流运行时能力直接复用 `smart-redis-limiter-starter:1.1.4` 的自动配置、算法、RedisTemplate、上下文、响应头和事件模型；AKSK 不复制 smart-limiter 的 Bean 装配逻辑。

OAuth2 端点限流默认通过 `AkskOAuth2ClientIdKeyProvider` 从 Basic Auth 或 `client_id` 参数提取 clientId，按 `client:{clientId}` 维度计数；只有取不到 clientId 时才回退到端点配置的 `key-strategy`。

## 变更详情

### 新增：OAuth2 Security Filter 限流

**新增类**：`AkskServerOAuth2LimiterFilter`

位置：`io.github.surezzzzzz.sdk.auth.aksk.server.filter.AkskServerOAuth2LimiterFilter`

覆盖端点：

| 端点 | 默认阈值 | 默认 fallback |
|------|----------|---------------|
| `/oauth2/token` | 60 / 1 minute | deny |
| `/oauth2/introspect` | 300 / 1 minute | allow |
| `/oauth2/revoke` | 120 / 1 minute | allow |

关键行为：

- 注册在 Spring Authorization Server 客户端认证之前，确保 OAuth2 端点进入 SAS filter 前先限流。
- 端点路径来自 `AuthorizationServerSettings`，支持自定义 token / introspect / revoke path。
- 将 `SimpleAkskServerProperties` 中 AKSK 自有轻量规则转换为 `SmartRedisLimiterProperties.SmartLimitRule`。
- 调用 `SmartRedisLimiterAlgorithmFactory` 获取算法实例，不复制 limiter 算法或 Redis/Lua 实现。
- 被限流时返回 429 JSON，并写入 `Retry-After`、`X-RateLimit-Limit`、`X-RateLimit-Remaining`、`X-RateLimit-Reset`。
- 按 smart-limiter 事件语义发布 `SmartRedisLimiterEvent`：默认只发布 blocked 事件；`logOnPass=true` 时也发布 passed 事件。

### 新增：OAuth2 clientId KeyProvider

**新增类**：`AkskOAuth2ClientIdKeyProvider`

位置：`io.github.surezzzzzz.sdk.auth.aksk.server.provider.AkskOAuth2ClientIdKeyProvider`

行为：

- 优先从 Basic Auth `Authorization` header 解析 clientId。
- Basic Auth 不存在时读取请求参数 `client_id`。
- 提取成功时生成 `client:{clientId}`，写入 smart-limiter 上下文 `PRECOMPUTED_KEY_PART`。
- 事件 `keyStrategy` 标记为 `custom:akskOAuth2ClientIdKeyProvider`。
- 返回空时回退到端点配置的 `key-strategy`，默认回退为 `ip`。
- provider 抛异常时按端点 fallback 处理：token 默认 deny，introspect / revoke 默认 allow。

### Redis 必需化

2.0.3 起 AKSK Server 不再维护无 Redis 模式。

变更：

- 删除无 Redis 测试 `OAuth2WithoutRedisTest`。
- 测试配置删除废弃的 `io.github.surezzzzzz.sdk.auth.aksk.server.redis.enabled`。
- README 改为说明 Redis 是必需基础设施。
- 保留 `redis.token.me` 作为 AKSK Server 集群应用标识，并要求 `cache.me`、`limiter.redis.smart.me` 与其一致。

### smart-limiter 复用方式

正确依赖关系：

| 模块 | 责任 |
|------|------|
| `simple-aksk-server-core` | 只提供 AKSK 自有轻量配置 DTO / 默认值，不依赖 smart-limiter |
| `simple-aksk-server-starter` | 在运行时转换配置并调用 smart-limiter 公共能力 |
| `smart-redis-limiter-starter` | 提供自动配置、算法、RedisTemplate、KeyGenerator、事件与响应头常量 |

业务方启用 AKSK OAuth2 限流时必须同时启用 smart-limiter，并让 `redis.token.me`、`cache.me`、`limiter.redis.smart.me` 使用同一个 AKSK Server 集群标识：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              redis:
                token:
                  me: my-aksk-server
              limiter:
                oauth2:
                  enable: true
        limiter:
          redis:
            smart:
              enable: true
              me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}
              mode: annotation
        cache:
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me}
```

AKSK 不再提供 `AkskServerLimiterConfiguration` 这类桥接配置，不复制 smart-limiter Bean 装配逻辑。AKSK 内建限流只覆盖 OAuth2 端点；smart-limiter MVC interceptor 仍由业务方按需开启并自行配置 rules，AKSK 不默认接管 `/api/**` 或 `/admin/**`。

### 依赖变更

| 依赖模块 | 版本 | 说明 |
|---------|------|------|
| `simple-aksk-server-core` | 2.0.3 | 读取 OAuth2 限流配置与默认值 |
| `smart-redis-limiter-starter` | 1.1.4 | 复用限流运行时能力 |
| `spring-boot-starter-data-redis` | Spring Boot 管理版本 | Redis 从 2.0.3 起为必需运行时依赖 |
| `spring-boot-starter-aop` | Spring Boot 管理版本 | smart-limiter starter 运行能力依赖 |

## 测试

### 新增 / 更新测试

| 测试类 | 覆盖内容 |
|--------|----------|
| `AkskServerOAuth2LimiterFilterTest` | OAuth2 filter 路径匹配、规则转换、clientId provider key、取不到 clientId 时回退 key-strategy、429 响应、事件发布、自定义 SAS endpoint |
| `OAuth2LimiterEndToEndTest` | 真实 Redis 滑动窗口限流：同 clientId 第二次请求 429，不同 clientId 独立计数通过，并验证 smart-limiter Redis key 使用 `client:{clientId}` |
| `TokenManagementFailureSemanticsTest` | Redis/SmartCache 关键路径失败时抛自定义异常，不吞异常、不静默降级 |
| `CachedOAuth2RegisteredClientEntityServiceTest` | SmartCache 异常时抛自定义异常，不降级直查 JPA |

## 文档

- README 版本更新为 2.0.3。
- README 配置示例补充 `io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true`、统一 `me` 引用和 `mode=annotation`，最小配置不启用 MVC interceptor。
- README 说明 OAuth2 限流默认按 clientId provider 计数，`key-strategy` 仅作为取不到 clientId 时的回退策略。
- README 删除 Redis 可选 / 无 Redis 降级描述。
- README 补充 smart-limiter MVC interceptor 可选配置示例，明确业务接口限流 rules 由业务方决定。
- `DESIGN.2.0.3.md` 修正为最终设计：直接复用 smart-limiter 自动配置，AKSK 仅提供 OAuth2 Security Filter 与 clientId provider。

## 兼容性说明

- 运行时必须有 Redis。
- `redis.token.me`、`cache.me`、`limiter.redis.smart.me` 必须配置为同一个 AKSK Server 集群标识。
- 如果只开启 `io.github.surezzzzzz.sdk.auth.aksk.server.limiter.oauth2.enable=true`，但未开启 `io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true`，应用应启动失败暴露配置缺失，而不是静默降级。
- `limiter.oauth2.*.key-strategy` 不是 OAuth2 端点的主限流维度；主维度是 clientId provider。该配置只在 provider 无法提取 clientId 时生效。

---

## 贡献者

- @surezzzzzz
