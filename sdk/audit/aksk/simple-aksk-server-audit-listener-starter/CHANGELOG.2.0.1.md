# CHANGELOG - simple-aksk-server-audit-listener-starter 2.0.1

## 发布日期

2026-06-22

## 版本类型

Patch Release - AKSK Server 2.0.3 依赖跟进

## 变更概述

本版本跟进 `simple-aksk-server-core:2.0.3` 和 `simple-aksk-server-starter:2.0.3`，避免审计 listener 在业务工程中继续声明旧的 server-core 2.0.2 依赖。

## 变更详情

### 依赖变更

| 依赖 | 旧版本 | 新版本 | 说明 |
|------|--------|--------|------|
| `simple-aksk-server-core` | 2.0.2 | 2.0.3 | 编译/传递依赖，提供 `AbstractTokenEvent` 等事件类型 |
| `simple-aksk-server-starter`（测试） | 2.0.0 | 2.0.3 | 集成测试依赖，验证真实 Token 生命周期事件 |

### 测试配置更新

- 删除废弃的 `io.github.surezzzzzz.sdk.auth.aksk.server.redis.enabled`。
- 测试配置补充 `io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true`。
- 测试配置中 `redis.token.me`、`cache.me`、`limiter.redis.smart.me` 保持同一个 AKSK Server 集群标识。
- smart-limiter 使用 `mode=annotation`，只启用自动配置与运行时 Bean，不启用 MVC interceptor。

### 行为变更

无。`ServerTokenAuditHandler` 接口、`ServerTokenAuditRecord` 字段和事件监听行为均不变。

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:2.0.1'
```

如果业务工程已升级到 `simple-aksk-server-starter:2.0.3`，建议同步升级本模块到 2.0.1，保持依赖链路一致。

## 贡献者

- @surezzzzzz
