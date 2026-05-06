# Changelog - v1.1.0

## [1.1.0] - 2026-05-06

### Added

- **TokenCachePreloadHandler**：实现 `CachePreloadHandler` 接口，接入 smart-cache 的 L2 预刷新机制
  - `needPreload()` 通过 `TokenRefreshExecutor.checkTokenStatus()` 解析 JWT 判断 `EXPIRING_SOON`，完全替代框架 TTL 查询，避免额外 Redis IO
  - `reload()` 调用 `TokenRefreshExecutor.fetchTokenFromServer()` 异步换 token，当前请求返回旧值不阻塞
  - 使用分布式锁防止多实例重复触发同一 key 的 preload
  - reload 失败时指数退避重试，旧值在容错窗口内仍可返回
- **L1 缓存（Caffeine）**：JVM 本地缓存，TTL 短（默认 2s），减少 Redis IO
- **多实例 L1 一致性**：`clearToken()` 通过 Pub/Sub 广播 L1 失效，各实例同步清除

### Changed

- **RedisTokenManager 重构**：直接实现 `TokenManager` 接口，不再继承 `AbstractTokenManager`；缓存读写全部交给 `SmartCacheManager`，移除手写分布式锁逻辑
- **TokenRefreshExecutor 改为 Spring Bean**：由 `AutoConfiguration` 统一注册，`RedisTokenManager` 和 `TokenCachePreloadHandler` 共享同一实例
- **smart-cache-starter 升级**：1.0.4 → 1.1.2（支持 L2 预刷新、自定义 TTL；修复 preload 重试多执行一次的 bug）
- **AutoConfiguration 更新**：新增 `TokenRefreshExecutor` Bean 注册

### Removed

- `RedisTokenCacheStrategy`：缓存逻辑交给 SmartCacheManager
- `RedisKeyHelper`：key 管理交给 SmartCacheManager

### Breaking Changes

- `redis.token.me` 废弃，改用 `io.github.surezzzzzz.sdk.cache.me`
- 接入方需要新增 smart-cache 配置块（含 `l2.preload.enabled=true`）
- `TokenManager` 接口不变，业务代码无需修改

### Configuration

- `l2.expire-seconds` 应与 server 端 `jwt.expires-in` 保持一致（默认 3600s），而非 `jwt.expires-in - 30`
- `l2.preload.before-expire-seconds` 与 `token.refresh-before-expire` 对齐（默认 300s）
- 两者关系：L2 TTL 剩余 `before-expire-seconds` 时触发 preload，旧 token 在此窗口内仍可用
