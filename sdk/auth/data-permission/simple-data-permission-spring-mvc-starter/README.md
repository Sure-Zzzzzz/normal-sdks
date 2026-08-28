# Simple Data Permission Spring MVC Starter

> **1.0.1**：维护版本；`simple-resource-server-core` 升级到 1.1.1（事件模型迁入 core、传递授权 core 1.0.1 的时钟容差修复），本模块行为不变。

为已接入 `simple-resource-server-starter` 的 Spring MVC 资源服务提供 DATA 访问计划评估与安全传递。它从唯一的 `VerifiedResourceContext` 读取 IAM、AKSK 或其他 Provider 已验证的 `DataGrantDocument`，不解析令牌、不依赖 IAM/AKSK 实现，也不自动生成 SQL、ES、JPA 或 MyBatis 条件。

## 接入

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-data-permission-spring-mvc-starter:1.0.1'
}
```

该 Starter 依赖 `simple-data-permission-core:1.1.0` 与 `simple-resource-server-core:1.1.1`。业务服务还需接入 `simple-resource-server-starter:1.1.0`，完成 Bearer / Provider 认证、应用准入和 API 权限校验。

Controller 方法显式声明 DATA 资源动作，并注入当前请求已评估的访问计划：

```java
@GetMapping("/api/orders")
@DataPermissionOperation(resource = "order", action = "read")
public Page<OrderView> list(@CurrentDataAccessPlan DataAccessPlan plan, OrderQuery query) {
    return orderService.list(query, plan);
}
```

未声明 `@DataPermissionOperation` 的接口不会被本 Starter 收紧。已声明但没有已验证 DATA 文档、未命中授权项或当前上下文无效时，接口返回 403。

非 MVC 入口可显式调用 `DataPermissionFacade`：

```java
DataAccessPlan plan = dataPermissionFacade.require("order", "export");
```

异步任务必须显式传递并校验已评估的计划；不得依赖 Servlet request attribute 或 `SecurityContext` 的隐式传播。

## 兼容性与验证

1.0.1 已在以下 Spring Boot 精确版本完成完整模块测试：

- `2.2.13.RELEASE`
- `2.3.12.RELEASE`
- `2.4.5`
- `2.7.9`

每组均执行 9 项测试，`skipped=0`、`failures=0`、`errors=0`。覆盖 MVC 自动配置、Resource Server API 权限优先于 DATA 评估、IAM 人员主体与 AKSK 服务主体的已验证上下文、缺失授权文档失败关闭、越权目标拒绝，以及完整 DNF grant 校验。

本模块使用 `javax.servlet` 与 Spring Security 5，不支持 Spring Boot 3、Spring Security 6 或 `jakarta.servlet`。

## API 与 DATA 的边界

```text
Bearer / Provider 认证
→ 应用准入与精确 API 权限
→ DATA 访问计划评估
→ 业务或存储适配器执行完整范围
→ 领域规则
```

API 权限只基于路径和精确 HTTP method（或 `@RequireApiPermission`）判断是否可调用接口。query、path variable、header、Cookie 与 request body 都不会选择 API 权限，也不能生成 DATA 授权范围。

例如 `GET /api/orders?tenantId=tenant-a` 与 `GET /api/orders?tenantId=tenant-b` 使用同一个 API 权限。`tenantId`、`departmentId` 等只是业务筛选或目标数据；业务必须将其与 `DataAccessPlan` 的完整 grant 求交。请求值超出授权范围时返回 403，不自动裁剪，不以空结果掩盖越权。

## 执行 DATA 计划

`ALLOW_RESTRICTED` 中每个 grant 都是不可拆分的 DNF 子句：同一 grant 的约束用 AND，不同 grant 用 OR。不得把不同 grant 的维度拆开或交叉组合。

列表、分页 count、聚合和导出必须使用同一完整范围谓词。详情、更新、删除应使用 `id AND DATA predicate` 的原子查询或等价限制，对外统一隐藏无权与不存在的差异并返回 404。创建校验最终写入的受控维度；更新同时校验旧记录和新值。批量操作必须对所有目标叠加完整 DATA 范围，任一目标无权即整体 403。

`DataAccessPlanRestrictionVerifier` 仅用于校验一个已明确的业务目标是否完整满足至少一个 grant，不能替代列表查询的 DNF 翻译。维度与字段的映射、JPA/MyBatis/ES/SQL 的真实谓词生成由业务或存储专用适配器实现；无法完整表达任意 grant 时必须拒绝，不能删除约束后继续查询。

## 边界

- Starter 只接受 `VerifiedResourceContext` 中的已验证授权快照，不读取原始凭据或请求参数作为授权来源。
- 不扫描实体、方法名或参数名推导资源、动作或维度。
- 不支持 SpEL、JSON Pointer、自动 SQL、跨 Provider 文档合并或隐式异步安全上下文传播。
- `@CurrentDataAccessPlan` 只能标注 `DataAccessPlan` 参数，且只从本请求内部评估结果注入，不能通过请求参数或请求体伪造。

## 版本历史

### 1.0.1

- 依赖升级：`simple-resource-server-core` `1.0.0` → `1.1.1`（`api`，事件模型迁入 core 并传递授权 core 1.0.1 的时钟容差修复）；测试组装 `simple-resource-server-starter` `1.0.1` → `1.1.0`。源码零改动，行为不变。

详见 [CHANGELOG.1.0.1.md](CHANGELOG.1.0.1.md)。

### 1.0.0

- 首个版本：MVC 自动配置、`@DataPermissionOperation` + `@CurrentDataAccessPlan`、`DataPermissionFacade` 与失败关闭语义。

## 许可证

Apache License 2.0
