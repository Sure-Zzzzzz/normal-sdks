# Changelog - v1.0.6

## 发布日期

2026-04-26

## 版本类型

**Minor Release** - AggDefinition 新增 filter / filters 聚合字段

## 变更概述

为支持 `filter` 和 `filters` 两种 bucket 聚合类型，`AggDefinition` 新增 `query` 和 `filters` 两个可选字段。

---

## 变更内容

### AggDefinition 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `query` | `QueryCondition` | filter 聚合的过滤条件（仅 filter 聚合使用） |
| `filters` | `Map<String, QueryCondition>` | filters 聚合的多命名过滤器，key 为 bucket 名称（仅 filters 聚合使用） |

两个字段均为可选，不填时对现有聚合类型行为无影响。

---

## 向后兼容性

✅ **完全向后兼容**

- 新增字段均标注 `@JsonInclude(NON_NULL/NON_EMPTY)`，序列化时不填不输出
- 现有所有聚合类型行为不变

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.6"
```

## 贡献者

- @surezzzzzz
