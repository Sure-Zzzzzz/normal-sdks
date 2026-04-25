# Simple Elasticsearch Search Starter

> 零代码侵入的 Elasticsearch 查询框架，完全配置驱动，开箱即用。

## 特性

| 特性 | 说明 | 版本 |
|------|------|------|
| 零代码侵入 | 完全配置驱动，无需编写任何业务代码 | v1.0.0+ |
| 多数据源路由 | 支持多个 ES 集群，自动路由 | v1.0.2+ |
| 版本兼容 | 支持 ES 6.x 和 7.x+，Spring Boot 2.2.5+ | v1.5.4+ |
| 动态 Mapping | 自动获取并缓存索引字段元数据 | v1.0.0+ |
| 灵活查询 | 支持 18 种操作符和嵌套逻辑组合 | v1.0.0+ |
| 条件表达式查询 | 类 SQL 表达式直接查询，支持中文字段名映射 | v1.5.2+ |
| 表达式提示 | 前端自动补全数据源（字段、运算符、时间范围） | v1.5.3+ |
| Pipeline 聚合 | bucket_sort Top N、bucket_selector HAVING（ES 6.1+） | v1.5.5+ |
| 表达式聚合 | 条件表达式作为聚合过滤条件 | v1.5.5+ |
| 聚合分析 | 支持指标聚合、桶聚合、嵌套聚合 | v1.0.0+ |
| composite 聚合翻页 | 突破 65535 条限制，支持全量遍历（ES 6.1+） | v1.4.0+ |
| 深分页 | search_after 多种模式（tiebreaker/pit/none） | v1.3.0+ |
| 日期分割索引 | 按年/月/日分割，自动路由 + 智能降级 | v1.0.0+ |
| 敏感字段保护 | 支持字段禁止访问和脱敏 | v1.0.0+ |
| 自然语言查询 | 中文自然语言转 ES DSL | v1.1.0+ |
| 查询事件发布 | 查询/聚合后发布 Spring 事件，支持审计扩展 | v1.2.0+ |

---

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.5'

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
| `type` | `offset` 或 `search_after` |
| `page` | 页码（offset 模式） |
| `size` | 每页数量 |
| `sort` | 排序字段列表 |
| `searchAfter` | 上一页游标（search_after 模式） |
| `searchAfterMode` | `tiebreaker`（默认）/ `pit` / `none` |
| `pitKeepAlive` | PIT 保活时间（pit 模式，如 `1m`） |
| `pitId` | PIT ID（pit 模式，首页不传） |

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

### GET /api/expression/validate — 校验表达式语法（v1.5.2+）

```bash
GET /api/expression/validate?expression=status = "paid" AND amount >= 100
```

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
| `suffix` | 后缀匹配 | `value` |
| `regex` | 正则匹配 | `value` |
| `exists` | 字段存在 | — |
| `not_exists` | 字段不存在 | — |
| `is_null` | 字段为空 | — |
| `is_not_null` | 字段不为空 | — |

---

## 聚合类型

### 指标聚合（Metrics）

`sum` / `avg` / `min` / `max` / `count` / `cardinality` / `stats` / `extended_stats` / `percentiles` / `percentile_ranks`

### 桶聚合（Bucket）

| 类型 | 说明 | 支持 composite |
|------|------|---------------|
| `terms` | 分组聚合 | ✅ |
| `date_histogram` | 日期直方图 | ✅ |
| `histogram` | 数值直方图 | ✅ |
| `range` | 范围聚合 | ❌ |

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

| 语法 | 示例 |
|------|------|
| 等于 / 不等于 | `status = "paid"` / `status != "failed"` |
| 比较 | `amount >= 100` |
| 范围 | `amount BETWEEN 10 AND 100` |
| IN / NOT IN | `status IN ("paid", "refunded")` |
| LIKE / NOT LIKE | `name LIKE "张%"` |
| IS NULL / IS NOT NULL | `remark IS NULL` |
| 时间范围 | `create_time = 最近7天` |
| 逻辑组合 | `A AND B` / `A OR B` / `NOT A` |
| 括号分组 | `(A OR B) AND C` |

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
  - ✅ `status = 'paid'` / `name LIKE '测试'` / `时间 = '近7天'` / `amount >= 100`
  - ❌ `status = paid`（不加引号会被识别为字段名）
  - 不支持中文引号 `""`

---

## ES 6.x 兼容性

- 自动检测 ES 版本，6.x 使用低级 API 绕过参数兼容性问题
- 聚合响应自动用 Jackson 手动解析，格式与 7.x+ 保持一致
- composite 聚合自动移除 7.x client 序列化的 `missing_bucket` 字段，保证 ES 6.1+ 可用
- 可选 `include-raw-response: true` 返回原始聚合 JSON，支持零侵入迁移
- v1.5.4+ 支持 Spring Boot 2.2.5 / ES 6.8.x，PIT 分页在 6.x 服务端下自动禁用
- pipeline 聚合（bucket_sort / bucket_selector）兼容 ES 6.1+，无需额外配置

---

## 许可证

Apache License 2.0
