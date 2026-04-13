# Changelog 1.0.3

## 新增

### REST API 权限控制
- 为 `/api/client` 和 `/api/token` 添加基于 OAuth2 scope 的权限控制
  - `/api/client` 要求 JWT token 包含 `/api/client` scope
  - `/api/token` 要求 JWT token 包含 `/api/token` scope
  - 使用 `@RequireExpression` 注解实现声明式权限控制
  - 支持精确 scope 匹配，通配符 scope（如 `/api/**`）不能访问这些高安全 API

### JWT Context Provider
- 新增 `CustomJwtContextProvider` 类，从 JWT claims 中提取安全上下文
  - 自动提取 `scope`、`client_id`、`user_id`、`username`、`client_type` 等字段
  - 支持 scope 的多种格式（`List<String>` 或 `String`）
  - 与 `simple-aksk-resource-core` 框架集成

### 安全异常处理
- 新增 `SimpleAkskSecurityExceptionHandler` 统一处理权限异常
  - 权限不足时返回 `403 FORBIDDEN` 状态码
  - 返回清晰的错误消息，便于调试

## 改进

### 管理页面优化
- Token 管理页面 (`/admin/token`) 现在从服务端直接获取 Client 信息
  - 移除了前端 AJAX 调用 `/api/client` 的代码
  - 提升页面加载速度，减少不必要的 API 调用
  - 降低安全风险，避免管理页面受 scope 限制影响

### Token 测试页面增强
- Token 测试页面 (`/admin/token/test`) 现在能正确处理 403 错误响应
  - 根据 Content-Type 自动判断响应格式（JSON 或纯文本）
  - 为 403 错误添加友好提示，引导用户使用正确 scope 的 token

## 技术细节

### 依赖更新
```gradle
api 'io.github.sure-zzzzzz:simple-aksk-resource-core:1.0.0'
api 'org.springframework.security:spring-security-oauth2-authorization-server:0.4.1'
```

### 配置变更
- `SimpleAkskServerAutoConfiguration` 添加 `@EnableAspectJAutoProxy` 启用 AOP
- 添加 `@ComponentScans` 扫描 `simple-aksk-resource-core` 包中的组件

### 新增类
- `io.github.surezzzzzz.sdk.auth.aksk.server.support.CustomJwtContextProvider`
- `io.github.surezzzzzz.sdk.auth.aksk.server.support.SimpleAkskSecurityExceptionHandler`

### 修改的类
- `io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerAutoConfiguration`
- `io.github.surezzzzzz.sdk.auth.aksk.server.controller.ClientManagementController`
- `io.github.surezzzzzz.sdk.auth.aksk.server.controller.TokenManagementController`
- `io.github.surezzzzzz.sdk.auth.aksk.server.controller.AdminController`

### 修改的模板
- `src/main/resources/templates/admin/token.html`
- `src/main/resources/templates/admin/token-test.html`

### 权限控制实现

使用 SpEL 表达式进行精确 scope 匹配：

```java
@RequireExpression(
    value = "#context['scope'] != null && " +
            "(' ' + #context['scope'] + ' ').contains(' /api/client ')",
    message = "Access denied: /api/client scope required"
)
```

通过在 scope 前后添加空格，确保精确匹配：
- ✅ `/api/client` → 匹配成功
- ✅ `/api/client /api/token` → 匹配成功
- ❌ `/api/**` → 匹配失败（通配符不能访问）
- ❌ `/api/client-admin` → 匹配失败（避免前缀匹配）

## 使用场景

### 场景1：创建带 scope 的 AKSK

```bash
# 创建包含 /api/client scope 的平台级 AKSK
POST /api/client
{
  "type": "platform",
  "name": "Client Management API",
  "scopes": ["/api/client"]
}

# 创建包含多个 scope 的 AKSK
POST /api/client
{
  "type": "platform",
  "name": "Full API Access",
  "scopes": ["/api/client", "/api/token"]
}
```

### 场景2：获取 Token 并访问 API

```bash
# 1. 获取 token（指定 scope）
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic {base64(clientId:clientSecret)}

grant_type=client_credentials&scope=/api/client

# 响应
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "/api/client"
}

# 2. 使用 token 访问 API
GET /api/client
Authorization: Bearer eyJhbGc...

# 成功响应（200 OK）
{
  "data": [...],
  "total": 10
}
```

### 场景3：权限不足的处理

```bash
# 使用没有 /api/client scope 的 token
GET /api/client
Authorization: Bearer {token_without_scope}

# 失败响应（403 Forbidden）
Access denied: /api/client scope required
```

## 测试覆盖

- ✅ 78 个测试用例全部通过
- ✅ 正面测试：持有正确 scope 的 token 可以访问 API
- ✅ 负面测试：无 scope 或错误 scope 的 token 被拒绝（返回 403）
- ✅ 边界测试：通配符 scope 不能访问高安全 API
- ✅ 兼容性测试：默认 scope（read, write）仍然正常工作

新增测试用例：
- `ClientManagementIntegrationTest.testAccessDeniedWithoutRequiredScope()`
- `ClientManagementIntegrationTest.testAccessDeniedWithWildcardScope()`
- `ClientManagementIntegrationTest.testGetTokenWithDefaultScope()`
- `TokenManagementIntegrationTest` 中的类似测试

## 破坏性变更

### ⚠️ API 访问要求变更

**影响范围**：所有通过 JWT token 访问 `/api/client` 和 `/api/token` 的客户端

**变更内容**：
- `/api/client` 端点现在要求 JWT token 包含 `/api/client` scope
- `/api/token` 端点现在要求 JWT token 包含 `/api/token` scope

**不受影响的部分**：
- 管理页面 (`/admin/**`) 不受影响，仍然使用表单登录
- OAuth2 token 端点 (`/oauth2/token`) 不受影响
- 已有的 AKSK 不受影响，但需要在创建时指定 scope 才能访问新的 API

## 升级指南

从 1.0.2 升级到 1.0.3：

### 1. 更新依赖
```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.0.3'
```

### 2. 检查现有客户端
确认哪些客户端需要访问 `/api/client` 或 `/api/token`：
- 如果是通过管理页面访问，无需修改
- 如果是通过 API 访问，需要创建包含相应 scope 的新 AKSK

### 3. 创建新 AKSK（如需要）
```bash
# 为需要访问 /api/client 的客户端创建 AKSK
POST /api/client
{
  "type": "platform",
  "name": "Client API Access",
  "scopes": ["/api/client"]
}
```

### 4. 更新客户端代码
在获取 token 时指定正确的 scope：
```bash
# 旧方式（不指定 scope）
grant_type=client_credentials

# 新方式（指定 scope）
grant_type=client_credentials&scope=/api/client
```

### 5. 处理 403 错误
在客户端代码中添加 403 错误处理：
```java
try {
    response = restTemplate.exchange(url, HttpMethod.GET, entity, ResponseType.class);
} catch (HttpClientErrorException.Forbidden e) {
    // 权限不足，检查 token 的 scope
    log.error("Access denied: {}", e.getResponseBodyAsString());
}
```

### 6. 测试验证
- 验证持有正确 scope 的 token 可以访问 API
- 验证无 scope 或错误 scope 的 token 被正确拒绝
- 验证管理页面功能正常

## 安全建议

1. **最小权限原则**：为每个客户端分配最小必要的 scope 集合
2. **定期审计**：定期审计 token 的 scope 使用情况
3. **监控告警**：监控 403 错误，及时发现权限配置问题
4. **避免通配符**：不要使用通配符 scope（如 `/api/**`）访问高安全 API

## 兼容性

- ✅ 向后兼容：管理页面功能不受影响
- ⚠️ API 变更：`/api/client` 和 `/api/token` 需要相应 scope
- ✅ 配置兼容：无需修改现有配置文件
- ✅ 数据兼容：无需迁移数据库
