# Simple Elasticsearch Persistence Jakarta Starter

为 Spring Boot 3 应用提供 Elasticsearch 文档写入、局部更新、删除、批量操作和按查询修改。业务只调用 `PersistenceEngine`；索引路由、连接、认证和 TLS（传输加密）统一交给 Elasticsearch Route。

适合已有 Route 数据源配置、需要确认写入结果的业务。不负责索引模板、集群运维或用户权限。

## 依赖

使用 Spring Boot `3.4.2`、Java `17` 或 `21`；编译目标为 Java `17`。Elasticsearch Route（数据源连接和索引路由组件）由本组件传递引入。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-persistence-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
}
```

## 最小配置

启用 Route 和 Persistence 即可注入 `PersistenceEngine`，无需再创建独立 ES 客户端。地址和服务端版本按实际环境替换：

```yaml
io.github.surezzzzzz.sdk.elasticsearch:
  route:
    enable: true
    default-source: primary
    sources:
      primary:
        urls: https://es.example.test:9200
        server-version: 8.17.0
  persistence:
    enable: true
```

地址、认证与证书策略只配在 `route` 下。启用 Persistence 而没有 Route 注册表时，应用启动失败，不回退直连。

## 完整配置

下面列出 Persistence 的全部配置项，Route 部分只保留最小连接配置。除显式启用外，写入配置采用组件默认值；批次大小、刷新策略、超时和文档路由等是每次调用的请求选项，不是全局配置。

```yaml
io.github.surezzzzzz.sdk.elasticsearch:
  route:
    enable: true
    default-source: primary
    sources:
      primary:
        urls: https://es.example.test:9200
        server-version: 8.17.0
  persistence:
    enable: true
    async:
      core-size: 4
      max-size: 16
      queue-capacity: 1000
    by-query:
      allow-match-all: false
```

## 配置项说明

### 连接配置

下列路径相对于 `io.github.surezzzzzz.sdk.elasticsearch.route`。业务连接、认证、TLS 和客户端生命周期统一由 Route 管理，业务不得自行关闭借用的客户端。

| 配置项 | 示例值 | 说明 |
| --- | --- | --- |
| `enable` | `true` | 启用 Route 数据源托管 |
| `default-source` | `primary` | 默认数据源键，须与 `sources` 中的键一致 |
| `sources.<key>.urls` | `https://es.example.test:9200` | ES 连接地址；示例域名仅作占位 |
| `sources.<key>.server-version` | `8.17.0` | 显式指定实际 ES 服务端版本，不能填宿主客户端的版本 |

### 写入配置

下列路径相对于 `io.github.surezzzzzz.sdk.elasticsearch.persistence`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enable` | `false` | 显式启用写入组件；必须同时有 Route 数据源注册表 |
| `async.core-size` | `4` | 客户端异步线程池的核心线程数，必须为正数 |
| `async.max-size` | `16` | 最大线程数，不能小于核心线程数 |
| `async.queue-capacity` | `1000` | 等待队列容量，不能为负数；`0` 表示不缓存等待任务 |
| `by-query.allow-match-all` | `false` | 是否允许没有有效过滤范围的按查询修改；开启后仍受具体索引和删除数量限制，不代表任意全量操作都可执行 |

### 每次调用的选项

请求的 `options` 与 YAML 配置分开。单条使用 `IndexOptions`、`UpdateOptions`、`DeleteOptions`，批量使用 `BulkOptions`，按查询修改使用 `ByQueryOptions`；通过各自的 `builder()` 构造。

| 选项 | 适用范围 | 说明 |
| --- | --- | --- |
| `refreshPolicy` | 写入、更新、删除、bulk | `true` 立即刷新，`false` 不强制刷新，`wait_for` 等待刷新后返回；未指定时使用 ES 默认行为 |
| `refresh` | 全部写操作 | Boolean 刷新选项；不要与 `refreshPolicy` 同时指定，值冲突会在发送前被拒绝；按查询修改不支持 `wait_for` |
| `routing` | 全部写操作 | 文档分片路由值，不是 Route 数据源键；更新和删除必须与原写入保持一致 |
| `timeoutMs` | 全部写操作 | 正数，作为 ES 请求的 `timeout` 参数传入；不是宿主 HTTP 连接/读取超时，也不是整个异步任务的截止时间 |
| `pipeline` | index/create、bulk | ES ingest pipeline（服务端写入处理流水线）名称；须由调用方预先创建 |
| `batchSize` | bulk | 每批发送的项数，必须为正数；未指定时一次发送全部输入，建议按文档大小限制批次 |
| `continueOnFailure` | bulk | 默认继续后续批次；设为 `false` 后在出现逐项失败的批次结束时停止，不回滚已成功项 |

`BulkItem` 还可指定自身的 `routing` 和 `pipeline`。按查询修改的处理数量、节流和服务端任务选项见下文示例；这些选项不会自动转换为访问权限或事务保障。

## 最佳实践

### 1. 单条写入、更新和删除

以下代码在注入的 `PersistenceEngine engine` 上调用。显式指定索引时可以使用普通 Map：

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import java.util.Map;

PersistenceResult saved = engine.index(IndexRequest.builder()
        .index("sample-record").id("record-001")
        .document(Map.of("status", "ready", "amount", 12))
        .options(IndexOptions.builder().refreshPolicy("wait_for").build())
        .build());
```

`index` 写入或覆盖文档；`create` 只新建，已有 ID 返回 HTTP 409 冲突。`index` 可以不指定 ID，由 ES 生成；`create` 必须能从请求或实体解析出 ID。

结果包含 `success`、`id`、`index`、`datasource`、`operationType`、`result` 和 `tookMs`（毫秒耗时）。正常执行返回结果，参数、协议和单条 ES 执行错误抛异常，不以一个空结果代表失败。`index` 是实际写入的物理索引，应和 `id` 一起保留用于后续更新和删除；结果不包含版本号或序列号。

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteRequest;

engine.update(UpdateRequest.builder()
        .index(saved.getIndex()).id(saved.getId())
        .fieldMap(Map.of("status", "done")).build());

engine.delete(DeleteRequest.builder()
        .index(saved.getIndex()).id(saved.getId()).build());
```

更新可传局部 `fieldMap` 或 Painless（ES 脚本语言）脚本，二者不能同时传；更新选项支持文档不存在时插入、无变化检测和版本冲突重试。删除仅在显式 `notFoundAsSuccess=true` 且确认是文档不存在的 404 时按成功返回，索引不存在不会被吞掉。

`refreshPolicy=wait_for` 适合写完马上读取的场景；吞吐优先时不要对每条写入强制刷新。成功写入不等于立即能被搜索命中，应结合 ES 的刷新周期和请求选项判断。

### 2. 批量写入与失败处理

`BulkRequest` 可混合 `INDEX`、`CREATE`、`UPDATE`、`DELETE`。全部 item（批量中的一条操作）先校验、确定唯一数据源并序列化，再发送第一批。

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import java.util.List;
import java.util.Map;

BulkResult result = engine.bulk(BulkRequest.builder()
        .itemList(List.of(
                BulkItem.builder().type(BulkItemType.CREATE).index("sample-record")
                        .id("record-002").document(Map.of("status", "ready")).build(),
                BulkItem.builder().type(BulkItemType.INDEX).index("sample-record")
                        .id("record-003").document(Map.of("status", "done")).build()))
        .options(BulkOptions.builder().batchSize(100).continueOnFailure(false)
                .refreshPolicy("wait_for").build())
        .build());
```

- ES 返回逐项失败时，正常返回 `BulkResult`，检查 `failureList`、`succeeded`、`failed`，不能只看 HTTP 成功。
- `continueOnFailure=false` 遇到失败后不再发送后续批次；`stoppedOnFailure` 表示提前停止，未执行项不计入成功或已执行失败。
- 网络或响应解析失败抛 `BulkPersistenceExecutionException`，通过 `partialResult` 获取已确认结果。失败的当前批次可能已写入，不能直接认定它可以安全重放。
- `batchTotal`、`batchSucceeded`、`batchFailed` 是已完成批次统计，不是文档数。

建议同时检查 `success`、`failureList` 和 `stoppedOnFailure`，用失败项的原始下标关联输入。`total` 是输入项数，提前停止时 `succeeded + failed` 可以小于 `total`。请求级异常要区分“已确认成功”和“执行结果未知”，只对经过业务幂等判断的目标重试，不能自动重放整批。

### 3. CREATE 冲突补偿

`createThenUpdateOnConflict(createRequest, updateRequest)` 仅在 CREATE 返回 409 后更新。补偿缺失的索引、ID、routing（文档分片路由值）继承 CREATE 的固定目标；显式传入不一致的值会在 CREATE 前失败，跨午夜也不会重算写分片。

批量使用 `bulkCreateThenUpdateOnConflict(request, resolver)`。resolver（补偿生成函数）只处理 CREATE 的 409 项，返回 `UPDATE`，不可切换目标。二阶段不是事务：UPDATE 阶段失败抛 `BulkConflictExecutionException`，同时保留 CREATE 结果、UPDATE 部分结果和原 item 下标映射。批次统计涵盖两个阶段，即使冲突全部修复，历史 CREATE 失败批次数仍保留。

适合以固定 ID 去重、重复创建时只更新指定字段的场景。不要将权限失败、网络超时或其他状态当成 409 补偿；也不要把它当成跨文档原子事务。

### 4. 客户端异步调用

单条、批量和补偿操作均有返回 `CompletableFuture` 的异步入口。默认执行器在排队前冻结协议请求，排队期间不再读调用方的正文和 options（操作选项）；预检错误可能同步抛出，队列拒绝和执行错误通过 Future 返回。批量补偿函数在发现冲突后执行，函数捕获的业务对象须由调用方保持稳定。

必须观察 Future 的完成和异常结果，并对提交速度施加限制；不要丢弃 Future 后直接宣布写入成功。增大线程数和队列不能代替业务背压（限制待处理任务量），参数应结合 ES 吞吐和宿主内存调整。

客户端异步与 ES 服务端任务是两种模式，本组件不使用 Route 的 `async-write` 即发即忘模式。

### 5. 实体写入与 Typed 门面

带 Spring Data `@Document`、`@Id` 注解的实体可直接 `engine.index(entity)`；也可用 `engine.forEntity(Entity.class)` 建立 `TypedPersistence<Entity>`（绑定实体类型的门面）。门面支持：

- `withIndexResolver`、`withIdResolver`、`withRoutingResolver` 自定义实体定位。
- `withValidator` 添加写前实体校验。
- `withDefaultIndexOptions`、`withDefaultBulkOptions` 复制无参调用的默认选项。
- 显式 options 是一次完整选择，不与默认值合并，也不接受 null 代表默认。

同类实体批量写入优先使用 Typed 门面，索引、ID、routing 的解析规则集中维护；无注解的实体也可以通过自定义解析函数定位。前置校验只负责本次写入的输入合法性，不代替业务访问控制。

例如，使用以下实体集中维护固定 ID 和写入选项。示例实体使用 Lombok，宿主需要配置 Lombok 注解处理：

```java
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Getter
@Builder
@Document(indexName = "sample-record")
public class SampleRecordEntity {
    @Id
    private String id;
    private String code;
    private String status;
}
```

以下代码使用已经注入的 `engine`。不需要自定义 ID 时可去掉 `withIdResolver`，改为给实体的 `@Id` 字段赋值；CREATE 不能缺少 ID：

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.TypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentIdHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.FieldValueNormalizerHelper;
import java.util.List;

TypedPersistence<SampleRecordEntity> records = engine.forEntity(SampleRecordEntity.class)
        .withValidator(row -> {
            if (FieldValueNormalizerHelper.blankToNull(row.getCode()) == null) {
                throw new IllegalArgumentException("code 不能为空");
            }
        })
        .withIdResolver(row -> DocumentIdHelper.sha256(
                FieldValueNormalizerHelper.trimLowerCase(row.getCode())))
        .withDefaultIndexOptions(IndexOptions.builder().refreshPolicy("wait_for").build())
        .withDefaultBulkOptions(BulkOptions.builder().batchSize(100)
                .continueOnFailure(false).refreshPolicy("wait_for").build());

SampleRecordEntity row = SampleRecordEntity.builder()
        .code("SAMPLE-001").status("ready").build();
PersistenceResult created = records.create(row);
BulkResult written = records.bulkIndex(List.of(row));
```

`with*` 方法返回新门面，必须保存返回值；默认选项会复制，不修改原门面。示例先 CREATE，再以相同固定 ID 覆盖同一文档，批量结果仍需逐项检查。解析出的 ID 不会回填实体，应从写入结果保留实际 ID 和物理索引。索引模板、字段映射和 pipeline 由调用方准备，本组件不会根据 `@Document` 自动创建它们。

### 6. 固定文档 ID 与字段标准化

`DocumentIdHelper` 提供 `uuid()`、`sha1(String/Object...)`、`sha256(String/Object...)` 和 `join(Object...)`。随机 UUID 适合新文档；重复写入同一目标时，应使用固定 ID 或固定字段的指纹。新接入优先 SHA-256，SHA-1 仅用于兼容已有文档 ID，不用于密码存储、签名或消息认证。

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentIdHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.FieldValueNormalizerHelper;

String code = FieldValueNormalizerHelper.trimLowerCase(" SAMPLE-001 ");
String documentId = DocumentIdHelper.sha256(code);

engine.index(IndexRequest.builder().index("sample-record").id(documentId)
        .document(Map.of("code", code, "status", "ready")).build());
```

Typed 门面可通过 `withIdResolver(row -> DocumentIdHelper.sha256(...))` 集中生成 ID。字符串哈希采用 UTF-8，返回定长小写十六进制字符串，null 按空字符串处理；应先校验必填定位字段，不能把缺失字段的哈希当成有效 ID。多字段方法按原顺序用 `|` 拼接，不转义分隔符，不区分 null 与空字符串，也不自动标准化；如果字段可能含 `|` 或必须区分 null，应先建立无歧义的固定编码再调用字符串哈希方法。不要用可变字段、未规范排序的 Map 或随机 UUID 计算需要重复定位的 ID。

`FieldValueNormalizerHelper` 的全部方法如下，可在 `DocumentPreProcessor`（写前处理器）中显式组合：

| 方法 | 行为 |
| --- | --- |
| `trim` | 按 Java `String.trim` 去除首尾空白 |
| `lowerCase` | 使用固定 ROOT 语言规则转小写 |
| `trimLowerCase` | 去首尾空白后转小写 |
| `fullWidthToHalfWidth` | 全角 ASCII 和全角空格转半角，其他字符不变 |
| `blankToNull` | 去首尾空白，空结果转 null |
| `collapseWhitespace` | 去首尾空白，正则 `\s` 匹配的连续空白压缩为一个空格 |
| `normalizeList` | 按顺序处理到新列表，不去重、不删除 null；函数为 null 时只复制列表 |

字符串方法均保留 null；列表为 null 时返回空列表。`trim` 和 `collapseWhitespace` 不覆盖全部 Unicode 空白；全角空格需先转半角。工具不会自动改变请求、索引或 ID，是否标准化由调用方决定。

若希望每次实体写入都标准化正文，可注册写前处理器。以下示例沿用上一节实体，返回新对象，不修改调用方原对象：

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.FieldValueNormalizerHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class SamplePersistenceConfiguration {
    @Bean
    @Order(100)
    public DocumentPreProcessor sampleRecordNormalizer() {
        return new DocumentPreProcessor() {
            @Override
            public boolean supports(Class<?> entityClass) {
                return SampleRecordEntity.class.equals(entityClass);
            }

            @Override
            public Object process(Object document, DocumentProcessContext context) {
                SampleRecordEntity row = (SampleRecordEntity) document;
                return SampleRecordEntity.builder().id(row.getId())
                        .code(FieldValueNormalizerHelper.trimLowerCase(row.getCode()))
                        .status(FieldValueNormalizerHelper.blankToNull(row.getStatus())).build();
            }
        };
    }
}
```

处理器按 Spring 顺序执行，后一个接收前一个的结果；返回 null 会明确失败，不表示跳过文档。处理发生在索引和 ID 定位之后，不能靠正文标准化重新计算目标；参与 ID 的字段应在 `withIdResolver` 中使用相同规则。此处理器适用于实体 index/create 及对应 bulk 项，不会替代局部更新或 by-query 脚本的输入处理。

### 7. 日期分片中的历史文档定位

Route 的 `write-index.template` 可以把 index/create 的逻辑索引渲染为当前物理分片。原逻辑索引与物理索引必须属于同一数据源。

`update`、`delete` 及 bulk 中对应 item 必须指定可定位文档的物理索引，绝不自动转到今天的分片。`updateByQuery`、`deleteByQuery` 使用原样读索引表达式，不渲染写模板。

不要仅保存逻辑索引名，随后按当前日期重新计算历史文档位置。需要自定义 routing 时，还应保留写入所用的 routing 值，在后续操作中一致传入。

### 8. 按查询修改与服务端任务

`PersistenceQuery` 支持 term、range 和原生 query JSON；原生 JSON 不能同时混用 term/range。默认拒绝空条件、`match_all`（匹配全量）及没有有效正向约束的 bool。此保护不是任意 DSL（查询描述语言）的全量语义证明，调用方仍须控制修改范围。

```java
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import java.util.Map;

ByQueryTaskResult task = engine.updateByQuery(UpdateByQueryRequest.builder()
        .index("sample-record")
        .query(PersistenceQuery.builder().termMap(Map.of("status", "ready")).build())
        .scriptSource("ctx._source.status = params.status")
        .scriptParamMap(Map.of("status", "done"))
        .options(ByQueryOptions.builder().waitForCompletion(false)
                .maxDocs(100L).requestsPerSecond(100f).build())
        .build());

ByQueryTaskResult progress = engine.getTask(task.getDatasource(), task.getTaskId());
```

该示例要求 `status` 为 keyword（精确值）字段。`waitForCompletion=false` 返回服务端 `taskId`，后续在原数据源以带截止时间的轮询查询进度，不做无间隔循环。`completed=true` 不等于全部成功，必须检查 `failureList` 和 `versionConflicts`，再核对 `updated/deleted/total`。任务查询 404 作为执行异常返回，不能当成任务完成。

显式放开全量时，只允许一个非通配的具体索引；全量 delete-by-query 还必须给正数 `maxDocs`。建议即使有过滤条件也限制处理数量并设置节流；脚本参数单独传入，避免把动态值拼接进脚本文本。

### 9. 扩展、事件与诊断

公开扩展接口包括 `PersistenceEngine`、`PersistenceExecutor`、`PersistencePayloadCodec`、`DocumentPreProcessor` 和 `BulkFailureClassifier`。默认 Engine、编解码器、分类器和处理链按类型让位；自定义请求执行器会替换该请求类型的默认实现，重复注册同一种请求的执行器会失败。默认 JSON 编解码器独占 ObjectMapper，不使用宿主应用的 JSON 配置。

异步执行器按名称 `esPersistenceAsyncExecutor` 让位，而非按 `Executor` 类型让位；宿主自定义时应注册同名、实现 `Executor` 的 Bean，并自行管理其容量、拒绝策略和生命周期。默认线程池由 Spring 关闭，不要手动关闭共享执行器或 Route 托管客户端。

成功和失败发布 Core 事件；事件 request 为 null，结果为独立统计快照，监听器不得依赖原始正文。监听器异常不改变业务结果。确需排障时按包 `io.github.surezzzzzz.sdk.elasticsearch.persistence` 开启 DEBUG，默认只记录操作类型、数据源键、状态、计数和耗时，不记录正文、脚本、ID、索引名、凭据或 ES 原始原因。

## 兼容性与接入边界

| 项目 | 范围与验证基线 |
| --- | --- |
| 本组件 | `1.0.0`，Java 17 编译目标 |
| Spring Boot | `3.4.2` |
| Java 运行时 | 已验证 `17.0.20.1`、`21.0.12.1` |
| ES 支持范围 | `7.17+`、`8.x`；不支持 `6.x`、`9.x` |
| 真实 ES 验证 | `7.17.16`、`8.17.0`，两个独立单节点集群，不代表多节点或真实网络故障验收 |

上述环境均执行完整模块测试；生成的 JAR/POM 已由独立 Spring Boot `3.4.2` / Java `17` 宿主消费，完成 ES 两版本的写入后计数验证。其他 Spring Boot 补丁版本和 ES 补丁组合没有由此自动获得实测结论。

- 同次操作不跨数据源；多数据源通配目标必须能由配置证明唯一归属，无法证明时拒绝，而非猜默认源。
- 不支持 RestHighLevelClient（旧版高层客户端）扩展，与旧 javax Persistence Starter 互斥使用。
- 旧版 `PersistenceRequestValidator` / `PersistenceRequestValidatorRegistry` 不属于本版扩展接口；实体校验使用 Typed 门面的 `withValidator`，请求级业务校验放在调用前或自定义执行器中，不能直接迁移旧校验器 Bean。
- 不负责索引授权和脚本授权；请求必须先经过宿主业务的访问控制。

## 兼容矩阵

| Spring Boot | Java | 验证范围 |
|---|---:|---|
| 3.4.2 | 17 / 21 | 真实 ES 7.17 / 8.17 双集群（基线） |
| 3.3.13 | 17 | 真实 ES 7.17 / 8.17 双集群 |
| 3.2.12 | 17 | 真实 ES 7.17 / 8.17 双集群 |
