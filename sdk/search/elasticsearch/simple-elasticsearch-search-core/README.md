# Simple Elasticsearch Search Core

## 概述

`simple-elasticsearch-search-core` 是 Elasticsearch 搜索 SDK 的核心包，提供数据模型、枚举常量和事件定义。

该包不包含任何实现逻辑，仅定义数据结构，用于解耦核心定义与具体实现。

## 版本

- 当前版本：`1.0.4`
- 依赖方：`simple-elasticsearch-search-starter:1.4.0+`

## 包结构

```
io.github.surezzzzzz.sdk.elasticsearch.search
├── core
│   ├── event                # Spring 事件定义（v1.0.1+）
│   │   ├── EsQueryEvent.java
│   │   └── EsAggEvent.java
│   └── model                # 执行上下文
│       ├── QueryExecutionContext.java
│       └── AggExecutionContext.java
├── query
│   └── model                # 查询请求/响应模型
│       ├── QueryRequest.java
│       ├── QueryResponse.java
│       ├── QueryCondition.java
│       └── PaginationInfo.java
├── agg
│   └── model                # 聚合请求/响应模型
│       ├── AggRequest.java
│       ├── AggResponse.java
│       └── AggDefinition.java
└── constant                 # 常量枚举
    ├── QueryOperator.java
    ├── PaginationType.java
    ├── AggType.java
    └── SearchAfterMode.java  # v1.0.2+
```

## 核心模型

### AggDefinition

聚合定义，v1.0.4 新增 `composite` 和 `order` 字段，支持 composite 聚合翻页：

| 字段 | 说明 |
|------|------|
| `composite` | 是否使用 composite 聚合，仅对 terms、date_histogram、histogram 生效 |
| `order` | composite 排序方向（asc / desc），默认 asc |

### AggRequest

聚合请求，v1.0.4 新增 `after` 字段，用于传入翻页游标：

```json
{
  "after": {
    "all_users": {"userId": "user_1000"}
  }
}
```

### AggResponse

聚合响应，v1.0.4 新增 `afterKey` 字段，为 null 时表示已无更多数据：

```json
{
  "afterKey": {
    "all_users": {"userId": "user_1000"}
  }
}
```

### PaginationInfo

分页信息，支持 `offset` 和 `search_after` 两种分页类型。

`search_after` 模式下可通过 `searchAfterMode` 控制翻页策略（v1.0.2+）：

| `searchAfterMode` | 说明 |
|-------------------|------|
| `tiebreaker`（默认） | 自动追加 `_id ASC` 保证排序稳定，兼容旧行为 |
| `pit` | 使用 Point In Time 快照翻页，需要 ES 7.10+，不追加 `_id` |
| `none` | 不追加任何 tiebreaker，由调用方保证排序字段唯一性 |

### QueryResponse.PaginationResult

查询响应中的分页结果，`pit` 模式下 `hasMore=true` 时会返回 `pitId`，调用方需将其带入下一次请求。

## 依赖

该包仅依赖：
- `lombok`（编译时）
- `spring-boot-starter-web`（compileOnly）
- `spring-context`（compileOnly）

无运行时依赖，保持轻量。

## 版本历史

- `1.0.0`：初始版本，定义拦截器接口和数据模型
- `1.0.1`：架构调整，改为 Spring 事件模式，新增 `EsQueryEvent` / `EsAggEvent`
- `1.0.2`：新增 `SearchAfterMode` 枚举，`PaginationInfo` 支持 PIT 翻页模式
- `1.0.3`：`SearchAfterMode` 枚举规范化，字段名改为 `code`/`description`，新增 `isValid()`/`getAllCodes()`，`fromString` 改为 `fromCode`
- `1.0.4`：`AggDefinition` 新增 `composite`/`order`，`AggRequest` 新增 `after`，`AggResponse` 新增 `afterKey`，支持 composite 聚合翻页


## 注意事项

1. 该包不应被业务直接依赖，由 `simple-elasticsearch-search-starter` 传递依赖
2. 事件监听使用 Spring `@EventListener` 注解

