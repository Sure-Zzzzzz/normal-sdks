# Simple Elasticsearch Persistence Starter

基于 Spring Boot 的 Elasticsearch 写侧自动配置组件，配合 `simple-elasticsearch-route-starter` 提供声明式写入能力，自动继承路由规则、日期分片和多数据源配置。

## ✨ 核心特性

- ✍️ **统一写侧入口**：`PersistenceEngine` 封装 index / create / update / delete / bulk / by-query，一个 Bean 搞定全部写操作
- 🔗 **零侵入继承路由**：自动继承 route 的多数据源路由、日期分片索引渲染、async-write 规则
- ⚡ **双模式异步**：SDK 级 `indexAsync`（CompletableFuture）与 route 级 `async-write`（fire-and-forget）并存，各自独立
- 🧩 **强类型门面**：`engine.forEntity(MyDoc.class)` 省去重复传类型，支持自定义 validator / indexResolver / idResolver
- 🧱 **写入前扩展链**：`DocumentPreProcessor` 支持写入前补字段、清洗字段、生成 ID
- 🧰 **写入辅助工具**：`DocumentIdHelper` / `FieldValueNormalizerHelper` 提供稳定 ID 与字段标准化能力
- **写入参数透传**：支持 routing / pipeline / refreshPolicy / retryOnConflict / detectNoop / versionType / notFoundAsSuccess
- **Bulk 可靠性增强**：`batchSize` 真实分批，`continueOnFailure` 控制失败后是否继续，失败明细包含 status / errorType / errorReason / retryable
- **Bulk 失败分类扩展**：`BulkFailureClassifier` 支持自定义可重试判断，默认覆盖 408 / 429 / 500 / 502 / 503 / 504
- **冲突转更新辅助**：支持 create-then-update-on-conflict，CREATE 遇到 409 后只对冲突文档执行 UPDATE
- 🔍 **按查询批量操作**：`updateByQuery` / `deleteByQuery` 支持同步等待和服务端异步（返回 taskId 可轮询）
- 📡 **事件总线**：每次写操作后发布 `EsPersistenceEvent`（成功）/ `EsPersistenceErrorEvent`（失败），可接入审计、监控
- 🔌 **多版本兼容**：支持 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9，覆盖 ES 6.x / 7.x

## 📦 依赖配置

persistence starter 已内置 route starter 依赖，无需重复声明：

### Gradle

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-persistence-starter:1.1.0'
    implementation "org.springframework.boot:spring-boot-starter-data-elasticsearch"
    implementation "org.apache.httpcomponents:httpclient"
    implementation "org.apache.httpcomponents:httpcore"
}
```

### 版本关系

| starter | core | route-starter | 说明 |
|---------|------|---------------|------|
| 1.1.0 | 1.0.2 | 1.2.0 | 适配 route 1.2.0 兼容公共 Helper，新增 create-then-update-on-conflict |
| 1.0.2 | 1.0.2 | 1.1.2 | 写入参数透传、bulk 分批与失败明细、TypedPersistence 增强 |
| 1.0.1 | 1.0.1 | 1.1.2 | 写入前处理链、稳定 ID/字段标准化 Helper、2.2.x / ES 6.2.2 byQuery 兼容 |
| 1.0.0 | 1.0.1 | 1.1.2 | 首个写侧 starter 版本，支持 scriptedUpsert 透传 |

## 1.1.0 升级指南

- 直接升级 starter 依赖到 `simple-elasticsearch-persistence-starter:1.1.0`。
- `simple-elasticsearch-persistence-core` 继续使用 `1.0.2`，调用方无需单独升级 core。
- starter 已传递依赖 `simple-elasticsearch-route-starter:1.2.0`；如果工程里显式声明了 route 依赖，请同步调整到 `1.2.0`，避免 Helper API 版本不一致。
- 原有 index / create / update / delete / bulk / byQuery 行为保持不变；create-then-update-on-conflict 是新增可选能力，不会改变默认写入语义。
- bulk 冲突转更新模式下，resolver 返回 `null` 会保留原 conflict failure；第二阶段 UPDATE 不继承 CREATE 阶段 pipeline。

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
              - pattern: "test-event.*"
                type: wildcard
                datasource: primary
                write-index:
                  template: "test-event-{yyyy.MM.dd}"
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
@Document(indexName = "test-event.type-a")
@Data
public class TestEvent {
    @Id
    private String id;
    private String fieldA;
    private String fieldB;
    private long ts;
}
```

### 4. 写入文档

```java
@Autowired
private PersistenceEngine engine;

TestEvent doc1 = new TestEvent();
doc1.setId("test-1");
doc1.setFieldA("field-a-1");
doc1.setFieldB("field-b-1");
doc1.setTs(now);

// index（upsert 语义，已存在则覆盖）
PersistenceResult result = engine.index(doc1);
// result.getId()        → "test-1"
// result.getDatasource() → "primary"
// result.getTookMs()     → 实际耗时

TestEvent doc2 = new TestEvent();
doc2.setId("test-2");
doc2.setFieldA("field-a-2");
doc2.setFieldB("field-b-2");
doc2.setTs(now);

// create（仅新建，已存在抛 PersistenceExecutionException）
engine.create(doc2);

// 局部更新（doc 字段）
Map<String, Object> fields = new LinkedHashMap<String, Object>();
fields.put("fieldB", "field-b-updated");
engine.update(UpdateRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-1")
    .fieldMap(fields)
    .build());

// script 更新
Map<String, Object> params = new LinkedHashMap<String, Object>();
params.put("newTs", newTs);
engine.update(UpdateRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-1")
    .scriptSource("ctx._source.ts = params.newTs")
    .scriptParamMap(params)
    .build());

// 按 ID 删除
engine.delete(DeleteRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-1")
    .build());
```

### 5. scriptedUpsert

适用于“文档不存在时初始化 `extraField`，文档已存在时只刷新 `extraField2`”这类写入语义：

```java
long now = System.currentTimeMillis();
Map<String, Object> params = new LinkedHashMap<String, Object>();
params.put("now", now);

engine.update(UpdateRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-1")
    .scriptSource(
        "if (ctx._source.extraField == null) { ctx._source.extraField = params.now } " +
        "ctx._source.extraField2 = params.now")
    .scriptParamMap(params)
    .options(UpdateOptions.builder()
        .scriptedUpsert(true)
        .upsertDoc(Collections.emptyMap())
        .build())
    .build());
```

`scriptedUpsert(true)` 会让 ES 在文档不存在时也执行脚本；`upsertDoc` 可以传空 Map，用来触发 scripted_upsert。

#### 写入参数透传

1.0.2 起，常用 ES 写入参数会透传到实际请求：

```java
engine.index(IndexRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-1")
    .document(doc1)
    .options(IndexOptions.builder()
        .routing("test-route")
        .pipeline("test-pipeline")
        .refreshPolicy(SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_WAIT_FOR)
        .timeoutMs(30000L)
        .build())
    .build());

engine.update(UpdateRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-1")
    .fieldMap(fields)
    .options(UpdateOptions.builder()
        .routing("test-route")
        .retryOnConflict(3)
        .detectNoop(true)
        .build())
    .build());

engine.delete(DeleteRequest.builder()
    .index("test-event-2026.07.08")
    .id("test-missing")
    .options(DeleteOptions.builder()
        .routing("test-route")
        .versionType("external")
        .notFoundAsSuccess(false)
        .build())
    .build());
```

`refreshPolicy` 优先级高于历史 `refresh` 布尔值，支持 `true` / `false` / `wait_for`。`notFoundAsSuccess` 默认为兼容旧版本语义：`null` 或 `true` 时 delete 返回 `not_found` 仍视为成功，显式 `false` 时视为失败。

### 6. 写入前处理与 Helper

`DocumentPreProcessor` 会在 ES 请求构建前执行，适合统一补字段、清洗字段或生成 ID：

```java
@Component
public class TestEventPreProcessor implements DocumentPreProcessor {

    @Override
    public boolean supports(Class<?> entityClass) {
        return TestEvent.class.isAssignableFrom(entityClass);
    }

    @Override
    public Object process(Object document, DocumentProcessContext context) {
        TestEvent event = (TestEvent) document;
        event.setFieldB(FieldValueNormalizerHelper.trimLowerCase(event.getFieldB()));
        if (event.getId() == null) {
            event.setId(DocumentIdHelper.sha1(event.getFieldA(), event.getFieldB(), event.getTs()));
        }
        return event;
    }
}
```

`DocumentPreProcessor` 对 `index/create/bulk index/bulk create` 生效，不处理局部 update、script update、delete、byQuery。

### 7. 批量写入

```java
// 便捷批量 index（同类文档，走 @Document 解析索引名）
List<TestEvent> eventList = new ArrayList<TestEvent>();
eventList.add(doc1);
eventList.add(doc2);
eventList.add(doc3);
BulkResult bulk = engine.bulkIndex(eventList, BulkOptions.builder()
    .batchSize(500)
    .continueOnFailure(true)
    .timeoutMs(30000L)
    .refreshPolicy(SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_WAIT_FOR)
    .build());
// bulk.getTotal()          → 3
// bulk.getSucceeded()      → 成功条数
// bulk.isHasFailure()      → 是否存在 item 失败
// bulk.getBatchTotal()     → 实际提交批次数
// bulk.getStoppedOnFailure() → 是否因失败批次停止后续提交

// 混合 bulk（不同操作类型）
Map<String, Object> updateFields = new LinkedHashMap<String, Object>();
updateFields.put("fieldB", "field-b-updated");
List<BulkItem> items = new ArrayList<BulkItem>();
items.add(BulkItem.builder()
    .type(BulkItemType.INDEX)
    .document(doc4)
    .id("test-4")
    .routing("test-route")
    .pipeline("test-pipeline")
    .build());
items.add(BulkItem.builder()
    .type(BulkItemType.UPDATE)
    .id("test-1")
    .fieldMap(updateFields)
    .retryOnConflict(3)
    .detectNoop(true)
    .build());
items.add(BulkItem.builder().type(BulkItemType.DELETE).id("test-old").build());
BulkRequest req = BulkRequest.builder()
    .defaultIndex("test-event-2026.07.08")
    .itemList(items)
    .options(BulkOptions.builder()
        .batchSize(500)
        .continueOnFailure(false)
        .routing("default-route")
        .build())
    .build();
BulkResult result = engine.bulk(req);

for (BulkItemFailure failure : result.getFailureList()) {
    log.warn("bulk item failed index={} id={} status={} retryable={}",
        failure.getIndex(), failure.getId(), failure.getStatus(), failure.getRetryable());
}
```

`BulkOptions.batchSize` 会真实拆分批次提交；`continueOnFailure=false` 时，某批出现 item failure 后停止提交后续批次。已提交部分会体现在 `batchTotal`、`batchSucceeded`、`batchFailed`、`stoppedOnFailure` 和 `partial` 字段中。

#### create-then-update-on-conflict

1.1.0 起可封装“首次 CREATE，409 conflict 后 UPDATE”的两阶段写入模式。该 helper 只处理 `409 conflict`，不会吞掉 mapping 错误、路由失败、脚本错误等其它写入失败。

```java
Map<String, Object> updateFields = new LinkedHashMap<String, Object>();
updateFields.put("extraField", "value-2");
updateFields.put("extraCount", 2);

PersistenceResult result = engine.createThenUpdateOnConflict(
    IndexRequest.builder()
        .index("test-event-2026.07.08")
        .id("test-1")
        .document(doc1)
        .build(),
    UpdateRequest.builder()
        .index("test-event-2026.07.08")
        .id("test-1")
        .fieldMap(updateFields)
        .options(UpdateOptions.builder().retryOnConflict(3).build())
        .build());
```

批量场景会先提交 CREATE bulk，再筛选 status=409 的 item 生成 UPDATE bulk；非 409 失败会保留在最终 `BulkResult.failureList` 中。

```java
BulkResult result = engine.bulkCreateThenUpdateOnConflict(
    BulkRequest.builder()
        .defaultIndex("test-event-2026.07.08")
        .itemList(items)
        .options(BulkOptions.builder()
            .batchSize(500)
            .continueOnFailure(true)
            .pipeline("test-create-pipeline")
            .build())
        .build(),
    (createItem, failure) -> {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("extraField", "value-updated");
        fields.put("extraCount", 3);
        return BulkItem.builder()
            .type(BulkItemType.UPDATE)
            .index(createItem.getIndex())
            .id(createItem.getId())
            .routing(createItem.getRouting())
            .fieldMap(fields)
            .retryOnConflict(3)
            .detectNoop(true)
            .build();
    });
```

第二阶段 UPDATE 不会继承 CREATE 阶段的 ingest pipeline；如果 UPDATE 也需要 pipeline，请在 resolver 返回的 update item 或独立逻辑中显式处理。

如果需要自定义失败是否可重试，可覆盖默认 `BulkFailureClassifier`：

```java
@Bean
public BulkFailureClassifier bulkFailureClassifier() {
    return (status, errorType, errorReason) -> Integer.valueOf(
        SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_TOO_MANY_REQUESTS).equals(status);
}
```

### 8. 客户端异步

```java
CompletableFuture<PersistenceResult> future = engine.indexAsync(doc5);
future.thenAccept(r -> log.info("写入完成 index={} took={}ms", r.getIndex(), r.getTookMs()));

// 也有 createAsync / updateAsync / deleteAsync / bulkAsync / bulkIndexAsync
```

### 9. 按查询批量操作

```java
// 同步等待完成
Map<String, Object> updateTerms = new LinkedHashMap<String, Object>();
updateTerms.put("fieldB.keyword", "field-b-1");
ByQueryTaskResult result = engine.updateByQuery(
    UpdateByQueryRequest.builder()
        .index("test-event-2026.07.08")
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
        .index("test-event-2026.07.07")
        .query(PersistenceQuery.builder()
            .termMap(deleteTerms)
            .build())
        .options(ByQueryOptions.builder().waitForCompletion(false).build())
        .build());
if (!task.isCompleted()) {
    ByQueryTaskResult progress = engine.getTask(task.getDatasource(), task.getTaskId());
}
```

### 10. 强类型门面

```java
// 绑定实体类型，省去重复传 Class
TypedPersistence<TestEvent> typed = engine.forEntity(TestEvent.class);
typed.index(doc);
typed.create(doc);
typed.bulkIndex(docList, BulkOptions.builder().build());
typed.bulkCreate(docList, BulkOptions.builder().batchSize(500).build());

// 自定义 validator / resolver / 默认 options
TypedPersistence<TestEvent> safe = engine.forEntity(TestEvent.class)
    .withValidator(doc -> {
        if (doc.getFieldA() == null) throw new IllegalArgumentException("fieldA 不能为空");
    })
    .withIdResolver(doc -> "test-" + doc.getTs())
    .withRoutingResolver(doc -> "route-" + doc.getFieldA())
    .withDefaultIndexOptions(IndexOptions.builder()
        .refreshPolicy(SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_WAIT_FOR)
        .pipeline("test-pipeline")
        .build())
    .withDefaultBulkOptions(BulkOptions.builder()
        .batchSize(500)
        .continueOnFailure(true)
        .build());

safe.index(doc);
safe.create(doc);
safe.bulkCreate(docList);

safe.createThenUpdateOnConflict(doc, d -> {
    Map<String, Object> fields = new LinkedHashMap<String, Object>();
    fields.put("extraField", d.getFieldB());
    return UpdateRequest.builder()
        .fieldMap(fields)
        .options(UpdateOptions.builder().retryOnConflict(3).build())
        .build();
});

safe.bulkCreateThenUpdateOnConflict(docList, d -> {
    Map<String, Object> fields = new LinkedHashMap<String, Object>();
    fields.put("extraField", d.getFieldB());
    return BulkItem.builder()
        .fieldMap(fields)
        .retryOnConflict(3)
        .build();
}, BulkOptions.builder().batchSize(500).build());
```

`create` / `createAsync` / `bulkCreate` / `bulkCreateAsync` 会强制使用 CREATE 语义，即使默认 `IndexOptions` 中误传了 INDEX，也不会覆盖 create 语义。`createThenUpdateOnConflict` / `bulkCreateThenUpdateOnConflict` 会复用 typed 的 indexResolver / idResolver / routingResolver，并自动补齐 update 请求的 index/id/routing。

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
| 2.2.x | 6.8.x | 6.x | 全量集成测试，byQuery 走 low-level REST 兼容 ES 6.2.2 |
| 2.3.12 | 7.6.x | 7.x | 全量集成测试 |
| 2.4.5 | 7.9.x | 7.x | 全量集成测试 |
| 2.7.9 | 7.17.x | 7.x | 全量集成测试（主版本） |

## 1.1.0 发版前验证

已从头重跑 `simple-elasticsearch-persistence-starter:test`：

| Spring Boot | 工具链 | 结果 |
|---|---|---|
| 2.7.9 | Gradle 8.5 + Java 11 | 通过 |
| 2.4.5 | Gradle 7.6 + Java 8 + `spring.profiles.active=2.4.5` | 通过 |
| 2.3.12 | Gradle 7.6 + Java 8 + `spring.profiles.active=2.3.12` | 通过 |
| 2.2.x | Gradle 7.6 + Java 8 + `spring.profiles.active=2.2.x` | 通过 |

