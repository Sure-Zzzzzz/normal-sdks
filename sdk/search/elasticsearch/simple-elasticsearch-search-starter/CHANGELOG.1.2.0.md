# Changelog - v1.2.0

## 发布日期

2026-03-31

## 版本类型

**Minor Release** - 向后兼容的功能增强

## 变更概述

本版本引入事件发布机制，在查询和聚合执行后自动发布 Spring 事件，支持业务监听事件实现审计、监控等功能。

## 新增功能

### 1. 查询事件发布

在查询执行成功后，自动发布 `EsQueryEvent` 事件。

**事件内容：**
- 查询请求（QueryRequest）
- 查询响应（QueryResponse）
- 执行上下文（QueryExecutionContext）：实际查询的物理索引、数据源
- 时间戳

### 2. 聚合事件发布

在聚合执行成功后，自动发布 `EsAggEvent` 事件。

**事件内容：**
- 聚合请求（AggRequest）
- 聚合响应（AggResponse）
- 执行上下文（AggExecutionContext）：实际查询的物理索引、数据源
- 时间戳

### 3. 事件发布异常处理

事件发布失败不会影响主流程，仅记录警告日志。

## 依赖变更

### 更新依赖

```gradle
api "simple-elasticsearch-search-core:1.0.1"  // 从 1.0.0 升级
```

**原因：** core 1.0.1 新增了事件定义（`EsQueryEvent`、`EsAggEvent`）。

### 无变更

- `simple-elasticsearch-route-starter:1.0.5`
- `natural-language-parser-starter:1.0.6`
- `log-truncate-starter:1.0.0`

## 向后兼容性

✅ **完全向后兼容**

- 所有 import 路径保持不变
- 现有业务代码无需修改
- 事件发布是新增功能，不影响现有逻辑

## 升级指南

### 从 1.1.3 升级到 1.2.0

1. **更新依赖版本**

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.2.0"
```

2. **无需修改代码**

现有代码无需任何修改，直接升级即可。

## 使用场景

### 1. 审计日志

记录谁在什么时间查询了哪些索引，返回了多少数据。

### 2. 性能监控

统计查询耗时、命中数、降级次数等指标。

### 3. 数据访问追踪

追踪敏感数据的访问情况。

### 4. 告警触发

当查询返回数据量超过阈值时触发告警。

## 事件监听示例

```java
@Component
public class MyQueryListener {

    @EventListener
    @Async  // 可选：异步处理
    public void onEsQueryEvent(EsQueryEvent event) {
        QueryRequest request = event.getRequest();
        QueryResponse response = event.getResponse();
        QueryExecutionContext context = event.getContext();

        log.info("Query executed: index={}, datasource={}, total={}, took={}ms",
            request.getIndex(),
            context.getDatasource(),
            response.getTotal(),
            response.getTook()
        );
    }
}
```

## 注意事项

1. **事件发布是同步的**：Spring 默认同步发布事件，如需异步处理，在监听器方法上添加 `@Async`
2. **事件监听器可以有多个**：多个 Bean 可以同时监听同一个事件
3. **事件发布失败不影响主流程**：捕获了事件发布异常，仅记录警告日志
4. **性能影响**：事件发布本身开销很小，但监听器应避免耗时操作

## 相关链接

- [simple-elasticsearch-search-core CHANGELOG 1.0.1](../simple-elasticsearch-search-core/CHANGELOG.1.0.1.md)

## 贡献者

- @surezzzzzz
