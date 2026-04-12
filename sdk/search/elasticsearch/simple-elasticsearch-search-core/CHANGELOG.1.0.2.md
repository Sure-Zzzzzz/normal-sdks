# Changelog - v1.0.2

## 发布日期

2026-04-11

## 版本类型

**Minor Release** - 新增功能，向后兼容

## 变更概述

新增 `SearchAfterMode` 枚举，并在 `PaginationInfo` 和 `QueryResponse.PaginationResult` 中增加 PIT 翻页相关字段，为 search_after 翻页模式提供更灵活的配置能力。

## 新增内容

### 1. `SearchAfterMode` 枚举

```java
public enum SearchAfterMode {
    TIEBREAKER,  // 自动追加 _id ASC 作为 tiebreaker（默认，兼容旧行为）
    PIT,         // 使用 Point In Time 快照翻页，需要 ES 7.10+
    NONE         // 不追加任何 tiebreaker，由调用方保证排序字段唯一性
}
```

**背景：** v1.2.1 的 search-starter 自动追加 `_id ASC` 作为 tiebreaker，在内存较小的 ES 集群上会触发 fielddata OOM。新增此枚举允许调用方按需选择翻页策略。

### 2. `PaginationInfo` 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `searchAfterMode` | `String` | 翻页模式：`tiebreaker`（默认）/ `pit` / `none` |
| `pitId` | `String` | PIT ID，首次请求不传，后续翻页从响应中带回 |
| `pitKeepAlive` | `String` | PIT 保活时间（如 `"1m"`），两次翻页间的最长空闲时间 |

新增工具方法：

```java
// 获取 SearchAfterMode 枚举，默认返回 TIEBREAKER
public SearchAfterMode getSearchAfterModeEnum()
```

### 3. `QueryResponse.PaginationResult` 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `pitId` | `String` | PIT 模式下，`hasMore=true` 时返回，调用方需带回下一次请求 |

## 向后兼容性

✅ **完全向后兼容**

- `PaginationInfo` 新增字段均为可选，不传时行为与旧版本完全一致
- `searchAfterMode` 不传时默认 `TIEBREAKER`，等价于 v1.2.1 的自动追加 `_id` 行为
- `QueryResponse.PaginationResult` 新增字段为 `null` 时不序列化（`@JsonInclude(NON_EMPTY)`）

## 升级指南

### 从 1.0.1 升级到 1.0.2

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-core:1.0.2"
```

无需修改任何代码，新字段均为可选。

## 贡献者

- @surezzzzzz
