# simple-elasticsearch-persistence-starter 1.1.0 Changelog

## 发布信息

- 发布日期：2026-07-09
- 类型：Feature / Compatibility

## 版本定位

`1.1.0` 是写侧体验增强版本，适配已发布的 `simple-elasticsearch-route-starter:1.2.0`，并新增 create 冲突转 update 的两阶段辅助能力。

## 依赖版本

| 模块 | 版本 |
|------|------|
| simple-elasticsearch-persistence-starter | 1.1.0 |
| simple-elasticsearch-persistence-core | 1.0.2 |
| simple-elasticsearch-route-starter | 1.2.0 |

## 变更内容

### create 冲突转 update

在“优先新建，已存在则补充更新”的写入场景中，调用方以前需要自行捕获 409 conflict、手动构造 UPDATE，并在 bulk 场景里合并两阶段结果。`1.1.0` 将该模式封装为可选 helper，不改变原有 create / bulk 默认语义。

- 新增 `CreateThenUpdateOnConflictHelper`：
  - 单条写入先执行 CREATE。
  - CREATE 遇到 `409 conflict` 后执行调用方提供的 UPDATE。
  - CREATE 非 409 失败不吞掉，保持原异常语义。
- 新增 `ConflictUpdateResolver`：
  - bulk CREATE 阶段只筛选 409 冲突 item。
  - resolver 决定冲突 item 的 UPDATE 字段、routing、retryOnConflict、detectNoop 等。
  - resolver 返回 null 时保留原 conflict failure，不误报成功。
- 扩展 `PersistenceEngine`：
  - `createThenUpdateOnConflict(...)`
  - `createThenUpdateOnConflictAsync(...)`
  - `bulkCreateThenUpdateOnConflict(...)`
  - `bulkCreateThenUpdateOnConflictAsync(...)`
- 扩展 `TypedPersistence`：
  - `createThenUpdateOnConflict(T, Function<T, UpdateRequest>)`
  - `createThenUpdateOnConflictAsync(T, Function<T, UpdateRequest>)`
  - `bulkCreateThenUpdateOnConflict(List<T>, Function<T, BulkItem>, BulkOptions)`
  - `bulkCreateThenUpdateOnConflictAsync(List<T>, Function<T, BulkItem>, BulkOptions)`

## 行为说明

- bulk helper 会强制第一阶段 CREATE，并强制两阶段 `continueOnFailure=true`，保证可以收集所有 item 结果。
- bulk 第二阶段 UPDATE 不继承 CREATE 阶段的 ingest pipeline，避免 CREATE-only pipeline 误用于 UPDATE。
- 最终 `BulkResult` 按原始 item 数统计：
  - CREATE 成功 item 计成功。
  - 409 后 UPDATE 成功 item 计成功。
  - 非 409 CREATE 失败保留失败明细。
  - UPDATE 阶段失败映射回原始 itemIndex，并保留最终 UPDATE 失败类型。

### route 1.2.0 适配

- starter 依赖从 `simple-elasticsearch-route-starter:1.1.2` 升级到 `1.2.0`。
- 写请求构造复用 route `ElasticsearchWriteRequestHelper`。
- refresh / timeout / byQuery 参数复用 route `ElasticsearchRequestOptionHelper`。
- byQuery / task endpoint 与 low-level request 复用 route endpoint / low-level helper。
- bulk failure status / reason / retryable 判断复用 route response helper。
- persistence 保留薄适配层，负责 DTO、写侧语义、结果合并和事件边界；ES 协议兼容细节下沉到 route helper。

## 新增测试

- `CreateThenUpdateOnConflictHelperTest`
  - 单条 CREATE 成功不触发 UPDATE。
  - 单条 CREATE 409 后触发 UPDATE，并继承 index/id/routing。
  - 单条 CREATE 非 409 异常原样抛出。
  - bulk 409 转 UPDATE 后合并最终结果。
  - bulk resolver 返回 null 时保留原 conflict failure。
  - bulk UPDATE 阶段失败映射回原始 itemIndex。
  - CREATE 阶段 pipeline 不透传到 UPDATE 阶段。
- `DefaultTypedPersistenceTest`
  - typed facade 自动补齐 create/update 的 index/id/routing。
  - typed bulk create-then-update-on-conflict 正确生成第二阶段 UPDATE item。
  - typed 默认 options 与 resolver 结果按预期合并。

## 向后兼容性

- 不升级 `simple-elasticsearch-persistence-core`，继续使用 `1.0.2` 公共模型。
- 不改变原 index/create/update/delete/bulk/byQuery 默认行为。
- 不引入 Spring Boot 3.x / jakarta 依赖。
- 不使用 Java 9+ API。

## 升级指南

- 调用方升级 starter 到 `simple-elasticsearch-persistence-starter:1.1.0` 即可。
- 如果工程显式声明了 `simple-elasticsearch-route-starter`，请同步升级到 `1.2.0`。
- 如果只使用原 index/create/update/delete/bulk/byQuery API，不需要修改业务代码。
- 如果需要“先 CREATE，409 后 UPDATE”，新增调用 `createThenUpdateOnConflict(...)` 或 `bulkCreateThenUpdateOnConflict(...)`。
- bulk resolver 返回 `null` 表示放弃该 conflict item 的 UPDATE，最终结果会保留原 conflict failure。

## 验证记录

| Spring Boot | 命令 | 结果 |
|-------------|------|------|
| 2.7.9 | Gradle 8.5 + Java 11，`:sdk:persistence:elasticsearch:simple-elasticsearch-persistence-starter:test` | 通过 |
| 2.4.5 | Gradle 7.6 + Java 8 + `spring.profiles.active=2.4.5`，同模块 test | 通过 |
| 2.3.12 | Gradle 7.6 + Java 8 + `spring.profiles.active=2.3.12`，同模块 test | 通过 |
| 2.2.x | Gradle 7.6 + Java 8 + `spring.profiles.active=2.2.x`，同模块 test | 通过 |
