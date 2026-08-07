# Simple Application Authorization Core

应用授权协议核心：将已经可信的人员或服务主体授权快照，收敛为资源服务可使用的应用准入、精确 API 权限和可选 DATA 授权信息。

## 这个模块解决什么问题

资源服务在完成 IAM 或 AKSK 身份认证后，还必须回答：**该主体是否可以访问当前应用的这个 API？**

本模块固定授权顺序：

```text
应用准入
→ 精确 API 权限
→ DATA 授权
→ 业务领域进一步约束
```

只有已经通过应用准入的主体才能构造 `ApplicationAuthorizationContext`；`admitted=false`、过期、未签发或字段不合法的快照不能放行。

其中：

- `PAGE` 权限仅用于页面可见性，不能授权服务端 API。
- 角色、OAuth scope、URL、HTTP 方法、Controller 名称、通配符和表达式都不能推导 API 权限。
- API 权限按精确字符串、区分大小写匹配。
- 缺少或无法执行 DATA grant 时，资源服务必须拒绝访问。

## 核心概念

| 类型 | 含义 |
| --- | --- |
| `ApplicationAuthorizationContext` | 已验证的应用授权快照。 |
| `ApplicationAuthorizationSubjectType` | 主体类型：`HUMAN` 或 `SERVICE`。 |
| `ApplicationAuthorizationEvaluator` | 对目标应用及精确 API 权限进行 fail-closed 判定。 |
| `ApplicationAuthorizationContextClaimMapper` | 严格编解码结构化 Claim。 |
| `DataGrantDocument` | 可选 DATA 授权文档，由 data-permission core 定义和计算。 |
| `ApplicationAuthorizationRevokedEvent` | 不携带 token 或原始授权文档的最小失效定位事件。 |

## 如何使用

构造经过 provider 完整验证的授权快照，再判定精确 API 权限：

```java
ApplicationAuthorizationContext context = new ApplicationAuthorizationContext(
        "simple-application-authorization",
        "1.0",
        ApplicationAuthorizationSubjectType.SERVICE,
        "service-a",
        "application-a",
        true,
        Collections.singletonList("role-reader"),
        Collections.singletonList("page.read"),
        Collections.singletonList("api.read"),
        null,
        1L,
        "manifest-1",
        "digest-a",
        issuedAt,
        expiresAt);

ApplicationAuthorizationEvaluator evaluator = new DefaultApplicationAuthorizationEvaluator();
ApplicationAuthorizationDecision decision = evaluator.evaluateApi(context, "application-a", "api.read");
```

`ALLOW` 只表示应用准入和精确 API 权限已满足。涉及数据范围时，资源服务还必须将 `dataGrantDocument` 交给 `simple-data-permission-core` 计算并实际执行返回的 `DataAccessPlan`。

## 结构化 Claim 接入

在 provider 已完成签名、解密、issuer、audience、主体、应用绑定、时效和授权版本校验后，可将快照转换为结构化 Claim，或从 Claim 还原：

```java
Map<String, Object> claim = ApplicationAuthorizationContextClaimMapper.toClaim(context);
ApplicationAuthorizationContext restored = ApplicationAuthorizationContextClaimMapper.fromClaim(claim);
```

`ApplicationAuthorizationContextClaimMapper` 只接受固定字段集合，拒绝未知、缺失或类型不精确的字段；只接受权限字段的 `List`、文本字段的 `String`、`admitted` 的 `Boolean` 与版本/时间的 `Integer` 或 `Long`。文本与节点均受预算限制，输出 Map、List 及 DATA 的所有嵌套对象均不可修改。

DATA grant 字段委托 `DataGrantDocumentClaimMapper` 处理，不能自行放宽其协议规则。非法 Claim、未准入快照、无法还原的 DATA 文档或超预算内容一律失败关闭；Mapper 不替代认证载体的签名、解密和可信来源校验。

## 撤销事件

当应用准入、角色、API 权限、DATA 授权或主体状态变化时，可发布最小定位事件：

```java
ApplicationAuthorizationRevokedEvent event = new ApplicationAuthorizationRevokedEvent(
        "event-a",
        "iam",
        ApplicationAuthorizationSubjectType.HUMAN,
        "subject-a",
        "application-a",
        1L,
        occurredAt,
        "authorization-change");
```

通过 `ApplicationAuthorizationRevocationPublisher` 发布、由 `ApplicationAuthorizationRevocationHandler` 消费。事件只携带来源、主体、应用、撤销前授权版本、发生时间和原因分类；不携带 token、AK/SK、密码、用户展示资料或原始授权文档。

## 模块边界

本模块是 Java 8 纯 Core：

- 不依赖 Spring、Servlet、Jackson、JPA、网络或加密实现。
- 不依赖 IAM 或 AKSK；两者只能作为上游 provider 生成可信快照。
- 不负责 token 解密、身份认证、授权持久化、撤销消息传输或 HTTP 401/403 映射。
- `@RequireApiPermission` 仅声明精确权限元数据，实际拦截由后续资源服务 Starter 完成。

## 许可证

Apache License 2.0
