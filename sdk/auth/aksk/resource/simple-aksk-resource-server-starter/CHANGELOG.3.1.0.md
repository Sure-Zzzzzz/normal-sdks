# simple-aksk-resource-server-starter 3.1.0 变更记录

## 新增：OWNER_INHERITED AKU 接收能力（资源端所属人继承）

资源服务可显式声明自身身份并接收 IAM 所属人继承 AKU 的访问令牌：

- **配置**：`Introspect.OwnerInheritedConfig`（`...aksk.resource.server.introspect.owner-inherited.*`）——`strict-online`（默认 `false`）声明本实例接收 inherited AKU；`target-application-id` 填本资源服务对应的 IAM 可信应用 ID（启用时必填正数，启动校验 fail-fast）。
- **严格在线语义**：启用后本实例全部 AKSK Bearer 不再先读本地内省缓存，一律在线内省——opaque token 在内省前没有可信的授权模式，先读缓存会让首次请求绕过 IAM 当前授权（撤权即时性优先，性能让位）。
- **目标应用绑定校验**：`authorization_mode=OWNER_INHERITED` 的令牌必须同时满足——本实例已启用 inherited、主体类型为 `HUMAN`、`target_application_id` claim 等于本实例绑定的应用 ID，任一不满足即拒绝（防止 A 应用的 inherited 凭据访问 B 应用的资源服务）。
- **默认行为不变**：未配置 `owner-inherited` 的存量部署，行为与 3.0.1 完全一致（inherited 令牌按原逻辑拒绝，`STATIC_LEGACY` AKP/AKU 按 SERVICE 主体认证）。

## 兼容性

- 纯增量：既有配置键、装配链、`ResourceAuthenticationAdapter` 注册与缓存行为零变化；不升级依赖（`simple-aksk-core:3.0.2` 的 `AkskAuthorizationMode`/claims 常量自 3.0.1 起已可用）。
- 与 AKSK Server `3.2.x` 组合：门户自助创建的 inherited AKU 换发的 AKSK Token 可直接访问本资源服务（需按上节配置）；与 jakarta 线 `simple-aksk-resource-server-jakarta-starter` 的能力差异收敛——jakarta `1.0.0` 仍不接收 inherited AKU，SB3 资源端待后续版本对齐。

## 测试

- 49 项模块测试全绿（3.0.1 基线 + 新增 inherited 组）：explicit STATIC_LEGACY SERVICE 认证、OWNER_INHERITED HUMAN 认证、跨应用 inherited 拒绝、STATIC 模式携 HUMAN 主体拒绝、严格在线跳过缓存路径。
