# Simple Elasticsearch Search Core

## 概述

`simple-elasticsearch-search-core` 是 Elasticsearch 搜索 SDK 的核心包，提供数据模型、枚举常量和事件定义。

该包不包含任何实现逻辑，仅定义数据结构，用于解耦核心定义与具体实现。

## 版本

- 当前版本：`1.0.2`
- 依赖方：`simple-elasticsearch-search-starter:1.3.0+`

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


## 注意事项

1. 该包不应被业务直接依赖，由 `simple-elasticsearch-search-starter` 传递依赖
2. 事件监听使用 Spring `@EventListener` 注解

