# Changelog - v1.5.6

## 发布日期

2026-04-26

## 版本类型

**Minor Release** - 补全 bucket 聚合类型，新增 AggRequest 校验链，修复 filter/filters 聚合 NPE

## 变更概述

补全 `AggType` 枚举中此前缺少实现的 5 种 bucket 聚合策略（filter、filters、missing、date_range、ip_range），覆盖常见的过滤分析、缺失值统计、日期/IP 范围分组场景。

同步引入 `AggRequestValidatorChain`，与 query 侧架构对齐。

修复 `filter`/`filters` 聚合在无 alias 索引下的 NPE 问题，`QueryDslBuilder` 新增接受 `IndexMetadata` 的重载，避免重复查询。

依赖升级：
- `simple-elasticsearch-search-core` 1.0.5 → 1.0.6

---

## 新增功能

### 1. 新增 5 种 bucket 聚合类型

| 类型 | 说明 | 所需字段 | ES 版本 |
|------|------|---------|---------|
| `filter` | 单过滤器聚合，只统计满足条件的文档，支持嵌套 metrics | `query`（QueryCondition） | 1.0+ |
| `filters` | 多命名过滤器聚合，每个过滤器产生独立 bucket，适合对比分析 | `filters`（Map<String, QueryCondition>） | 1.0+ |
| `missing` | 统计指定字段值为 null 或不存在的文档数 | `field` | 1.0+ |
| `date_range` | 日期范围分组，支持相对时间表达式（如 `now-1M`、`now/M`） | `field`、`ranges` | 1.0+ |
| `ip_range` | IP 范围分组，支持 CIDR 表示法（如 `10.0.0.0/8`） | `field`、`ranges` | 6.1+ |

**filter 示例：**

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [{
    "name": "high_value",
    "type": "filter",
    "query": {"field": "amount", "op": "gte", "value": 1000},
    "aggs": [
      {"name": "total", "type": "sum", "field": "amount"},
      {"name": "count", "type": "count", "field": "order_id"}
    ]
  }]
}
```

响应结构：`{"high_value": {"count": N, "total": X}}`

**filters 示例：**

```json
{
  "name": "by_status",
  "type": "filters",
  "filters": {
    "completed": {"field": "status", "op": "eq", "value": "completed"},
    "pending":   {"field": "status", "op": "eq", "value": "pending"}
  }
}
```

**ip_range + CIDR filter 聚合示例：**

```json
{
  "name": "subnet_stats",
  "type": "filter",
  "query": {"field": "client_ip", "op": "eq", "value": "10.0.0.0/24"},
  "aggs": [{"name": "total_amount", "type": "sum", "field": "amount"}]
}
```

---

### 2. AggRequest 校验链

新增 `AggRequestValidatorChain`，与 query 侧 `QueryRequestValidatorChain` 架构对齐。

| 校验器 | Order | 说明 |
|--------|-------|------|
| `AggIndexAliasValidator` | 10 | index 不能为空 |
| `AggDefinitionValidator` | 20 | aggs 不能为空 |

`AggExecutor.validateRequest` 改为委托给 chain，default-date-range 注入逻辑（依赖 mappingManager）保留在 executor 中。

---

### 3. filter/filters 聚合响应解析

`AggExecutor.parseAggregation` 补充 `SingleBucketAggregation` 分支，`filter`/`missing` 聚合现在返回结构化数据：

```json
{
  "high_value": {
    "count": 3,
    "total_amount": 45000.0
  }
}
```

ES 6.x JSON 解析路径（`parseAggregationValue`）同步补充 `doc_count` 识别，6.x 和 7.x 响应格式一致。

---

## Bug 修复

### filter/filters 聚合在无 alias 索引下 NPE

**问题**：`FilterAggregationStrategy` 和 `FiltersAggregationStrategy` 调用 `queryDslBuilder.build(metadata.getAlias(), condition)`，当索引未配置 alias 时 `metadata.getAlias()` 返回 null，`ConcurrentHashMap.get(null)` 抛 NPE。

**修复**：`QueryDslBuilder` 新增 `build(IndexMetadata metadata, QueryCondition condition)` 重载，直接使用已有的 metadata 对象，不再重复查询。`FilterAggregationStrategy` 和 `FiltersAggregationStrategy` 改用新重载。

**影响范围**：所有使用 `filter`/`filters` 聚合且索引未配置 alias 的场景（无 alias 是主流配置）。

---

## 改动范围

**新增文件**

| 文件 | 说明 |
|------|------|
| `agg/builder/strategy/bucket/FilterAggregationStrategy.java` | filter 聚合策略 |
| `agg/builder/strategy/bucket/FiltersAggregationStrategy.java` | filters 聚合策略 |
| `agg/builder/strategy/bucket/MissingAggregationStrategy.java` | missing 聚合策略 |
| `agg/builder/strategy/bucket/DateRangeAggregationStrategy.java` | date_range 聚合策略 |
| `agg/builder/strategy/bucket/IpRangeAggregationStrategy.java` | ip_range 聚合策略 |
| `agg/validator/AggRequestValidator.java` | 聚合请求校验器接口 |
| `agg/validator/AggRequestValidatorChain.java` | 聚合请求校验责任链 |
| `agg/validator/AggIndexAliasValidator.java` | index 非空校验 |
| `agg/validator/AggDefinitionValidator.java` | aggs 非空校验 |

**修改文件**

| 文件 | 改动 |
|------|------|
| `agg/builder/strategy/AggregationStrategyRegistry.java` | 注册 5 个新策略，内置策略数更新为 17 种 |
| `agg/builder/AggregationDslBuilder.java` | composite 分支提前校验 pipelineAggs，防止 composite 下挂 pipeline |
| `agg/executor/AggExecutor.java` | validateRequest 委托给 chain；parseAggregation 补充 SingleBucketAggregation；parseAggregationValue 补充 doc_count 识别 |
| `query/builder/QueryDslBuilder.java` | 新增 `build(IndexMetadata, QueryCondition)` 重载 |
| `constant/ErrorCode.java` | 新增 `SEARCH_AGG_010/011` |
| `constant/ErrorMessage.java` | 新增 2 条错误信息 |
| `build.gradle` | core 1.0.5 → 1.0.6 |
| `version.properties` | 1.5.5 → 1.5.6 |

**core 模块（v1.0.6）**：`AggDefinition` 新增 `query`、`filters` 字段

---

## 向后兼容性

✅ **完全向后兼容**

- 现有 12 种聚合类型行为不变
- `AggRequestValidatorChain` 替换了 executor 内的硬编码校验，行为等价
- `AggDefinition` 新增字段均为可选，不填时对现有聚合无影响
- `QueryDslBuilder` 新增重载，原有 `build(String, QueryCondition)` 不变

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.6"
```

无需任何配置变更。

## 贡献者

- @surezzzzzz
