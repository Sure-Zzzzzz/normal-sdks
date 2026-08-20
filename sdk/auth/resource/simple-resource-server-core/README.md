# Simple Resource Server Core

来源中立的资源认证核心：在 IAM、AKSK 或未来 Provider 完成自身认证后，将已验证主体与应用授权快照绑定成不可变请求上下文。

## 核心边界

本模块只表达认证完成后的安全事实：

| 类型 | 含义 |
| --- | --- |
| `ResourceAuthenticationSourceId` | Provider 自己声明的稳定来源标识。 |
| `BearerResourceCredential` | 公共层完成来源路由后交给 Provider 的短生命周期凭据，日志固定脱敏。 |
| `VerifiedResourcePrincipal` | 仅含来源、主体类型和 Provider 内稳定主体标识。 |
| `ResourceAuthenticationResult` | Provider 返回的认证成功、拒绝或不适用结果。 |
| `VerifiedResourceContext` | 已绑定主体与应用授权快照的请求级上下文。 |

认证成功后，资源服务可用 `ResourceAuthenticationContextHelper` 创建上下文：

```java
VerifiedResourceContext context = ResourceAuthenticationContextHelper.createVerifiedContext(result, requestId);
```

创建时会校验主体类型和主体标识与 `ApplicationAuthorizationContext` 完全一致。任何不匹配、认证结果不完整或无效请求标识都会拒绝，不能产生上下文。

## 安全约束

- Provider 负责 token、JWE/JWS、introspection、issuer、audience、撤销与缓存。
- Core 不保存 cookie、证书、claims、用户显示名、邮箱、角色、scope 或请求对象；`BearerResourceCredential` 仅在一次 Provider 调用期携带原始凭据，禁止进入日志、事件或已验证上下文。
- `AUTHENTICATED` 只能同时携带主体和完整应用授权快照。
- `REJECTED` 只能携带安全失败分类；`NOT_APPLICABLE` 不携带主体或失败事实。
- 两个不同来源即使 subjectId 文本相同，也不会自动合并。

## 模块边界

这是 Java 8 纯 Core，不依赖 Spring、Servlet、IAM、AKSK、JSON、网络、数据库或加密实现。HTTP credential 采集、唯一 Provider 路由、Spring Security 上下文和 401/403 映射由 `simple-resource-server-starter` 负责。

## 许可证

Apache License 2.0
