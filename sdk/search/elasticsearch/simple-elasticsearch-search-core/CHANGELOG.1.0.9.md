# Changelog - v1.0.9

## 发布日期

2026-05-18

## 版本类型

**Minor Release** - ExecutionContext 重构 + Error Events + AggRequest 补齐 dateRange

---

## 变更内容

### 新增：ExecutionContext 基类

新增 `ExecutionContext` 作为 `QueryExecutionContext` 和 `AggExecutionContext` 的公共基类，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `actualIndices` | `String[]` | 实际查询的物理索引 |
| `datasource` | `String` | 数据源 key |
| `downgradeLevel` | `int` | 降级级别（0 = 未降级，1~3 = 降级程度递增） |
| `sourceType` | `String` | 来源类型（由 starter 端点设置，如 QUERY_API / NL_API / EXPRESSION_API） |

### 修改：QueryExecutionContext / AggExecutionContext 改为继承

`QueryExecutionContext` 和 `AggExecutionContext` 改为继承 `ExecutionContext`，**现有监听器代码零改动**，可直接通过父类字段获取 `downgradeLevel`、`sourceType` 等新增信息。

### 新增：EsQueryErrorEvent / EsAggErrorEvent

查询/聚合执行失败时发布，供审计/监控扩展使用。

| 字段 | 类型 | 说明 |
|------|------|------|
| `request` | `QueryRequest` / `AggRequest` | 原始请求 |
| `error` | `Throwable` | 异常信息 |
| `datasource` | `String` | 路由到的数据源，路由前失败时为 null |

> 注意：仅在 executor 执行阶段失败时发布，端点层的参数校验失败（400）不触发此事件。

### 修改：QueryRequest 新增 sourceType 字段

```java
@JsonIgnore
private String sourceType;
```

不参与 JSON 序列化/反序列化，由 starter 端点在调用 executor 前设置。

### 修改：AggRequest 新增 dateRange 和 sourceType 字段

**Bug Fix**：`AggRequest` 补齐 `dateRange` 字段，与 `QueryRequest` 对称。

此前 `AggExecutor` 只能通过从 query 条件中找 `BETWEEN` 操作符来推断日期范围，使用 `gte`/`lte` 条件时日期路由不生效，agg 会扫全部分片。

| 字段 | 类型 | 说明 |
|------|------|------|
| `dateRange` | `QueryRequest.DateRange` | 日期范围，用于日期分割索引路由，优先级高于从 query 条件推断 |
| `sourceType` | `String` | 同 QueryRequest，`@JsonIgnore` |

---

## 向后兼容性

✅ **完全向后兼容**

- `QueryExecutionContext` / `AggExecutionContext` 保留，改为继承，现有监听器零改动
- `EsQueryEvent` / `EsAggEvent` 的 context 类型不变
- `QueryRequest` / `AggRequest` 新增字段均为可选，`@JsonIgnore` 不影响序列化
- `AggRequest.dateRange` 为可选字段，不传时 `AggExecutor` fallback 到原有推断逻辑

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.9"
```

## 贡献者

- @surezzzzzz
