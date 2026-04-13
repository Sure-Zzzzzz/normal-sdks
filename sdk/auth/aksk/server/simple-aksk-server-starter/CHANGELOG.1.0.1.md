# Changelog 1.0.1

## 新增

- 添加 `ClientManagementService.updateClientScopes()` 方法，支持更新客户端权限范围
- 添加 `ErrorCode.CLIENT_UPDATE_FAILED` 错误码常量
- 添加 `DefaultScopeAuthenticationConverter` 自定义认证转换器，支持换token时scope参数可选
- Admin 创建页面支持指定 scopes（默认值：`/api/**`）
- Admin 详情页支持查看和编辑 scopes（RESTful PATCH API）

## 修复

- 修复 `disableClient` 和 `enableClient` 方法使用错误的错误码（`CLIENT_CREATE_FAILED` → `CLIENT_UPDATE_FAILED`）

## 改进

### API 安全性
- `/api/client` 列表和详情接口不再返回 `clientSecret` 字段（设置为 null），仅创建时返回一次

### OAuth2 Token 换取
- 换token时 `scope` 参数变为可选
- 如果请求中不传 `scope`，自动使用数据库中注册的 scope
- 如果数据库中也没有 scope，抛出 `invalid_scope` 异常（防止数据完整性问题）

### Admin 页面体验优化
- `AdminController.createPlatform()` 和 `createUser()` 方法支持可选的 `scopes` 参数
- `AdminController` PATCH 端点支持更新 `scopes` 字段（使用 RESTful API）
- Admin 详情页新增"编辑权限范围"功能（Bootstrap Modal + AJAX）
- Admin 详情页编辑 scope 成功后先关闭 Modal 再刷新页面，提升用户体验
- Admin 首页启用/禁用 AKSK 成功后直接刷新页面，不再弹出成功提示
- Token 测试页面 scope 输入框支持留空，提示"留空则使用数据库中注册的scope"

