# Simple Elasticsearch Search Starter

> 一个零代码侵入的 Elasticsearch Search 框架，提供开箱即用的 RESTful API 进行灵活的数据查询和聚合。

## 特性

- **零代码侵入**：完全配置驱动，无需编写任何业务代码
- **多数据源路由**：支持多个 Elasticsearch 集群，自动路由到对应数据源
- **版本兼容性**：支持 ES 6.x 和 7.x+ 版本，自动适配 API 差异
- **动态 Mapping**：自动获取并缓存索引的字段元数据
- **灵活查询**：支持多种查询操作符和复杂的逻辑组合
- **聚合分析**：支持指标聚合和桶聚合，支持嵌套聚合
- **敏感字段保护**：支持字段禁止访问和脱敏
- **深分页支持**：自动切换 offset 和 search_after 分页策略
- **日期分割索引**：支持按日期分割的索引（如 `log-2025.01.01`）
- **RESTful API**：提供标准的 REST 接口

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.0.2'

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

            # 查询限制配置
            query-limits:
              max-size: 10000                    # 单次查询最大返回数量
              default-size: 20                   # 默认分页大小
              max-offset: 10000                  # from + size 的最大值（超过此值强制使用 search_after）

            # API 配置
            api:
              enabled: true                       # 启用 REST API
              base-path: "/api"                   # API 基础路径
              include-score: false                # 是否返回 _score（评分）
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
    date-field: "timestamp"

# 按年分割
  - name: "yearly_archive_*"
    alias: yearly_archive
    date-split: true
    date-pattern: "yyyy"            # 按年分割
    date-field: "created_at"
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
