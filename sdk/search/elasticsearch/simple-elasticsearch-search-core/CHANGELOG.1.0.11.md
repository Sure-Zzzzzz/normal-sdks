# Changelog - v1.0.11

## 发布日期

2026-06-10

## 版本类型

**Minor Release** - countOnly 支持 + Error 事件补全 ExecutionContext

---

## 变更内容

### 新增：QueryRequest 新增 countOnly 字段

```java
/**
 * 是否仅返回总数（不走 _search，走 _count API）
 * 默认 false
 */
private Boolean countOnly;
```

### 修改：EsQueryErrorEvent 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `downgradeLevel` | `int` | 降级级别（0 = 未降级，1~3 = 降级程度递增） |
| `countOnly` | `boolean` | 是否为 countOnly 请求（取自 request.getCountOnly()） |
| `context` | `QueryExecutionContext` | 执行上下文（可为 null），未来扩展字段加在此处 |

原有 `datasource`、`sourceType` 字段保持不变，**构造函数签名完全兼容**：
- 旧四参数：`EsQueryErrorEvent(source, request, error, datasource)` — 零改动
- 新七参数：新增七参数构造函数供 starter 使用，传入 `downgradeLevel`、`countOnly`、`context`

### 修改：EsAggErrorEvent 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `downgradeLevel` | `int` | 降级级别 |
| `context` | `AggExecutionContext` | 执行上下文（可为 null），未来扩展字段加在此处 |

聚合无 countOnly 场景，不加此字段。

---

## 向后兼容性

✅ **完全向后兼容**

- 原有四参数构造函数签名不变，现有代码零改动
- 新增构造函数用于传递 `downgradeLevel`、`context`
- `EsQueryEvent` / `EsAggEvent` 成功事件不受影响

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.11"
```

配合 starter `1.6.6` / audit-listener `1.0.3` / metrics-starter `1.0.1` 使用。

## 贡献者

- @surezzzzzz
