# Simple Elasticsearch Persistence Starter

基于 Spring Boot 的 Elasticsearch 写侧自动配置组件，配合 `simple-elasticsearch-route-starter` 提供声明式写入能力，自动继承路由规则、日期分片和多数据源配置。

## ✨ 核心特性

- ✍️ **统一写侧入口**：`PersistenceEngine` 封装 index / create / update / delete / bulk / by-query，一个 Bean 搞定全部写操作
- 🔗 **零侵入继承路由**：自动继承 route 的多数据源路由、日期分片索引渲染、async-write 规则
- ⚡ **双模式异步**：SDK 级 `indexAsync`（CompletableFuture）与 route 级 `async-write`（fire-and-forget）并存，各自独立
- 🧩 **强类型门面**：`engine.forEntity(MyDoc.class)` 省去重复传类型，支持自定义 validator / indexResolver / idResolver
- 🔍 **按查询批量操作**：`updateByQuery` / `deleteByQuery` 支持同步等待和服务端异步（返回 taskId 可轮询）
- 📡 **事件总线**：每次写操作后发布 `EsPersistenceEvent`（成功）/ `EsPersistenceErrorEvent`（失败），可接入审计、监控
- 🔌 **多版本兼容**：支持 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9，覆盖 ES 6.x / 7.x

## 📦 依赖配置

persistence starter 已内置 route starter 依赖，无需重复声明：

### Gradle

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-persistence-starter:1.0.0'
    implementation "org.springframework.boot:spring-boot-starter-data-elasticsearch"
    implementation "org.apache.httpcomponents:httpclient"
    implementation "org.apache.httpcomponents:httpcore"
}
```

## 🔧 快速开始

### 1. 前置：配置 route

persistence 本身不管数据源和路由规则，这些由 route 统一管理。确保已有如下最简 route 配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true
            default-source: primary
            sources:
              primary:
                urls: http://localhost:9200
            rules:
              - pattern: "app-event.*"
                type: wildcard
                datasource: primary
                write-index:
                  template: "app-event-{yyyy.MM.dd}"
```

### 2. 启用 persistence

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          persistence:
            enable: true
```

### 3. 声明实体

```java
@Document(indexName = "app-event.click")
@Data
public class ClickEvent {
    @Id
    private String id;
    private String userId;
    private String eventType;
    private long ts;
}
```

### 4. 写入文档

```java
@Autowired
private PersistenceEngine engine;

// index（upsert 语义，已存在则覆盖）
PersistenceResult result = engine.index(new ClickEvent("evt-1", "u001", "click", now));
// result.getId()        → "evt-1"
// result.getDatasource() → "primary"
// result.getTookMs()     → 实际耗时

// create（仅新建，已存在抛 PersistenceExecutionException）
engine.create(new ClickEvent("evt-2", "u002", "scroll", now));

// 局部更新（doc 字段）
Map<String, Object> fields = new LinkedHashMap<String, Object>();
fields.put("eventType", "dblclick");
engine.update(UpdateRequest.builder()
    .index("app-event-2026.07.08")
    .id("evt-1")
    .fieldMap(fields)
    .build());

// script 更新
Map<String, Object> params = new LinkedHashMap<String, Object>();
params.put("newTs", newTs);
engine.update(UpdateRequest.builder()
    .index("app-event-2026.07.08")
    .id("evt-1")
    .scriptSource("ctx._source.ts = params.newTs")
    .scriptParamMap(params)
    .build());

// 按 ID 删除
engine.delete(DeleteRequest.builder()
    .index("app-event-2026.07.08")
    .id("evt-1")
    .build());
```

### 5. scriptedUpsert

适用于“文档不存在时初始化 `createTime`，文档已存在时只刷新 `updateTime`”这类写入语义：

```java
long now = System.currentTimeMillis();
Map<String, Object> params = new LinkedHashMap<String, Object>();
params.put("now", now);

engine.update(UpdateRequest.builder()
    .index("app-event-2026.07.08")
    .id("evt-1")
    .scriptSource(
        "if (ctx._source.createTime == null) { ctx._source.createTime = params.now } " +
        "ctx._source.updateTime = params.now")
    .scriptParamMap(params)
    .options(UpdateOptions.builder()
        .scriptedUpsert(true)
        .upsertDoc(Collections.emptyMap())
        .build())
    .build());
```

`scriptedUpsert(true)` 会让 ES 在文档不存在时也执行脚本；`upsertDoc` 可以传空 Map，用来触发 scripted_upsert。

### 6. 批量写入

```java
// 便捷批量 index（同类文档，走 @Document 解析索引名）
List<ClickEvent> eventList = new ArrayList<ClickEvent>();
eventList.add(evt1);
eventList.add(evt2);
eventList.add(evt3);
BulkResult bulk = engine.bulkIndex(eventList, BulkOptions.builder().timeoutMs(30000L).build());
// bulk.getTotal()    → 3
// bulk.getSucceeded() → 成功条数
// bulk.isHasFailure() → 是否有部分失败

// 混合 bulk（不同操作类型）
Map<String, Object> updateFields = new LinkedHashMap<String, Object>();
updateFields.put("eventType", "dblclick");
List<BulkItem> items = new ArrayList<BulkItem>();
items.add(BulkItem.builder().type(BulkItemType.INDEX).document(evt4).id("evt-4").build());
items.add(BulkItem.builder().type(BulkItemType.UPDATE).id("evt-1").fieldMap(updateFields).build());
items.add(BulkItem.builder().type(BulkItemType.DELETE).id("evt-old").build());
BulkRequest req = BulkRequest.builder()
    .defaultIndex("app-event-2026.07.08")
    .itemList(items)
    .build();
BulkResult result = engine.bulk(req);
```

### 6. 客户端异步

```java
CompletableFuture<PersistenceResult> future = engine.indexAsync(evt5);
future.thenAccept(r -> log.info("写入完成 index={} took={}ms", r.getIndex(), r.getTookMs()));

// 也有 createAsync / updateAsync / deleteAsync / bulkAsync / bulkIndexAsync
```

### 7. 按查询批量操作

```java
// 同步等待完成
Map<String, Object> updateTerms = new LinkedHashMap<String, Object>();
updateTerms.put("eventType.keyword", "click");
ByQueryTaskResult result = engine.updateByQuery(
    UpdateByQueryRequest.builder()
        .index("app-event-2026.07.08")
        .query(PersistenceQuery.builder()
            .termMap(updateTerms)
            .build())
        .scriptSource("ctx._source.processed = true")
        .build());
// result.getUpdated() → 更新条数

// 服务端异步（wait-for-completion=false）：返回 taskId，可轮询
Map<String, Object> deleteTerms = new LinkedHashMap<String, Object>();
deleteTerms.put("processed", true);
ByQueryTaskResult task = engine.deleteByQuery(
    DeleteByQueryRequest.builder()
        .index("app-event-2026.07.07")
        .query(PersistenceQuery.builder()
            .termMap(deleteTerms)
            .build())
        .options(ByQueryOptions.builder().waitForCompletion(false).build())
        .build());
if (!task.isCompleted()) {
    ByQueryTaskResult progress = engine.getTask(task.getDatasource(), task.getTaskId());
}
```

### 8. 强类型门面

```java
// 绑定实体类型，省去重复传 Class
TypedPersistence<ClickEvent> typed = engine.forEntity(ClickEvent.class);
typed.index(evt);
typed.create(evt);
typed.bulkIndex(evtList, BulkOptions.builder().build());

// 自定义 validator / resolver
TypedPersistence<ClickEvent> safe = engine.forEntity(ClickEvent.class)
    .withValidator(doc -> {
        if (doc.getUserId() == null) throw new IllegalArgumentException("userId 不能为空");
    })
    .withIdResolver(doc -> "evt-" + doc.getTs());
```

---

## ⚡ 两种异步模式

persistence starter 支持两种完全独立的异步模式，按需选择：

| 维度 | route async-write | SDK indexAsync |
|---|---|---|
| **触发方式** | route rule 配置 `async-write: true` | 调用 `engine.indexAsync(...)` |
| **返回值** | `null`（fire-and-forget） | `CompletableFuture<PersistenceResult>` |
| **结果保证** | 不保证（不关心成功失败） | 保证（可 thenAccept / exceptionally） |
| **适合场景** | 日志/审计等允许丢失的场景 | 需感知写入结果的异步场景 |

**注意**：若 route rule 开启了 `async-write: true`，对该索引的所有写入（包括 `indexAsync`）都会被 route 代理接管为 fire-and-forget，`PersistenceResult` 返回 `null`，SDK 级异步语义失效。启动时 persistence 会打 WARN 提醒。

---

## 📡 事件

每次写操作完成后，persistence 会通过 Spring `ApplicationEventPublisher` 发布事件，可用于审计、监控、链路追踪：

```java
@EventListener
public void onPersistence(EsPersistenceEvent event) {
    // event.getDatasource()     → 实际路由到的数据源名
    // event.getOperationType()  → INDEX / UPDATE / DELETE / BULK / UPDATE_BY_QUERY / DELETE_BY_QUERY
    // event.getTookMs()         → 耗时
    // event.getResult()         → PersistenceResult
}

@EventListener
public void onPersistenceError(EsPersistenceErrorEvent event) {
    // event.getError()          → 原始异常
    // event.getTookMs()         → 失败前耗时
}
```

---

## 📋 配置参考

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          persistence:
            enable: true            # 必填，false 时整个模块不加载（默认 false）
            async:
              core-size: 4          # 异步执行器核心线程数（默认 4）
              max-size: 16          # 异步执行器最大线程数（默认 16）
              queue-capacity: 1000  # 异步执行器队列容量（默认 1000）
```

persistence 自身无需配置数据源和路由规则，这些全部由 route 管理。

---

## 🧩 错误码

| 错误码 | 常量 | 说明 |
|---|---|---|
| `CONFIG_001` | `ErrorCode.CONFIG_VALIDATION_FAILED` | 配置校验失败 |
| `REQUEST_001` | `ErrorCode.REQUEST_VALIDATION_FAILED` | 请求参数校验失败 |
| `EXECUTOR_001` | `ErrorCode.EXECUTOR_NOT_FOUND` | 未找到对应执行器 |
| `EXECUTION_001` | `ErrorCode.EXECUTION_FAILED` | ES 执行失败 |
| `ROUTE_001` | `ErrorCode.ROUTE_RESOLVE_FAILED` | 路由解析失败 |
| `ES_001` | `ErrorCode.ES_REQUEST_BUILD_FAILED` | ES 请求构建失败 |
| `ES_002` | `ErrorCode.ES_RESPONSE_PARSE_FAILED` | ES 响应解析失败 |
| `UNSUPPORTED_001` | `ErrorCode.UNSUPPORTED_OPERATION` | 不支持的操作 |

异常类型为 `PersistenceExecutionException`，实现 `getErrorCode()` 方法。

---

## 🔌 版本兼容性

| Spring Boot | ES 客户端 | ES 服务端 | 验证级别 |
|---|---|---|---|
| 2.2.x | 6.8.x | 6.x | 主链路全量集成测试；byQuery 在 ES 6.2.2 下因 `ignore_throttled` 参数兼容问题暂不支持 |
| 2.3.12 | 7.6.x | 7.x | 全量集成测试 |
| 2.4.5 | 7.9.x | 7.x | 全量集成测试 |
| 2.7.9 | 7.17.x | 7.x | 全量集成测试（主版本） |

> 说明：Spring Boot 2.2.x 搭配的 ES 6.8.x 客户端在 byQuery 请求中会自动带 `ignore_throttled` 参数，ES 6.2.2 服务端不识别该参数。后续版本会补齐低版本 ES 的 byQuery 兼容路径。

