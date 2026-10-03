# Changelog - simple-aksk-redis-token-manager 3.0.2

## 变更概述

修复 Token 缓存接近或达到服务端过期时间时的有效期计算与并发收敛边界，增强 Redis 缓存故障的失败关闭行为。公开 API、配置键和依赖坐标不变，属 Patch Release。

## 行为修复

- Token 写入 L1/L2 与 L2 预刷新统一按服务端 `expires_in` 计算缓存有效期，并预留 30 秒失效窗口；已过期的 Token 不再回退使用全局 L2 TTL 继续缓存。
- 同一 JVM 的刷新等待改为固定数量的分片锁，缓存键数量持续增长时不再使本地锁容器随之无限增长；分布式锁仍负责跨实例互斥。
- Redis 缓存读取、写入或清理异常统一收口为 `TokenFetchException`，调用方不会因基础设施异常继续携带不可靠 Token 发起请求。
- 本模块日志不再主动输出 Token、安全上下文或缓存键的实际值；测试断言失败报告不回显访问凭据。

## 兼容性

- `TokenManager`、`SecurityContextProvider`、`auth.aksk.client.redis.*` 和 `cache.*` 配置键均无变化，调用方无需修改代码或配置。
- `clearToken()` 仍只作用于当前安全上下文；强一致配置下继续通过 Smart Cache 的 Pub/Sub 失效链路同步其他实例 L1。
- 依赖版本与声明方式保持 `3.0.1` 的口径。

## 测试

- Spring Boot 2.7.9 真实 Redis、AKSK Server、并发、L1/L2、Pub/Sub、预刷新和多安全上下文回归共 44 项，0 skipped、0 failures、0 errors。
