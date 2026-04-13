# Changelog - v1.0.5

## 发布日期

2026-04-10

## 版本类型

**Minor Release** - 功能增强 + Bug 修复

## 变更概述

本版本引入 token 全生命周期审计事件、完整的 token 撤销能力、Admin 管理页面撤销操作，以及品牌 Logo/Favicon。

---

## 新增功能

### 1. Token 审计事件（依赖 simple-aksk-server-core:1.0.2）

新增 `AuditableOAuth2AuthorizationService` 审计层，始终包装在最外层，无论是否启用 Redis 均会发布事件：

| 事件 | 触发时机 |
|------|----------|
| `TokenIssuedEvent` | `/oauth2/token` 颁发新 token |
| `TokenRevokedEvent` | token 被撤销 |
| `TokenRemovedEvent` | Spring Authorization Server 内部删除 token |
| `TokenIntrospectedEvent` | `/oauth2/introspect` 查询 token 状态，含 `active` 字段 |

所有事件继承 `AbstractTokenEvent`，包含完整审计字段：`clientId`、`clientType`、`userId`、`username`、`tokenValue`、`scopes`、`issuedAt`、`expiresAt`、`eventTime`。通过 `TokenEventType` 枚举区分事件类型，无需硬编码字符串。

服务分层结构：
```
JdbcOAuth2AuthorizationService（数据库）
  └── CachedOAuth2AuthorizationService（Redis 缓存，可选）
        └── AuditableOAuth2AuthorizationService（审计事件，始终启用）
```

### 2. Token 撤销能力

新增 `TokenManagementService.revokeToken(id)` 方法，直接操作数据库 metadata 标记 token 为 invalidated，不依赖 client 信息，不走 Spring Authorization Server 的 `save()` 流程，避免 `RegisteredClient not found` 错误。

撤销时同步处理：
- 更新数据库 `access_token_metadata`，写入 `metadata.token.invalidated=true`
- evict Redis 缓存（Spring Cache 按 id 索引 + 按 token value 索引 + RedisTokenRepository 扫描 key）
- 发布 `TokenRevokedEvent`

`deleteToken` 改为先撤销再删除，确保 token 在删除前已失效。

### 3. Admin 页面 Token 撤销操作

- Token 列表页（`/admin/token`）新增"撤销"按钮，撤销后状态显示为"已撤销"（橙色 badge）
- Token 详情页新增"撤销此Token"按钮
- 统计卡片新增"已撤销Token"计数
- 状态过滤支持 REVOKED 状态

新增 Admin 接口（走 session 认证，无需 scope）：
- `POST /admin/token/{id}/revoke` — 撤销 token
- `DELETE /admin/token/{id}` — 删除 token（先撤销再删除）

### 4. Token 状态 REVOKED

`TokenInfo.TokenStatus` 新增 `REVOKED` 枚举值（依赖 simple-aksk-core:1.0.2）。

MySQL 列表页通过解析 `access_token_metadata` JSON 中的 `metadata.token.invalidated` 字段判断撤销状态。Redis 列表页通过 `OAuth2Authorization.Token.isInvalidated()` 判断。

### 5. 品牌 Logo / Favicon

新增 `/admin/img/aksk-logo.svg` 和 `/admin/img/aksk-icon.svg`，所有 Admin 页面统一引用。`/admin/img/**` 路径已加入 Security 放行规则。

---

## Bug 修复

### revoke 后 introspect 仍返回 active=true

revoke 后 token 被标记为 invalidated 并重新 save，但 token 缓存（按 token value 索引）未同步清除，导致 introspect 从缓存拿到旧的未撤销对象。

修复：save 时检测到 token 被撤销，同时 evict token 缓存（null 和 access_token 两种 key 都清除）。

### CachedOAuth2AuthorizationService 字段注入失效

`CachedOAuth2AuthorizationService` 不再是最终注册的 bean，Spring 不会对其做字段注入。改为构造注入，由 `AuthorizationServerConfiguration` 显式传入 `CacheManager`、`RedisKeyHelper`、`TaskRetryExecutor`。

---

## 依赖变更

```gradle
api 'io.github.sure-zzzzzz:simple-aksk-core:1.0.2'      // 新增 TokenStatus.REVOKED
api 'io.github.sure-zzzzzz:simple-aksk-server-core:1.0.2' // 新增审计事件类
```

## 向后兼容性

✅ **完全向后兼容**，现有配置无需修改。
