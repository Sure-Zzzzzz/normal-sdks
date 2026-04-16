# Changelog - v1.0.2

## [1.0.2] - 2026-04-16

### Added
- **Redis Key 格式自定义功能**: 新增 `io.github.surezzzzzz.sdk.cache.l2.key-format` 配置项，支持自定义 Redis key 格式模板
  - 支持占位符：`{keyPrefix}`, `{cacheName}`, `{me}`, `{key}`
  - 默认格式：`{keyPrefix}:{cacheName}:{me}::{key}`（保持向后兼容）
  - 示例：`{keyPrefix}:{me}:{cacheName}::{key}`（AKSK 老格式）
  - 自动添加 hash tag 确保 Redis Cluster 兼容性
- 新增 `KeyHelper.buildCacheKey(String keyFormat, ...)` 方法支持自定义格式
- 新增 `KeyHelper.buildCacheKeyPattern(String keyFormat, ...)` 方法支持自定义格式的扫描 pattern
- 新增 `KeyFormatCustomizationTest` 测试类，包含 7 个测试用例验证自定义格式功能

### Changed
- `L2Cache.buildKey()` 方法改为从配置读取 `keyFormat`
- `L2Cache.clear()` 和 `L2Cache.size()` 方法使用新的 `buildKeyPattern()` 方法

### Deprecated
- `KeyHelper.buildCacheKey(String keyPrefix, String cacheName, String me, String key)` 方法标记为 `@Deprecated`，建议使用新的重载方法
- `KeyHelper.buildCacheKeyPattern(String keyPrefix, String cacheName, String me)` 方法标记为 `@Deprecated`，建议使用新的重载方法
- `SmartCacheProperties.L2Config.keyPrefix` 字段标记为 `@Deprecated`，建议使用 `keyFormat` 替代

### Fixed
- 无
