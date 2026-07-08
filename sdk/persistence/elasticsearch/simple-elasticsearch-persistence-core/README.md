# Simple Elasticsearch Persistence Core

## 概述

`simple-elasticsearch-persistence-core` 是 Elasticsearch 写操作 SDK 的核心包，提供数据模型、枚举常量、异常定义和 Spring 事件。

该包不包含任何实现逻辑，仅定义数据结构，用于解耦核心定义与具体实现。

## 版本

- 当前版本：`1.0.0`
- 依赖方：`simple-elasticsearch-persistence-starter:1.0.0+`

## 包结构

```
io.github.surezzzzzz.sdk.elasticsearch.persistence.core
├── constant                     # 常量与枚举
│   ├── SimpleElasticsearchPersistenceCoreConstant.java  # 公共常量
│   ├── ErrorCode.java           # 错误码
│   ├── ErrorMessage.java        # 错误信息
│   ├── BulkItemType.java        # Bulk 项类型枚举
│   ├── IndexOperationType.java  # 索引操作类型枚举
│   └── PersistenceOperationType.java  # 持久化操作类型枚举
├── exception
│   └── SimpleElasticsearchPersistenceException.java     # 基础异常
├── event                        # Spring 事件
│   ├── EsPersistenceEvent.java
│   └── EsPersistenceErrorEvent.java
└── model
    ├── PersistenceExecutionContext.java  # 执行上下文
    ├── option                       # 选项模型
    │   ├── WriteOptions.java        # 写入选项基类
    │   ├── IndexOptions.java        # 索引选项
    │   ├── UpdateOptions.java       # 更新选项
    │   ├── DeleteOptions.java       # 删除选项
    │   ├── BulkOptions.java         # Bulk 选项
    │   └── ByQueryOptions.java      # 按查询选项
    ├── query
    │   └── PersistenceQuery.java    # 查询条件
    ├── request                      # 请求模型
    │   ├── PersistenceRequest.java  # 请求基类
    │   ├── IndexRequest.java
    │   ├── UpdateRequest.java
    │   ├── DeleteRequest.java
    │   ├── BulkRequest.java
    │   ├── BulkItem.java
    │   ├── UpdateByQueryRequest.java
    │   └── DeleteByQueryRequest.java
    └── result                       # 结果模型
        ├── PersistenceResult.java
        ├── BulkResult.java
        ├── BulkItemFailure.java
        ├── ByQueryTaskResult.java
        └── ByQueryFailure.java
```

## 核心模型

### PersistenceExecutionContext

执行上下文，随 `EsPersistenceEvent` / `EsPersistenceErrorEvent` 发布。

| 字段 | 类型 | 说明 |
|------|------|------|
| `operationType` | `PersistenceOperationType` | 操作类型 |
| `index` | `String` | 目标索引 |
| `datasource` | `String` | 数据源 key |
| `clientAsync` | `boolean` | 客户端异步（CompletableFuture） |
| `routeAsyncWrite` | `boolean` | Route async-write（fire-and-forget） |
| `serverAsyncTask` | `boolean` | 服务端异步任务（wait_for_completion=false） |
| `taskId` | `String` | 服务端异步任务 ID |
| `startTimeMs` | `long` | 开始时间戳 |
| `tookMs` | `long` | 耗时（毫秒） |

### BulkItem

Bulk 批量项，支持混合操作类型。

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `BulkItemType` | 操作类型（INDEX/CREATE/UPDATE/DELETE） |
| `document` | `Object` | 文档对象（INDEX/CREATE/UPDATE 时使用） |
| `index` | `String` | 目标索引（空则用 BulkRequest.defaultIndex） |
| `id` | `String` | 文档 ID |
| `fieldMap` | `Map<String, Object>` | 更新字段（UPDATE 时使用） |
| `scriptSource` | `String` | Painless 脚本（UPDATE 时使用） |
| `scriptParamMap` | `Map<String, Object>` | 脚本参数 |
| `docAsUpsert` | `Boolean` | 不存在时是否插入 |
| `routing` | `String` | 路由值 |

### ByQueryOptions

按查询批量操作选项。

| 字段 | 类型 | 说明 |
|------|------|------|
| `waitForCompletion` | `Boolean` | false 时返回 taskId，服务端异步执行 |
| `refresh` | `Boolean` | 操作后是否刷新 |
| `timeoutMs` | `Long` | 超时（毫秒） |
| `batchSize` | `Integer` | 批大小 |
| `scrollSize` | `Integer` | 滚动大小 |
| `slices` | `Integer` | 并行切片数 |
| `conflicts` | `String` | 冲突策略（proceed / abort） |
| `routing` | `String` | 路由值 |

### PersistenceQuery

查询条件：

| 字段 | 类型 | 说明 |
|------|------|------|
| `rawJson` | `String` | 原始 JSON 查询 DSL（优先级最高，存在时忽略 term/range） |
| `termMap` | `Map<String, Object>` | term 查询条件 |
| `rangeMap` | `Map<String, Object>` | range 查询条件（gte 语义） |

`rawJson` 为空时，`termMap` 与 `rangeMap` 组合为 bool 查询；三者全空匹配 match_all。

### 枚举说明

**BulkItemType**：`INDEX`（upsert）/ `CREATE`（仅新增）/ `UPDATE`（局部更新）/ `DELETE`（按 ID 删除）

**IndexOperationType**：`INDEX`（upsert）/ `CREATE`（仅新增）

**PersistenceOperationType**：`INDEX` / `CREATE` / `UPDATE` / `DELETE` / `BULK` / `UPDATE_BY_QUERY` / `DELETE_BY_QUERY` / `GET_TASK`

各枚举均提供 `fromCode()` / `isValid()` / `getAllCodes()` / `toString()` 方法。

## 依赖

该包仅依赖：
- `lombok`（编译时）
- `spring-context`（compileOnly）

无运行时依赖，保持轻量。

## 版本历史

- `1.0.1`：`UpdateOptions` 新增 `upsertDoc`（兜底初始化文档）和 `scriptedUpsert`（scripted_upsert 语义），支持文档不存在时通过 Painless 脚本初始化字段
- `1.0.0`：初始版本，提供写操作核心模型、枚举常量、事件定义

## 注意事项

1. 该包不应被业务直接依赖，由 `simple-elasticsearch-persistence-starter` 传递依赖
2. 事件监听使用 Spring `@EventListener` 注解
3. 异常使用 `ErrorCode` + `ErrorMessage` 配合抛出，错误信息支持占位符格式化
