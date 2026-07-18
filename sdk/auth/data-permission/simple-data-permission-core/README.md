# Simple Data Permission Core

数据权限协议核心：把一份“允许访问哪些资源、哪些数据范围”的授权信息，转换为资源服务可执行的访问结果。

## 这个模块解决什么问题

资源服务通常已经知道“当前是谁”，但还需要回答：**这个调用方能访问哪些数据？**

例如读取订单时：

- 没有订单读取权限：拒绝访问。
- 有全部订单权限：允许读取全部订单。
- 仅有指定组织的订单权限：只能读取这些组织的订单。

本模块将上述授权信息计算为 `DENY`、`ALLOW_ALL` 或 `ALLOW_RESTRICTED`，让资源服务据此完成查询或写入校验。

## 核心概念

| 类型 | 含义 |
| --- | --- |
| `DataGrantDocument` | 一份完整的数据授权信息。 |
| `DataGrant` | 某个资源、动作及其允许的数据范围。 |
| `DataConstraint` | 一个范围条件，例如允许哪些组织。 |
| `DataPermissionRequest` | 当前要访问的资源和动作。 |
| `DataAccessPlan` | 最终访问结果。 |

一个授权项可以表达为：**允许读取订单，但仅限 organization 为 organization-a 或 organization-b。**

```java
DataConstraint organization = new DataConstraint(
        "organization",
        DataConstraintOperator.IN,
        Arrays.asList("organization-a", "organization-b"));

DataGrant grant = new DataGrant(
        "order",
        Collections.singletonList("read"),
        false,
        Collections.singletonList(organization));
```

如果不限制范围，使用 `all=true`：

```java
DataGrant grant = new DataGrant(
        "order",
        Collections.singletonList("read"),
        true,
        Collections.<DataConstraint>emptyList());
```

## 如何使用

将授权项组成文档，再评估当前请求：

```java
DataGrantDocument document = new DataGrantDocument(
        "simple-data-permission",
        "1.0",
        Collections.singletonList(grant));

DataPermissionEvaluator evaluator = new DefaultDataPermissionEvaluator();
DataAccessPlan plan = evaluator.evaluate(
        document,
        new DataPermissionRequest("order", "read"));
```

资源服务根据结果执行业务操作：

```java
if (plan.getOutcome() == DataAccessOutcome.DENY) {
    throw new AccessDeniedException("数据权限不足");
}
if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
    return queryAllOrders();
}
return queryOrdersByOrganization(plan.getGrants());
```

| 结果 | 表示什么 |
| --- | --- |
| `DENY` | 当前调用方不能执行该资源动作。 |
| `ALLOW_ALL` | 当前调用方可访问该资源的全部数据。 |
| `ALLOW_RESTRICTED` | 当前调用方只能访问 `plan.getGrants()` 指定范围的数据。 |

多个 grant 之间表示“或”；同一个 grant 中的多个约束表示“且”。资源服务必须完整使用每个 grant，不能把不同 grant 的条件拆开重组。

## 授权文档从哪里来

本模块接收已经确认可信的 `DataGrantDocument`，不关心它来自 IAM、AKSK、RPC 还是业务上下文。

如果授权信息从当前上下文读取，实现 `DataGrantDocumentSource` 后直接评估：

```java
DataAccessPlan plan = evaluator.evaluate(documentSource,
        new DataPermissionRequest("order", "read"));
```

没有授权文档时，结果就是 `DENY`。资源服务必须把无法获取、无法理解或无法完整执行授权范围的情况当作拒绝，不能退化成全量访问。

## 模块边界

本模块只负责**表达和计算数据范围**。以下工作由 adapter 或资源服务完成：

- 从 JWT、AKSK、RPC 等上下文读取授权信息。
- 将授权信息编解码为 `DataGrantDocument`。
- 将受限范围翻译为 SQL、ES 或其他查询条件。
- 对列表、详情、导出、创建、更新、删除和批量操作实际执行范围校验。

完整协议规则和演进约束见 [DESIGN.1.0.0.md](DESIGN.1.0.0.md)。

## 许可证

Apache License 2.0
