# Simple Elasticsearch Search Starter

> 零代码侵入的 Elasticsearch 查询框架，完全配置驱动，开箱即用。

## 特性

| 特性 | 说明 | 版本 |
|------|------|------|
| 零代码侵入 | 完全配置驱动，无需编写任何业务代码 | v1.0.0+ |
| 多数据源路由 | 支持多个 ES 集群，自动路由 | v1.0.2+ |
| 版本兼容 | 支持 ES 6.x 和 7.x+，Spring Boot 2.2.5+ | v1.5.4+ |
| 动态 Mapping | 自动获取并缓存索引字段元数据 | v1.0.0+ |
| 灵活查询 | 支持 21 种操作符和嵌套逻辑组合 | v1.0.0+ |
| 条件表达式查询 | 类 SQL 表达式直接查询，支持中文字段名映射 | v1.5.2+ |
| 表达式提示 | 前端自动补全数据源（字段、运算符、时间范围） | v1.5.3+ |
| Pipeline 聚合 | bucket_sort Top N、bucket_selector HAVING（ES 6.1+） | v1.5.5+ |
| 表达式聚合 | 条件表达式作为聚合过滤条件 | v1.5.5+ |
| 聚合分析 | 支持 19 种聚合类型（指标/桶/pipeline），含 filter、filters、missing、date_range、ip_range、percentiles、percentile_ranks | v1.5.7+ |
| composite 聚合翻页 | 突破 65535 条限制，支持全量遍历（ES 6.1+） | v1.4.0+ |
| 深分页 | search_after 多种模式（tiebreaker/pit/none） | v1.3.0+ |
| **scroll 全量遍历** | **快照一致性遍历，适合数据导出/全量迁移，兼容 ES 1.x+** | **v1.5.8+** |
| 日期分割索引 | 按年/月/日分割，自动路由 + 智能降级 | v1.0.0+ |
| 敏感字段保护 | 支持字段禁止访问和脱敏 | v1.0.0+ |
| 自然语言查询 | 中文自然语言转 ES DSL，FieldBinder 字段绑定 | v1.1.0+ |
| **NL 直接查询** | **直接用中文自然语言发起查询/聚合，支持 pagination/dateRange/fields/collapse/after 参数覆盖，scroll 续页** | **v1.6.2+** |
| 查询事件发布 | 查询/聚合后发布 Spring 事件，支持审计扩展 | v1.2.0+ |
| **独立计数查询** | **`countOnly=true` 走 ES `_count` API，仅返回 total，性能远优于 `_search + size=0`** | **v1.6.6+** |

---

## 版本选型

> route-starter 通过 `api` 依赖自动传递，无需单独声明。

### 按需选版本

| 你需要的功能 | 推荐 search-starter | 自动传递 route-starter | 说明 |
|------------|--------------------|-----------------------|------|
| 基础查询 / 聚合 / 深分页 / scroll | **1.5.8** | 1.0.8 | 所有基础功能 bug 已修复 |
| + 条件表达式查询（类 SQL） | **1.5.8** | 1.0.8 | 表达式在 1.5.2 引入，1.5.8 稳定 |
| + NL 转 DSL（`/api/nl/dsl`） | **1.6.2** | 1.0.10 | NL 翻译器 AND 条件 / 聚合名称 bug 在 1.6.1 修复 |
| + NL 直接查询（`/api/query/nl`） | **1.6.2** | 1.0.10 | + pagination/dateRange/fields/collapse 覆盖、scroll 续页 |
| + 表达式 TEXT 字段精确匹配 / AND-OR 扁平化 | **1.6.5** | 1.0.10 | Bug Fix：`=` 操作符自动使用 `.keyword` 子字段；多条件 AND/OR 生成单层 bool |
| + 独立计数查询（`countOnly=true`，走 `_count` API） | **1.6.6** | 1.0.10 | 仅返回 total，无文档 fetch/sort，性能远优于 `_search + size=0` |
| + 通配符索引字段元数据合并 | **1.6.8** | 1.0.10 | Bug Fix：通配符索引 fields / query 探测从"取第一个"改为"合并全部匹配索引" |

### route-starter 各版本能力

| 版本 | 新增能力 |
|------|---------|
| 1.0.1 | SpEL 表达式动态索引名 |
| 1.0.2 | `urls` 字段支持完整 URL 格式，SpEL / 正则编译缓存 |
| 1.0.3 | 自定义异常体系，`server-version` 手动指定 ES 版本，版本探测配置 |
| 1.0.6 | 自定义 IndexNameExtractor 扩展点 |
| 1.0.7 | Spring Boot 2.4.x CGLIB 兼容性修复 |
| 1.0.8 | Spring Boot 2.2.5 / ES 6.8.x 兼容性修复 |
| 1.0.9 | Spring Boot 2.3.12 启动报错修复 |
| **1.0.10** | Registry 层 BootstrapMethodError 修复（**当前推荐**） |

### Spring Boot 版本兼容性

| Spring Boot | ES Client | 单数据源 | 多数据源路由 | 推荐 route-starter |
|-------------|-----------|---------|------------|-------------------|
| 2.2.5 | 6.5.x | ✅ | ❌ | 1.0.10 |
| 2.3.12 | 6.8.x | ✅ | ❌ | 1.0.10 |
| 2.4.x | 7.9+ | ✅ | ❌ | 1.0.10 |
| **2.7.x+** | **7.17+** | **✅** | **✅** | **1.0.10** |

> 多数据源路由需要 Spring Boot 2.7.x+。低版本下 `RestHighLevelClient` 正常可用，但 `ElasticsearchRestTemplate` 代理创建失败，路由功能不可用。

---



### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.8'

    // 需要自行引入
    implementation "org.springframework.boot:spring-boot-starter-data-elasticsearch"
    implementation "org.apache.httpcomponents:httpclient"
    implementation "org.apache.httpcomponents:httpcore"
    implementation "org.springframework.boot:spring-boot-starter-web"
    annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"
}
```

> route-starter 会自动传递依赖，无需手动添加。支持 Spring Boot 2.2.5 / 2.4.x / 2.7.x。

### 2. 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true
            default-source: primary
            sources:
              primary:
                urls: http://localhost:9200

          search:
            enable: true
            indices:
              - name: "user_behavior_*"
                alias: user_behavior
                date-split: true
                date-pattern: "yyyy.MM.dd"
                date-field: "createTime"
```

### 3. 发起查询

```bash
POST /api/query
{
  "index": "user_behavior",
  "query": {
    "field": "status",
    "op": "eq",
    "value": "active"
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 20,
    "sort": [{"field": "createTime", "order": "desc"}]
  }
}
```

---

## API 参考

### POST /api/query — 数据查询

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `index` | String | 索引别名或名称（必填） |
| `query` | QueryCondition | 查询条件（可选，不传则查全量） |
| `dateRange` | DateRange | 时间范围，用于日期分割索引路由 |
| `pagination` | PaginationInfo | 分页配置（可选，默认 offset 第1页） |
| `fields` | List\<String\> | 字段投影，只返回指定字段 |
| `collapse` | CollapseConfig | 字段折叠去重（v1.1.3+） |
| `countOnly` | Boolean | `true` 时仅返回 total，走 `_count` API（v1.6.6+） |

**QueryCondition 字段：**

| 字段 | 说明 |
|------|------|
| `field` | 字段名 |
| `op` | 操作符（见操作符表） |
| `value` | 单值 |
| `values` | 多值（in/between/not_in 使用） |
| `logic` | 逻辑操作符（and/or），用于嵌套条件 |
| `conditions` | 子条件列表 |

**PaginationInfo 字段：**

| 字段 | 说明 |
|------|------|
| `type` | `offset` / `search_after` / `scroll` |
| `page` | 页码（offset 模式） |
| `size` | 每页数量 |
| `sort` | 排序字段列表 |
| `searchAfter` | 上一页游标（search_after 模式） |
| `searchAfterMode` | `tiebreaker`（默认）/ `pit` / `none` |
| `pitKeepAlive` | PIT 保活时间（pit 模式，如 `1m`） |
| `pitId` | PIT ID（pit 模式，首页不传） |
| `scrollTtl` | scroll 保活时间（scroll 模式必填，如 `2m`） |
| `scrollId` | scroll ID（scroll 模式，首页不传） |

---

### POST /api/agg — 聚合查询

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `index` | String | 索引别名或名称（必填） |
| `query` | QueryCondition | 过滤条件（可选） |
| `aggs` | List\<AggDefinition\> | 聚合定义列表（必填） |
| `after` | Map | composite 聚合翻页游标（v1.4.0+） |

**AggDefinition 字段：**

| 字段 | 说明 |
|------|------|
| `name` | 聚合名称（响应中的 key） |
| `type` | 聚合类型（见聚合类型表） |
| `field` | 聚合字段 |
| `size` | bucket 数量（terms/histogram）或每页大小（composite） |
| `interval` | 时间间隔（date_histogram，如 `day`/`1d`） |
| `ranges` | 范围列表（range 聚合） |
| `aggs` | 嵌套子聚合 |
| `composite` | 是否使用 composite 翻页模式（v1.4.0+） |
| `order` | composite 排序方向 asc/desc（v1.4.0+，默认 asc） |
| `pipelineAggs` | pipeline 聚合列表（v1.5.5+，仅普通 bucket 聚合有效） |
| `query` | 过滤条件（filter 聚合专用，v1.5.6+，类型同 QueryCondition） |
| `filters` | 多命名过滤器（filters 聚合专用，v1.5.6+，key 为 bucket 名称，value 为 QueryCondition） |
| `percents` | 百分位列表（percentiles 聚合专用，v1.5.7+，不填使用 ES 默认 1/5/25/50/75/95/99） |
| `values` | 值列表（percentile_ranks 聚合专用，v1.5.7+，必填） |

**PipelineAggDefinition 字段（v1.5.5+）：**

| 字段 | 说明 |
|------|------|
| `name` | 聚合名称 |
| `type` | `bucket_sort` 或 `bucket_selector` |
| `sort` | 排序字段（bucket_sort），key 为同级 metrics agg 名称，value 为 asc/desc |
| `size` | Top N 数量（bucket_sort，不填则仅排序） |
| `from` | 跳过的 bucket 数量（bucket_sort，默认 0） |
| `script` | Painless 脚本（bucket_selector 必填），通过 `params.xxx` 引用同级 metrics agg |
| `bucketsPath` | 变量名→聚合名映射（bucket_selector 可选，不填时自动从 script 推断） |

**响应字段：**

| 字段 | 说明 |
|------|------|
| `aggregations` | 聚合结果，key 为聚合名称 |
| `afterKey` | composite 翻页游标，null 表示已遍历完（v1.4.0+） |
| `took` | 耗时（毫秒） |
| `rawResponse` | 原始 ES 聚合响应（仅 ES 6.x + 配置启用时） |

---

### POST /api/agg/expression — 表达式聚合（v1.5.5+）

使用条件表达式字符串作为聚合过滤条件，其余字段与 `/api/agg` 完全一致。

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `index` | String | 索引别名或名称（必填） |
| `expression` | String | 条件表达式字符串（必填） |
| `aggs` | List\<AggDefinition\> | 聚合定义列表（必填） |
| `after` | Map | composite 聚合翻页游标（可选） |

```json
POST /api/agg/expression
{
  "index": "order",
  "expression": "状态 = \"completed\" AND amount >= 100",
  "aggs": [{
    "name": "by_product",
    "type": "terms",
    "field": "product_name",
    "size": 10,
    "aggs": [{"name": "total_amount", "type": "sum", "field": "amount"}]
  }]
}
```

响应格式与 `/api/agg` 完全一致。

---

### GET /api/indices — 索引列表

### GET /api/indices/{alias}/fields — 字段信息

### POST /api/indices/refresh — 刷新所有 Mapping

### POST /api/indices/{alias}/refresh — 刷新指定索引 Mapping

### GET /api/nl/dsl — 自然语言转 DSL（v1.1.0+）

```bash
GET /api/nl/dsl?text=查询user_behavior索引，age大于18，按createTime降序，取50条
```

返回可直接传给 `/api/query` 或 `/api/agg` 的 DSL 对象。

---

### POST /api/query/nl — 自然语言直接查询（v1.6.2+）

直接用中文自然语言发起查询，无需手动构造 `QueryRequest`。

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `nl` | String | 自然语言查询文本（scroll 续页时可为空） |
| `dataSource` | String | 索引别名，优先级高于 NL 文本中的索引提示 |
| `pagination` | PaginationInfo | 覆盖 NL 解析出的分页参数（可选），支持 scroll/search_after/pit |
| `dateRange` | DateRange | 覆盖 NL 解析出的时间范围（可选），用于日期分割索引路由 |
| `fields` | List\<String\> | 字段投影（可选），只返回指定字段 |
| `collapse` | CollapseConfig | 字段折叠去重（可选） |
| `countOnly` | Boolean | `true` 时仅返回 total，走 `_count` API（v1.6.6+） |

**基础查询：**

```json
POST /api/query/nl
{
  "nl": "查询test_user索引，城市等于北京并且年龄大于25",
  "dataSource": "test_user"
}
```

**scroll 全量遍历（第一页 + 续页）：**

```json
// 第一页：传入 sort，按年龄升序返回2条
POST /api/query/nl
{
  "nl": "查询test_user索引",
  "dataSource": "test_user",
  "pagination": {"type": "scroll", "size": 2, "scrollTtl": "1m", "sort": [{"field": "age", "order": "asc"}]}
}

// 续页：scrollId 非空时跳过 NL 解析
POST /api/query/nl
{
  "dataSource": "test_user",
  "pagination": {"type": "scroll", "size": 2, "scrollTtl": "1m", "scrollId": "FGluY2x1ZGVfY29udGV4dF91dWlk..."}
}
```

响应格式与 `/api/query` 完全一致。

---

### POST /api/agg/nl — 自然语言直接聚合（v1.6.2+）

直接用中文自然语言发起聚合，无需手动构造 `AggRequest`。

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `nl` | String | 自然语言查询文本（必填） |
| `dataSource` | String | 索引别名，优先级高于 NL 文本中的索引提示 |
| `dateRange` | DateRange | 覆盖 NL 解析出的时间范围（可选） |
| `after` | Map | composite 聚合翻页游标（可选） |

```json
POST /api/agg/nl
{
  "nl": "按城市分组前10个统计平均年龄",
  "dataSource": "test_user"
}
```

响应格式与 `/api/agg` 完全一致。

---

### POST /api/query/expression — 条件表达式查询（v1.5.2+）

通过类 SQL 表达式字符串直接发起查询，无需手动构造 `QueryCondition`。

**请求字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `index` | String | 索引别名或名称（必填） |
| `expression` | String | 条件表达式字符串（必填） |
| `pagination` | PaginationInfo | 分页配置（可选） |
| `fields` | List\<String\> | 字段投影（可选） |
| `dateRange` | DateRange | 日期分割索引路由范围（可选） |
| `countOnly` | Boolean | `true` 时仅返回 total，走 `_count` API（v1.6.6+） |

```json
POST /api/query/expression
{
  "index": "order",
  "expression": "status = \"paid\" AND amount >= 100",
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

响应格式与 `/api/query` 一致。

---

### GET /api/expression/validate — 校验表达式语法（v1.5.2+，v1.6.4 index 必填）

```bash
GET /api/expression/validate?expression=订单ID = 'xxx' AND 状态 = '已完成'&index=order
```

`index` 参数从 v1.6.4 起必填，用于 label 预替换时查找字段映射。语法正确时：

```json
{"data": {"valid": true, "errorMessage": null, "errorPosition": -1}}
```

校验失败时 `valid` 为 `false`，返回错误位置和提示信息。

---

### GET /api/expression/hints — 表达式提示（v1.5.3+）

获取指定索引的表达式提示信息，供前端搜索框自动补全。`index` 参数可选，不传时 `fields` 为空。

```bash
GET /api/expression/hints?index=order
```

```json
{
  "data": {
    "fields": [
      {"name": "status", "label": ["状态", "订单状态"]},
      {"name": "amount", "label": ["金额"]}
    ],
    "operators": [
      {"op": "=", "description": "等于", "chinese": "等于"},
      {"op": "AND", "description": "逻辑与", "chinese": "且、并且"}
    ],
    "timeRanges": ["近5分钟", "近1小时", "近7天", "近1个月", "近3个月", "今天"],
    "valueRules": {
      "stringNeedsQuote": true,
      "supportedQuotes": ["'", "\""],
      "booleanKeywords": ["true", "false", "真", "假"],
      "numberNoQuote": true
    }
  }
}
```

- `fields`：来自索引 `field-mapping` 配置，只有配置了映射的字段才会出现
- `operators`：所有支持的运算符及其中文别名
- `timeRanges`：时间范围主关键字
- `valueRules`：字符串/中文值必须加引号，数字和布尔值不需要

---

## 操作符

| 操作符 | 说明 | 值字段 |
|--------|------|--------|
| `eq` | 等于 | `value` |
| `ne` | 不等于 | `value` |
| `gt` / `gte` | 大于 / 大于等于 | `value` |
| `lt` / `lte` | 小于 / 小于等于 | `value` |
| `in` | 在列表中 | `values` |
| `not_in` | 不在列表中 | `values` |
| `between` | 在范围内 | `values: [from, to]` |
| `like` | 模糊匹配（wildcard） | `value` |
| `not_like` | 模糊不匹配 | `value` |
| `prefix` | 前缀匹配 | `value` |
| `not_prefix` | 前缀不匹配 | `value` |
| `suffix` | 后缀匹配 | `value` |
| `not_suffix` | 后缀不匹配 | `value` |
| `regex` | 正则匹配 | `value` |
| `not_regex` | 正则不匹配 | `value` |
| `exists` | 字段存在 | — |
| `not_exists` | 字段不存在 | — |
| `is_null` | 字段为空 | — |
| `is_not_null` | 字段不为空 | — |

> **表达式语法支持范围**：`regex` / `not_regex` 仅通过 JSON API（`/api/query`）可用，表达式语法无 REGEX 关键字。`between` 仅由时间范围关键字（如 `最近7天`）自动生成，表达式无 BETWEEN 关键字。其余操作符均可通过表达式语法实现，详见[场景九](#场景九条件表达式查询-v152)。

---

## 聚合类型

### 指标聚合（Metrics）

| 类型 | 说明 | 所需字段 |
|------|------|---------|
| `sum` / `avg` / `min` / `max` / `count` | 基础指标 | `field` |
| `cardinality` | 去重计数（近似值） | `field` |
| `stats` | 5 项统计（count/min/max/avg/sum） | `field` |
| `extended_stats` | 9 项统计（含 variance/std_deviation/std_deviation_bounds） | `field` |
| `percentiles` | 百分位数，如 P50/P95/P99（v1.5.7+） | `field`，可选 `percents` |
| `percentile_ranks` | 百分位排名，如"1000 以下占比"（v1.5.7+） | `field`、`values`（必填） |

### 桶聚合（Bucket）

| 类型 | 说明 | 支持 composite | 所需字段 |
|------|------|---------------|---------|
| `terms` | 分组聚合 | ✅ | `field` |
| `date_histogram` | 日期直方图 | ✅ | `field`、`interval` |
| `histogram` | 数值直方图 | ✅ | `field`、`interval` |
| `range` | 数值范围分组 | ❌ | `field`、`ranges` |
| `date_range` | 日期范围分组，支持相对时间（如 `now-1M`）（v1.5.6+） | ❌ | `field`、`ranges` |
| `ip_range` | IP 范围分组，支持 CIDR（v1.5.6+） | ❌ | `field`、`ranges` |
| `filter` | 单过滤器，只统计满足条件的文档（v1.5.6+） | ❌ | `query` |
| `filters` | 多命名过滤器，每个过滤器产生独立 bucket（v1.5.6+） | ❌ | `filters` |
| `missing` | 统计字段值为 null 或不存在的文档数（v1.5.6+） | ❌ | `field` |

---

## 响应格式

```json
// 成功（HTTP 200）
{"data": { ... }}

// 业务错误（HTTP 400）
{"error": "错误信息"}

// 服务器错误（HTTP 500）
{"error": "服务器内部错误"}
```

---

## 配置参考

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true
            default-source: primary
            sources:
              primary:
                urls: http://localhost:9200
                connect-timeout: 5000
                socket-timeout: 60000
                keep-alive-strategy: 300
                max-conn-total: 100
                max-conn-per-route: 10
                enable-connection-reuse: true
              legacy:                              # 多数据源（ES 6.x）
                urls: http://legacy-es:9200
                server-version: 6.2.2             # 指定版本，自动适配 API 差异
            rules:
              - pattern: "old_*"
                datasource: legacy
                type: prefix
                priority: 10

          search:
            enable: true
            indices:
              - name: "user_behavior_*"
                alias: user_behavior
                date-split: true
                date-pattern: "yyyy.MM.dd"
                date-field: "createTime"
                field-mapping:                    # 字段名映射（v1.5.2+，表达式查询用）
                  status:                         # key = ES 字段名
                    - 用户状态                     # value = 中文标签列表（支持多个别名）
                    - 状态
                  amount:
                    - 金额
                lazy-load: false
                cache-mapping: true
                sensitive-fields:
                  - field: "phone"
                    strategy: "MASK"
                    mask-start: 3
                    mask-end: 4
                    mask-pattern: "****"
                  - field: "password"
                    strategy: "FORBIDDEN"

            mapping-refresh:
              enabled: true
              interval-seconds: 300

            query-limits:
              max-size: 10000
              default-size: 20
              max-offset: 10000
              ignore-unavailable-indices: false    # 日期分割索引推荐开启
              strict-date-filter: true             # 防止跨天脏数据（默认 true）
              default-date-range: 30d              # 通配索引默认时间范围（v1.4.0+）

            api:
              enabled: true
              base-path: "/api"
              include-score: false
              include-raw-response: false          # ES 6.x 原始聚合响应
              expression:                          # 表达式查询配置（v1.5.2+）
                max-length: 2048                   # 表达式最大长度（0 表示不限制）

            downgrade:
              enabled: true
              max-http-line-length: 4096
              max-level: 3
              enable-estimate: true
              auto-downgrade-index-count-threshold: 200

            pit:
              max-keep-alive: 5m                   # PIT 保活时间上限（v1.3.0+）

            scroll:
              max-ttl: 5m                          # scroll 保活时间上限（v1.5.8+，默认 5m）
```

---

## 使用场景

### 场景一：普通分页查询

```json
POST /api/query
{
  "index": "user_behavior",
  "query": {
    "logic": "and",
    "conditions": [
      {"field": "status", "op": "eq", "value": "active"},
      {"field": "age", "op": "gte", "value": 18}
    ]
  },
  "pagination": {
    "type": "offset",
    "page": 1,
    "size": 20,
    "sort": [{"field": "createTime", "order": "desc"}]
  }
}
```

翻页修改 `page` 字段即可。`(page-1)*size > max-offset`（默认 10000）时报 400，超深分页请改用 search_after。

---

### 场景二：大规模数据深度分页（search_after）

**第一页（不传 searchAfter）：**

```json
POST /api/query
{
  "index": "app_access_log",
  "dateRange": {"from": "2025-01-01T00:00:00", "to": "2025-12-31T23:59:59"},
  "query": {"field": "clientIP", "op": "eq", "value": "192.168.1.1"},
  "pagination": {
    "type": "search_after",
    "size": 500,
    "sort": [
      {"field": "timestamp", "order": "desc"},
      {"field": "requestId", "order": "asc"}
    ]
  }
}
```

**下一页（传入 nextSearchAfter）：**

```json
{
  "pagination": {
    "type": "search_after",
    "size": 500,
    "searchAfter": [1735660800000, "req_abc123"],
    "sort": [
      {"field": "timestamp", "order": "desc"},
      {"field": "requestId", "order": "asc"}
    ]
  }
}
```

- 排序字段组合必须能唯一确定一条记录，推荐末尾加唯一字段
- 默认 `tiebreaker` 模式自动追加 `_id ASC`，可以不手动加
- 每次翻页必须传相同的 `sort` 和 `dateRange`

---

### 场景三：内存敏感场景（PIT 模式，ES 7.10+）

**第一页（不传 pitId）：**

```json
POST /api/query
{
  "index": "large_index",
  "pagination": {
    "type": "search_after",
    "searchAfterMode": "pit",
    "pitKeepAlive": "1m",
    "size": 1000,
    "sort": [{"field": "createTime", "order": "desc"}]
  }
}
```

**下一页（带回 pitId 和 nextSearchAfter）：**

```json
{
  "pagination": {
    "type": "search_after",
    "searchAfterMode": "pit",
    "pitKeepAlive": "1m",
    "pitId": "46ToAwMDaWR...",
    "searchAfter": [1735660800000],
    "size": 1000,
    "sort": [{"field": "createTime", "order": "desc"}]
  }
}
```

- `pitKeepAlive` 建议 `1m`~`5m`，每次翻页带回上一页的 `pitId`，框架自动续期
- 最后一页（`hasMore: false`）框架自动关闭 PIT
- 服务端可配置 `pit.max-keep-alive` 限制最大保活时间（默认 5m）

---

### 场景四：全量遍历聚合数据（composite 翻页）

**第一页（不传 after）：**

```json
POST /api/agg
{
  "index": "order_index",
  "query": {"field": "status", "op": "eq", "value": "completed"},
  "aggs": [{
    "name": "user_total",
    "type": "terms",
    "field": "userId",
    "composite": true,
    "size": 1000,
    "aggs": [
      {"name": "total_amount", "type": "sum", "field": "amount"},
      {"name": "order_count", "type": "count", "field": "orderId"}
    ]
  }]
}
```

**下一页（传入 afterKey）：**

```json
{
  "after": {"user_total": {"userId": "user_002"}},
  "aggs": [...]
}
```

`afterKey` 为 `null` 表示遍历完成。`size` 建议 500~2000，composite 内只允许嵌套 metrics 子聚合。

---

### 场景五：嵌套逻辑查询

等价于 `WHERE status='active' AND age BETWEEN 18 AND 60 AND (city='Beijing' OR city='Shanghai')`：

```json
POST /api/query
{
  "index": "user_behavior",
  "query": {
    "logic": "and",
    "conditions": [
      {"field": "status", "op": "eq", "value": "active"},
      {"field": "age", "op": "between", "values": [18, 60]},
      {
        "logic": "or",
        "conditions": [
          {"field": "city", "op": "eq", "value": "Beijing"},
          {"field": "city", "op": "eq", "value": "Shanghai"}
        ]
      }
    ]
  },
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

---

### 场景六：字段折叠去重（v1.1.3+）

按源 IP 去重，每个 IP 只返回最新一条：

```json
POST /api/query
{
  "index": "access_log",
  "fields": ["sourceIP", "lastAccessTime", "requestCount"],
  "collapse": {"field": "sourceIP"},
  "pagination": {
    "type": "search_after",
    "size": 100,
    "sort": [{"field": "sourceIP", "order": "asc"}]
  }
}
```

使用 collapse 时必须指定排序字段，仅支持单字段去重。

---

### 场景七：日期分割索引查询

```json
POST /api/query
{
  "index": "app_log",
  "dateRange": {"from": "2025-01-01T00:00:00", "to": "2025-01-07T23:59:59"},
  "query": {"field": "level", "op": "eq", "value": "ERROR"},
  "pagination": {"type": "offset", "page": 1, "size": 50}
}
```

框架自动路由到 `app_log_2025.01.01` ~ `app_log_2025.01.07`。查询范围超大时（如全年 365 天）自动降级为月级通配符，避免 HTTP 请求行超限。

- 索引配置中必须设置 `date-split: true` 和 `date-pattern`
- 建议配置 `date-field`，框架自动追加时间过滤防止跨天脏数据
- 开启 `ignore-unavailable-indices: true` 可忽略不存在的索引

---

### 场景八：通配索引防全量扫描（v1.4.0+）

配置 `default-date-range` 后，查询通配索引未传时间范围时自动补充，防止全量扫描：

```yaml
query-limits:
  default-date-range: 30d  # 支持 30d / 7d / 24h / 1h 等
```

用户自己传了 `dateRange` 则不覆盖，精确索引不受影响。

---

### 场景九：条件表达式查询（v1.5.2+）

用类 SQL 表达式替代手动构造 `QueryCondition`，适合前端搜索框、低代码平台等场景。

#### 1. 基本用法

```json
POST /api/query/expression
{
  "index": "order",
  "expression": "status = \"paid\" AND amount >= 100",
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

#### 2. 字段名映射

在配置中定义 ES 字段名到中文标签的映射，表达式中即可使用中文：

```yaml
indices:
  - name: "order_*"
    alias: order
    field-mapping:
      status:
        - 订单状态
        - 状态
      amount:
        - 金额
      first_alert_time:
        - 首次告警时间
```

`订单状态 = "paid" AND 金额 >= 100`（`状态` 同样有效）

#### 3. 支持的表达式语法

> 所有英文关键字大小写不敏感：`AND` / `and` / `And` 等效。中文字段名可直接使用，无需引号。

**比较运算符**

| 语法 | 中文关键字 | 示例 |
|------|-----------|------|
| `=` / `==` | `等于` | `status = "paid"` |
| `!=` / `<>` | `不等于` | `status != "failed"` |
| `>` | `大于` / `晚于` | `amount > 100` |
| `>=` | `大于等于` / `不小于` | `amount >= 100` |
| `<` | `小于` / `早于` | `amount < 100` |
| `<=` | `小于等于` / `不大于` | `amount <= 100` |

**集合与匹配**

| 语法 | 中文关键字 | 示例 |
|------|-----------|------|
| `IN (...)` | `包含于` | `status IN ("paid", "refunded")` |
| `NOT IN (...)` | — | `status NOT IN ("failed")` |
| `LIKE value` | `模糊匹配` / `包含` | `name LIKE "张"` |
| `NOT LIKE value` | — | `name NOT LIKE "test"` |
| `PREFIX LIKE value` | `前缀` | `name PREFIX LIKE "张"` |
| `NOT PREFIX LIKE value` | — | `name NOT PREFIX LIKE "test"` |
| `SUFFIX LIKE value` | `后缀` | `email SUFFIX LIKE "@spam.com"` |
| `NOT SUFFIX LIKE value` | — | `email NOT SUFFIX LIKE "@spam.com"` |

**存在性检查**

| 语法 | 中文关键字 | 示例 | 说明 |
|------|-----------|------|------|
| `field EXISTS` | `存在` / `有` | `remark EXISTS` | 字段在 ES 索引中存在 |
| `field NOT EXISTS` | — | `remark NOT EXISTS` | 字段不存在 |
| `field IS NULL` | `是 空` | `remark IS NULL` | 字段值为 null |
| `field IS NOT NULL` | — | `remark IS NOT NULL` | 字段值不为 null |

**逻辑组合**

| 语法 | 中文关键字 | 优先级 |
|------|-----------|--------|
| `NOT expr` | `非` | 最高 |
| `A AND B` | `且` / `并且` | 中 |
| `A OR B` | `或` / `或者` | 最低 |
| `(expr)` | — | 括号覆盖优先级 |

> `NOT` 对复合条件做德摩根展开：`NOT (A AND B)` → `NOT A OR NOT B`；对叶子条件翻转操作符：`=` → `!=`，`LIKE` → `NOT LIKE`，`EXISTS` → `NOT EXISTS` 等。

**时间范围**

| 语法 | 示例 | 说明 |
|------|------|------|
| `field = 时间关键字` | `create_time = 最近7天` | `=` 生成 between（from ~ now） |
| `field > 时间关键字` | `create_time > 近1小时` | `>` / `>=` / `<` / `<=` 生成对应比较（against from 时间点） |

时间关键字完整列表见 `/api/expression/hints` 返回的 `timeRanges`，包含近N分钟/小时/天/周/月/年、今天/昨天/本周/上月等。

**值类型**

| 类型 | 示例 | 说明 |
|------|------|------|
| 字符串 | `'value'` / `"value"` | 必须加引号（ASCII 单引号或双引号） |
| 数字 | `100` / `3.14` / `-5` | 不需要引号 |
| 布尔 | `true` / `false` / `真` / `假` / `否` | 不需要引号 |
| 时间关键字 | `最近7天` / `今天` | 不需要引号 |

#### 4. 表达式校验

```bash
GET /api/expression/validate?expression=status = "paid" AND amount >= 100
```

```json
{"data": {"valid": true, "errorMessage": null, "errorPosition": -1}}
```

#### 5. 前端自动补全（v1.5.3+）

```javascript
async function loadHints(index) {
  const resp = await fetch(`/api/expression/hints?index=${index}`);
  const { data } = await resp.json();
  // data.fields     → 字段列表（name, label）
  // data.operators  → 运算符列表（op, description, chinese）
  // data.timeRanges → 时间范围关键字
  // data.valueRules → 值规则（引号要求等）
  return data;
}
```

#### 6. 注意事项

- `condition-expression-parser-starter` 已通过 starter 传递依赖，无需单独添加
- 表达式最大长度默认 2048 字符，可通过 `api.expression.max-length` 调整
- `field-mapping` key 为 ES 字段名，value 为中文标签列表，多个标签均可在表达式中使用
- `strategy: FORBIDDEN` 的敏感字段不允许同时出现在 `field-mapping` 中
- **字符串值必须加引号**（ASCII 单引号 `'` 或双引号 `"`），数字和布尔值不需要
  - ✅ `status = 'paid'` / `name LIKE '测试'` / `create_time = 最近7天` / `amount >= 100`
  - ❌ `status = paid`（不加引号会被识别为字段名）
  - 不支持中文引号 `""`
- 布尔值支持 `true` / `false` / `真` / `假` / `否`，不需要引号
- 所有英文关键字大小写不敏感：`AND` / `and` / `And` 等效

---

### 场景十：filter / filters 聚合对比分析（v1.5.6+）

**filter — 单过滤器，统计满足条件的子集：**

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
      {"name": "count", "type": "count", "field": "orderId"}
    ]
  }]
}
```

**filters — 多命名过滤器，同时对比多个子集：**

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [{
    "name": "by_status",
    "type": "filters",
    "filters": {
      "completed": {"field": "status", "op": "eq", "value": "completed"},
      "pending":   {"field": "status", "op": "eq", "value": "pending"},
      "cancelled": {"field": "status", "op": "eq", "value": "cancelled"}
    },
    "aggs": [
      {"name": "total_amount", "type": "sum", "field": "amount"}
    ]
  }]
}
```

响应中每个 key 对应一个 bucket，适合同时对比多个分类的指标。

---

### 场景十一：missing 聚合统计数据质量（v1.5.6+）

统计某字段为空或缺失的文档数，常用于数据质量检查：

```json
POST /api/agg
{
  "index": "user_profile",
  "aggs": [
    {"name": "no_email",    "type": "missing", "field": "email"},
    {"name": "no_phone",    "type": "missing", "field": "phone"},
    {"name": "no_category", "type": "missing", "field": "category"}
  ]
}
```

---

### 场景十二：date_range / ip_range 聚合（v1.5.6+）

**date_range — 按日期范围分组，支持相对时间：**

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [{
    "name": "by_period",
    "type": "date_range",
    "field": "created_at",
    "ranges": [
      {"key": "this_month", "from": "now/M",  "to": "now"},
      {"key": "last_month", "from": "now-1M/M", "to": "now/M"},
      {"key": "older",      "to": "now-1M/M"}
    ]
  }]
}
```

**ip_range — 按 IP 段分组：**

```json
POST /api/agg
{
  "index": "access_log",
  "aggs": [{
    "name": "by_network",
    "type": "ip_range",
    "field": "client_ip",
    "ranges": [
      {"key": "internal",  "from": "10.0.0.0",   "to": "10.255.255.255"},
      {"key": "loopback",  "from": "127.0.0.0",  "to": "127.255.255.255"},
      {"key": "external",  "from": "1.0.0.0",    "to": "9.255.255.255"}
    ]
  }]
}
```

---

### 场景十三：Pipeline 聚合 Top N + HAVING（v1.5.5+）

先过滤再排序取 Top N，等价于 SQL 的 `HAVING + ORDER BY + LIMIT`：

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

---

### 场景十四：ip 字段查询与 CIDR 聚合（v1.5.6+）

ES 的 `ip` 字段类型原生支持 CIDR 表示法，`eq` 操作符传入 CIDR 即可匹配子网内所有 IP。

**精确 IP 查询：**

```json
POST /api/query
{
  "index": "access_log",
  "query": {"field": "client_ip", "op": "eq", "value": "10.0.0.1"},
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

**CIDR 子网查询（匹配 10.0.0.0/24 内所有 IP）：**

```json
POST /api/query
{
  "index": "access_log",
  "query": {"field": "client_ip", "op": "eq", "value": "10.0.0.0/24"},
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

**CIDR + filter 聚合（统计子网内的指标）：**

```json
POST /api/agg
{
  "index": "access_log",
  "aggs": [{
    "name": "internal_traffic",
    "type": "filter",
    "query": {"field": "client_ip", "op": "eq", "value": "10.0.0.0/8"},
    "aggs": [
      {"name": "total_bytes", "type": "sum", "field": "bytes"},
      {"name": "request_count", "type": "count", "field": "request_id"}
    ]
  }]
}
```

响应：`{"internal_traffic": {"count": N, "total_bytes": X, "request_count": Y}}`

**ip_range 聚合（按 IP 段分组统计）：**

```json
POST /api/agg
{
  "index": "access_log",
  "aggs": [{
    "name": "by_network",
    "type": "ip_range",
    "field": "client_ip",
    "ranges": [
      {"key": "internal",  "from": "10.0.0.0",   "to": "10.255.255.255"},
      {"key": "private",   "from": "192.168.0.0", "to": "192.168.255.255"},
      {"key": "loopback",  "from": "127.0.0.0",   "to": "127.255.255.255"}
    ]
  }]
}
```

- `ip` 字段类型在 ES 6.x 和 7.x 均支持
- CIDR 查询通过 `eq` 操作符传入，如 `10.0.0.0/24`、`192.168.0.0/16`
- `ip_range` 聚合支持 ES 6.1+

---

### 场景十五：percentiles / percentile_ranks 聚合（v1.5.7+）

**percentiles — 计算百分位数，适合分析响应时间、订单金额分布：**

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

不填 `percents` 时使用 ES 默认百分位（1/5/25/50/75/95/99）。

**percentile_ranks — 计算指定值的百分位排名，适合分析"达标率"：**

```json
POST /api/agg
{
  "index": "orders",
  "aggs": [{
    "name": "amount_ranks",
    "type": "percentile_ranks",
    "field": "amount",
    "values": [1000, 5000, 10000]
  }]
}
```

响应：`{"amount_ranks": {"5000.0": 48.0, "10000.0": 72.7}}`

key 为传入的原始值，value 为百分位排名。表示约 48% 的订单金额 ≤ 5000，约 73% ≤ 10000。`values` 字段必填，不填时报 400。

---

### 场景十六：scroll 全量遍历（v1.5.8+）

适用于数据导出、全量迁移等需要遍历全量数据的场景。与 search_after 不同，scroll 具备快照一致性，遍历期间新写入的数据不会出现在结果中，且不触发 `_id` fielddata，内存更友好。

**第一页（不传 scrollId，必须传 sort）：**

```json
POST /api/query
{
  "index": "order",
  "pagination": {
    "type": "scroll",
    "size": 500,
    "scrollTtl": "2m",
    "sort": [{"field": "createTime", "order": "asc"}]
  }
}
```

响应：

```json
{
  "data": {
    "total": 100000,
    "items": [...],
    "pagination": {
      "type": "scroll",
      "hasMore": true,
      "scrollId": "DXF1ZXJ5QW..."
    }
  }
}
```

**后续翻页（带 scrollId，不需要再传 sort/query）：**

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

`hasMore: false` 时 SDK 自动清除 scroll 上下文，无需手动操作。

**注意事项：**
- `scrollTtl` 必填，表示两次请求间的最大空闲时间，每次请求自动续期
- 第一页必须传 `sort`，后续翻页不需要
- 不支持与 `collapse` 同时使用
- 不支持跳页，只能顺序翻页
- 服务端可通过 `scroll.max-ttl` 限制最大保活时间（默认 5m）

---

### 场景十七：自然语言直接查询（v1.6.2+）

无需构造 DSL，直接用中文描述查询意图，支持参数覆盖和 scroll 续页。

**基础查询（带字段投影）：**

```json
POST /api/query/nl
{
  "nl": "查询test_user索引，姓名等于张三",
  "dataSource": "test_user",
  "fields": ["name", "age"]
}
```

**scroll 全量遍历：**

```json
// 第一页
POST /api/query/nl
{
  "nl": "查询test_user索引",
  "dataSource": "test_user",
  "pagination": {"type": "scroll", "size": 500, "scrollTtl": "2m", "sort": [{"field": "createTime", "order": "asc"}]}
}

// 续页（scrollId 非空时自动跳过 NL 解析）
POST /api/query/nl
{
  "dataSource": "test_user",
  "pagination": {"type": "scroll", "size": 500, "scrollTtl": "2m", "scrollId": "FGluY2x1ZGVfY29udGV4dF91dWlk..."}
}
```

**聚合（带 composite 续页）：**

```json
POST /api/agg/nl
{
  "nl": "按城市分组统计用户数",
  "dataSource": "test_user",
  "dateRange": {"from": "2025-01-01T00:00:00", "to": "2025-12-31T23:59:59"},
  "after": {"by_city": {"city": "深圳"}}
}
```

响应格式与 `/api/query`、`/api/agg` 完全一致。

**支持的 NL 语法示例：**

| 意图 | NL 文本示例 |
|------|------------|
| 等值查询 | `姓名等于张三` |
| 范围查询 | `年龄在20到30之间` |
| IN 查询 | `城市在北京、上海中` |
| AND 条件 | `年龄大于25并且城市等于北京` |
| OR 条件 | `城市等于北京或者城市等于上海` |
| 分页 | `第1页每页20条` |
| 排序 | `按年龄降序` |
| AVG 聚合 | `统计平均年龄` |
| TERMS 聚合 | `按城市分组前10个` |
| 嵌套聚合 | `按城市分组前10个统计平均年龄` |

> 需要在索引配置中设置 `field-mapping`，将中文字段名（如"年龄"）映射到 ES 字段名（如 `age`）。

---

### 场景十八：独立计数查询（v1.6.6+）

只需要总数、不需要文档内容时，使用 `countOnly=true` 走 ES `_count` API，无文档 fetch、无 sort，性能远优于 `_search + size=0`。

**基础用法：**

```json
POST /api/query
{
  "index": "user_behavior",
  "query": {
    "field": "status",
    "op": "eq",
    "value": "active"
  },
  "countOnly": true
}
```

响应：

```json
{"data": {"total": 12345, "items": null, "pagination": null}}
```

**与 NL 查询结合：**

```json
POST /api/query/nl
{
  "nl": "查询test_user索引",
  "dataSource": "test_user",
  "countOnly": true
}
```

**与表达式查询结合：**

```json
POST /api/query/expression
{
  "index": "order",
  "expression": "status = \"paid\" AND amount >= 100",
  "countOnly": true
}
```

**行为说明：**
- `countOnly=true` 时：仅返回 `total`，`items`/`pagination`/`page`/`size` 均为 null
- `countOnly=true` + PIT 分页：400 报错（`_count` 不支持 PIT）
- `countOnly=true` + offset / scroll / search_after(tiebreaker)：分页参数静默忽略
- `countOnly=false` 或不传：走正常 `_search` 路径

---

## 最佳实践

### 分页策略选型

| 场景 | 推荐策略 | 原因 |
|------|----------|------|
| 浅分页（< 1 万条） | `offset` | 简单直接，支持跳页 |
| 交互式深翻页，内存充裕 | `search_after` tiebreaker | 无深度限制，自动追加 `_id` 保证稳定性 |
| 交互式深翻页，内存敏感 | `search_after` pit（ES 7.10+） | 不触发 fielddata，快照一致性 |
| 全量遍历 / 数据导出 | `scroll` | 快照一致性，不触发 fielddata，兼容 ES 1.x+ |
| 全量聚合遍历 | composite 翻页 | 突破 65535 限制，支持 ES 6.1+ |

**不要用 offset 做深分页**：`(page-1)*size` 超过 `max-offset`（默认 10000）会报 400，且性能随页码线性下降。

**scroll 不适合交互式翻页**：scroll 上下文占用 ES 堆内存，长时间不用会超时，适合批处理而非用户点击翻页。

---

### 日期分割索引

- 必须配置 `date-field`，否则框架无法追加时间过滤，可能查出跨天脏数据
- 开启 `ignore-unavailable-indices: true`，避免查询不存在的日期索引时报 404
- 通配索引（含 `*`）建议配置 `default-date-range`，防止未传时间范围时全量扫描
- 降级阈值 `auto-downgrade-index-count-threshold` 根据实际 ES 集群 HTTP 行长度限制调整，默认 200

---

### 聚合

- **terms 聚合 `size` 要显式设置**：默认 10，如果分组值超过 10 会丢数据，全量遍历用 composite 翻页
- **composite 聚合 `size` 建议 500~2000**：太小翻页次数多，太大单次响应体大
- **composite 内只允许嵌套 metrics 子聚合**：不支持嵌套 bucket 聚合（ES 限制）
- **pipeline 聚合的 `bucket_selector` 先过滤再 `bucket_sort` 排序**：顺序不能反，否则 HAVING 不生效
- **ES 6.x 聚合响应走 JSON 手动解析路径**：格式与 7.x+ 保持一致，无需业务层感知版本差异

---

### 敏感字段

- `FORBIDDEN` 字段不允许同时出现在 `field-mapping` 中，否则表达式查询可以绕过保护
- `MASK` 字段仍可作为查询条件，只是响应中脱敏；`FORBIDDEN` 字段查询时直接报 400
- 敏感字段配置变更后需调用 `/api/indices/{alias}/refresh` 刷新 Mapping 缓存

---

### 性能

- **Mapping 缓存**：`cache-mapping: true`（默认）避免每次查询都请求 ES 获取字段信息；`lazy-load: true` 延迟到首次查询时加载，适合索引数量多的场景
- **定时刷新**：`mapping-refresh.enabled: true` 配合 `interval-seconds` 定期刷新，适合字段会动态变化的索引
- **连接池**：`max-conn-total` 和 `max-conn-per-route` 根据并发量调整，默认 100/10
- **socket-timeout**：复杂聚合（如 percentiles 全量数据）耗时长，建议适当调大，默认 60s

---

### ES 6.x 兼容

- 指定 `server-version: 6.x.x` 让框架跳过版本探测，直接走低级 API 路径，减少启动时的探测请求
- PIT 分页在 ES 6.x 服务端下自动禁用（validate 阶段报 400），改用 `search_after` none 模式或 scroll
- `include-raw-response: true` 可在 ES 6.x 聚合响应解析失败时返回原始 JSON，便于排查

---

### 自然语言查询

- **优先用 `dataSource` 指定索引**：NL 文本中的索引提示（如"查询user索引"）依赖 nl-parser 的分词，`dataSource` 字段更可靠，不受分词影响
- **AND 多条件顺序**：nl-parser 对 AND 条件的解析存在顺序敏感性，建议将主要过滤条件放在前面（如"年龄大于25并且城市等于北京"）
- **IN 列表用顿号分隔**：`城市在北京、上海中`，不要用"和"连接（"城市在北京和上海中"会被解析为单值）
- **stats 聚合用英文关键字**：`年龄stats` 比"年龄全面统计"更可靠，避免分词器拆分"全面统计"关键字
- **NL 不走降级重试**：`/api/query/nl` 和 `/api/agg/nl` 不经过 QueryExecutor 的降级逻辑，日期分割索引的降级需自行处理；`dateRange` 覆盖可解决日期路由问题
- **PIT 已支持 alias**：`searchAfterMode: pit` 自动将 alias 解析为物理索引名后再打开 PIT，无需手动传物理索引名
- **NL 不适合生产高频查询**：NL 解析有额外的 CPU 开销，高频场景建议先用 `/api/nl/dsl` 转换一次，缓存 DSL 后直接调 `/api/query`

---

### 条件表达式

- **表达式 vs JSON API**：表达式适合前端搜索框、低代码平台等用户输入场景；JSON API（`/api/query`）适合程序构造，支持全量操作符（含 `regex` / `not_regex`，表达式不支持）
- **NOT 语义不是简单取反**：`NOT` 对复合条件做德摩根展开（`NOT (A AND B)` → `NOT A OR NOT B`），对叶子条件翻转操作符（`=` → `!=`，`LIKE` → `NOT LIKE`，`EXISTS` → `NOT EXISTS`，`prefix` → `not_prefix` 等）
- **通配符性能**：`PREFIX LIKE` 是前缀通配（可利用倒排索引，快）；`SUFFIX LIKE` 是后缀通配（`*value`，慢）；`LIKE` 是双向通配（`*value*`，最慢）。能用 `PREFIX LIKE` 就不用 `SUFFIX LIKE` / `LIKE`
- **字段映射标签要有区分度**：label 在解析前做字符串替换，按长度降序匹配。标签之间不应存在子串重叠（如 `订单` 和 `订单ID` 同时配置时，`订单ID = 'xxx'` 中的 `订单` 先被替换会导致错误）
- **先校验再执行**：用户输入的表达式先调 `/api/expression/validate`，通过后再调 `/api/query/expression`，避免语法错误导致 400
- **用 `/api/expression/hints` 做自动补全**：前端搜索框集成 hints 接口获取字段列表、运算符、时间范围关键字，引导用户正确输入

---

## 许可证

Apache License 2.0
