# Changelog - v1.5.5

## 发布日期

2026-04-25

## 版本类型

**Minor Release** - 新增 Pipeline Aggregation 支持（bucket_sort / bucket_selector），新增表达式聚合端点

## 变更概述

在现有聚合策略模式基础上，新增 `bucket_sort` 和 `bucket_selector` 两种 pipeline 聚合类型，支持对聚合结果做 Top N 排序和 HAVING 过滤，无需在业务层处理全量数据。

同步新增 `POST /api/agg/expression` 端点，与 `/api/query/expression` 对齐，允许用条件表达式字符串作为聚合过滤条件。

---

## 新增功能

### 1. Pipeline Aggregation

在 bucket 聚合的 `pipelineAggs` 字段中配置，作为 sub-aggregation 发送给 ES。

**支持类型：**

| 类型 | ES 支持版本 | 说明 |
|------|------------|------|
| `bucket_sort` | 6.1+ | 对 bucket 结果按指定 metrics 排序，支持 Top N 截取 |
| `bucket_selector` | 6.1+ | 通过 Painless 脚本过滤 bucket，等价于 SQL HAVING |

**bucket_sort 字段：**

| 字段 | 说明 |
|------|------|
| `name` | 聚合名称 |
| `type` | `bucket_sort` |
| `sort` | 排序字段，key 为同级 metrics agg 名称，value 为 `asc`/`desc` |
| `size` | Top N 数量（不填则仅排序，不截断） |
| `from` | 跳过的 bucket 数量（默认 0） |

**bucket_selector 字段：**

| 字段 | 说明 |
|------|------|
| `name` | 聚合名称 |
| `type` | `bucket_selector` |
| `script` | Painless 脚本，通过 `params.xxx` 引用同级 metrics agg（必填） |
| `bucketsPath` | 变量名→聚合名映射（可选，不填时自动从 script 中推断） |

**使用示例：**

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [{
    "name": "by_city",
    "type": "terms",
    "field": "city",
    "size": 1000,
    "aggs": [
      {"name": "total_sales", "type": "sum", "field": "amount"}
    ],
    "pipelineAggs": [
      {
        "name": "having",
        "type": "bucket_selector",
        "script": "params.total_sales > 10000"
      },
      {
        "name": "top5",
        "type": "bucket_sort",
        "sort": {"total_sales": "desc"},
        "size": 5
      }
    ]
  }]
}
```

**校验规则：**

| 场景 | 错误码 |
|------|--------|
| `pipelineAggs` 挂在 composite 聚合下 | `SEARCH_AGG_008` |
| `type` 不是 `bucket_sort` / `bucket_selector` | `SEARCH_AGG_007` |
| `bucket_selector` 未填 `script` | `SEARCH_AGG_009` |

---

### 2. 表达式聚合端点

```
POST /api/agg/expression
```

使用条件表达式字符串作为聚合过滤条件，其余字段与 `/api/agg` 完全一致。

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `index` | String | 索引别名或名称（必填） |
| `expression` | String | 条件表达式字符串（必填） |
| `aggs` | List\<AggDefinition\> | 聚合定义列表（必填） |
| `after` | Map | composite 聚合翻页游标（可选） |

**说明：**
- 表达式语法与 `/api/query/expression` 完全一致
- field-mapping 里的字段可用中文标签，未配置 field-mapping 的字段直接用 ES 字段名
- 响应格式与 `/api/agg` 完全一致，事件机制正常发布

---

## 改动范围

**新增文件**

| 文件 | 说明 |
|------|------|
| `agg/builder/strategy/PipelineAggregationStrategy.java` | pipeline 聚合策略接口 |
| `agg/builder/strategy/pipeline/BucketSortPipelineStrategy.java` | bucket_sort 实现 |
| `agg/builder/strategy/pipeline/BucketSelectorPipelineStrategy.java` | bucket_selector 实现 |
| `endpoint/request/ExpressionAggRequest.java` | 表达式聚合请求 DTO |

**修改文件**

| 文件 | 改动 |
|------|------|
| `agg/builder/strategy/AggregationStrategyRegistry.java` | 新增 pipeline 策略注册 |
| `agg/builder/AggregationDslBuilder.java` | 新增 `appendPipelineAggs()`，composite 下挂 pipeline 提前校验 |
| `endpoint/SimpleElasticsearchSearchApiEndpoint.java` | 新增 `aggByExpression` 端点 |
| `constant/ErrorCode.java` | 新增 `SEARCH_AGG_007/008/009` |
| `constant/ErrorMessage.java` | 新增 3 条错误信息 |
| `version.properties` | 1.5.4 → 1.5.5 |

**core 模块**：无需改动（`PipelineAggDefinition`、`PipelineAggType`、`AggDefinition.pipelineAggs` 已在 v1.0.5 完成）

---

## 向后兼容性

✅ **完全向后兼容**

- 所有已有 API 行为不变
- `pipelineAggs` 字段不填时行为与之前完全一致
- 新增端点为独立接口

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.5"
```

无需任何配置变更。

## 贡献者

- @surezzzzzz
