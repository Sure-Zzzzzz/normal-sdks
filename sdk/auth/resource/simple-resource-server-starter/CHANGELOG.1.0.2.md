# simple-resource-server-starter 1.0.2 变更记录

## Provider 凭据契约下沉

- 依赖已发布的 `simple-resource-server-core:1.0.1`。
- `BearerResourceCredential` 作为公共 Provider 调用期契约由 `simple-resource-server-core` 统一提供。
- 公共 Resolver 和 Resolution 直接使用 Core `BearerResourceCredential`，移除 Starter 私有的同名类型，避免将 Provider 凭据契约绑定在 HTTP 执行层。
- IAM、AKSK 及未来 Provider 统一依赖公共 Core 凭据契约；Provider Starter 不反向传递公共 Resource Starter。

## 公共安全链保持不变

- 唯一 Bearer 入口、载体歧义拒绝和 `kid` 来源路由保持不变。
- Provider 认证编排、应用准入、精确 API permission、DATA 授权、统一 `401/403` 和 Spring Security 链行为保持不变。
- Provider 仍不得创建竞争的最终业务 `SecurityFilterChain`，也不得从 URL、HTTP method、角色、OAuth scope 或未验证上下文推导 API/DATA 权限。

## 兼容矩阵

已按完整模块测试验证：

- Spring Boot `2.2.13.RELEASE`
- Spring Boot `2.3.12.RELEASE`
- Spring Boot `2.4.5`
- Spring Boot `2.7.9`

每档测试均为完整模块测试，未使用定向测试替代关键场景；最后结果为 `30 tests / 0 skipped / 0 failures / 0 errors`。公共 Core 外部坐标和 AKSK Provider 外部 Core 坐标联调也已验证通过。

## 升级说明

业务方升级到 1.0.2 时，无需修改现有资源路径或安全配置。Provider Starter 应与本 Starter 显式并列引入，并由各 Provider 自己依赖公共 Core 的认证契约。原来直接引用 Starter 私有 `BearerResourceCredential` 的 Provider，须改为引用 Core 同名类型。
