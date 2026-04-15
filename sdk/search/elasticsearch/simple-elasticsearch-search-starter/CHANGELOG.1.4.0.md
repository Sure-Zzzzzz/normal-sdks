# Changelog - v1.4.0

## 发布日期

2026-04-15

## 版本类型

**Minor Release** - 新功能，向后兼容

## 变更概述

新增 composite 聚合翻页支持（ES 6.1+），新增通配索引默认时间范围保护，新增 `TimeRangeHelper` 工具类。

---

## 新功能

### 1. composite 聚合翻页

支持对 `terms`、`date_histogram`、`histogram` 类型的聚合使用 composite 模式，实现无限翻页，突破 `terms` 聚合 65535 条的限制。

**使用方式**：在 `AggDefinition` 中设置 `composite: true`：

```json
POST /api/agg
{
  "index": "order_index",
  "aggs": [
    {
      "name": "all_users",
      "type": "terms",
      "field": "userId",
      "composite": true,
      "size": 1000
    }
  ]
}
```

响应中包含 `afterKey`，将其传入下次请求的 `after` 字段即可翻页：

```json
{
  "data": {
    "aggregations": {
      "all_users": [
        {"key": "user_001", "count": 42},
        {"key": "user_002", "count": 18}
      ]
    },
    "afterKey": {
      "all_users": {"userId": "user_002"}
    }
  }
}
```

第二页请求：

```json
POST /api/agg
{
  "index": "order_index",
  "after": {
    "all_users": {"userId": "user_002"}
  },
  "aggs": [
    {
      "name": "all_users",
      "type": "terms",
      "field": "userId",
      "composite": true,
      "size": 1000
    }
  ]
}
```

`afterKey` 为 `null` 时表示已遍历完所有数据。

**支持的聚合类型：**

| 类型 | 说明 |
|------|------|
| `terms` | 按字段值分组 |
| `date_histogram` | 按时间间隔分组 |
| `histogram` | 按数值间隔分组 |

**新增字段：**

`AggDefinition`：
- `composite`（Boolean）：是否使用 composite 聚合，默认 null/false
- `order`（String）：排序方向（asc/desc），默认 asc，按分组字段值排序

`AggRequest`：
- `after`（Map<String, Map<String, Object>>）：翻页游标，key 为聚合名称

`AggResponse`：
- `afterKey`（Map<String, Map<String, Object>>）：下一页游标，null 表示已无更多数据

**限制：**
- composite 内部只允许嵌套 metrics 聚合（sum/avg/min/max 等），不允许嵌套 bucket 聚合
- 不支持 `range` 等其他 bucket 类型

**ES 6.x 兼容性：**
ES 6.x 低级 API 路径自动移除 7.x client 序列化的 `missing_bucket` 字段，保证 ES 6.1+ 均可正常使用。

---

### 2. 通配索引默认时间范围（default-date-range）

对含通配符（`*` 或 `?`）的索引，若请求未传 `dateRange`（query）或 `query`（agg），自动补充默认时间范围，防止全量扫描。

**配置：**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            query-limits:
              default-date-range: 30d  # 支持 30d / 7d / 24h / 1h 等，null 表示不限制
```

**行为说明：**

| 场景 | 行为 |
|------|------|
| 通配索引 + 未传时间范围 | 自动补充最近 N 时间 |
| 通配索引 + 已传时间范围 | 不覆盖，使用用户传入值 |
| 精确索引（无通配符） | 不触发，全量查询 |

**启动校验：**
配置格式不合法时（如 `30days`），启动时抛出 `ConfigurationException`，快速失败。

---

### 3. TimeRangeHelper 工具类

新增 `support.TimeRangeHelper`，提供时间字符串解析能力：

| 方法 | 用途 |
|------|------|
| `parseToMillis(String)` | 解析为毫秒数，用于数值比较（如 PIT keepAlive 上限校验） |
| `buildRecentRange(String)` | 构建"最近 N 时间"的 DateRange，用于默认时间范围注入 |

支持格式：`30d`、`7d`、`24h`、`1h`、`30m`、`60s` 等。

`PitPaginationStrategy.parseKeepAliveToMillis()` 已委托给 `TimeRangeHelper.parseToMillis()`。

---

## 依赖升级

- `simple-elasticsearch-search-core`: `1.0.3` → `1.0.4`

---

## 向后兼容性

✅ **完全向后兼容**

- `AggDefinition.composite` 默认 null，不影响现有聚合行为
- `AggRequest.after` 默认 null，不影响现有请求
- `AggResponse.afterKey` 默认 null，不影响现有响应解析
- `default-date-range` 默认 null，不影响现有查询行为
- `AggregationDslBuilder.build()` 新增 `after` 参数，内部调用已同步更新

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.4.0"
```

同步升级 core 包（如直接依赖）：

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-core:1.0.4"
```

## 贡献者

- @surezzzzzz
