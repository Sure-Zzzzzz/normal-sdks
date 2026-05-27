# Changelog - 2.0.0

## Breaking Changes

- **移除 Token 状态解析**：不再解析 JWT/TokenStatus，Token 有效性完全由 Redis TTL 保证
  - 移除 `TokenCacheStrategy`、`AbstractTokenManager` 继承
  - 移除 `checkTokenStatus()`、`TokenStatus` 枚举
  - 不再依赖 `nimbus-jose-jwt` 依赖

## New Features

- **TokenWithExpiry 模型**：Token 存储结构包含 `{ token, expiresAt, securityContext }`
  - `expiresIn` 改为 `expiresAt`（绝对 epoch 秒），避免相对值在缓存期间不变造成误导
  - `expiresAt` = fetchTime + server返回的expiresIn，每次 fetch 动态计算
- **分布式一致性**：reload() 从 Redis 读取 securityContext，保证多实例使用相同上下文
- **动态 TTL**：TTL 直接使用 server 返回的 `expiresIn`，无减法偏移
- **本地锁兜底**：分布式锁失效时使用本地锁防止击穿

## Architecture

```
缓存流程：L1 → L2 → 抢分布式锁 → fetch → 写回 L1 + L2

L1: Caffeine（2s），减少 Redis IO
L2: Redis（expiresIn 秒），多实例共享
分布式锁: 防止缓存击穿
L2 预刷新: Redis TTL <= 60s 时触发
```

## Configuration

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l2:
            expire-seconds: 3600    # TTL 兜底
            preload:
              enabled: true
              before-expire-seconds: 60  # 预刷新窗口
```

## Dependencies

- `simple-aksk-client-core:2.0.0`
- `smart-cache-starter:1.1.2`