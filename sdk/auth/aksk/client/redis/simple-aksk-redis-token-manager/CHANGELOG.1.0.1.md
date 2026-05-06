# Changelog - v1.0.1

## 发布日期

2026-05-03

## 版本类型

重构

## 变更概述

### RedisTokenManager 重构（Template Method 模式）

继承 `AbstractTokenManager`（client-core 1.0.1 新增），`getToken()` 通用流程由基类统一管理，`RedisTokenManager` 只保留分布式锁逻辑。

### 递归重试改为循环

`fetchTokenWithLock()` 原来锁等待后递归调用自身，存在栈溢出风险。改为循环重试，最多 `LOCK_MAX_RETRY_TIMES`（50）次，超过抛 `TokenLockException`。

### 常量化

- 分布式锁超时从局部常量 `LOCK_TIMEOUT_SECONDS = 10` 改为引用 `SimpleAkskClientCoreConstant.DEFAULT_LOCK_TIMEOUT_SECONDS`
- 锁等待间隔引用 `SimpleAkskClientCoreConstant.LOCK_RETRY_SLEEP_MS`

### RedisTokenCacheStrategy TTL 计算统一

`put()` 中 TTL 计算从 `Math.max(expiresInSeconds - 30, 60)` 改为调用 `TokenCacheStrategy.calculateTtl()`，与 httpsession 模块保持一致。

## 依赖升级

- `simple-aksk-client-core`: 1.0.0 → 1.0.1
- `simple-redis-lock-starter`: 1.0.0 → 1.0.1（修复 unlock 竞态条件，建议升级）

## 向后兼容性

完全向后兼容，行为与原版本一致。

## 贡献者

- @surezzzzzz
