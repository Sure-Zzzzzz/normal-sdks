# Changelog - v1.5.7

## 发布日期

2026-04-27

## 版本类型

**Patch Release** - 修复 percentiles/percentile_ranks 策略缺失、extended_stats 响应解析错误、percentiles 响应解析缺失

## 变更概述

修复 3 个遗留 Bug：

1. `percentiles` 和 `percentile_ranks` 聚合类型在 `AggType` 枚举中存在但从未实现，用户传这两个类型会直接报
   `UNSUPPORTED_AGG_TYPE`（400）。
2. `extended_stats` 聚合响应只返回 5 个基础字段（count/min/max/avg/sum），丢失了 `std_deviation`、`variance`、
   `std_deviation_bounds` 等扩展字段。
3. `percentiles` 聚合响应返回无意义的 `toString()` 字符串，而非结构化的百分位 Map。

依赖升级：

- `simple-elasticsearch-search-core` 1.0.6 → 1.0.7

---

## Bug 修复

### 1. percentiles / percentile_ranks 策略缺失

**问题**：`AggType` 枚举中有 `PERCENTILES` 和 `PERCENTILE_RANKS`，但 `AggregationStrategyRegistry` 中从未注册对应策略，策略类文件也不存在。

**修复**：

- 新增 `PercentilesAggregationStrategy`，支持可选的 `percents` 字段指定百分位列表，不填时使用 ES 默认（1/5/25/50/75/95/99）
- 新增 `PercentileRanksAggregationStrategy`，`values` 字段必填，不填时报 `SEARCH_AGG_012`（400）
- 在 `AggregationStrategyRegistry` 中注册两个新策略，内置策略数从 17 升至 19

**AggDefinition 新增字段（core 1.0.7）**：

| 字段         | 类型             | 说明                              |
|------------|----------------|---------------------------------|
| `percents` | `List<Double>` | percentiles 聚合的百分位列表，不填使用 ES 默认 |
| `values`   | `List<Double>` | percentile_ranks 聚合的值列表，必填      |

---

### 2. extended_stats 响应解析错误

**问题**：`ExtendedStats extends Stats`，`parseAggregation` 中 `instanceof Stats` 分支先于 `ExtendedStats` 匹配，导致只返回
5 个基础字段，丢失扩展字段。ES 6.x 的 `parseAggregationValue` 同样只识别 5 个基础字段。

**修复**：

- `parseAggregation` 中将 `instanceof ExtendedStats` 判断移至 `instanceof Stats` 之前
- `parseAggregationValue`（ES 6.x 路径）中通过 `sum_of_squares` 字段识别 extended_stats，返回完整的 9 个字段

**extended_stats 响应结构（修复后）**：

```json
{
  "amount_stats": {
    "count": 5,
    "min": 1999.0,
    "max": 15999.0,
    "avg": 7599.0,
    "sum": 37995.0,
    "sum_of_squares": 4018000.0,
    "variance": 21200000.0,
    "std_deviation": 4604.3,
    "std_deviation_bounds": {
      "upper": 16807.6,
      "lower": -1609.6
    }
  }
}
```

---

### 3. percentiles 响应解析缺失

**问题**：`Percentiles` 实现 `NumericMetricsAggregation.MultiValue`（不是 `SingleValue`），`parseAggregation` 中没有对应分支，直接走到
`aggregation.toString()`，返回无意义字符串。ES 6.x 的 `parseAggregationValue` 同样没有处理 percentiles 的
`{"values": {...}}` 结构。

**修复**：

- `parseAggregation` 中新增 `instanceof Percentiles` 分支，遍历 `Iterable<Percentile>` 返回有序 Map
- `parseAggregationValue`（ES 6.x 路径）中通过 `values` 字段为 Map 识别 percentiles/percentile_ranks，直接返回该 Map

**percentiles 响应结构（修复后）**：

```json
{
  "amount_percentiles": {
    "50.0": 1999.0,
    "75.0": 6999.0,
    "90.0": 15999.0,
    "95.0": 12999.0,
    "99.0": 15999.0
  }
}
```

---

## 新增功能

### percentiles 聚合

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [
    {
      "name": "amount_percentiles",
      "type": "percentiles",
      "field": "amount",
      "percents": [
        50,
        75,
        90,
        95,
        99
      ]
    }
  ]
}
```

响应：`{"amount_percentiles": {"50.0": 1999.0, "75.0": 6999.0, "90.0": 15999.0, "95.0": 12999.0, "99.0": 15999.0}}`

### percentile_ranks 聚合

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [
    {
      "name": "amount_ranks",
      "type": "percentile_ranks",
      "field": "amount",
      "values": [
        1000,
        5000,
        10000
      ]
    }
  ]
}
```

响应：`{"amount_ranks": {"5000.0": 48.0, "10000.0": 72.7}}`（key 为传入的值，value 为百分位排名；表示 48% 的订单金额 ≤ 5000）

---

## 改动范围

**新增文件**

| 文件                                                                    | 说明                    |
|-----------------------------------------------------------------------|-----------------------|
| `agg/builder/strategy/metric/PercentilesAggregationStrategy.java`     | percentiles 聚合策略      |
| `agg/builder/strategy/metric/PercentileRanksAggregationStrategy.java` | percentile_ranks 聚合策略 |

**修改文件**

| 文件                                                      | 改动                                                                                                      |
|---------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `agg/builder/strategy/AggregationStrategyRegistry.java` | 注册 2 个新策略，内置策略数更新为 19 种                                                                                 |
| `agg/executor/AggExecutor.java`                         | parseAggregation 补充 ExtendedStats/Percentiles 分支；parseAggregationValue 补充 extended_stats/percentiles 识别 |
| `constant/ErrorCode.java`                               | 新增 `SEARCH_AGG_012`                                                                                     |
| `constant/ErrorMessage.java`                            | 新增 1 条错误信息                                                                                              |
| `constant/SimpleElasticsearchSearchConstant.java`       | 新增 extended_stats 和 percentiles 相关常量                                                                    |
| `build.gradle`                                          | core 1.0.6 → 1.0.7                                                                                      |
| `version.properties`                                    | 1.5.6 → 1.5.7                                                                                           |

**core 模块（v1.0.7）**：`AggDefinition` 新增 `percents`、`values` 字段

---

## 向后兼容性

✅ **完全向后兼容**

- `AggDefinition` 新增字段均为可选，不影响现有聚合
- `extended_stats` 原来只返回 5 个字段，现在返回完整 9 个字段，是 bug 修复
- `percentiles`/`percentile_ranks` 原来直接报 400，现在正常执行，是 bug 修复

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.7"
```

无需任何配置变更。

## 贡献者

- @surezzzzzz
