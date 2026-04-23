# Changelog - v1.0.5

## 发布日期

2026-04-22

## 版本类型

**Minor Release** - 新增枚举值和模型字段，向后兼容

## 变更概述

为高级表达式查询（starter v1.5.2）和 Pipeline Aggregation（starter v1.5.3）预置所需的模型和枚举。

---

## 变更内容

### 1. `QueryOperator` 新增枚举值

| 枚举值 | code | 说明 |
|--------|------|------|
| `NOT_LIKE` | `not_like` | 模糊不匹配，根据字段类型自动选择 `must_not match` 或 `must_not wildcard` |

### 2. 新增 `PipelineAggType` 枚举

Pipeline 聚合类型，提供标准方法 `fromCode()` / `isValid()` / `getAllCodes()`，`toString()` 返回 code。

| 枚举值 | code | 说明 |
|--------|------|------|
| `BUCKET_SORT` | `bucket_sort` | 对 bucket 结果排序 + 分页（Top N） |
| `BUCKET_SELECTOR` | `bucket_selector` | 过滤不满足条件的 bucket（HAVING 语义） |

### 3. 新增 `PipelineAggDefinition` 类

Pipeline 聚合定义模型，位于 `agg/model/` 包下。

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | `String` | 聚合名称（结果中的 key） |
| `type` | `String` | 聚合类型：bucket_sort / bucket_selector |
| `sort` | `Map<String, String>` | 排序字段（bucket_sort 专用），key 为同级 metrics agg 名称，value 为 asc/desc |
| `size` | `Integer` | Top N 数量（bucket_sort 专用），不填则不限制 |
| `from` | `Integer` | 跳过的 bucket 数量（bucket_sort 专用），默认 0 |
| `script` | `String` | 过滤脚本 Painless（bucket_selector 专用） |
| `bucketsPath` | `Map<String, String>` | buckets_path 映射（bucket_selector 专用，可选，不填时自动从 script 推断） |

### 4. `AggDefinition` 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `pipelineAggs` | `List<PipelineAggDefinition>` | Pipeline 聚合列表，仅对普通 bucket 聚合有效，composite 聚合下不允许使用 |

---

## 向后兼容性

✅ **完全向后兼容**

- `QueryOperator.NOT_LIKE` 为新增枚举值，不影响现有操作符行为
- `PipelineAggType` / `PipelineAggDefinition` 为新增类，不影响现有代码
- `AggDefinition.pipelineAggs` 默认 null，`@JsonInclude(NON_EMPTY)` 序列化时不输出，不影响现有聚合行为

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-core:1.0.5"
```

## 贡献者

- @surezzzzzz
