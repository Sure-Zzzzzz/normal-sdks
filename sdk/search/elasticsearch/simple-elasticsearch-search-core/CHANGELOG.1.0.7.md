# Changelog - v1.0.7

## 发布日期

2026-04-27

## 版本类型

**Minor Release** - AggDefinition 新增 percentiles / percentile_ranks 聚合字段

## 变更概述

为支持 `percentiles` 和 `percentile_ranks` 两种指标聚合类型，`AggDefinition` 新增 `percents` 和 `values` 两个可选字段。

---

## 变更内容

### AggDefinition 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `percents` | `List<Double>` | percentiles 聚合的百分位列表，不填时使用 ES 默认（1/5/25/50/75/95/99） |
| `values` | `List<Double>` | percentile_ranks 聚合的值列表，必填（不填时 starter 报 400） |

两个字段均为可选，不填时对现有聚合类型行为无影响。

---

## 向后兼容性

✅ **完全向后兼容**

- 新增字段均标注 `@JsonInclude(NON_EMPTY)`，序列化时不填不输出
- 现有所有聚合类型行为不变

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.7"
```

## 贡献者

- @surezzzzzz
