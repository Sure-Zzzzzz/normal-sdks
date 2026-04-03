# Changelog - v1.2.1

## 发布日期

2026-04-03

## 版本类型

**Patch Release** - Bug 修复与行为增强

## 变更概述

本版本修复了日期分割索引查询中的数据准确性问题，并增强了 search_after 分页的稳定性。

## Bug 修复

### 1. 日期分割索引可能查出跨天脏数据

**问题：**
当索引内存在入库延迟数据（即数据的实际时间与写入索引的日期不一致）时，整天范围查询（`T00:00:00 ~ T23:59:59`）会跳过 date range filter，导致查出不属于该时间范围的脏数据。

**根因：**
原有逻辑假设"按天分割的索引内数据日期与索引名严格对应"，对整天范围查询做了跳过 filter 的优化，但该假设在数据按写入时间路由（而非事件时间路由）的场景下不成立。

**修复：**
新增配置项 `strict-date-filter`（默认 `true`），默认对所有日期范围查询都在 DSL 中追加 `range` 过滤条件，确保按字段值过滤数据，而不是仅靠索引路由来限定范围。

### 2. search_after 分页在非唯一排序字段下可能丢数据

**问题：**
使用 search_after 分页时，若排序字段存在重复值，ES 无法保证稳定排序，导致翻页时出现数据丢失或重复。

**修复：**
当使用 search_after 分页且存在自定义排序字段时，自动追加 `_id ASC` 作为 tiebreaker，保证排序稳定性。collapse 场景除外（ES 不允许 collapse + search_after 同时使用多个排序字段）。

### 3. `nextSearchAfter` 在最后一页仍返回游标值

**问题：**
即使当前页已是最后一页（`hasMore=false`），响应中仍会返回 `nextSearchAfter`，语义不准确，可能导致调用方误判还有下一页。

**修复：**
`nextSearchAfter` 仅在 `hasMore=true` 时返回。

### 4. `hasMore` 判断使用 `>=` 导致逻辑不严谨

**问题：**
`hasMore` 的判断条件为 `items.size() >= pageSize`，但实际返回数量不可能超过 pageSize，`>=` 与 `==` 等价，改为 `==` 使语义更明确。

## 新增配置

### `strict-date-filter`

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            query-limits:
              strict-date-filter: true  # 默认 true
```

| 值 | 行为 |
|----|------|
| `true`（默认） | 始终追加 date range filter，防止跨天脏数据 |
| `false` | 整天范围（`T00:00:00 ~ T23:59:59`）跳过 filter，依赖索引路由覆盖（仅在数据按事件时间严格路由时可开启） |

**建议：** 除非明确知道数据按事件时间路由且无入库延迟，否则保持默认值 `true`。

## 向后兼容性

✅ **完全向后兼容**

- 新增配置项 `strict-date-filter` 默认为 `true`，行为比旧版本更保守（旧版本等价于 `false`）
- 如需恢复旧版本行为，显式配置 `strict-date-filter: false`

## 升级指南

### 从 1.2.0 升级到 1.2.1

1. **更新依赖版本**

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.2.1"
```

2. **无需修改代码**

3. **可选：关闭严格日期过滤（仅在确认数据无入库延迟时）**

```yaml
query-limits:
  strict-date-filter: false
```

## 贡献者

- @surezzzzzz
