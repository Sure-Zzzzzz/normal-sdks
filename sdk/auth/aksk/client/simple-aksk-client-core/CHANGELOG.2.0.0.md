# CHANGELOG - simple-aksk-client-core 2.0.0

## 发布日期

2026-05-26

## 版本类型

Breaking Change - 移除 JWT Token 解析，兼容 JWE 加密格式

## 变更概述

1. **移除 JWT Token 解析**：`checkTokenStatus()` 依赖 Nimbus 解析 JWT `exp` 字段，server 2.0.0 签发 JWE 加密 Token 后无法解析，导致每次请求都同步刷新 Token，缓存完全失效。移除该逻辑，Token 有效性改由缓存 TTL 保证。
2. **移除 nimbus-jose-jwt 依赖**：不再需要解析 Token 内容。
3. **简化 AbstractTokenManager**：缓存命中直接返回，不再检查 Token 状态；缓存未命中同步获取。
4. **依赖升级**：simple-aksk-core 升至 2.0.0，task-retry-starter 升至 1.0.1。

## 变更详情

### 删除

| 内容 | 说明 |
|------|------|
| `TokenRefreshExecutor.TokenStatus` 枚举 | VALID / EXPIRING_SOON / EXPIRED / UNPARSABLE，不再需要 |
| `TokenRefreshExecutor.checkTokenStatus()` | 依赖 JWT 解析，JWE 格式下永远返回 UNPARSABLE |
| `TokenRefreshExecutor.asyncRefreshToken()` | 原来为 EXPIRING_SOON 预刷新用，现已无调用 |
| `TokenParseException` 异常类 | JWT 解析场景已移除，不再需要 |
| `nimbus-jose-jwt:10.0.2` 依赖 | 移除 |
| `TokenConfig.refreshBeforeExpire` 配置项 | 移除，整个 TokenConfig 内部类删除 |
| `DEFAULT_REFRESH_BEFORE_EXPIRE` 常量 | 移除 |
| `SESSION_TOKEN_EXPIRY_KEY` 常量 | 移除 |
| `TOKEN_EARLY_EXPIRY_SECONDS` / `TOKEN_MIN_TTL_SECONDS` 常量 | 合并为硬编码值（30s / 60s） |

### 简化

**AbstractTokenManager.getToken()**：

```
旧：缓存命中 → checkTokenStatus() → VALID 返回 / EXPIRING_SOON 异步刷新后返回 / EXPIRED|UNPARSABLE 同步刷新
新：缓存命中 → 直接返回（缓存 TTL 由 calculateTtl 保证提前 30s 过期）
```

### 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-core` | 1.0.0 | 2.0.0 |
| `task-retry-starter` | 1.0.0 | 1.0.1 |
| `nimbus-jose-jwt` | 10.0.2 | **已移除** |

## 行为变更

- **EXPIRING_SOON 异步预刷新优化已移除**：原来 Token 快过期时会提前异步刷新，避免下次请求等待。现在改为缓存过期后同步刷新，每个 Token 生命周期末尾有一次请求需等待刷新（通常 < 500ms）。
- **缓存 TTL 是唯一有效性保证**：`TokenCacheStrategy.calculateTtl()` 将缓存 TTL 设为 `expiresIn - 30s`（且不低于 60s），确保缓存过期时 Token 仍有 30s 有效期，刷新窗口充足。
- **TTL 常量合并为硬编码**：`TOKEN_EARLY_EXPIRY_SECONDS`（30s）和 `TOKEN_MIN_TTL_SECONDS`（60s）不再作为常量暴露，直接在 `calculateTtl()` 中硬编码。

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-client-core:2.0.0'
```

无需修改业务代码。如果你的代码直接引用了 `TokenRefreshExecutor.TokenStatus` 或 `checkTokenStatus()`，需要删除相关调用。

## 贡献者

- @surezzzzzz
