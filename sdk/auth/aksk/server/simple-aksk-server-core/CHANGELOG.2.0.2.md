# CHANGELOG - simple-aksk-server-core 2.0.2

## 发布日期

2026-06-15

## 版本类型

Patch Release - 新增缓存常量，向后兼容

## 变更概述

新增 `RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY` 缓存常量，供 `simple-aksk-server-starter` 的 OAuth2 Client Entity 两级缓存方案使用。core 模块本身不引入新依赖、不改变现有行为。

## 变更详情

### RedisKeyHelper 新增常量

| 常量 | 值 | 说明 |
|------|----|------|
| `CACHE_OAUTH2_CLIENT_ENTITY` | `oauth2:client:entity` | OAuth2 Registered Client Entity 缓存名称（按 clientId 索引） |

位置：`RedisKeyHelper.java`（`io.github.surezzzzzz.sdk.auth.aksk.server.support` 包），与其他缓存常量 `CACHE_OAUTH2_AUTHORIZATION` / `CACHE_OAUTH2_AUTHORIZATION_TOKEN` 同类。

### 消费者说明

| 下游模块 | 当前版本 | 是否受影响 |
|---------|---------|-----------|
| `simple-aksk-server-starter` | 2.0.1 | 需升级到 2.0.2 后使用新常量 |
| `simple-aksk-server-audit-listener-starter` | 2.0.1 | 不引用 `RedisKeyHelper`，无影响 |
| `simple-aksk-server-metrics-starter` | 1.0.4 | 锁定版本，不升级，无影响 |

> `CACHE_OAUTH2_CLIENT_ENTITY` 是 `public static final` 常量。Java 编译器对常量做编译期内联，下游不主动引用该常量时不会触发重新编译；即使引用，引入新常量字段也不会破坏既有行为。2.0.2 patch 版本号合理。

---

## 贡献者

- @surezzzzzz