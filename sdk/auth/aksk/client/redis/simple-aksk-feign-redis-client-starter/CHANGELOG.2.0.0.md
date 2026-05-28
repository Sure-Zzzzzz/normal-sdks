# CHANGELOG - simple-aksk-feign-redis-client-starter 2.0.0

## 发布日期

2026-05-27

## 版本类型

Breaking Change - 依赖升级，1.x 封版

## 变更概述

`simple-aksk-redis-token-manager` 从 1.1.0 升级至 2.0.0，带来了以下核心变化：

1. **Token 有效性不再依赖 JWT 解析**：JWE 格式 Token 密文不可读，Token 有效性完全由 Redis TTL 保证
2. **TokenWithExpiry 模型变更**：`expiresIn`（相对秒数）改为 `expiresAt`（绝对 epoch 秒），避免相对值在缓存期间不变造成误导
3. **Preload 机制由框架驱动**：不再自己解析 JWT，改为接入 smart-cache 框架的 Redis TTL 预刷新机制

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-redis-token-manager` | 1.1.0 | 2.0.0 |

## 行为变更

- `AkskFeignRequestInterceptor` 行为不变，Token 获取逻辑由 `RedisTokenManager` 内部保证
- Token 刷新时机由 Redis TTL 驱动，不再依赖 JWT 解析

## 配置变更

新增 smart-cache 配置块（详见 README.md）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l2:
            expire-seconds: 3600
            preload:
              enabled: true
              before-expire-seconds: 60
```

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:2.0.0'
```

## 贡献者

- @surezzzzzz