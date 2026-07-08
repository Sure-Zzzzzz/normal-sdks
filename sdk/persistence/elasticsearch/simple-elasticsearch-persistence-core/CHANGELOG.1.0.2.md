# simple-elasticsearch-persistence-core 1.0.2 更新日志

## 发布日期

2026-07-08

## 类型

Feature

## 版本定位

1.0.2 是写入语义增强的核心模型版本，为 `simple-elasticsearch-persistence-starter` 1.0.2 承载更完整的业务写入参数与结果表达。

本版本只新增模型字段与公共常量，不包含 ES 执行逻辑；执行语义由 starter 1.0.2 落地，保持向后兼容。

## 依赖变更

| 依赖 | 变化 |
|------|------|
| Lombok | 无变化，仍由父 Gradle 提供 |
| Spring Context | 无变化，仍为 compileOnly |
| Elasticsearch Client | 不新增，core 不直接依赖 ES 客户端 |

## 新增功能

### 写入选项增强

**变更文件**：`model/option/WriteOptions.java`

新增字段：

```java
private String refreshPolicy;
```

用于表达 ES refresh policy：`true` / `false` / `wait_for`。starter 侧会按 `refreshPolicy` 优先于 `refresh` 的规则透传。

### IndexOptions 新增 pipeline

**变更文件**：`model/option/IndexOptions.java`

新增字段：

```java
private String pipeline;
```

用于 index/create 写入时指定 ingest pipeline。

### UpdateOptions 新增冲突与 noop 控制

**变更文件**：`model/option/UpdateOptions.java`

新增字段：

```java
private Integer retryOnConflict;
private Boolean detectNoop;
```

用于 update 写入时透传 ES `retry_on_conflict` 与 `detect_noop`。

### DeleteOptions 新增 not_found 语义开关

**变更文件**：`model/option/DeleteOptions.java`

新增字段：

```java
private Boolean notFoundAsSuccess;
```

starter 侧默认保持 1.0.1 兼容：`null` / `true` 时 delete 返回 `not_found` 仍视为成功；显式设置 `false` 时启用严格删除语义。

### BulkItem 新增 item 级写入参数

**变更文件**：`model/request/BulkItem.java`

新增字段：

```java
private String pipeline;
private Integer retryOnConflict;
private Boolean detectNoop;
private Object upsertDoc;
private Boolean scriptedUpsert;
```

用于 bulk 中按 item 细粒度表达 pipeline、update 冲突重试、noop 检测与 scripted_upsert 初始化语义。

### BulkItemFailure 新增 ES 失败明细

**变更文件**：`model/result/BulkItemFailure.java`

新增字段：

```java
private Integer status;
private String errorType;
private String errorReason;
private Boolean retryable;
```

用于 starter 侧保留 bulk item 失败的 HTTP 状态、ES 错误类型、错误原因和可重试分类。

### BulkResult 新增分批执行结果

**变更文件**：`model/result/BulkResult.java`

新增字段：

```java
private Integer batchTotal;
private Integer batchSucceeded;
private Integer batchFailed;
private Boolean stoppedOnFailure;
private Boolean partial;
```

用于 starter 侧表达 `batchSize` 实际分批、`continueOnFailure` 停止语义，以及部分批次已提交后请求级异常的 partial 结果。

### PersistenceResult 新增 ES result

**变更文件**：`model/result/PersistenceResult.java`

新增字段：

```java
private String result;
```

用于返回 ES `DocWriteResponse.Result`，例如 `created` / `updated` / `deleted` / `not_found` / `noop`。

### 公共常量补充

**变更文件**：`constant/SimpleElasticsearchPersistenceCoreConstant.java`

新增 ES result、refresh policy、bulk partial 错误消息和常用 HTTP 状态码常量，供 starter 1.0.2 复用，避免执行层硬编码。

### 模型字段注释补齐

**变更范围**：`model` 包

补齐 core model 包中所有非 static 字段的字段注释，清理历史模型注释债，符合 SDK 字段注释规范。

## 向后兼容性

- 所有新增字段默认值均为 `null`，不改变已有调用方构造行为。
- 现有枚举、异常、事件类型不做破坏性调整。
- delete `not_found` 默认语义保持 1.0.1 兼容，只有显式设置 `notFoundAsSuccess=false` 才启用严格模式。
- core 仍只提供模型、枚举、异常、事件和常量，不引入 ES 客户端运行时依赖。

## 验证

- `simple-elasticsearch-persistence-core:compileJava` 通过。
- Java 8 禁用 API 扫描无命中。
- 文档生产信息关键词扫描无命中。

core 模块当前没有测试类，本版本为模型字段和常量增强，执行语义将在 starter 1.0.2 的集成测试中覆盖。

## 升级指南

1. 发布 core 1.0.2。
2. starter 1.0.2 将依赖升级到 core 1.0.2 后，再落地执行层透传和集成测试。
3. 已有调用方无需修改；只有需要新语义时才设置新增字段。

## 非本版本范围

- 不实现 routing、pipeline、refreshPolicy、retryOnConflict、detectNoop 等 ES 请求透传逻辑。
- 不实现 bulk 分批、continueOnFailure、失败分类或 partial 异常。
- 不实现 mapping/template/alias/rollover/forcemerge/snapshot/cluster health/reindex 平台等 ES 运维能力。
- 不调整 starter 的自动配置、执行器、强类型门面或集成测试。

上述执行语义由 `simple-elasticsearch-persistence-starter` 1.0.2 实现。
