# Simple Elasticsearch Search Starter

> 一个零代码侵入的 Elasticsearch Search 框架，提供开箱即用的 RESTful API 进行灵活的数据查询和聚合。

## 特性

- **零代码侵入**：完全配置驱动，无需编写任何业务代码
- **多数据源路由**：支持多个 Elasticsearch 集群，自动路由到对应数据源
- **版本兼容性**：支持 ES 6.x 和 7.x+ 版本，自动适配 API 差异
  - **ES 6.x 聚合兼容**：自动检测并使用低级 API + 手动 JSON 解析，完美支持 ES 6.x 聚合查询
  - **可选 rawResponse**：提供原始 ES 聚合响应，支持零侵入迁移（从 RestTemplate/TransportClient 迁移）
- **动态 Mapping**：自动获取并缓存索引的字段元数据
- **灵活查询**：支持多种查询操作符和复杂的逻辑组合
- **聚合分析**：支持指标聚合和桶聚合，支持嵌套聚合
- **敏感字段保护**：支持字段禁止访问和脱敏
- **深分页支持**：自动切换 offset 和 search_after 分页策略
- **日期分割索引**：支持按日期分割的索引（如 `log-2025.01.01`）
- **索引路由智能降级**：自动处理大范围日期查询的 HTTP 请求行过长问题（v1.0.10+）
- **自然语言查询**：支持将中文自然语言直接转换为 Elasticsearch DSL（v1.1.0+）
  - **时间范围支持**：支持从自然语言中解析时间范围条件（v1.1.1+）
  - **search_after 深度分页**：支持从自然语言中解析 search_after 游标值（v1.1.2+）
- **RESTful API**：提供标准的 REST 接口

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.1.2'

    // 需要自行引入以下依赖
    implementation "org.springframework.boot:spring-boot-starter-data-elasticsearch"
    implementation "org.apache.httpcomponents:httpclient"
    implementation "org.apache.httpcomponents:httpcore"
    implementation "org.springframework.boot:spring-boot-starter-web"
    annotationProcessor "org.springframework.boot:spring-boot-configuration-processor"
}
```

**注意**：
- 从 1.0.2 版本开始，search-starter 完全依赖 `simple-elasticsearch-route-starter` 进行多数据源管理
- route-starter 会自动被依赖引入（版本 1.0.3+），无需手动添加
- 部分依赖使用 compileOnly，需要用户自行引入上述依赖
- **Spring Boot 版本兼容性**（1.0.4+）：
  - 推荐使用 Spring Boot 2.7.x 或 3.x
  - Spring Boot 2.4.x 单数据源可正常使用（自动降级）
  - Spring Boot 2.4.x 多数据源需升级到 2.7.x+（详见 CHANGELOG.1.0.4.md）

### 2. 配置

#### 基础配置（单数据源）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          # route-starter 配置
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

          # search-starter 配置
          search:
            enable: true

            # 索引配置
            indices:
              - name: "user_behavior_*"          # 索引名称或匹配模式
                alias: user_behavior              # 索引别名（API 中使用）
                date-split: true                  # 是否按日期分割
                date-pattern: "yyyy.MM.dd"        # 日期格式（支持 yyyy, yyyy.MM, yyyy.MM.dd 等）
                date-field: "createTime"          # 日期字段 可选，用于自动添加 DSL 时间过滤
                lazy-load: false                  # 是否延迟加载 mapping
                cache-mapping: true               # 是否缓存 mapping

                # 敏感字段配置
                sensitive-fields:
                  - field: "userId"
                    strategy: "MASK"              # 脱敏策略: MASK / FORBIDDEN
                    mask-start: 3                 # 保留前3位
                    mask-end: 4                   # 保留后4位
                    mask-pattern: "****"          # 脱敏字符

                  - field: "password"
                    strategy: "FORBIDDEN"         # 禁止访问

            # Mapping 刷新配置
            mapping-refresh:
              enabled: true                       # 启用定时刷新
              interval-seconds: 300              # 刷新间隔（秒），默认300秒

            # 索引路由降级配置（v1.0.10+）
            downgrade:
              enabled: true                       # 启用降级功能（默认：true）
              max-http-line-length: 4096         # HTTP 请求行最大长度（字节，默认：4096）
              max-level: 3                       # 最大降级级别（0-3，默认：3）
              enable-estimate: true              # 是否启用预估触发（默认：true）
              auto-downgrade-index-count-threshold: 200  # 索引数量阈值（默认：200）

            # 查询限制配置
            query-limits:
              max-size: 10000                    # 单次查询最大返回数量
              default-size: 20                   # 默认分页大小
              max-offset: 10000                  # from + size 的最大值（超过此值强制使用 search_after）
              ignore-unavailable-indices: false  # 是否忽略不存在的索引（日期分割索引推荐开启）

            # API 配置
            api:
              enabled: true                       # 启用 REST API
              base-path: "/api"                   # API 基础路径
              include-score: false                # 是否返回 _score（评分）
              include-raw-response: false         # 是否返回原始聚合响应（仅 ES 6.x 聚合场景）
```

#### 多数据源配置（支持版本兼容）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          # route-starter 配置（多数据源）
          route:
            enable: true
            default-source: primary
            sources:
              # 主数据源（ES 7.x）
              primary:
                urls: http://localhost:9200
                connect-timeout: 5000
                socket-timeout: 60000

              # 次数据源（ES 6.2.2）
              legacy:
                urls: http://legacy-es:9200
                server-version: 6.2.2           # 指定服务端版本（推荐配置）
                connect-timeout: 5000
                socket-timeout: 60000

            # 路由规则
            rules:
              - pattern: "old_*"                 # 匹配 old_ 开头的索引
                datasource: legacy
                type: prefix
                priority: 10

          # search-starter 配置
          search:
            enable: true
            indices:
              - name: "user_behavior_*"
                alias: user_behavior
                # 默认路由到 primary 数据源

              - name: "old_logs_*"
                alias: old_logs
                # 通过路由规则自动路由到 legacy 数据源
```

**版本兼容性说明**：
- 配置 `server-version` 后，框架会针对不同 ES 版本自动适配 API 差异
- ES 6.x：使用低级 RestClient API 绕过参数兼容性问题
- ES 7.x+：使用标准的高级 API
- 未配置版本时，框架会异步检测并自动适配

### 3. 使用 API

#### 查询数据

```bash
POST /api/query
Content-Type: application/json

{
  "index": "user_behavior",
  "query": {
    "logic": "and",
    "conditions": [
      {
        "field": "age",
        "operator": "gte",
        "value": 18
      },
      {
        "field": "city",
        "operator": "in",
        "values": ["Beijing", "Shanghai"]
      }
    ]
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

#### 聚合查询

```bash
POST /api/agg
Content-Type: application/json

{
  "index": "user_behavior",
  "query": {
    "field": "status",
    "operator": "eq",
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

#### 获取索引列表

```bash
GET /api/indices
```

#### 获取字段信息

```bash
GET /api/indices/user_behavior/fields
```

#### 刷新 Mapping

```bash
# 刷新所有索引
POST /api/indices/refresh

# 刷新指定索引
POST /api/indices/user_behavior/refresh
```

#### 自然语言转 DSL（v1.1.0+）

将中文自然语言查询转换为标准的 QueryRequest 或 AggRequest 对象：

**查询示例：**

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
        "values": ["Beijing", "Shanghai"]
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

**聚合示例：**

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

**时间范围查询示例（v1.1.1+）：**

```bash
GET /api/nl/dsl?text=查询user_behavior索引，status等于active，时间范围2025-01-01到2025-12-31，按createTime降序
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
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2025-12-31T00:00:00"
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

**search_after 深度分页示例（v1.1.2+）：**

```bash
GET /api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，接着[1704110400000,user_123,doc_456]继续查询，返回500条
```

响应：

```json
{
  "index": "app_access_log-*",
  "query": {
    "field": "clientIP",
    "op": "eq",
    "value": "192.168.1.1"
  },
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2026-01-01T00:00:00"
  },
  "pagination": {
    "type": "search_after",
    "searchAfter": [1704110400000, "user_123", "doc_456"],
    "size": 500,
    "sort": [
      {
        "field": "timestamp",
        "order": "desc"
      }
    ]
  }
}
```

**与其他 API 配合使用：**

生成的 DSL 对象可以直接传递给 `/api/query` 或 `/api/agg` 执行查询：

```javascript
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

**注意事项：**
- 自然语言中的字段名需要与索引中的实际字段名一致
- 必须通过自然语言或 `index` 参数指定索引名
- 未指定分页时自动应用 `query-limits.default-size`（默认 20 条）
- **时间范围支持（v1.1.1+）**：支持"时间范围2025-01-01到2025-12-31"等语法
- **search_after 深度分页（v1.1.2+）**：支持"接着[value1,value2]继续查询"等语法
- 详细语法和示例请参考 [CHANGELOG.1.1.0.md](CHANGELOG.1.1.0.md)、[CHANGELOG.1.1.1.md](CHANGELOG.1.1.1.md) 和 [CHANGELOG.1.1.2.md](CHANGELOG.1.1.2.md)

## DSL 最佳实践

以下示例展示了如何使用自然语言查询 + search_after 深度分页处理复杂的日志查询场景，涵盖了条件查询、时间范围过滤、排序和深度分页等核心功能。

### 场景：大规模日志深度分页查询

**需求**：查询某个客户端的访问日志，时间范围为一整年，按时间戳降序，使用 search_after 进行深度分页，每次返回 500 条。

**步骤 1：生成第一页查询 DSL**

```bash
GET /api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，返回500条
```

响应（自动生成的 DSL）：

```json
{
  "index": "app_access_log-*",
  "query": {
    "field": "clientIP",
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
        "field": "timestamp",
        "order": "desc"
      }
    ]
  }
}
```

**步骤 2：执行第一页查询**

将生成的 DSL 发送给 `/api/query` 执行：

```javascript
const dsl = await fetch('/api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，返回500条')
  .then(res => res.json());

const result = await fetch('/api/query', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(dsl)
}).then(res => res.json());

// 响应数据中包含 search_after 值
const lastHit = result.data.hits[result.data.hits.length - 1];
const searchAfterValue = lastHit.sort; // 例如：[1704110400000, "user_123", "doc_456"]
```

**步骤 3：生成第二页查询 DSL（使用 search_after）**

使用上一页的最后一条记录的 `sort` 值作为 searchAfter 参数：

```bash
GET /api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，接着[1704110400000,user_123,doc_456]继续查询，返回500条
```

响应（DSL 自动切换到 search_after 分页）：

```json
{
  "index": "app_access_log-*",
  "query": {
    "field": "clientIP",
    "op": "eq",
    "value": "192.168.1.1"
  },
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2026-01-01T00:00:00"
  },
  "pagination": {
    "type": "search_after",
    "searchAfter": [1704110400000, "user_123", "doc_456"],
    "size": 500,
    "sort": [
      {
        "field": "timestamp",
        "order": "desc"
      }
    ]
  }
}
```

**步骤 4：执行第二页查询**

```javascript
const nextPageDsl = await fetch('/api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，接着[1704110400000,user_123,doc_456]继续查询，返回500条')
  .then(res => res.json());

const nextResult = await fetch('/api/query', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(nextPageDsl)
}).then(res => res.json());

// 继续获取下一页的 search_after 值...
```

### 关键特性说明

1. **条件查询**：`clientIP等于192.168.1.1` → 精确匹配过滤
2. **时间范围过滤**：`时间范围2025-01-01到2026-01-01` → 自动转换为 `dateRange` 字段
3. **日期分割索引路由**：`app_access_log-*` → 框架根据 `dateRange` 自动计算需要查询的具体索引
4. **排序**：`按timestamp降序` → 确保结果按时间倒序排列
5. **深度分页**：`接着[value1,value2,value3]继续查询` → 使用 search_after 避免 Elasticsearch 10000 条的 `max_result_window` 限制
6. **多值 searchAfter**：支持多个排序字段的游标值（timestamp、user_id、doc_id 等）

### 适用场景

- ✅ 日志导出（需要遍历大量数据）
- ✅ 数据迁移（跨集群同步）
- ✅ 安全审计（长时间跨度的用户行为分析）
- ✅ 实时数据流（持续获取新增数据）

### 性能优化建议

- 使用 `search_after` 替代 `offset` 进行深度分页（offset > 10000 时强制切换）
- 排序字段建议包含唯一标识字段（如 `_id`）以保证结果稳定性
- 大范围时间查询时，框架自动启用索引路由降级策略（v1.0.10+）
- 配置 `date-field` 后，框架自动在 DSL 中添加时间过滤以提高查询精度

## 查询操作符

| 操作符 | 说明 | 示例 |
|--------|------|------|
| `eq` | 等于 | `{"field": "status", "operator": "eq", "value": "active"}` |
| `ne` | 不等于 | `{"field": "status", "operator": "ne", "value": "deleted"}` |
| `gt` | 大于 | `{"field": "age", "operator": "gt", "value": 18}` |
| `gte` | 大于等于 | `{"field": "age", "operator": "gte", "value": 18}` |
| `lt` | 小于 | `{"field": "score", "operator": "lt", "value": 60}` |
| `lte` | 小于等于 | `{"field": "score", "operator": "lte", "value": 60}` |
| `in` | 在列表中 | `{"field": "city", "operator": "in", "values": ["Beijing", "Shanghai"]}` |
| `not_in` | 不在列表中 | `{"field": "status", "operator": "not_in", "values": ["deleted", "banned"]}` |
| `between` | 在范围内 | `{"field": "age", "operator": "between", "values": [18, 65]}` |
| `like` | 模糊匹配 | `{"field": "username", "operator": "like", "value": "admin"}` |
| `prefix` | 前缀匹配 | `{"field": "email", "operator": "prefix", "value": "admin"}` |
| `suffix` | 后缀匹配 | `{"field": "email", "operator": "suffix", "value": "@gmail.com"}` |
| `exists` | 字段存在 | `{"field": "phone", "operator": "exists"}` |
| `not_exists` | 字段不存在 | `{"field": "deletedAt", "operator": "not_exists"}` |
| `is_null` | 字段为空 | `{"field": "remark", "operator": "is_null"}` |
| `is_not_null` | 字段不为空 | `{"field": "userId", "operator": "is_not_null"}` |
| `regex` | 正则匹配 | `{"field": "phone", "operator": "regex", "value": "1[3-9]\\d{9}"}` |

## 聚合类型

### 指标聚合

- `sum` - 求和
- `avg` - 平均值
- `min` - 最小值
- `max` - 最大值
- `count` - 计数（使用 value_count）
- `cardinality` - 基数（去重计数）
- `stats` - 统计（包含 count、min、max、avg、sum）

### 桶聚合

- `terms` - 分组聚合
- `date_histogram` - 日期直方图
- `histogram` - 数值直方图
- `range` - 范围聚合

## 响应格式

### 成功响应（HTTP 200）

```json
{
  "data": {
    // 响应数据，如查询结果、聚合结果等
  }
}
```

### 业务错误（HTTP 400）

```json
{
  "error": "参数错误信息"
}
```

### 服务器错误（HTTP 500）

```json
{
  "error": "服务器内部错误"
}
```

## 高级特性

### ES 6.x 聚合兼容性

**背景**：
在使用 Spring Boot 2.4.x（ES 6.x 客户端）查询 ES 6.x 集群时，由于 ES 7.x 客户端的 `NamedXContentRegistry` 无法正确解析 ES 6.x 的聚合响应格式，会导致聚合查询失败。

**解决方案**：
SDK 自动检测 ES 6.x 聚合响应，使用低级 API + 手动 JSON 解析，完美支持 ES 6.x 聚合查询。

**特性**：
- **自动检测**：无需配置，SDK 自动识别 ES 6.x 聚合响应
- **手动解析**：使用 Jackson 手动解析聚合数据，避免 NamedXContentRegistry 问题
- **格式统一**：自动提取嵌套值（如 `{"value": 123.0}` → `123.0`），保持与 ES 7.x+ 的响应格式一致
- **零侵入**：业务代码无需任何修改

### rawResponse 支持（零侵入迁移）

**背景**：
从 Spring Boot 2.4.x + RestTemplate/TransportClient 迁移到 SDK 时，现有业务代码可能直接解析 ES 原始响应格式（如 `{"avg_price": {"value": 6439.0}}`）。

**解决方案**：
SDK 提供可选的 `rawResponse` 字段，包含未经解析的原始聚合数据，让用户可以：
1. **零侵入迁移**：业务代码无需修改，直接使用 `rawResponse`
2. **数据对比**：对比 `aggregations`（解析后）和 `rawResponse`（原始），验证数据正确性
3. **自主选择**：根据需要选择使用解析后的统一格式或原始 ES 格式

**配置示例**：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            api:
              include-raw-response: true  # 启用原始响应（默认 false）
```

**响应格式对比**：

**未启用**（默认）：
```json
{
  "data": {
    "aggregations": {
      "avg_price": 6439.0,
      "total_stock": 550.0
    },
    "took": 15
  }
}
```

**已启用**：
```json
{
  "data": {
    "aggregations": {
      "avg_price": 6439.0,
      "total_stock": 550.0
    },
    "rawResponse": {
      "avg_price": {"value": 6439.0},
      "total_stock": {"value": 550.0}
    },
    "took": 15
  }
}
```

**使用场景**：
- ✅ 从旧版 ES 客户端迁移，需要兼容现有 JSON 解析逻辑
- ✅ 需要对比验证 SDK 解析结果的正确性
- ❌ 新项目，推荐直接使用解析后的统一格式（`aggregations`）

**注意事项**：
- `rawResponse` 仅在 **ES 6.x 聚合场景**下有值
- ES 7.x+ 聚合查询，`rawResponse` 为 `null`（不会序列化）
- 非聚合查询（普通 query），`rawResponse` 为 `null`

### 日期分割索引查询

框架支持按 **年、月、日** 三种粒度的日期分割索引：

**支持的日期格式：**
- `yyyy` - 按年分割（如 `log_2025`）
- `yyyy.MM` 或 `yyyy-MM` - 按月分割（如 `log_2025.01`）
- `yyyy.MM.dd` 或 `yyyy-MM-dd` - 按天分割（如 `log_2025.01.15`）

**配置示例：**

```yaml
# 按月分割
indices:
  - name: "monthly_log_*"
    alias: monthly_log
    date-split: true
    date-pattern: "yyyy.MM"        # 按月分割
    date-field: "timestamp"        # 可选，配置后自动添加 DSL 时间过滤

# 按年分割
  - name: "yearly_archive_*"
    alias: yearly_archive
    date-split: true
    date-pattern: "yyyy"            # 按年分割
    date-field: "created_at"       # 可选，配置后自动添加 DSL 时间过滤
```

**查询示例：**

对于按日期分割的索引（如 `user_behavior_2025.12.17`），可以使用 `dateRange` 参数进行精确查询：

```json
{
  "index": "user_behavior",
  "dateRange": {
    "from": "2025-12-17T00:00:00",
    "to": "2025-12-18T23:59:59"
  },
  "query": {
    "field": "action",
    "operator": "eq",
    "value": "login"
  }
}
```

说明：
- `dateRange` 用于索引路由，框架会根据 `date-pattern` 自动判断分割粒度
- **按年分割**：查询 2023-2025 年数据，生成 `log_2023`, `log_2024`, `log_2025`
- **按月分割**：查询 2025-01 到 2025-03，生成 `log_2025.01`, `log_2025.02`, `log_2025.03`
- **按天分割**：查询 3 天数据，生成对应的 3 个索引
- 如果配置了 `date-field`，框架会自动在 DSL 中添加时间范围过滤
- 日期格式支持 ISO 格式（`2025-12-17T00:00:00`）和纯日期格式（`2025-12-17`）

### 索引路由智能降级（v1.0.10+）

**背景问题：**
查询大范围日期分割索引时（如查询一年365天的日志），索引名过多会导致 HTTP 请求行超过 Elasticsearch 默认的 4096 字节限制，触发 `too_long_frame_exception` 异常。

**解决方案：**
框架采用多级智能降级策略，自动将具体索引名转换为通配符模式，减少 HTTP 请求行长度。

**降级策略：**

| 降级级别 | 日粒度索引示例 | 月粒度索引示例 | 年粒度索引示例 |
|---------|-------------|-------------|-------------|
| LEVEL_0 | `log_2025.01.01` | `log_2025.01` | `log_2025` |
| LEVEL_1 | `log_2025.01.*` | `log_2025.*` | `log_*` |
| LEVEL_2 | `log_2025.*` | `log_*` | `log_*` |
| LEVEL_3 | `log_*` | `log_*` | `log_*` |

**触发方式：**
1. **预估触发**（默认启用）：查询前预估索引数量和请求行长度，如果超过阈值，自动使用合适的降级级别
2. **异常触发**（兜底）：捕获 `too_long_frame_exception` 异常，自动降级重试

**配置示例：**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            downgrade:
              enabled: true                       # 启用降级（默认 true）
              max-http-line-length: 4096         # HTTP 请求行限制（默认 4096）
              max-level: 3                       # 最大降级级别（默认 3）
              enable-estimate: true              # 启用预估触发（默认 true）
              auto-downgrade-index-count-threshold: 200  # 索引数量阈值（默认 200）
```

**使用示例：**

```json
{
  "index": "daily_log",
  "dateRange": {
    "from": "2025-01-01",
    "to": "2025-12-31"
  },
  "query": {
    "field": "level",
    "operator": "eq",
    "value": "ERROR"
  }
}
```

**执行流程：**
1. 初始生成 365 个具体索引（超过阈值200，且HTTP请求行 > 4096字节）
2. 自动降级到 LEVEL_1：生成 12 个月级通配符（`log_2025.01.*`, ..., `log_2025.12.*`）
3. 查询成功

**注意事项：**
- 降级级别越高，查询精度越低，可能查询到范围外的索引
- 建议在查询条件中添加时间过滤以保证精度
- 通过日志可以监控降级触发情况

### 复杂查询条件

支持嵌套的逻辑组合（AND/OR）：

```json
{
  "logic": "and",
  "conditions": [
    {
      "field": "status",
      "operator": "eq",
      "value": "active"
    },
    {
      "logic": "or",
      "conditions": [
        {
          "field": "city",
          "operator": "eq",
          "value": "Beijing"
        },
        {
          "field": "city",
          "operator": "eq",
          "value": "Shanghai"
        }
      ]
    }
  ]
}
```

### 嵌套聚合

```json
{
  "name": "city_distribution",
  "type": "terms",
  "field": "city",
  "aggs": [
    {
      "name": "gender_distribution",
      "type": "terms",
      "field": "gender",
      "aggs": [
        {
          "name": "avg_age",
          "type": "avg",
          "field": "age"
        }
      ]
    }
  ]
}
```

### 深分页优化

当 offset 超过配置的最大值时，自动切换到 search_after 分页：

```json
{
  "pagination": {
    "type": "search_after",
    "size": 20,
    "searchAfter": [1640000000000, "doc_id_123"],
    "sort": [
      {
        "field": "createTime",
        "order": "desc"
      }
    ]
  }
}
```

## 许可证

Apache License 2.0
