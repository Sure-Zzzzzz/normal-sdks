# simple-aksk-resource-server-starter 3.0.0

## 版本定位

3.0.0 将 AKSK Resource Server Starter 收口为公共 Resource Server Starter 的 Provider-only 适配模块。

## 变更

- 仅注册 AKSK `ResourceAuthenticationAdapter`，不再创建独立 Bearer 入口、最终业务 `SecurityFilterChain`、路径权限链、旧 AOP 权限链、Header/request context 或 AKSK 私有访问事件。
- AKSK Provider 统一使用公共 Resource Core 的 `BearerResourceCredential`，按 `sourceId=aksk` 接收公共资源链路路由的凭据。
- 公共 Resource Server Starter 负责 Bearer 解析、`kid` 路由、Provider 编排、统一 `401/403`、API/DATA 授权和公共访问事件。
- AKSK 认证要求 introspection 结果为 `active=true`，`client_id` 为字符串，应用授权主体为 `SERVICE`，且 `client_id` 与授权主体 ID 一致。
- 启用 AKSK Provider 时，`introspect.endpoint`、`client-id` 和 `client-secret` 缺任一项均显式 fail-fast；自定义命名为 `akskOpaqueTokenIntrospector` 的内省器可接管 HTTP 内省实现。
- 主 introspection 缓存默认关闭；stale fallback 默认关闭，只有显式开启且缓存中存在 `active=true` 条目时才允许故障降级。
- 清理 2.x 独立安全链、路径和 JWT/PEM 公钥相关实现及未使用的测试令牌辅助代码。
- 删除 AKSK Starter 对 Nimbus OAuth2 SDK 的生产 `api` 暴露，仅保留测试所需依赖。
- Caffeine 本地缓存库从生产 `api` 降级为 `implementation` 依赖：`IntrospectLocalCacheHelper` 的缓存字段为 `private`，公开方法只暴露自定义 `IntrospectResult`，不需要向下游暴露 Caffeine 类型；运行时仍按 Gradle 依赖图仲裁版本，业务方无需额外声明该依赖。

## 接入变化

业务资源服务需要显式并列引入：

```gradle
implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.0.2'
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.0.0'
```

AKSK Provider Starter 不再通过传递依赖提供公共 Resource Server Starter，也不依赖 IAM Resource Server Starter。

## 安全边界

- `kid` 只用于 Provider 路由，不能替代 AKSK Server 对令牌、签发方、受众、时效、撤销和授权快照的验证。
- 认证失败不回退到其他 Provider。
- scope、角色、PAGE 权限、URL、HTTP method、Controller 名称和未验证 `security_context` 不授予 API/DATA 权限。
- 日志和测试 fixture 不输出 Token、认证头、Secret、Cookie、完整 introspection response、完整授权响应或本机凭据。

## 升级提示

3.0.0 是安全链边界调整版本。使用 2.x 独立安全链、旧路径配置、旧 Header/request context 或 AKSK 私有访问事件的应用，需要迁移到公共 Resource Server Starter 提供的统一安全链；业务方必须显式配置公共 Starter 和 AKSK Provider Starter。
