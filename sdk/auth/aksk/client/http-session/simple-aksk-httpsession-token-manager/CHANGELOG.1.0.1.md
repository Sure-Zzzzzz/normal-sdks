# Changelog - v1.0.1

## 发布日期

2026-05-04

## 版本类型

重构

## 变更概述

### HttpSessionTokenManager 重构（Template Method 模式）

继承 `AbstractTokenManager`（client-core 1.0.1 新增），`getToken()` 通用流程由基类统一管理，`HttpSessionTokenManager` 只保留 JVM 本地锁逻辑，代码量大幅减少。

### HttpSessionTokenCacheStrategy TTL 计算统一

`put()` 中 TTL 计算改为调用 `TokenCacheStrategy.calculateTtl()`，`CachedToken` 构造函数直接接收已计算好的 ttl，消除重复的 `Math.max(expiresInSeconds - 30, 60)` 逻辑。

### 测试断言加强

- 去掉 `assertNotNull` 之后冗余的 `assertTrue(token.length() > 0)`
- `testGetTokenMultipleTimes` 补充 JWT 格式断言

## 依赖升级

`simple-aksk-client-core`: 1.0.0 → 1.0.1

## 向后兼容性

完全向后兼容，行为与原版本一致。

## 贡献者

- @surezzzzzz
