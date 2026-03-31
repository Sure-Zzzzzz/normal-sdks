# Changelog - v1.0.1

## 发布日期

2026-03-31

## 版本类型

**Patch Release** - 架构调整，向后兼容

## 变更概述

本版本调整了审计扩展机制的架构设计，从拦截器模式改为 Spring 事件模式，简化了扩展点设计，使审计功能更加解耦。

## 架构变更

### 删除拦截器接口

- ❌ 删除 `QueryExecutionInterceptor` 接口
- ❌ 删除 `AggExecutionInterceptor` 接口

**原因：** 改用 Spring 事件机制，简化扩展点设计。

### 新增事件定义

- ✅ 新增 `EsQueryEvent` - ES 查询事件
- ✅ 新增 `EsAggEvent` - ES 聚合事件

**事件包路径：**
```
io.github.surezzzzzz.sdk.elasticsearch.search.core.event
├── EsQueryEvent.java
└── EsAggEvent.java
```

## 新增功能

### 1. EsQueryEvent（查询事件）

```java
@Getter
public class EsQueryEvent extends ApplicationEvent {
    private final QueryRequest request;
    private final QueryResponse response;
    private final QueryExecutionContext context;
    private final Long timestamp;
}
```

**包含信息：**
- 查询请求（QueryRequest）
- 查询响应（QueryResponse）
- 执行上下文（QueryExecutionContext）：实际索引、数据源
- 时间戳

### 2. EsAggEvent（聚合事件）

```java
@Getter
public class EsAggEvent extends ApplicationEvent {
    private final AggRequest request;
    private final AggResponse response;
    private final AggExecutionContext context;
    private final Long timestamp;
}
```

**包含信息：**
- 聚合请求（AggRequest）
- 聚合响应（AggResponse）
- 执行上下文（AggExecutionContext）：实际索引、数据源
- 时间戳

## 依赖变更

### 新增依赖

```gradle
compileOnly "org.springframework:spring-context"
```

用于 `ApplicationEvent` 基类。

### 无变更

- `spring-boot-starter-web`（compileOnly）
- `lombok`（compileOnly）

## 向后兼容性

⚠️ **不兼容变更**

如果业务代码直接实现了 `QueryExecutionInterceptor` 或 `AggExecutionInterceptor` 接口，需要调整为监听 Spring 事件。

**迁移方式：**

**旧方式（1.0.0）：**
```java
@Component
public class MyInterceptor implements QueryExecutionInterceptor {
    @Override
    public void afterQueryExecuted(QueryRequest request, QueryResponse response, QueryExecutionContext context) {
        // 处理逻辑
    }
}
```

**新方式（1.0.1）：**
```java
@Component
public class MyListener {
    @EventListener
    public void onEsQueryEvent(EsQueryEvent event) {
        QueryRequest request = event.getRequest();
        QueryResponse response = event.getResponse();
        QueryExecutionContext context = event.getContext();
        // 处理逻辑
    }
}
```

## 升级指南

### 从 1.0.0 升级到 1.0.1

1. **更新依赖版本**

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-core:1.0.1"
```

2. **检查是否实现了拦截器接口**

如果业务代码实现了 `QueryExecutionInterceptor` 或 `AggExecutionInterceptor`，需要改为监听事件（见上方迁移方式）。

3. **无需修改其他代码**

如果没有实现拦截器接口，无需任何修改。

## 设计理念

### 为什么改为事件模式？

使用 Spring 标准的事件机制，更符合 Spring 生态的设计理念，降低学习成本。

### 架构对比

**旧架构（1.0.0）：**
```
拦截器接口 → 业务实现拦截器
```

**新架构（1.0.1）：**
```
事件定义 → 业务监听事件
```

更简洁，更标准。

## 注意事项

1. **事件只是定义**：core 包只定义事件，不负责发布和监听
2. **业务需要监听器**：业务需要实现 `@EventListener` 来监听这些事件
3. **推荐使用 audit-listener**：可以引入 `simple-elasticsearch-audit-listener-starter` 来简化审计功能的实现

## 贡献者

- @surezzzzzz
