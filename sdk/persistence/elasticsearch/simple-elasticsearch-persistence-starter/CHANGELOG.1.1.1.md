# simple-elasticsearch-persistence-starter 1.1.1 Changelog

## 发布信息

- 发布日期：2026-07-14
- 类型：Patch / Fix

## 版本定位

`1.1.1` 是 by-query REST 参数透传修复版本，修复 1.1.0 中 `update_by_query` / `delete_by_query` 同步与服务端异步路径错误地把 URL query parameter 写入 JSON body 的问题，并顺带收敛 by-query 执行路径的失败传播与硬编码。

本版本只修复已发布能力，不新增业务写入语义，不扩展 ES 运维能力。

## 依赖版本

| 模块 | 版本 |
|------|------|
| simple-elasticsearch-persistence-starter | 1.1.1 |
| simple-elasticsearch-persistence-core | 1.0.3 |
| simple-elasticsearch-route-starter | 1.2.1 |

## 变更内容

### by-query 参数位置修复（核心）

1.1.0 通过 `applyByQueryBodyOptions(...)` 把 `refresh` / `timeout` / `slices` / `conflicts` / `scroll_size` / `wait_for_completion` 写入 JSON body，其中 `refresh` / `slices` / `scroll_size` 不是 by-query body 字段，ES 7.9.3 / 7.17.16 会返回 HTTP 400 `parsing_exception`。

1.1.1 将 by-query body 收敛为只承载 `query` / `script`，执行参数全部移到 URL query parameter：

- `buildUpdateByQueryBody(...)` / `buildDeleteByQueryBody(...)` 删除 `applyByQueryBodyOptions(...)` 调用，不再向 body 写执行参数。
- 新增 `buildByQueryRequest(endpoint, body, options, waitForCompletion)` 统一同步/异步 low-level Request 构造，调用 route 1.2.1 `ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(...)` 写入全部 10 个 URL 参数，避免同步/异步两套分叉。
- `executeByQuerySync(...)` / `submitByLowLevel(...)` 都改为调用 `buildByQueryRequest(...)`，不再各自内联拼参数。

### scrollSize / batchSize 兼容规则

新增 `resolveScrollSize(options)`：`scrollSize` 非 null 时取 `scrollSize`，否则取 `batchSize` 作为兼容别名映射到 `scroll_size`。修复 1.1.0 中 `batchSize` 在 low-level 路径上静默失效的问题。两者同时配置时 `scrollSize` 覆盖 `batchSize`，不报错，避免 patch 版本变为启动失败。

### 新增 4 个 by-query 执行参数

persistence-core 升级到 1.0.3，`ByQueryOptions` 新增 `requestsPerSecond`（Float）/ `maxDocs`（Long）/ `waitForActiveShards`（Integer）3 个字段；`routing` 继承自 `WriteOptions`，by-query 搜索阶段路由复用该字段。这 4 个参数连同既有 6 个参数一起写入 URL query。

ES 6.x 不支持 `requests_per_second` / `max_docs` / `wait_for_active_shards`，传了 ES 忽略不报错，对 6.x 兼容无破坏性影响。

### 异步任务失败传播

1.1.0 的 `getTask(...)` 在任务不存在/已过期、任务执行失败时静默返回结果，调用方无法感知失败。1.1.1 增强失败传播：

- 任务不存在或已过期（响应既无 `completed` 也无 `task`）抛 `PersistenceExecutionException`（`EXECUTION_FAILED`）。
- 任务已完成但含 `error` 字段（任务级失败）抛 `PersistenceExecutionException`，原因取自 `ElasticsearchResponseHelper.extractFailureCause(...)`。
- `submitUpdateByQueryTask(...)` / `submitDeleteByQueryTask(...)` 提交后若未返回 `task` 标识，抛 `PersistenceExecutionException`，不再返回 `null`。

> 说明：此改动涉及 task 错误包装语义，超出 1.1.1 设计文档原定"不改变错误包装"范围，详见 `DESIGN.1.1.1.md` 偏差说明。理由是 1.1.0 的静默返回会让调用方误以为任务成功，属于缺陷修复。

### by-query DSL 常量化与错误消息模板化

by-query query DSL 关键字（`match_all` / `bool` / `must` / `term` / `range` / script `source`）此前以字符串字面量散落在 `ElasticsearchWriteApiHelper`，现统一提取到 `SimpleElasticsearchPersistenceConstant`（route 未定义这些 DSL 关键字，persistence 暂管，沿用既有 `JSON_FIELD_ERROR` 模式）。

异步任务错误原因此前用字符串拼接（`"异步任务不存在或已过期：" + taskId`），改为 `TEMPLATE_TASK_NOT_FOUND` / `TEMPLATE_TASK_EXECUTION_FAILED` 模板 + `String.format`，与模块既有 `String.format(ErrorMessage.EXECUTION_FAILED, ...)` 用法一致。

### High Level Client by-query builder 废弃标注

`PersistenceEsRequestHelper` 的 `buildUpdateByQueryRequest(...)` / `buildDeleteByQueryRequest(...)` 标记 `@Deprecated`，Javadoc 注明 by-query 执行路径已切换到 low-level REST（见 `ElasticsearchWriteApiHelper`），这两个方法无调用方，仅为 HL Client 兼容保留。私有 `applyByQueryOptions(...)` 同步补充"非执行路径"说明。本版本不重构 HL Client request builder。

### query 为 null 的安全提示

`buildQueryJson(...)` 在 `query == null` 时返回 `match_all`，会匹配索引全部文档。异步 `update_by_query` / `delete_by_query` 误用会批量更新/删除全索引。补充 Javadoc 警告，调用方应显式传入限定范围的 query。

## 行为说明

- by-query body 只含 `query` / `script`，执行参数全部在 URL query。
- `scrollSize` 优先于 `batchSize`；两者都为 null 时不透传 `scroll_size`。
- 异步任务失败（不存在/已失败/提交未返回 task）以 `PersistenceExecutionException` 抛出，不再静默返回。
- `wait_for_completion == false` 提交服务端异步任务返回 taskId；`null/true` 同步等待返回 `ByQueryTaskResult`。
- 同步/异步 taskId、结果字段、事件发布语义无回归。

## 新增测试

- `ElasticsearchWriteApiHelperTest`（`test.cases` 包，纯静态方法单测，`@Slf4j`）
  - 完整 options：10 个执行参数全部映射到 URL query。
  - `options=null`：仅 `wait_for_completion` 透传，其余参数不出现。
  - `scrollSize` 优先级：scrollSize 覆盖 batchSize、仅 batchSize fallback、仅 scrollSize、两者都 null 返回 null、`resolveScrollSize(null)` 返回 null。
- `PersistenceEngineIntegrationTest` 扩展
  - 同步 / 异步 × `update_by_query` / `delete_by_query` 四条主链路真实 ES 测试（中性测试索引 + `match_none`，不删除测试数据）。
  - 异步任务失败传播：任务不存在抛异常、异步任务执行失败抛异常。

## 向后兼容性

- persistence-core 升级到 1.0.3（新增 3 个可选字段，不删除、不改名既有字段，保持 binary compatibility）。
- 不删除、不改名 `ByQueryOptions` 既有字段。
- 不修改 `PersistenceEngine` / `TypedPersistence` 公共方法。
- `waitForCompletion` 默认同步语义不变。
- `conflicts` / `timeout` 从 body 移到 URL 后 ES 语义保持一致。
- 以前因非法 body 报 400 的 `refresh` / `slices` / `scrollSize` 开始正常生效，属于缺陷修复。
- `batchSize` 从静默失效修复为 `scroll_size` fallback。
- 异步任务失败传播是行为增强：1.1.0 静默返回的场景，1.1.1 会抛异常。如果调用方依赖 `getTask(...)` 对失败任务返回非异常结果，需要改为捕获 `PersistenceExecutionException`。

## 升级指南

- 调用方升级 starter 到 `simple-elasticsearch-persistence-starter:1.1.1` 即可。
- starter 传递依赖 `simple-elasticsearch-route-starter:1.2.1` 和 `simple-elasticsearch-persistence-core:1.0.3`；如果工程显式声明了这两个依赖，请同步调整，避免 API 版本不一致。
- 原有 index / create / update / delete / bulk / byQuery 行为保持不变。
- 如果使用了异步 by-query 任务（`waitForCompletion(false)` + `getTask(...)`），注意失败任务现在会抛 `PersistenceExecutionException`，而不是静默返回。
- `scrollSize` 与 `batchSize` 同时配置时以 `scrollSize` 为准。

## 验证记录

| Spring Boot | 工具链 | 目标 ES | 命令 | 结果 |
|-------------|--------|---------|------|------|
| 2.7.9 | Gradle 8.5 + Java 11 | ES 7.17.16（9200） | `:sdk:persistence:elasticsearch:simple-elasticsearch-persistence-starter:test` | 通过 |
| 2.4.5 | Gradle 7.6 + Java 8 + `spring.profiles.active=2.4.5` | ES 7.9.3（9204） | 同模块 test | 通过 |
| 2.3.12 | Gradle 7.6 + Java 8 + `spring.profiles.active=2.3.12` | ES 7.9.3（9204） | 同模块 test | 通过 |
| 2.2.x | Gradle 7.6 + Java 8 + `spring.profiles.active=2.2.x` | ES 6.2.2（9203） | 同模块 test | 通过 |

多版本 profile 激活经实证确认（测试报告 `The following profiles are active: persistence-test,<版本>,persistence-test-<版本>`），非假绿。`persistence-test` profile 下 primary = 9202（故意失败，测路由错误处理），secondary 为各版本对应 ES。

旧版本 Spring Boot 测试必须把 profile 放进 `JAVA_TOOL_OPTIONS`，仅写在 Gradle 命令 `-D` 上不会传给 fork 的 test JVM，profile 会失效。
