# simple-elasticsearch-persistence-starter 1.0.2 CHANGELOG

## 发布日期

2026-07-08

## 类型

Feature

## 版本定位

1.0.2 是写入语义补齐版本，主线是把 core 1.0.2 新增的公共写入参数和结果字段落到 starter 执行层。

本版本重点补齐调用方写入 ES 时常用的参数透传、bulk 分批可靠性、bulk 失败明细和 `TypedPersistence` 强类型门面能力；不扩展 mapping、template、alias、rollover、snapshot、cluster health、cat API、reindex 平台等 ES 运维能力。

## 依赖升级

| 依赖 | 旧版本 | 新版本 | 说明 |
|------|--------|--------|------|
| simple-elasticsearch-persistence-core | 1.0.1 | 1.0.2 | 承载新增 option/result 字段与公共常量 |
| simple-elasticsearch-route-starter | 1.1.2 | 1.1.2 | 不变 |

## 新增功能

### 写入参数透传

`IndexOptions` / `UpdateOptions` / `DeleteOptions` / `BulkOptions` / `BulkItem` 中的写入选项已落到实际 ES 请求：

- `routing`
- `pipeline`
- `refreshPolicy`
- `timeoutMs`
- `retryOnConflict`
- `detectNoop`
- `version`
- `versionType`
- `notFoundAsSuccess`
- bulk item 级 `pipeline` / `routing` / update 参数

`refreshPolicy` 优先于历史 `refresh` 布尔值，支持 `true` / `false` / `wait_for`。

`notFoundAsSuccess` 默认保持兼容：`null` 或 `true` 时 delete 返回 `not_found` 仍视为成功；显式 `false` 时启用严格删除语义。

### Bulk 分批与失败控制

`BulkOptions.batchSize` 已从模型字段落为真实执行语义：bulk 会按 batchSize 拆成多个批次提交。

`BulkOptions.continueOnFailure` 已落地：

- `null` / `true`：某批出现 item failure 后继续提交后续批次
- `false`：某批出现 item failure 后停止提交后续批次

`BulkResult` 新增字段会表达实际执行情况：

- `batchTotal`
- `batchSucceeded`
- `batchFailed`
- `stoppedOnFailure`
- `partial`

后续批次发生请求级异常时，已提交部分会通过 `BulkPersistenceExecutionException.partialResult` 返回。

### Bulk 失败明细增强

`BulkItemFailure` 已填充更完整的 ES 失败上下文：

- `itemIndex`
- `status`
- `errorType`
- `errorReason`
- `retryable`

`itemIndex` 按原始 bulk item 列表全局下标计算，分批后仍能定位原始 item。

### BulkFailureClassifier 扩展点

新增 `BulkFailureClassifier`，用于判断 bulk item 失败是否适合后续重试。

默认实现 `DefaultBulkFailureClassifier` 将以下 HTTP 状态视为可重试：

- 408
- 429
- 500
- 502
- 503
- 504

调用方可通过自定义 `BulkFailureClassifier` Bean 覆盖默认分类逻辑。

### TypedPersistence 增强

`TypedPersistence` 新增并落地以下能力：

- `withRoutingResolver`
- `withDefaultIndexOptions`
- `withDefaultBulkOptions`
- `bulkCreate`
- `bulkCreateAsync`

`create` / `createAsync` / `bulkCreate` / `bulkCreateAsync` 会强制使用 CREATE 语义，默认 `IndexOptions.operationType` 不会覆盖 create 语义。

为保持 1.0.1 二进制兼容，`DefaultTypedPersistence` 保留旧构造函数，新字段默认 `null`。

### 写入结果 result 字段

单条 index / update / delete 返回的 `PersistenceResult.result` 会填充 ES 结果语义：

- `created`
- `updated`
- `deleted`
- `not_found`
- `noop`

## 兼容性修复

### ES 6.2.2 Bulk Failure API 兼容

`BulkItemResponse.Failure` 在低版本 ES 客户端中没有 `getReason()` 方法，本版本统一使用 `getCause().getMessage()` 作为 `errorReason` 来源，确保 Spring Boot 2.2.x / ES 6.2.2 组合可编译、可测试。

### Spring 工具方法兼容

避免使用低版本 Spring 不存在的集合工具方法，保持 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 兼容。

## 新增测试

新增或增强测试覆盖：

- `DefaultBulkFailureClassifierTest`
- `PersistenceResultHelperTest`
- `BulkExecutorTest`
- `DefaultTypedPersistenceTest`
- `PersistenceEsRequestHelperTest`

覆盖重点：

- refreshPolicy / routing / pipeline 透传
- update retryOnConflict / detectNoop 透传
- delete version / versionType / notFoundAsSuccess 语义
- bulk batchSize 分批
- continueOnFailure 停止与继续语义
- bulk partial 异常承载已提交结果
- bulk failure status / errorType / errorReason / retryable / 全局 itemIndex
- TypedPersistence routingResolver、默认 options、bulkCreate、CREATE 强制语义

## 验证矩阵

| Spring Boot | ES 客户端 | 验证结果 |
|-------------|-----------|----------|
| 2.7.9 | 7.17.x | starter 全量测试通过 |
| 2.4.5 | 7.9.x | starter 全量测试通过 |
| 2.3.12 | 7.6.x | starter 全量测试通过 |
| 2.2.x | 6.8.x | starter 全量测试通过，兼容 ES 6.2.2 API |

## 向后兼容性

- 依赖升级到 core 1.0.2；已有 1.0.1 调用方不设置新增字段时行为保持兼容。
- `refresh` 布尔值保留，只有 `refreshPolicy` 有值时才优先生效。
- delete `not_found` 默认仍按成功解释；只有显式 `notFoundAsSuccess=false` 时改变结果成功标记。
- 无自定义 `BulkFailureClassifier` Bean 时使用默认分类器。
- `TypedPersistence` 原有 index/create/bulkIndex/validator/indexResolver/idResolver 用法保持不变。

## 升级指南

1. 将依赖升级到 `simple-elasticsearch-persistence-starter:1.0.2`。
2. 如需等待刷新语义，优先使用 `refreshPolicy=wait_for`。
3. 如需控制 bulk 执行规模，设置 `BulkOptions.batchSize`。
4. 如需失败后立即停止后续批次，设置 `BulkOptions.continueOnFailure(false)`。
5. 如需自定义可重试判断，提供 `BulkFailureClassifier` Bean。
6. 如需强类型写入默认参数，使用 `withDefaultIndexOptions` / `withDefaultBulkOptions` / `withRoutingResolver`。

## 非本版本范围

- mapping / template / alias / rollover 管理
- pipeline 创建、更新、删除
- snapshot / restore / forcemerge / cluster health / cat API
- 通用 task 管理或 reindex 平台
- 自动 bulk 重试
- 跨批次 checkpoint / 断点续跑
- route async-write 语义调整
- Spring Boot 3.x / jakarta 依赖升级
