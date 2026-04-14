# Changelog - v1.0.4

## 发布日期

2026-04-15

## 版本类型

**Minor Release** - 新增字段，向后兼容

## 变更概述

为 composite 聚合翻页功能新增模型字段，供 `simple-elasticsearch-search-starter:1.4.0` 使用。

---

## 变更内容

### `AggDefinition` 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `composite` | `Boolean` | 是否使用 composite 聚合（支持翻页），仅对 terms、date_histogram、histogram 生效，默认 null/false |
| `order` | `String` | composite 聚合的排序方向（asc / desc），按分组字段值排序，默认 asc |

### `AggRequest` 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `after` | `Map<String, Map<String, Object>>` | composite 聚合的翻页游标，key 为聚合名称，value 为上一页响应返回的 afterKey，null 时不序列化 |

### `AggResponse` 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `afterKey` | `Map<String, Map<String, Object>>` | composite 聚合的下一页游标，key 为聚合名称，为 null 或不含某聚合名时表示该聚合已无更多数据，null 时不序列化 |

---

## 向后兼容性

✅ **完全向后兼容**

- `AggDefinition.composite` 默认 null，不影响现有聚合行为
- `AggRequest.after` 默认 null，不影响现有请求
- `AggResponse.afterKey` 默认 null，不影响现有响应解析

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-core:1.0.4"
```

## 贡献者

- @surezzzzzz
