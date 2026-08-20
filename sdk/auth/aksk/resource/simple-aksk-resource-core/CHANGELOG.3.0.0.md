# CHANGELOG - simple-aksk-resource-core 3.0.0

## 发布日期

2026-08-20

## 版本类型

Breaking Change - 与 AKSK Resource Server 3.0.0 的 Provider-only 架构同步升级。

## 变更概述

- Core 收敛为纯 Java 的 AKSK 资源内省协议契约，不再包含 Spring、Servlet、Spring Security、AOP、SpEL、网络或持久化依赖。
- 公共 `simple-resource-server-starter` 是唯一 Bearer 入口、认证编排、应用准入、精确 API permission 和 DATA 授权执行链。
- AKSK Provider 仅通过已认证 introspection 校验服务主体和应用授权快照，再返回公共 `ResourceAuthenticationResult`。

## 不兼容变更

以下 2.x API 已从 3.0.0 完全删除，不提供 deprecated 壳、转发类型或运行时兼容路径：

- `AkskAccessEvent`、`AkskContextHelper`；
- `SimpleAkskSecurityAspect`、`SimpleAkskSecurityContextProvider`、`SimpleAkskSecurityContextHelper`；
- `RequireContext`、`RequireField`、`RequireFieldValue`、`RequireExpression`；
- `SimpleAkskSecurityException`、`SimpleAkskExpressionException`；
- `SimpleAkskResourceCoreComponent`、`HeaderNameConverter`、`SimpleAkskResourceConstant`。

3.0 不再提供 request attribute、Header 转上下文、任意 claims 转 context Map、scope authority、角色或 scope 推导 API/DATA 权限、SpEL 授权和 AKSK 专属 Spring 访问事件。

需要继续使用该 2.x 安全上下文链的业务必须维持已发布的 2.x 依赖；它不是 AKSK Resource 3.0 的升级或兼容路径。

## 新的访问观测边界

已认证访问统一由 `simple-resource-server-starter` 发布 `ResourceAccessEvent`。事件只包含已验证来源、主体、应用授权和请求摘要，不包含 Token、认证头、Secret、Cookie、完整 claims、Servlet request、request attribute 或未验证 `security_context`。

## 验证

- 新增 Core 生产源码依赖边界测试，禁止框架和 Starter 反向依赖；
- Core 完整测试覆盖唯一协议字段 `active` 与依赖边界；
- Gradle 运行时依赖报告为空，构建产物仅包含 `AkskResourceIntrospectionClaimConstant`。
