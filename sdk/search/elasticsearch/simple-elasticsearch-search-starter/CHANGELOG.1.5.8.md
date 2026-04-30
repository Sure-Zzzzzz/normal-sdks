# Changelog - v1.5.8

## 发布日期

2026-04-30

## 版本类型

**Minor Release** - 新增 scroll 滚动翻页策略；重构聚合响应解析；修复 PIT 硬编码违规

## 变更概述

新增第 5 种分页策略：scroll 滚动翻页，适用于数据导出、全量迁移等需要快照一致性遍历的场景，兼容 ES 6.x+。

同步完成两项内部重构：聚合响应解析逻辑从 `AggExecutor` 提取到独立的 `AggregationResponseParser`（SRP），`PaginationStrategy.buildResult()` 接口签名统一加入 `QueryRequest` 参数，消除 `QueryExecutor` 中的 `instanceof` 分支（OCP）。

修复 `PitPaginationStrategy` 中存在的硬编码字符串。

依赖升级：`simple-elasticsearch-search-core` 1.0.7 → 1.0.8

---

## 新增功能

### scroll 滚动翻页

**适用场景**：数据导出、全量迁移、批量处理，需要遍历全量数据且不需要跳页。

**与现有方案对比**：

| 对比项 | search_after + tiebreaker | search_after + PIT | scroll |
|--------|--------------------------|-------------------|--------|
| _id fielddata | 需要 | 不需要 | 不需要 |
| ES 版本 | 6.x+ | 7.10+ | 1.x+ |
| 快照一致性 | 无 | 有 | 有 |
| 适用场景 | 交互翻页 | 交互翻页 | 全量遍历 |

**使用方式**：

第一页（不传 scrollId）：

```json
POST /api/query
{
  "index": "order",
  "pagination": {
    "type": "scroll",
    "size": 500,
    "scrollTtl": "2m",
    "sort": [{"field": "createTime", "order": "desc"}]
  }
}
```

响应：

```json
{
  "data": {
    "pagination": {
      "type": "scroll",
      "hasMore": true,
      "scrollId": "DXF1ZXJ5QW..."
    }
  }
}
```

后续翻页（带 scrollId，不需要再传 sort/query）：

```json
POST /api/query
{
  "index": "order",
  "pagination": {
    "type": "scroll",
    "size": 500,
    "scrollTtl": "2m",
    "scrollId": "DXF1ZXJ5QW..."
  }
}
```

最后一页 `hasMore: false` 时，SDK 自动清除 scroll 上下文，无需手动操作。

**校验规则**：

| 场景 | 错误码 | 说明 |
|------|--------|------|
| 不传 scrollTtl | `SEARCH_SCROLL_001` | scroll 分页必须提供 scrollTtl |
| scrollTtl 超过服务端上限 | `SEARCH_SCROLL_002` | 默认上限 5m，可通过配置调整 |
| scrollTtl 格式不合法 | `SEARCH_SCROLL_003` | 支持 1d / 1h / 5m / 30s 格式 |
| 第一页不传 sort | `SEARCH_SCROLL_004` | 第一页必须指定排序字段 |
| scroll + collapse 同时使用 | `SEARCH_SCROLL_005` | 两者不兼容 |

**配置**：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            scroll:
              max-ttl: 5m  # scroll 保活时间上限，默认 5m（与 PIT 一致）
```

---

## 内部重构

### 聚合响应解析提取（SRP）

将 `AggExecutor` 中约 300 行的聚合响应解析逻辑提取到独立的 `AggregationResponseParser` 类，`AggExecutor` 只负责编排。

- `ObjectMapper` 从每次调用 `new` 改为 `static final`，避免重复创建
- ES 7.x 和 ES 6.x 两条解析路径各自独立，逻辑清晰

### PaginationStrategy 接口统一（OCP）

`PaginationStrategy.buildResult()` 签名加入 `QueryRequest` 参数：

```java
// 修改前
QueryResponse.PaginationResult buildResult(SearchResponse searchResponse, PaginationInfo pagination);

// 修改后
QueryResponse.PaginationResult buildResult(SearchResponse searchResponse, PaginationInfo pagination, QueryRequest request);
```

消除了 `QueryExecutor.processResponse()` 中的 `instanceof PitPaginationStrategy` 分支，所有策略统一调用 `buildResult()`。`PitPaginationStrategy.buildResultWithRequest()` 合并到 `buildResult()` 并删除。

### PitPaginationStrategy 硬编码修复

| 硬编码 | 替换为 |
|--------|--------|
| `"POST"` | `ElasticsearchApiConstant.HTTP_METHOD_POST` |
| `"DELETE"` | `ElasticsearchApiConstant.HTTP_METHOD_DELETE` |
| `"/_pit"` | `SimpleElasticsearchSearchConstant.ES_API_PIT` |
| `"?keep_alive="` | `SimpleElasticsearchSearchConstant.ES_PIT_KEEP_ALIVE_PARAM` |
| `"{\"id\":\"...\"}"`  | `SimpleElasticsearchSearchConstant.ES_PIT_CLOSE_TEMPLATE` |

---

## 改动范围

**新增文件**

| 文件 | 说明 |
|------|------|
| `query/pagination/ScrollPaginationStrategy.java` | scroll 分页策略实现 |
| `agg/executor/AggregationResponseParser.java` | 聚合响应解析器（从 AggExecutor 提取） |

**修改文件**

| 文件 | 改动 |
|------|------|
| `constant/SimpleElasticsearchSearchConstant.java` | 新增 scroll 常量（`ES_API_SCROLL`、`ES_SCROLL_CONTINUE_TEMPLATE`、`ES_SCROLL_DELETE_TEMPLATE`、`ES_SCROLL_QUERY_PARAM`、`DEFAULT_SCROLL_MAX_TTL`）及 PIT 常量（`ES_API_PIT`、`ES_PIT_KEEP_ALIVE_PARAM`、`ES_PIT_CLOSE_TEMPLATE`） |
| `constant/ErrorCode.java` | 新增 `SEARCH_SCROLL_001` ~ `SEARCH_SCROLL_005` |
| `constant/ErrorMessage.java` | 新增 5 条 scroll 错误信息 |
| `configuration/SimpleElasticsearchSearchProperties.java` | 新增 `ScrollConfig` 内部类（含 `maxTtl` 字段） |
| `query/pagination/PaginationStrategy.java` | `buildResult()` 加入 `QueryRequest` 参数 |
| `query/pagination/PaginationStrategyKey.java` | 新增 `SCROLL = "scroll"` 常量 |
| `query/pagination/PaginationStrategyRegistry.java` | 注入并注册 `ScrollPaginationStrategy`，`buildKey` 支持 scroll 类型 |
| `query/pagination/PitPaginationStrategy.java` | 合并 `buildResultWithRequest` 到 `buildResult`；修复硬编码 |
| `query/pagination/OffsetPaginationStrategy.java` | `buildResult()` 签名适配 |
| `query/pagination/TiebreakerPaginationStrategy.java` | `buildResult()` 签名适配 |
| `query/pagination/NonePaginationStrategy.java` | `buildResult()` 签名适配 |
| `query/validator/SearchAfterSortValidator.java` | scroll 分页委托给 `ScrollPaginationStrategy.validate()` 做独立校验 |
| `query/executor/QueryExecutor.java` | scroll 后续翻页走 scroll API；最后一页自动清除 scroll 上下文；删除 `instanceof` 分支 |
| `agg/executor/AggExecutor.java` | 解析逻辑委托给 `AggregationResponseParser` |
| `support/ElasticsearchCompatibilityHelper.java` | ES 6.x 低级 API 路径追加 scroll query param |
| `build.gradle` | core 1.0.7 → 1.0.8 |
| `version.properties` | 1.5.7 → 1.5.8 |

---

## 向后兼容性

✅ **完全向后兼容**

- scroll 策略默认不使用，只有用户传 `type: "scroll"` 时才生效
- 现有 offset / search_after 分页行为不变
- `PaginationStrategy.buildResult()` 签名变更为内部接口，不影响使用方
- `PitPaginationStrategy` 硬编码修复和 `AggregationResponseParser` 提取均为纯内部重构，外部行为不变

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.8"
```

无需任何配置变更。如需调整 scroll 保活时间上限，添加：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            scroll:
              max-ttl: 5m
```

## 贡献者

- @surezzzzzz
