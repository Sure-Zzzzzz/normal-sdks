# Changelog - v1.1.0

## [1.1.0] - 2026-04-17

### 重要变更

引入 `smart-cache-starter` 替换原有 Spring Cache + Redis 方案，实现 L1（Caffeine）+ L2（Redis）两级缓存，并通过 Redis Pub/Sub 在多实例间同步缓存失效，保证强一致性。

### Added

- **L1+L2 两级缓存**：`CachedOAuth2AuthorizationService` 完全重写，使用 `SmartCacheManager` 替代原 `CacheManager`。L1 Caffeine 本地缓存（默认 10s TTL），L2 Redis 缓存（与 JWT 过期时间一致），introspect 热路径命中 L1 无需访问 Redis
- **多实例 Pub/Sub 缓存失效**：`consistency.mode=strong` 模式下，任意实例 revoke token 后通过 Redis Pub/Sub 广播，其他实例的 L1 缓存立即清除，避免多副本间缓存不一致
- **SmartCache 配置**：新增 `io.github.surezzzzzz.sdk.cache.*` 配置项，key 格式与原 AKSK Redis key 格式完全兼容（`{keyPrefix}:{me}:{cacheName}::{key}`）

### Changed

- `CachedOAuth2AuthorizationService`：移除 `CacheManager` 依赖，改用 `SmartCacheManager`；`evictTokenCache` 方法改为 public，供 `TokenManagementServiceImpl` 直接调用
- `TokenManagementServiceImpl`：`evictTokenCache` 改用 `SmartCacheManager.evict()`，触发 Pub/Sub 广播
- `RedisTokenRepository`：注入 `smartCacheRedisTemplate` 替代默认 `redisTemplate`；JSON 反序列化改为直接解析 `JsonNode`，不再尝试将 `OAuth2Authorization` 完整反序列化（该类无 Jackson mixin，无法直接反序列化）
- `AuthorizationServerConfiguration`：移除 `OAuth2RedisCacheConfiguration` 依赖，改用 `SmartCacheManager` 构建缓存服务

### Removed

- `OAuth2RedisCacheConfiguration`：原 Spring Cache 配置类已删除

### Dependencies

- 新增 `io.github.sure-zzzzzz:smart-cache-starter:1.0.4`
- 新增 `com.github.ben-manes.caffeine:caffeine:2.9.3`

### Configuration

启用两级缓存需在应用配置中添加：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              redis:
                enabled: true
                token:
                  me: my-app
        cache:
          key-prefix: sure-auth-aksk
          me: ${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me:my-app}
          l1:
            enabled: true
            expire-seconds: 10
            max-size: 10000
          l2:
            enabled: true
            expire-seconds: ${io.github.surezzzzzz.sdk.auth.aksk.server.jwt.expires-in:3600}
            key-format: "{keyPrefix}:{me}:{cacheName}::{key}"
          consistency:
            mode: strong
```
