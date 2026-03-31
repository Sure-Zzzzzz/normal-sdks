# Simple Elasticsearch Search Core

## 概述

`simple-elasticsearch-search-core` 是 Elasticsearch 搜索 SDK 的核心包，提供拦截器接口、数据模型和常量定义。

该包不包含任何实现逻辑，仅定义接口和数据结构，用于解耦核心定义与具体实现。

## 版本

- 当前版本：`1.0.0`
- 依赖方：`simple-elasticsearch-search-starter:1.2.0+`

## 包结构

```
io.github.surezzzzzz.sdk.elasticsearch.search
├── core
│   ├── interceptor          # 拦截器接口
│   │   ├── QueryExecutionInterceptor.java
│   │   └── AggExecutionInterceptor.java
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
    └── AggType.java
```

## 核心接口

### QueryExecutionInterceptor

查询执行拦截器，在查询执行后触发。

```java
public interface QueryExecutionInterceptor {
    void afterQueryExecuted(QueryRequest request, QueryResponse response, QueryExecutionContext context);
}
```

### AggExecutionInterceptor

聚合执行拦截器，在聚合执行后触发。

```java
public interface AggExecutionInterceptor {
    void afterAggExecuted(AggRequest request, AggResponse response, AggExecutionContext context);
}
```

## 使用场景

### 1. 实现审计监听器

```java
@Component
public class ElasticsearchAuditInterceptor implements QueryExecutionInterceptor {

    @Override
    public void afterQueryExecuted(QueryRequest request, QueryResponse response, QueryExecutionContext context) {
        // 发布审计事件
        eventPublisher.publishEvent(new ElasticsearchQueryEvent(
            request.getIndex(),
            context.getActualIndices(),
            response.getTotal()
        ));
    }
}
```

### 2. 实现性能监控

```java
@Component
public class PerformanceMonitorInterceptor implements QueryExecutionInterceptor {

    @Override
    public void afterQueryExecuted(QueryRequest request, QueryResponse response, QueryExecutionContext context) {
        // 记录查询性能指标
        metrics.record("es.query.took", response.getTook());
        metrics.record("es.query.hits", response.getTotal());
    }
}
```

## 依赖

该包仅依赖：
- `lombok`（编译时）
- `spring-boot-starter-web`（compileOnly）

无其他运行时依赖，保持轻量。

## 版本兼容性

- `1.0.0`：初始版本，定义拦截器接口和数据模型
- 向后兼容：所有 import 路径保持不变，业务代码无需修改

## 注意事项

1. 该包不应被业务直接依赖，由 `simple-elasticsearch-search-starter` 传递依赖
2. 拦截器实现类应注册为 Spring Bean，会被自动注入到 Executor
3. 拦截器执行失败不会影响主流程，仅记录警告日志
