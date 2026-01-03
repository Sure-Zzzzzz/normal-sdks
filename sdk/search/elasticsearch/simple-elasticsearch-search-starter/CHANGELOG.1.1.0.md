# 1.1.0 更新日志

## 发布日期

2026-01-03

## 主要功能

### 自然语言转 DSL

新增自然语言查询支持，可以将中文自然语言直接转换为 Elasticsearch DSL 查询对象（QueryRequest 或 AggRequest），降低 API 使用门槛。

#### 功能特性

- **自然语言解析**：支持中文自然语言查询语法
- **智能类型识别**：自动识别查询意图（普通查询 / 聚合查询）
- **DSL 生成**：将自然语言转换为标准的 QueryRequest 或 AggRequest 对象
- **索引推断**：支持从自然语言中提取索引名，或通过参数覆盖
- **默认分页**：未指定分页时自动应用配置的默认值（默认 20 条），避免全表扫描

#### 使用方式

**1. 查询示例（QueryRequest）**

```bash
GET /api/nl/dsl?text=查询user_behavior索引，age大于等于18并且city在Beijing、Shanghai，按createTime降序，取50条
```

响应（直接返回 DSL 对象，无包装）：

```json
{
  "index": "user_behavior",
  "query": {
    "logic": "and",
    "conditions": [
      {
        "field": "age",
        "op": "gte",
        "value": 18
      },
      {
        "field": "city",
        "op": "in",
        "values": [
          "Beijing",
          "Shanghai"
        ]
      }
    ]
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 50,
    "sort": [
      {
        "field": "createTime",
        "order": "desc"
      }
    ]
  }
}
```

**2. 聚合示例（AggRequest）**

```bash
GET /api/nl/dsl?text=查询user_behavior索引，status等于active，按city分组前10名计算age平均值，按createTime每天统计
```

响应：

```json
{
  "index": "user_behavior",
  "query": {
    "field": "status",
    "op": "eq",
    "value": "active"
  },
  "aggs": [
    {
      "name": "city_distribution",
      "type": "terms",
      "field": "city",
      "size": 10,
      "aggs": [
        {
          "name": "avg_age",
          "type": "avg",
          "field": "age"
        }
      ]
    },
    {
      "name": "daily_stats",
      "type": "date_histogram",
      "field": "createTime",
      "interval": "1d"
    }
  ]
}
```

**3. 索引覆盖**

```bash
# 自然语言中指定索引
GET /api/nl/dsl?text=查询user_logs索引，level等于ERROR

# 通过参数覆盖（优先级更高）
GET /api/nl/dsl?text=level等于ERROR&index=user_logs
```

索引优先级：`index` 参数 > 自然语言中的索引提示 > 抛出异常

#### 支持的自然语言特性

基于 `natural-language-parser-starter:1.0.4` 提供的解析能力：

**查询条件：**

- 比较运算：大于、小于、等于、不等于、大于等于、小于等于
- 逻辑组合：并且（AND）、或者（OR）
- 范围查询：在...之间、在...列表中
- 模糊匹配：包含、以...开头、以...结尾
- 存在性：存在、不存在、为空、不为空

**聚合操作：**

- 指标聚合：求和、平均值、最大值、最小值、计数
- 桶聚合：按字段分组（terms）、时间直方图（date_histogram）
- 嵌套聚合：支持多级嵌套聚合
- 多个聚合：支持同时定义多个顶级聚合

**分页排序：**

- 分页限制：取 N 条、前 N 条
- 排序：按字段升序/降序

*
*更多语法示例，请参考 [natural-language-parser-starter 文档](https://github.com/Sure-Zzzzzz/normal-sdks/tree/main/sdk/natural-language/parser/natural-language-parser-starter)
**

#### API 接口

**端点：** `GET /api/nl/dsl`

**请求参数：**

| 参数    | 类型     | 必填 | 说明                  |
|-------|--------|----|---------------------|
| text  | String | 是  | 自然语言查询文本            |
| index | String | 否  | 索引别名（优先级高于自然语言中的提示） |

**响应格式：**

- **成功（200）**：直接返回 DSL 对象（QueryRequest 或 AggRequest），无包装
- **参数错误（400）**：返回 `{"error": "错误消息"}`
- **服务器错误（500）**：返回 `{"error": "错误消息"}`

**异常处理：**

- `NLParseException`：自然语言解析失败（如语法错误、不支持的表达式）
- `NLDslTranslationException`：DSL 转换失败（如未指定索引、不支持的 Intent 类型）

#### 典型使用场景

**场景 1：快速原型开发**

前端可以直接通过自然语言生成 DSL，无需手写复杂的 JSON 查询对象，加速开发迭代。

**场景 2：用户友好的查询界面**

为非技术用户提供自然语言查询入口，降低使用门槛。

**场景 3：DSL 生成工具**

作为查询构建器的后端，将用户在可视化界面上的操作转换为自然语言，再通过此接口生成标准 DSL。

**场景 4：与现有 API 配合**

1. 调用 `/api/nl/dsl` 生成 DSL
2. 将生成的 DSL 直接传递给 `/api/query` 或 `/api/agg` 执行查询

```javascript
// 示例：两步查询
// 步骤 1：生成 DSL
const dsl = await fetch('/api/nl/dsl?text=查询订单，状态为已完成，按时间降序，取20条')
  .then(res => res.json());

// 步骤 2：执行查询
const result = await fetch('/api/query', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(dsl)
}).then(res => res.json());
```

## 新增类

### 1. NLDslService

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.nl.service.NLDslService`

**功能：** 自然语言 DSL 服务

**职责：**

1. 调用 NLParser 解析自然语言
2. 调用 Translator 转换 Intent 为 Request
3. 处理索引参数的优先级逻辑

**核心方法：**

- `translateToRequest(text, indexOverride)`：将自然语言转换为 DSL Request 对象

### 2. SimpleElasticsearchIntentTranslator

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.nl.translator.SimpleElasticsearchIntentTranslator`

**功能：** Elasticsearch Intent 转换器

**职责：** 纯粹的数据结构转换，将 natural-language-parser 的 Intent 对象转换为 search-starter 的 Request 对象

**核心方法：**

- `translate(QueryIntent, index)`：转换 QueryIntent 为 QueryRequest
- `translate(AnalyticsIntent, index)`：转换 AnalyticsIntent 为 AggRequest
- `translateCondition(ConditionIntent)`：转换查询条件
- `translateAggregation(AggregationIntent)`：转换聚合定义
- `translatePagination(PaginationIntent, sorts)`：转换分页信息（含默认值）
- `translateSort(SortIntent)`：转换排序信息

**特性：**

- 实现 `IntentTranslator<Object>` 接口（SPI）
- 返回 `dataSourceType = "elasticsearch"`
- 支持嵌套聚合、多种分页模式、逻辑组合条件

### 3. NLDslTranslationException

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.exception.NLDslTranslationException`

**功能：** 自然语言 DSL 翻译失败异常

**特性：**

- 继承自 `SimpleElasticsearchSearchException`
- 用于不支持的 Intent 类型、未指定索引等场景

## 代码质量改进

### 1. 配置化默认分页

**问题：** 之前 `SimpleElasticsearchIntentTranslator` 中硬编码了默认分页大小 `DEFAULT_PAGE_SIZE = 20`

**改进：**

- 注入 `SimpleElasticsearchSearchProperties` 依赖
- 从配置中读取 `properties.getQueryLimits().getDefaultSize()`
- 支持通过 YAML 配置自定义默认分页大小

**配置示例：**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            query-limits:
              default-size: 20  # 可自定义默认分页大小
```

### 2. 日志优化

**移除了过度详细的调试日志：**

- `NLDslService`：移除完整 Intent 对象的 toString() 打印
- `SimpleElasticsearchIntentTranslator`：移除所有 verbose 日志（QueryIntent.pagination、AnalyticsIntent
  详细结构、translatePagination 所有分支日志）

**保留了关键决策点的日志：**

- DEBUG：解析结果类型、索引确定逻辑
- INFO：用户操作（收到请求、翻译成功/失败）
- ERROR：异常情况

**收益：**

- 减少性能开销（避免大对象 toString()）
- 提升日志可读性
- 降低日志存储成本

### 3. 代码结构优化

**使用现代 Java 特性：**

- 统一使用 diamond operator `<>`，移除冗余的显式类型参数
  ```java
  // 优化前
  List<AggDefinition> aggDefinitions = new ArrayList<AggDefinition>();

  // 优化后
  List<AggDefinition> aggDefinitions = new ArrayList<>();
  ```

**线程安全验证：**

- 所有组件遵循 Spring 无状态服务模式
- `NLDslService`：无状态单例，仅有 @Autowired 依赖
- `SimpleElasticsearchIntentTranslator`：无状态单例，构造函数注入不可变配置
- `SimpleElasticsearchSearchApiEndpoint`：无状态 Controller，所有方法仅操作局部变量

**性能验证：**

- 所有日志使用参数化格式（无字符串拼接）
- 无不必要的对象创建
- 纯数据转换逻辑，无 I/O 阻塞

## 核心改动

### 1. SimpleElasticsearchSearchApiEndpoint

**新增方法：**

- `translateNLToDsl(text, index)`：自然语言转 DSL API

**特性：**

- 支持 LogTruncator 日志截断（自动检测是否可用）
- 细粒度异常处理（NLParseException、NLDslTranslationException）
- 直接返回 DSL 对象，无包装（与 `/api/query` 格式兼容）

### 2. SimpleElasticsearchIntentTranslator

**优化：**

- 构造函数注入 `SimpleElasticsearchSearchProperties`
- `translatePagination` 使用配置的默认分页大小
- 所有 List 初始化使用 diamond operator

### 3. ErrorCode

**新增错误码：**

- `NL_TRANSLATION_FAILED`（SEARCH_NL_001）：自然语言翻译失败

## 依赖变更

### 新增依赖

```gradle
// 自然语言解析器
api "${group}:natural-language-parser-starter:1.0.4"
```

**natural-language-parser-starter:1.0.4 主要特性：**

- 支持复杂逻辑组合（AND/OR）
- 支持嵌套聚合和多个顶级聚合
- 支持 size、interval 等聚合参数
- 支持分页限制识别（"取 N 条" → limit=N）
- 提供 IntentTranslator SPI 接口

## 注意事项

### 1. 自然语言解析限制

自然语言解析能力取决于 `natural-language-parser-starter` 版本：

- **当前版本（1.0.4）**：支持基本的查询条件、聚合操作、分页排序
- **不支持**：非常复杂的嵌套逻辑、特定领域的自定义语法
- **建议**：复杂查询仍推荐使用 JSON DSL

### 2. 字段映射

自然语言中的字段名需要与索引中的实际字段名一致：

- ✅ 正确：`age大于18`（索引中字段名为 `age`）
- ❌ 错误：`年龄大于18`（除非索引中字段名为 `年龄`）

**未来改进方向**：结合 Mapping 元数据支持字段名智能匹配（中文别名 → 英文字段名）

### 3. 索引必须指定

自然语言查询必须通过以下方式之一指定索引：

1. 自然语言中包含索引名：`查询user_behavior索引，...`
2. 通过 `index` 参数传递：`?text=...&index=user_behavior`

如果两者都未提供，会抛出 `NLDslTranslationException`。

### 4. 默认分页保护

为防止全表扫描，当用户未指定分页限制时：

- 自动应用 `query-limits.default-size`（默认 20 条）
- 建议：根据业务场景调整配置值

### 5. 响应格式差异

与其他 API 不同，`/api/nl/dsl` 直接返回 DSL 对象，无 `{data: ...}` 包装，便于直接传递给 `/api/query` 或 `/api/agg`。

## 使用示例

### 示例 1：简单查询

```bash
# 请求
GET /api/nl/dsl?text=查询订单索引，status等于completed，按createTime降序

# 响应
{
  "index": "订单",
  "query": {
    "field": "status",
    "op": "eq",
    "value": "completed"
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 20,
    "sort": [
      {
        "field": "createTime",
        "order": "desc"
      }
    ]
  }
}
```

### 示例 2：复杂聚合

```bash
# 请求
GET /api/nl/dsl?text=查询商品索引，category等于电子产品，按品牌分组前5名计算平均价格和总库存

# 响应
{
  "index": "商品",
  "query": {
    "field": "category",
    "op": "eq",
    "value": "电子产品"
  },
  "aggs": [
    {
      "name": "brand_distribution",
      "type": "terms",
      "field": "brand",
      "size": 5,
      "aggs": [
        {
          "name": "avg_price",
          "type": "avg",
          "field": "price"
        },
        {
          "name": "total_stock",
          "type": "sum",
          "field": "stock"
        }
      ]
    }
  ]
}
```

### 示例 3：条件组合

```bash
# 请求
GET /api/nl/dsl?text=查询用户索引，age大于等于18并且city在北京、上海，取100条

# 响应
{
  "index": "用户",
  "query": {
    "logic": "and",
    "conditions": [
      {
        "field": "age",
        "op": "gte",
        "value": 18
      },
      {
        "field": "city",
        "op": "in",
        "values": ["北京", "上海"]
      }
    ]
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 100
  }
}
```

## 升级建议

### 从 1.0.10 升级

**1. 无需修改代码**

- 自然语言功能为新增功能，不影响现有 API
- 所有现有查询和聚合接口保持向后兼容

**2. 可选启用**

- 如果不使用自然语言功能，无需任何配置
- `natural-language-parser-starter` 依赖会被自动引入，但不影响现有功能

**3. 调整默认分页（可选）**

- 如需修改默认分页大小，在配置中添加：
  ```yaml
  io.github.surezzzzzz.sdk.elasticsearch.search.query-limits.default-size: 50
  ```

**4. 集成建议**

- 先在开发环境测试自然语言查询效果
- 验证字段名映射是否正确
- 确认解析结果符合预期后再推广到生产

## 兼容性

- **向后兼容**：完全兼容 1.0.10 及之前版本
- **新增功能**：自然语言转 DSL（可选使用）
- **配置兼容**：新增配置项有默认值，无需强制配置
- **依赖兼容**：新增依赖 `natural-language-parser-starter:1.0.4`

## 贡献者

- surezzzzzz
