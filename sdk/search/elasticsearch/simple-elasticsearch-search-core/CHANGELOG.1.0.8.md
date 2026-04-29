# Changelog - v1.0.8

## 发布日期

2026-04-29

## 版本类型

**Minor Release** - 新增 scroll 分页支持所需的 model 字段

## 变更概述

为支持 scroll 滚动翻页策略，`PaginationType` 枚举新增 `SCROLL` 值，`PaginationInfo` 新增 `scrollId`、`scrollTtl` 字段及
`isScrollPagination()` 方法，`QueryResponse.PaginationResult` 新增 `scrollId` 字段。

---

## 变更内容

### PaginationType 新增枚举值

| 枚举值      | code       | 说明                  |
|----------|------------|---------------------|
| `SCROLL` | `"scroll"` | 滚动翻页，适用于全量遍历/数据导出场景 |

### PaginationInfo 新增字段

| 字段          | 类型       | 说明                                         |
|-------------|----------|--------------------------------------------|
| `scrollId`  | `String` | scroll ID，第一页不传；后续翻页将响应中的 scrollId 带回      |
| `scrollTtl` | `String` | scroll 上下文保活时间，如 `"2m"`、`"5m"`，scroll 分页必填 |

新增方法：

```java

@JsonIgnore
public boolean isScrollPagination()
```

### QueryResponse.PaginationResult 新增字段

| 字段         | 类型       | 说明                                                         |
|------------|----------|------------------------------------------------------------|
| `scrollId` | `String` | scroll 分页且 hasMore=true 时返回，调用方带入下次请求的 pagination.scrollId |

---

## 向后兼容性

✅ **完全向后兼容**

- `PaginationType` 新增枚举值，不影响现有 `OFFSET` 和 `SEARCH_AFTER`
- `PaginationInfo` 新增字段均为可选，`@JsonInclude(NON_EMPTY)` 下不传不输出
- `PaginationResult` 新增字段为 null 时不影响现有响应结构

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.8"
```

## 贡献者

- @surezzzzzz
