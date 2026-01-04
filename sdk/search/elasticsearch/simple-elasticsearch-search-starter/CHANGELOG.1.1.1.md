# 1.1.1 更新日志

## 发布日期

2026-01-04

## 主要功能

### 时间范围查询支持

新增对自然语言时间范围查询的支持，可以从自然语言中解析时间范围条件并转换为 Elasticsearch 的 DateRange 查询。

#### 功能特性

- **时间范围解析**：支持从自然语言中提取时间范围（如"时间范围2025-01-01到2026-01-01"）
- **自动转换**：将 `DateRangeIntent` 自动转换为 `QueryRequest.DateRange`
- **灵活格式**：支持 ISO 8601 格式的日期时间
- **查询集成**：时间范围与其他查询条件无缝集成

#### 支持的时间范围语法

基于 `natural-language-parser-starter:1.0.5` 提供的解析能力：

**时间范围表达：**

- "时间范围2025-01-01到2026-01-01"
- "从2025-01-01至2026-01-01"
- "2025-01-01到2026-01-01之间"

**字段提示（可选）：**

- "创建时间范围2025-01-01到2026-01-01"
- "更新时间从2025-01-01至2026-01-01"

#### 使用示例

**示例 1：带时间范围的简单查询**

```bash
GET /api/nl/dsl?text=查询订单索引，status等于completed，时间范围2025-01-01到2026-01-01
```

响应：

```json
{
  "index": "订单",
  "query": {
    "field": "status",
    "op": "eq",
    "value": "completed"
  },
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2026-01-01T00:00:00"
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 20
  }
}
```

**示例 2：时间范围 + 复杂条件 + 排序**

```bash
GET /api/nl/dsl?text=查询k01_attack_trajectory_log-*索引，目标IP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按时间戳降序，返回500条
```

响应：

```json
{
  "index": "k01_attack_trajectory_log-*",
  "query": {
    "field": "目标IP",
    "op": "eq",
    "value": "192.168.1.1"
  },
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2026-01-01T00:00:00"
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 500,
    "sort": [
      {
        "field": "时间戳",
        "order": "desc"
      }
    ]
  }
}
```

**示例 3：仅时间范围查询**

```bash
GET /api/nl/dsl?text=查询用户行为索引，时间范围2025-01-01到2025-01-31
```

响应：

```json
{
  "index": "用户行为",
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2025-01-31T00:00:00"
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 20
  }
}
```

## 核心改动

### 1. SimpleElasticsearchIntentTranslator

**新增方法：**

- `translateDateRange(DateRangeIntent)`：转换日期范围意图为 DateRange 对象

**优化方法：**

- `translate(QueryIntent, index)`：新增 dateRange 转换逻辑

**实现细节：**

```java
// 在 translate(QueryIntent) 方法中新增
if (queryIntent.hasDateRange()) {
    builder.dateRange(translateDateRange(queryIntent.getDateRange()));
}

// 新增 translateDateRange 方法
private QueryRequest.DateRange translateDateRange(DateRangeIntent dateRangeIntent) {
    if (dateRangeIntent == null) {
        return null;
    }

    return QueryRequest.DateRange.builder()
            .from(dateRangeIntent.getFrom())
            .to(dateRangeIntent.getTo())
            .build();
}
```

**字段映射：**

- `DateRangeIntent.from` → `QueryRequest.DateRange.from`
- `DateRangeIntent.to` → `QueryRequest.DateRange.to`
- `DateRangeIntent.fieldHint` → 不映射（保留在 Intent 层用于解析）
- `DateRangeIntent.includeFrom/includeTo` → 不映射（Elasticsearch 默认包含边界）

## 依赖变更

### 升级依赖

```gradle
// 自然语言解析器（从 1.0.4 升级到 1.0.5）
api "${group}:natural-language-parser-starter:1.0.5"
```

**natural-language-parser-starter:1.0.5 主要特性：**

- **支持时间范围过滤**（"时间范围2025-01-01到2026-01-01" → DateRangeIntent）
- 支持复杂逻辑组合（AND/OR）
- 支持嵌套聚合和多个顶级聚合
- 支持 size、interval 等聚合参数
- 支持分页限制识别（"取 N 条" → limit=N）
- 提供 IntentTranslator SPI 接口

**1.0.5 新增能力：**

- `DateRangeIntent` 数据结构
- 时间范围语法解析（"时间范围"、"从...到"、"...之间"）
- ISO 8601 日期格式支持
- 可选的字段提示识别

## 注意事项

### 1. 时间格式要求

自然语言中的时间必须使用标准格式：

- ✅ 正确：`2025-01-01`（会自动补充为 `2025-01-01T00:00:00`）
- ✅ 正确：`2025-01-01T08:00:00`（完整 ISO 8601 格式）
- ❌ 错误：`01-01-2025`（月日年格式）

### 2. 时间范围与查询条件的关系

- 时间范围（`dateRange`）与查询条件（`query`）是独立的
- 两者会在 Elasticsearch 查询时进行 AND 组合
- 时间范围不会出现在 `query.conditions` 中，而是作为顶级字段

### 3. 字段提示的处理

- `DateRangeIntent.fieldHint` 不会传递到 `QueryRequest.DateRange`
- 如需指定时间字段，应通过其他机制（如索引 mapping 配置）
- 建议：在索引设计时统一时间字段名（如 `@timestamp`、`createTime`）

### 4. 边界包含行为

- Elasticsearch 的 range 查询默认包含边界（`gte` 和 `lte`）
- `DateRangeIntent.includeFrom/includeTo` 字段在当前版本中不映射到 DSL
- 如需排他边界，需要在 Elasticsearch 执行层处理

## 使用场景

### 场景 1：日志查询

```bash
GET /api/nl/dsl?text=查询应用日志索引，level等于ERROR，时间范围2025-01-01到2025-01-31，按时间戳降序
```

适用于查询特定时间段的错误日志。

### 场景 2：数据分析

```bash
GET /api/nl/dsl?text=查询销售数据索引，时间范围2024-01-01到2024-12-31，按地区分组计算总额
```

适用于年度销售数据统计。

### 场景 3：安全审计

```bash
GET /api/nl/dsl?text=查询访问日志索引，用户等于admin，时间范围2025-01-03到2025-01-04，返回1000条
```

适用于审计特定用户在特定时间段的操作记录。

## 升级建议

### 从 1.1.0 升级

**1. 无需修改代码**

- 时间范围功能为新增功能，不影响现有 API
- 所有现有查询和聚合接口保持向后兼容

**2. 依赖自动升级**

- `natural-language-parser-starter` 从 1.0.4 升级到 1.0.5
- 升级是向后兼容的，不影响现有功能

**3. 可选启用**

- 如果不使用时间范围功能，无需任何配置
- 时间范围语法是自动识别的，不需要额外配置

## 兼容性

- **向后兼容**：完全兼容 1.1.0 及之前版本
- **新增功能**：时间范围查询支持（可选使用）
- **依赖升级**：`natural-language-parser-starter:1.0.4` → `1.0.5`（向后兼容）
- **API 兼容**：所有现有 API 保持不变

## 贡献者

- surezzzzzz
