# Changelog - v1.1.0

## [1.1.0] - 2026-05-06

### Fixed

- **AkskFeignRequestInterceptor 覆盖 Authorization 头**：`template.header()` 是追加语义，当请求已有 `Authorization` 头时会产生重复值。改为先 `removeHeader` 再 `header`，确保始终只有一个 `Authorization` 头

### Changed

- **simple-aksk-redis-token-manager 升级**：1.0.1 → 1.1.0
  - Token 缓存接入 SmartCacheManager（L1 Caffeine + L2 Redis 两级缓存）
  - 新增 `TokenCachePreloadHandler`，通过解析 JWT 判断 `EXPIRING_SOON` 触发 L2 预刷新，避免 token 过期导致请求失败
  - 多实例 L1 一致性：`clearToken()` 通过 Pub/Sub 广播 L1 失效
  - 接入方需新增 smart-cache 配置块（详见 redis-token-manager README）
