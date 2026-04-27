# 1.1.1 版本变更 (2026-04-27)

## 新增功能

### 1. Client Secret 重置

**接口**：`PUT /api/client/{clientId}/secret`

- 支持单独重置 Client Secret，不影响 Client ID
- 可选参数 `revokeTokens`（默认 true），控制是否同时撤销所有 Token
- 成功后返回新的明文 Secret（仅显示一次）
- 与 `regenerateSecretKey()` 方法复用，BCrypt 加密存储

**Admin 页面**：

- 在 AKSK 详情页新增"重置Secret"按钮
- 支持取消勾选"同时撤销该Client下所有现存Token"
- 成功后跳转到创建成功页面展示新Secret

### 2. 批量撤销 Token

**接口**：`DELETE /api/token?clientId={clientId}`

- 与 `GET /api/token?clientId=xxx` 对称，使用 DELETE 方法
- 仅撤销该 Client 下的活跃 Token（未过期、未撤销）
- 跳过已过期和已撤销的 Token，不计入 `revokedCount`
- 分页查询（每批 200 条），避免大量 Token 导致 OOM

**Admin 页面**：

- 在 AKSK 详情页新增"撤销所有Token"按钮
- 确认 Modal 显示警告信息
- 撤销结果展示实际影响的 Token 数量

## 优化

### 1. 过期 Token 处理优化

**问题**：1.1.0 及之前版本允许撤销已过期的 Token，这在逻辑上是不合理的。

**修复**：

- `revokeToken(id)` 新增过期检查，已过期直接返回，不更新 metadata
- `revokeAllByClientId()` 在调用 revokeToken 前也检查过期状态
- Admin token.html 页面：EXPIRED 状态的撤销按钮禁用
- 新增警告信息："已过期的Token无需撤销，可直接删除"

**效果**：过期 Token 统一由 `deleteExpiredTokens()` 处理。

### 2. 事件流优化

**TokenRevokedEvent 发布**：

- 批量撤销内部循环调用 `revokeToken()`，每个 token 发独立事件
- 不经过 `AuditableOAuth2AuthorizationService.save()`，避免重复事件
- 已过期和已撤销的 Token 不发布事件

### 3. 错误码和消息新增

**ErrorCode.java**：

- 新增 `TOKEN_CLIENT_ID_REQUIRED = "TOKEN_001"`

**ServerErrorMessage.java**：

- 新增 `CLIENT_ID_REQUIRED = "clientId 不能为空"`

## 接口文档

### Client Management API 新增

#### 重置 Client Secret

```
PUT /api/client/{clientId}/secret?revokeTokens=true
```

**请求参数**：

| 参数           | 类型      | 默认值  | 说明                         |
|--------------|---------|------|----------------------------|
| revokeTokens | boolean | true | 是否同时撤销该 Client 下所有现存 Token |

**响应**：

```json
{
  "clientId": "AKP1234567890abcdefgh",
  "clientSecret": "SK1234567890abcdefghijklmnopqrstuvwxyz1234"
}
```

### Token Management API 新增

#### 批量撤销 Token

```
DELETE /api/token?clientId={clientId}
```

**响应**：

```json
{
  "revokedCount": 5
}
```

## Admin 页面更新

### AKSK 详情页 (`/admin/{clientId}`)

1. 新增"撤销所有Token"按钮 → 确认 Modal
2. 新增"重置Secret"按钮 → 确认 Modal（含可选撤销 Token 复选框）

### Token 列表页 (`/admin/token`)

- EXPIRED 状态的撤销按钮禁用，并显示 tooltip 提示

## 实现细节

### 技术方案

1. **批量处理**：使用 Spring Data JPA 分页查询（每页 200 条）
2. **Direct SQL**：直接更新 `oauth2_authorization.access_token_metadata`，不走 `AuditableOAuth2AuthorizationService`
3. **Transaction**：所有修改操作使用 `@Transactional` 注解
4. **Cache Invalidation**：每个 token 撤销后清除 L1+L2 缓存，并通过 Redis Pub/Sub 广播
5. **Client ID 转换**：通过 AKSK 字符串查找 OAuth2RegisteredClient 实体（UUID）

### 代码变更

#### 新增类

| 类名                  | 包路径                                                           | 说明           |
|---------------------|---------------------------------------------------------------|--------------|
| BatchRevokeResponse | io.github.surezzzzzz.sdk.auth.aksk.server.controller.response | 批量撤销响应       |
| ResetSecretResponse | io.github.surezzzzzz.sdk.auth.aksk.server.controller.response | 重置 Secret 响应 |

#### 修改类

| 类名                          | 方法                     | 说明                                      |
|-----------------------------|------------------------|-----------------------------------------|
| TokenManagementService      | revokeAllByClientId()  | 接口新增                                    |
| TokenManagementServiceImpl  | revokeAllByClientId()  | 实现批量撤销                                  |
| TokenManagementServiceImpl  | revokeToken()          | 新增过期检查                                  |
| ClientManagementService     | resetSecret()          | 接口新增                                    |
| ClientManagementServiceImpl | resetSecret()          | 实现重置 Secret，依赖注入 TokenManagementService |
| TokenManagementController   | revokeByClientId()     | 新增 DELETE /api/token 接口                 |
| ClientManagementController  | resetSecret()          | 新增 PUT /api/client/{clientId}/secret 接口 |
| AdminController             | adminRevokeAllTokens() | 新增 DELETE /admin/{clientId}/token 接口    |
| AdminController             | adminResetSecret()     | 新增 PUT /admin/{clientId}/secret 接口      |
| admin/detail.html           | 新增按钮+Modal             | 新增批量撤销和重置 Secret 操作                     |
| admin/token.html            | 撤销按钮禁用                 | EXPIRED 状态按钮禁用                          |

## Bug 修复

### 1. 删除 Token 时 MySQL 报 EmptyResultDataAccessException

**问题**：在 Redis Token 列表页删除 token 时，若该 token 已不在 MySQL 中，`deleteById()` 会抛出异常。

**修复**：`deleteToken()` 删除前先检查 MySQL 中是否存在，不存在则跳过，不再抛出异常。

### 2. 只在 Redis 中存在的 Token 撤销时不发布事件

**问题**：当 token 只存在于 Redis（MySQL 已清理）时，`revokeToken()` 直接删除 Redis 数据，未发布 `TokenRevokedEvent`
，导致审计链路断裂。

**修复**：MySQL 中找不到 token 时，先从 Redis 扫描获取 token 信息，发布完整的 `TokenRevokedEvent` 后再删除 Redis 数据。

### 3. Redis 列表页撤销 Token 后刷新状态不更新

**问题**：`RedisTokenRepository.deleteById()` 使用固定 key 格式删除，若 Redis 中实际 key 格式不匹配则删除失败，导致刷新后仍能看到已撤销的
token。

**修复**：`deleteById()` 先尝试标准 key 格式删除，失败时 fallback 为 SCAN 扫描匹配 id 的所有 key 并删除，确保彻底清理。

### 4. Token 详情页缺少 REVOKED 状态显示

**问题**：`token-detail.html` 状态 badge 只处理了 `ACTIVE` 和 `EXPIRED`，已撤销的 token 详情页状态栏空白。

**修复**：

- 补充 `REVOKED` 状态 badge（红色"已撤销"）
- `EXPIRED` badge 颜色改为灰色（`bg-secondary`），与"有效"区分更清晰
- `REVOKED` 状态下隐藏"剩余时间"行，避免显示无意义的时间信息

## 测试覆盖

### 单元测试

- `RevokeTokenServiceTest`：覆盖正常撤销、过期 Token 跳过、已撤销 Token 跳过
- `BatchRevokeServiceTest`：覆盖批量撤销、混合状态、分页查询、异常场景
- `ResetSecretServiceTest`：覆盖重置 Secret、顺序验证、参数验证
- `AdminControllerTest`：新增 Token 详情页三种状态（ACTIVE / REVOKED / EXPIRED）渲染验证

### 集成测试

- `BatchRevokeTest`：通过实际接口测试，验证 introspect 返回 active=false
- `ResetSecretTest`：验证新 Secret 生效、旧 Token 失效（可选）

## 版本兼容性

- 向下兼容所有 1.x 版本
- 新增接口不影响现有功能
- 数据库结构无变化
- Token 格式保持一致

## 升级指南

### 修改依赖版本

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.1.1'
}
```

### 无其他配置变更

1.1.1 版本为纯功能新增和优化，无需修改配置文件。
