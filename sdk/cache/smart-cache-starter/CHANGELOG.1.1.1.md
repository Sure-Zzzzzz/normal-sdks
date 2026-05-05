# Changelog - v1.1.1

## [1.1.1] - 2026-05-05

### Added

- **自定义 L2 TTL**：业务侧可为特定缓存场景覆盖全局 `l2.expire-seconds`，无需改配置
  - 编程式 API：`SmartCacheManager.put(cacheName, key, value, ttlSeconds)` 和 `get(cacheName, key, loader, ttlSeconds)`
  - 注解式 API：`@SmartCacheable(l2TtlSeconds = 300)` 和 `@SmartCachePut(l2TtlSeconds = 300)`
  - `l2TtlSeconds = 0`（默认）表示使用全局配置，向后完全兼容
  - `l2TtlSeconds <= 0` 统一 fallback 到全局配置，不抛异常
- **CachePreloadHandler.getReloadTtlSeconds**：preload 续期时可指定写 L2 的 TTL，默认返回 0（使用全局配置），返回 >0 表示业务侧覆盖，与 `l2TtlSeconds` 语义一致。解决自定义 TTL 的 key 续期后 TTL 被静默回退到全局配置的问题

### Changed

- **L2Cache 显式 TTL 重载加 jitter**：`put(cacheName, key, value, ttlSeconds)` 现在也通过 `calculateActualTtl` 加随机偏移，与全局配置路径行为一致，防止缓存雪崩
- **L2Cache.calculateActualTtl 增强**：增加 `properties.getL2()` 的 null guard；运算改用 `long` 防止极端 TTL + ratio 组合下 int 溢出
- **SmartCacheManager.get 去重**：`get(cacheName, key, loader)` 委托到 `get(cacheName, key, loader, 0)`，消除循环依赖检测的重复代码
- **SmartCacheManager.asyncPreload 使用 handler TTL**：续期时调用 `put(cacheName, key, newValue, handler.getReloadTtlSeconds(cacheName, key))`
- **删除 L2Cache.put(TimeUnit) 重载**：该重载跳过 `calculateActualTtl`，破坏防雪崩保证且无外部调用者

### Fixed

- **L2Cache.put(int) 传 0 时写 Redis 异常**：原实现 `calculateActualTtl(0)` 返回 0 传给 `set(key, value, 0, SECONDS)`，在部分 Redis 版本下会删除 key 或抛异常。`SmartCacheManager` 层保证 `ttlSeconds <= 0` 时走全局配置路径，不会调用此重载
