# Simple Elasticsearch Search Core

## 概述

`simple-elasticsearch-search-core` 是 Elasticsearch 搜索 SDK 的核心包，提供数据模型、枚举常量和事件定义。

该包不包含任何实现逻辑，仅定义数据结构，用于解耦核心定义与具体实现。

## 版本

- 当前版本：`1.0.9`
- 依赖方：`simple-elasticsearch-search-starter:1.6.2+`

## 包结构

```
io.github.surezzzzzz.sdk.elasticsearch.search
├── core
│   ├── event                # Spring 事件定义（v1.0.1+）
│   │   ├── EsQueryEvent.java
│   │   ├── EsAggEvent.java
│   │   ├── EsQueryErrorEvent.java   # v1.0.9+
│   │   └── EsAggErrorEvent.java     # v1.0.9+
│   └── model                # 执行上下文
│       ├── ExecutionContext.java     # v1.0.9+ 基类
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
│       ├── AggDefinition.java
│       └── PipelineAggDefinition.java
└── constant                 # 常量枚举
    ├── QueryOperator.java
    ├── PaginationType.java
    ├── AggType.java
    ├── PipelineAggType.java
    └── SearchAfterMode.java
```

## 核心模型

### ExecutionContext（v1.0.9+）

查询/聚合执行上下文基类，随 `EsQueryEvent`/`EsAggEvent` 发布。`QueryExecutionContext` 和 `AggExecutionContext` 均继承此类。

| 字段 | 类型 | 说明 |
|------|------|------|
| `actualIndices` | `String[]` | 实际查询的物理索引 |
| `datasource` | `String` | 数据源 key |
| `downgradeLevel` | `int` | 降级级别（0 = 未降级） |
| `sourceType` | `String` | 来源类型（QUERY_API / NL_API / EXPRESSION_API） |

### EsQueryErrorEvent / EsAggErrorEvent（v1.0.9+）

查询/聚合执行失败时发布，仅在 executor 执行阶段失败时触发（不含端点层 400 校验失败）。

```java
@EventListener
public void onQueryError(EsQueryErrorEvent event) {
    log.error("查询失败: index={}, datasource={}, error={}",
        event.getRequest().getIndex(),
        event.getDatasource(),
        event.getError().getMessage());
}
```

### AggRequest

聚合请求，v1.0.9 新增 `dateRange` 字段，修复日期分割索引路由问题：

| 字段 | 说明 |
|------|------|
| `dateRange` | 日期范围，优先级高于从 query 条件推断，用于日期分割索引路由 |
| `after` | composite 聚合翻页游标（v1.0.4+） |

### AggDefinition

聚合定义，v1.0.4 新增 `composite` 和 `order` 字段，支持 composite 聚合翻页：

| 字段 | 说明 |
|------|------|
| `composite` | 是否使用 composite 聚合，仅对 terms、date_histogram、histogram 生效 |
| `order` | composite 排序方向（asc / desc），默认 asc |

### PaginationInfo

分页信息，支持 `offset`、`search_after`、`scroll` 三种分页类型。

`search_after` 模式下可通过 `searchAfterMode` 控制翻页策略（v1.0.2+）：

| `searchAfterMode` | 说明 |
|-------------------|------|
| `tiebreaker`（默认） | 自动追加 `_id ASC` 保证排序稳定 |
| `pit` | 使用 Point In Time 快照翻页，需要 ES 7.10+ |
| `none` | 不追加任何 tiebreaker，由调用方保证排序字段唯一性 |

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
- `1.0.3`：`SearchAfterMode` 枚举规范化，新增 `isValid()`/`getAllCodes()`/`fromCode`
- `1.0.4`：`AggDefinition` 新增 `composite`/`order`，`AggRequest` 新增 `after`，`AggResponse` 新增 `afterKey`
- `1.0.5` ~ `1.0.7`：兼容性修复
- `1.0.8`：`PaginationType` 新增 `SCROLL`，`PaginationInfo` 新增 `scrollId`/`scrollTtl`
- `1.0.9`：`ExecutionContext` 基类，Error Events，`AggRequest.dateRange` Bug Fix，`sourceType` 透传

## 注意事项

1. 该包不应被业务直接依赖，由 `simple-elasticsearch-search-starter` 传递依赖
2. 事件监听使用 Spring `@EventListener` 注解
3. `QueryRequest.sourceType` / `AggRequest.sourceType` 为内部字段，`@JsonIgnore`，不参与序列化

