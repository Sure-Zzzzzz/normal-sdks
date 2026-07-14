# simple-elasticsearch-persistence-core 1.0.3 更新日志

## 发布日期

2026-07-14

## 类型

Feature

## 版本定位

1.0.3 是 by-query 执行参数扩展版本，为 `simple-elasticsearch-persistence-starter` 1.1.1 的 by-query REST 参数透传修复提供完整的 `ByQueryOptions` 字段。

本版本只新增可选字段，保持 binary compatibility，不删除、不改名既有字段；执行语义由 starter 1.1.1 落地。

## 依赖变更

| 依赖 | 变化 |
|------|------|
| Lombok | 无变化，仍由父 Gradle 提供 |
| Spring Context | 无变化，仍为 compileOnly |
| Elasticsearch Client | 不新增，core 不直接依赖 ES 客户端 |

## 新增功能

### ByQueryOptions 新增 by-query 执行参数

**变更文件**：`model/option/ByQueryOptions.java`

新增三个字段：

```java
/** 每秒请求数节流参数，null 表示不限制。ES 默认不限制（传 -1 或不传），可设正数如 500f，或 0 暂停。 */
private Float requestsPerSecond;

/** 限制处理的最大文档数，超出后任务停止。0 表示处理 0 个文档。 */
private Long maxDocs;

/** 开始执行前要求的活跃分片数。0 表示要求全部必需分片就绪，比默认（1 个分片）更严格。 */
private Integer waitForActiveShards;
```

用于 by-query 执行时透传 ES `requests_per_second`、`max_docs`、`wait_for_active_shards` 三个 URL query parameter。

### routing 字段继承复用

by-query 搜索阶段的分片路由复用 `WriteOptions` 既有 `routing` 字段。ES `_routing` 参数对单 doc 和 by-query 操作均有效，`ByQueryOptions` 不重复声明，避免 Java 字段遮蔽。

## 字段约束

| 字段 | 类型 | ES 参数 | 合法的 null | 合法的 0 |
|------|------|---------|------------|-----------|
| `requestsPerSecond` | Float | `requests_per_second` | 不限制 | 暂停 |
| `maxDocs` | Long | `max_docs` | 不限制 | 处理 0 个文档 |
| `waitForActiveShards` | Integer | `wait_for_active_shards` | 不传，ES 视为 0（全部必需分片就绪） | 全部必需分片就绪才执行，比默认「1 个分片」更严格 |
| `routing`（继承） | String | `routing` | 不传 | - |

## 向后兼容性

- 所有新增字段默认值均为 `null`，不改变已有调用方构造行为。
- 不删除、不改名 `ByQueryOptions` 既有字段。
- `ByQueryOptions.builder(...)` 行为不变，未配置的新字段按 null 处理。
- core 仍只提供模型、枚举、异常、事件和常量，不引入 ES 客户端运行时依赖。

## 验证

- `simple-elasticsearch-persistence-core:compileJava` 通过。
- Java 8 禁用 API 扫描无命中。
- 字段命名与 route `ByQueryRequestOptions` 对齐：`requestsPerSecond` / `maxDocs` / `waitForActiveShards`。

core 模块当前没有测试类，本版本为模型字段增强，执行语义将在 starter 1.1.1 的集成测试中覆盖。

## 升级指南

1. 发布 core 1.0.3。
2. starter 1.1.1 将依赖升级到 core 1.0.3 后，接入 route 1.2.1 的 `applyByQueryRequestOptions` 完成执行参数透传。
3. 已有调用方无需修改；只有需要 by-query 节流、限量或活跃分片约束时才设置新增字段。

## 非本版本范围

- 不实现 `requestsPerSecond` / `maxDocs` / `waitForActiveShards` 的 ES 请求透传逻辑，由 starter 1.1.1 落地。
- 不删除 `batchSize` / `scrollSize` 任一公共字段；二者优先级规则（`scrollSize` 优先、`batchSize` fallback）由 starter 1.1.1 定义。
- 不调整其他 Option 模型、枚举、异常、事件。
- 不引入 mapping/template/alias/rollover/forcemerge/snapshot/cluster health 等 ES 运维能力。
