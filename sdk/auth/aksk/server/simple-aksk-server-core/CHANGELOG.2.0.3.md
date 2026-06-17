# CHANGELOG - simple-aksk-server-core 2.0.3

## 发布日期

2026-06-17

## 版本类型

Patch Release - 删除 Redis 可选配置，新增 AKSK OAuth2 限流配置模型

## 变更概述

2.0.3 为 `simple-aksk-server-starter` 强制 Redis 和接入 `smart-redis-limiter-starter` 1.1.4 提供 core 配置基础：删除 `RedisConfig.enabled`，新增 AKSK Server 自己的 OAuth2 限流配置对象和默认常量。core 仍不依赖 limiter starter 或 limiter core。

## 变更详情

### 删除 Redis 可选开关

`SimpleAkskServerProperties.RedisConfig` 删除：

```java
private Boolean enabled = false;
```

AKSK Server 2.0.3 不再维护无 Redis 模式。starter 侧将始终要求 Redis / SmartCache 相关 Bean 可用。

### 新增 LimiterConfig

`SimpleAkskServerProperties` 新增：

```java
private LimiterConfig limiter = new LimiterConfig();
```

配置结构：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `limiter.oauth2.enable` | `true` | OAuth2 端点限流默认开启 |
| `limiter.oauth2.token.algorithm` | `sliding` | token 端点限流算法 |
| `limiter.oauth2.token.fallback` | `deny` | token 端点异常降级策略 |
| `limiter.oauth2.token.key-strategy` | `ip` | provider 返回空时的 key 策略 |
| `limiter.oauth2.token.limits[0]` | `60 / 1 MINUTES` | token 端点默认阈值 |
| `limiter.oauth2.introspect.algorithm` | `sliding` | introspect 端点限流算法 |
| `limiter.oauth2.introspect.fallback` | `allow` | introspect 异常降级策略 |
| `limiter.oauth2.introspect.key-strategy` | `ip` | introspect 默认按 IP 限流 |
| `limiter.oauth2.introspect.limits[0]` | `300 / 1 MINUTES` | introspect 默认阈值 |
| `limiter.oauth2.revoke.algorithm` | `sliding` | revoke 端点限流算法 |
| `limiter.oauth2.revoke.fallback` | `allow` | revoke 异常降级策略 |
| `limiter.oauth2.revoke.key-strategy` | `ip` | revoke 默认按 IP 限流 |
| `limiter.oauth2.revoke.limits[0]` | `120 / 1 MINUTES` | revoke 默认阈值 |

`LimitRuleConfig.unit` 使用 JDK `java.util.concurrent.TimeUnit`。starter 运行时再转换为 limiter 1.1.4 的 `SmartRedisLimiterProperties.SmartLimitRule`。

### 新增限流常量

`SimpleAkskServerConstant` 新增 OAuth2 限流默认值常量和事件来源常量：

- `DEFAULT_LIMITER_OAUTH2_ENABLE`
- `DEFAULT_LIMITER_ALGORITHM`
- `DEFAULT_LIMITER_KEY_STRATEGY`
- `DEFAULT_LIMITER_TOKEN_FALLBACK`
- `DEFAULT_LIMITER_INTROSPECT_FALLBACK`
- `DEFAULT_LIMITER_REVOKE_FALLBACK`
- `DEFAULT_LIMITER_WINDOW`
- `DEFAULT_LIMITER_WINDOW_UNIT`
- `DEFAULT_LIMITER_TOKEN_COUNT`
- `DEFAULT_LIMITER_INTROSPECT_COUNT`
- `DEFAULT_LIMITER_REVOKE_COUNT`
- `LIMITER_SOURCE_OAUTH2_FILTER`

## 兼容性说明

- `RedisConfig.enabled` 被删除，业务方应移除 `io.github.surezzzzzz.sdk.auth.aksk.server.redis.enabled` 配置。
- Java 公共配置模型有字段删除；运行行为由 starter 2.0.3 强制 Redis。
- core 不新增第三方依赖，不依赖 `smart-redis-limiter-starter`。
- 数据库表结构无变化。

## 下游要求

`simple-aksk-server-starter` 需要升级到 2.0.3 后消费新增配置，并在 starter 侧完成：

- 强制 Redis Bean 注入。
- 接入 `smart-redis-limiter-starter:1.1.4`。
- 将 `LimitRuleConfig` 转换为 limiter starter 的 `SmartLimitRule`。

## 贡献者

- @surezzzzzz
