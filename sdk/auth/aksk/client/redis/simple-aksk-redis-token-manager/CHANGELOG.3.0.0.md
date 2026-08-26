# Changelog - simple-aksk-redis-token-manager 3.0.0

## 变更概述

适配 AKSK 3.0 客户端核心，并升级 Smart Cache 依赖版本、收紧依赖声明边界（`smart-cache-starter` 转为 `implementation`）；Redis Token Manager 的缓存、分布式锁、Pub/Sub 和 L2 预刷新职责保持不变。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-client-core` | 2.0.0 | 3.0.0 |
| `smart-cache-starter` | 1.1.2 | 2.1.0 |

## 行为说明

- Token 获取和刷新由 `simple-aksk-client-core:3.0.0` 统一执行 Client Credentials 流程和重试。
- OAuth2 Token 响应必须包含非空 `access_token` 和正数 `expires_in`；缺失或非法响应返回 `HTTP_RESPONSE_INVALID`，不会写入 Redis 缓存。
- Redis L2 TTL 使用服务端返回的 `expires_in` 计算，preload 继续由 Smart Cache 的 Redis TTL 窗口驱动。
- L2 预刷新写回的 TTL 严格等于服务端 `expires_in`：`reload()` 只返回新值，TTL 经 `getReloadTtlSeconds()` 交给框架写回。修复了旧实现预刷新值可能被全局 L2 TTL 覆盖、导致缓存活得比 Token 长的问题。
- 分布式锁键与 `SmartCacheManager` 同源构造（`{keyPrefix}-lock:{cacheName}:{me}:{cacheKey}`），按应用组 `me` 互斥、跨组隔离；锁服务不可用时降级为 JVM 本地锁，`getToken()` 不因锁故障直接失败。
- `me` 为必配项：应用组标识，同一应用的多个实例必须配置相同值（共享 L2 缓存、锁互斥、Pub/Sub 频道互通），不同应用用不同值隔离。
- 依赖声明收紧：`simple-aksk-client-core` 保持 `api`（`TokenManager`、`SecurityContextProvider` 是使用方公开契约）；`smart-cache-starter` 从 `api` 调整为 `implementation`——它对本模块是实现细节，运行时仍向使用方传递（自动装配、开箱即用不变），仅编译期不再传递，使用方直接引用 smart-cache API 时需自行声明。

## smart-cache 2.x 适配

- 缓存值读写全部走类型化 API（`get(cacheName, key, TokenWithExpiry.class)`）：smart-cache 2.x 对 `Object.class` 读取执行 trusted-packages 白名单校验，业务包类型会被反序列化拒绝、L2 恒穿透。
- `TokenWithExpiry` 保持为可变 POJO（无参构造 + setter），满足 Jackson `convertValue` 反序列化契约。

## 兼容性

- TokenManager、Redis 配置、security context 和缓存 key 使用方式不变，业务代码无需修改。
- `smart-cache-starter:2.1.0` 仍基于 Spring Boot 2.x / `javax` 体系；Redis、Spring Web、Caffeine 和 AOP 运行时依赖仍由使用方按 README 自行提供。
- 升级前应确认 OAuth2 Server 始终返回有效的 `access_token` 和正数 `expires_in`。

## 测试

- 保留 Redis 真实端到端、并发、Pub/Sub、preload 和多 security context 隔离测试。
- 修正 TTL/preload 测试说明，使其与 client-core 3.0.0 的严格 Token 响应校验一致。
- Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本矩阵，每版本 41 个测试全绿（真实 Redis + AKSK Server E2E），命令与记录见 [LOCAL_TEST_COMMANDS.md](LOCAL_TEST_COMMANDS.md)。
- 测试隔离依赖 `cache.route.scan-enabled: true`（`clear` 对 L2 才有效）与 Redis Route 同数据源清理；测试类 `@ActiveProfiles` 显式附带 `local`，保证凭据在 SB 2.2-2.7 全版本加载（`spring.config.import` 是 2.4+ 特性，不能作为加载机制）。
