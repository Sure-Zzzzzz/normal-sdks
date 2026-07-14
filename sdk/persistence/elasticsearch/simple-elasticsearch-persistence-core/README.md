# Simple Elasticsearch Persistence Core

## 概述

`simple-elasticsearch-persistence-core` 是 Elasticsearch 写操作 SDK 的核心包，提供数据模型、枚举常量、异常定义和 Spring 事件。

该包不包含任何实现逻辑，仅定义数据结构，用于解耦核心定义与具体实现。

## 版本

- 当前版本：`1.0.3`
- 依赖方：`simple-elasticsearch-persistence-starter:1.0.1+`；1.0.3 新增 by-query 执行参数字段由 starter 1.1.1 起落地执行语义

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

### WriteOptions

写入选项基类。

| 字段 | 类型 | 说明 |
|------|------|------|
| `refresh` | `Boolean` | 是否立即刷新，保留布尔刷新语义 |
| `routing` | `String` | 路由值 |
| `timeoutMs` | `Long` | 超时（毫秒） |
| `refreshPolicy` | `String` | ES refresh policy：`true` / `false` / `wait_for` |

### IndexOptions

索引选项。

| 字段 | 类型 | 说明 |
|------|------|------|
| `operationType` | `IndexOperationType` | 索引操作类型（INDEX/CREATE） |
| `pipeline` | `String` | 写入时使用的 ingest pipeline |

### UpdateOptions

更新选项。

| 字段 | 类型 | 说明 |
|------|------|------|
| `docAsUpsert` | `Boolean` | 文档不存在时是否将 doc 作为初始内容插入 |
| `fetchSource` | `Boolean` | 是否返回 source |
| `upsertDoc` | `Object` | 文档不存在时的兜底初始化内容 |
| `scriptedUpsert` | `Boolean` | 是否启用 scripted_upsert |
| `retryOnConflict` | `Integer` | 版本冲突时的 ES 端重试次数 |
| `detectNoop` | `Boolean` | 是否启用 ES detect_noop |

### DeleteOptions

删除选项。

| 字段 | 类型 | 说明 |
|------|------|------|
| `version` | `Long` | 版本号 |
| `versionType` | `String` | 版本类型 |
| `notFoundAsSuccess` | `Boolean` | 删除目标不存在时是否视为成功 |

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
| `pipeline` | `String` | INDEX/CREATE item 使用的 ingest pipeline |
| `retryOnConflict` | `Integer` | UPDATE item 版本冲突时的 ES 端重试次数 |
| `detectNoop` | `Boolean` | UPDATE item 是否启用 ES detect_noop |
| `upsertDoc` | `Object` | UPDATE item 文档不存在时的兜底初始化内容 |
| `scriptedUpsert` | `Boolean` | UPDATE item 是否启用 scripted_upsert |

### PersistenceResult

单条写入结果。

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | `boolean` | 是否成功 |
| `id` | `String` | 文档 ID |
| `index` | `String` | 实际索引 |
| `datasource` | `String` | 数据源 key |
| `operationType` | `PersistenceOperationType` | 操作类型 |
| `asyncRouted` | `boolean` | 是否被 route async-write 接管 |
| `tookMs` | `long` | 耗时（毫秒） |
| `result` | `String` | ES 写入结果：`created` / `updated` / `deleted` / `not_found` / `noop` |

### BulkResult

Bulk 写入结果。

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | `boolean` | 是否整体成功 |
| `hasFailure` | `boolean` | 是否存在 item 失败 |
| `total` | `int` | 总 item 数 |
| `succeeded` | `int` | 成功 item 数 |
| `failed` | `int` | 失败 item 数 |
| `datasource` | `String` | 数据源 key |
| `tookMs` | `long` | 耗时（毫秒） |
| `failureList` | `List<BulkItemFailure>` | item 失败明细 |
| `batchTotal` | `Integer` | 已提交的批次数 |
| `batchSucceeded` | `Integer` | 无 item 失败的批次数 |
| `batchFailed` | `Integer` | 存在 item 失败的批次数 |
| `stoppedOnFailure` | `Boolean` | 是否因失败批次停止后续提交 |
| `partial` | `Boolean` | 是否出现部分批次已提交后的请求级异常 |

### BulkItemFailure

Bulk item 失败明细。

| 字段 | 类型 | 说明 |
|------|------|------|
| `itemIndex` | `int` | item 下标 |
| `type` | `BulkItemType` | item 操作类型 |
| `id` | `String` | 文档 ID |
| `index` | `String` | 目标索引 |
| `datasource` | `String` | 数据源 key |
| `errorCode` | `String` | SDK 错误码 |
| `errorMessage` | `String` | 错误信息 |
| `status` | `Integer` | ES 返回的 HTTP 状态码 |
| `errorType` | `String` | ES 错误类型 |
| `errorReason` | `String` | ES 错误原因 |
| `retryable` | `Boolean` | 是否建议重试 |

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
| `requestsPerSecond` | `Float` | 每秒请求数节流，null 不限制，0 暂停 |
| `maxDocs` | `Long` | 限制处理的最大文档数，0 处理 0 个 |
| `waitForActiveShards` | `Integer` | 执行前要求的活跃分片数，0 要求全部必需分片就绪 |

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

## 公共常量

`SimpleElasticsearchPersistenceCoreConstant` 提供 starter 执行层可复用的常量：

| 常量类别 | 常量 | 说明 |
|----------|------|------|
| 脚本语言 | `SCRIPT_LANG_PAINLESS` | Painless 脚本语言 |
| ES result | `ES_RESULT_CREATED` / `ES_RESULT_UPDATED` / `ES_RESULT_DELETED` / `ES_RESULT_NOT_FOUND` / `ES_RESULT_NOOP` | 单条写入结果字符串 |
| refresh policy | `REFRESH_POLICY_TRUE` / `REFRESH_POLICY_FALSE` / `REFRESH_POLICY_WAIT_FOR` | ES refresh policy 字符串 |
| bulk partial | `BULK_PARTIAL_EXECUTION_FAILED` | 分批 bulk 部分提交后请求级异常的错误信息 |
| HTTP status | `HTTP_STATUS_BAD_REQUEST` / `HTTP_STATUS_NOT_FOUND` / `HTTP_STATUS_CONFLICT` / `HTTP_STATUS_REQUEST_TIMEOUT` / `HTTP_STATUS_TOO_MANY_REQUESTS` / `HTTP_STATUS_INTERNAL_SERVER_ERROR` / `HTTP_STATUS_BAD_GATEWAY` / `HTTP_STATUS_SERVICE_UNAVAILABLE` / `HTTP_STATUS_GATEWAY_TIMEOUT` | bulk 失败分类可复用状态码 |

## 依赖

该包仅依赖：
- `lombok`（编译时）
- `spring-context`（compileOnly）

无运行时依赖，保持轻量。

## 版本历史

- `1.0.3`：`ByQueryOptions` 新增 `requestsPerSecond`、`maxDocs`、`waitForActiveShards` 三个 by-query 执行参数字段；`routing` 由 `WriteOptions` 继承复用，by-query 搜索阶段路由复用该字段
- `1.0.2`：补充业务写入增强模型，新增 `refreshPolicy`、`pipeline`、`retryOnConflict`、`detectNoop`、delete `notFoundAsSuccess`、bulk item 级 upsert/scriptedUpsert、bulk 失败明细和分批结果字段
- `1.0.1`：`UpdateOptions` 新增 `upsertDoc`（兜底初始化文档）和 `scriptedUpsert`（scripted_upsert 语义），支持文档不存在时通过 Painless 脚本初始化字段
- `1.0.0`：初始版本，提供写操作核心模型、枚举常量、事件定义

## 注意事项

1. 该包不应被业务直接依赖，由 `simple-elasticsearch-persistence-starter` 传递依赖
2. 事件监听使用 Spring `@EventListener` 注解
3. 异常使用 `ErrorCode` + `ErrorMessage` 配合抛出，错误信息支持占位符格式化
