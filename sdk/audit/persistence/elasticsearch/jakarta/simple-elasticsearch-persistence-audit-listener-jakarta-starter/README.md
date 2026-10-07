# simple-elasticsearch-persistence-audit-listener-jakarta-starter

将 Elasticsearch 持久化事件转换为审计记录，交给 Handler（审计处理器）异步处理。适用于已经接入 Persistence Jakarta、需要记录写入结果、任务状态或关联调用主体的 Spring Boot 应用。

支持单文档写入、批量写入、按查询更新/删除、任务查询的结果审计；可选接入宿主身份和 traceId（调用链追踪标识）。本模块不执行业务写入，也不提供审计存储、检索或 Metrics（指标采集）。

## 最小配置

审计模块只传递依赖 Core（公共事件契约），不会自动启用写入主链。以下配置适用于已接入 Persistence Jakarta、已有 Route（连接路由）和写入配置的应用：

```groovy
implementation "io.github.sure-zzzzzz:simple-elasticsearch-persistence-audit-listener-jakarta-starter:1.0.0"
implementation "io.github.sure-zzzzzz:simple-elasticsearch-persistence-jakarta-starter:1.0.0"
```

保留主模块已有 Route 和写入配置，追加：

```yaml
io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener:
  enable: true
  handler:
    log:
      enabled: true
```

总开关和默认日志处理器均默认关闭。上例将后续持久化事件转换为记录，以 INFO 级别写入日志，日志前缀为 `ES_PERSISTENCE_AUDIT`。它不回补历史事件，也不自动写入审计数据库。

需要自定义存储时，将 `handler.log.enabled` 设为 `false`，并注册自己的 `EsPersistenceAuditHandler`。没有任何处理器时，不注册监听器；仅设置 `enable=true` 不能代替处理器注册。

## 完整配置

```yaml
io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener:
  enable: true
  executor:
    core-size: 4
    max-size: 20
    queue-capacity: 2000
    keep-alive-seconds: 60
    reject-policy: CALLER_RUNS
  record:
    max-failure-size: 20
  handler:
    log:
      enabled: false
```

上例列出本模块全部配置项，使用自定义处理器而非默认日志处理器。身份与追踪标识 Provider（提供器）是可选的 Java 扩展接口，不是额外 YAML 配置项；不配置时对应记录字段为空，不引入 IAM、AKSK 或其他认证依赖。

## 配置说明

以下配置相对于 `io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener`：

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `enable` | `false` | 审计总开关；关闭时不装配 SDK 默认组件，不删除宿主自定义 Bean |
| `executor.core-size` | `4` | 核心线程数，必须大于 0 |
| `executor.max-size` | `20` | 最大线程数，不得小于核心线程数 |
| `executor.queue-capacity` | `2000` | 等待任务容量，不得为负；0 表示不排队 |
| `executor.keep-alive-seconds` | `60` | 超出核心数量的空闲线程存活秒数，不得为负 |
| `executor.reject-policy` | `CALLER_RUNS` | 饱和时策略，见下表；不区分大小写，非法值使启动失败 |
| `record.max-failure-size` | `20` | 事件包含失败明细时的记录上限；非正数回退 20，不改变总体失败统计 |
| `handler.log.enabled` | `false` | 是否追加默认日志处理器；可与自定义处理器并存 |

| 拒绝策略 | 饱和时行为 | 取舍 |
| --- | --- | --- |
| `CALLER_RUNS` | 在提交审计任务的线程处理 | 不主动丢弃饱和任务，但会增加该线程的处理延迟 |
| `DISCARD` | 丢弃当前任务 | 接受审计缺失，保留拒绝告警 |
| `DISCARD_OLDEST` | 丢弃队列最旧任务并重提当前任务 | 接受历史审计缺失；零容量队列直接丢弃当前任务 |
| `ABORT` | 拒绝提交，监听器隔离该异常 | 业务不因审计拒绝而失败，但该记录不会送达 |

上述为 SDK 默认执行器的行为。关闭后的提交不保证送达，均有拒绝告警；宿主替换执行器时，拒绝行为以宿主实现为准。默认线程池由 Spring 初始化和销毁，不是持久化队列，不提供必达、重试、严格顺序或恰好一次保证。

线程池优先使用核心线程并排队，队列满后才扩容至最大线程数。较大的队列会延迟扩容并增加内存占用；`max-size` 不是通常情况下立即启动的线程数。

## 接入自定义处理器

公开类型位于 `io.github.surezzzzzz.sdk.audit.persistence.elasticsearch`。处理器使用 `EsPersistenceAuditHandler`，记录使用 `EsPersistenceAuditRecord`，主体与追踪标识使用 `EsPersistenceAuditUserProvider` 和 `EsPersistenceAuditTraceIdProvider`。

```java
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SamplePersistenceAuditConfiguration {
    /** 接入宿主提供的审计存储。 */
    @Bean
    public EsPersistenceAuditHandler samplePersistenceAuditHandler(AuditSink sink) {
        return sink::append;
    }

    public interface AuditSink {
        /** 记录一条操作摘要；持久化和去重由实现负责。 */
        void append(EsPersistenceAuditRecord record);
    }
}
```

`AuditSink` 由应用实现并注册为 Bean。本模块会依次调用所有处理器；单个处理器修改记录或抛出异常，不影响其他处理器。每个处理器拿到独立记录快照，不修改原事件。

处理器应在宿主配置中按接口类型注册。若处理器由另一个自动配置提供，需要让它先于 `PersistenceAuditConfiguration` 注册，确保装配时能识别该处理器。日志处理器与自定义处理器可以并存，但它们收到的是同一操作的独立快照，不是两次业务写入。

## 记录说明

### 操作与结果

`operationType` 使用以下代码；具体操作事件由 Persistence 主模块发布，监听器不会自行轮询任务或执行写入。

| `operationType` | 操作 |
| --- | --- |
| `index` | 索引写入 |
| `create` | 仅新增写入 |
| `update` | 局部更新 |
| `delete` | 按 ID 删除 |
| `bulk` | 批量写入 |
| `update_by_query` | 按查询更新 |
| `delete_by_query` | 按查询删除 |
| `get_task` | 查询服务端任务状态 |

审计结果：

| `result` | 含义 |
| --- | --- |
| `success` | 事件明确报告成功 |
| `failure` | 明确的失败终态 |
| `partial_failure` | 批量部分结果或失败；按查询结果有明确失败/冲突明细时也可产生 |
| `task_submitted` | 服务端任务已提交，尚未完成；不能当作执行成功 |
| `task_completed` | 任务已完成，但事件未提供失败明细，不能推定成功 |

`task_submitted` 的 `success=true` 只表示提交被接受，不表示任务执行成功。消费端应先判断 `result`，再结合操作类型和统计字段处理；不能只用 `success` 判断所有操作的最终结果。

### 字段如何使用

| 字段 | 含义与使用方式 |
| --- | --- |
| `timestamp` | 事件时间，Unix 毫秒时间戳；不是处理器落库完成时间 |
| `sourceType`、`operationType` | 来源固定为 `PERSISTENCE`；操作类型用于区分写入、批量和任务查询 |
| `clientId`、`clientType`、`userId`、`username`、`traceId` | 由可选 Provider 提供，不从请求正文或凭据推导 |
| `datasource` | 事件报告的数据源标识，不是连接地址 |
| `result`、`success`、`partial`、`conflict` | 操作结果及证据标记；可空字段的 `null` 表示未知 |
| `clientAsync`、`routeAsyncWrite`、`serverAsyncTask` | 分别表示客户端异步、Route 异步写接管、服务端任务形态；不能互相替代 |
| `bulkItemCount`、`bulkSucceeded`、`bulkFailed` | 批量文档数量和结果统计，不是失败明细条数 |
| `batchTotal`、`batchSucceeded`、`batchFailed` | 批量执行的批次统计，不是文档数量 |
| `total`、`updated`、`deleted`、`versionConflicts` | 按查询任务的处理和版本冲突统计 |
| `startTimeMs`、`tookMs` | 事件报告的开始时间和耗时，单位毫秒；不包含审计排队及落库耗时 |
| `errorCode`、`errorClass`、`errorMessage` | 失败事件提供的错误摘要；不存在的值不补造 |
| `requestType`、`index`、`documentId`、`taskId`、`failureList` | 受事件发布者的数据裁剪约束，不保证提供，见下文 |

正式 Persistence Jakarta 发布的记录包含操作类型、数据源、耗时、异步标记和批量/按查询统计；请求、索引、文档 ID、正文、脚本、任务 ID 和失败明细不发布。对应记录字段为 `null`，失败明细列表为空。批量明细缺失时 `conflict=null` 表示未知；按查询完成事件没有明细时 `success=null`，没有冲突证据时 `partial=null`。这些值不能解释为成功或没有失败。

扩展发布者提供完整 Core 结果时，监听器能转换限量失败明细；总体冲突判断扫描完整明细，不受记录上限影响。监听器不收集文档正文或脚本。

## 最佳实践

### 1. 关联宿主已有身份

实现 `EsPersistenceAuditUserProvider`，返回已认证的客户端或用户信息；实现 `EsPersistenceAuditTraceIdProvider`，返回当前调用链标识。不要返回原始凭据、Authorization、Cookie 或令牌。缺少 Provider 或 Provider 某一字段提取失败时，仅该字段为空，不阻断审计。

```java
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SamplePersistenceAuditIdentityConfiguration {
    /** 从宿主已有认证上下文读取主体，不在此解析或验证凭据。 */
    @Bean
    public EsPersistenceAuditUserProvider samplePersistenceAuditUserProvider(CurrentAuditContext context) {
        return new EsPersistenceAuditUserProvider() {
            @Override
            public String getClientId() { return context.getClientId(); }
            @Override
            public String getClientType() { return context.getClientType(); }
            @Override
            public String getUserId() { return context.getUserId(); }
            @Override
            public String getUsername() { return context.getUsername(); }
        };
    }

    /** 使用宿主已有调用链标识。 */
    @Bean
    public EsPersistenceAuditTraceIdProvider samplePersistenceAuditTraceIdProvider(CurrentAuditContext context) {
        return context::getTraceId;
    }

    /** 由宿主实现并注册；不存在或不允许记录的信息返回 null。 */
    public interface CurrentAuditContext {
        String getClientId();
        String getClientType();
        String getUserId();
        String getUsername();
        String getTraceId();
    }
}
```

`CurrentAuditContext` 是示例中的宿主适配接口，不是 SDK 自带组件。机器调用没有用户信息时，`userId` 和 `username` 应保持 `null`；用户名不宜作为稳定主体标识。每种 Provider 保持一个可选候选 Bean，多实现时用 `@Primary` 明确选择，不将全部 Provider 当作处理器列表分发。

身份在监听方法中、投递专用审计线程池之前提取。默认同步事件广播时，这就是事件发布线程；宿主改用异步事件广播器时，需要自行处理上下文传递。客户端异步写入通常在 Persistence 工作线程发布事件，原请求 ThreadLocal（线程本地上下文）不会自动继承；不能依赖本模块补回调用者身份。

### 2. 对未知值保留未知语义

区分任务提交、任务完成与明确成功；不要把 `success=null`、无失败明细或 `taskId=null` 当作成功。批量失败数量有值但冲突标记为空时，不推断失败原因。

### 3. 根据可靠性要求选择处理器

日志适合接入初期或诊断。需要长期保存时实现独立处理器，并自行处理写入失败、去重、保留期与存储权限。若处理器仍调用相同 Persistence 主链，应隔离事件来源或使用不触发同类事件的存储路径，避免递归审计。

审计处理失败仅记录处理器类型和异常类型，不输出异常消息、正文或堆栈；默认日志处理器会输出完整审计记录，启用前确认主体与扩展事件字段符合日志数据政策。

### 4. 按峰值调整队列与线程

先量测处理器延迟，再配置线程和队列。默认 `CALLER_RUNS` 可将压力反馈到提交任务的线程，同步事件广播时会增加业务延迟；延迟敏感应用可以选择丢弃策略，但应监控拒绝告警并接受记录缺失。不要把内存队列当作可靠审计存储。

### 5. 按模块隔离处理器和执行器

同名 `esPersistenceAuditExecutor` Bean 可替换专用执行器，类型必须实现 `java.util.concurrent.Executor`；宿主自行负责其资源生命周期和拒绝策略。此时 `executor.*` 不再配置宿主执行器。

注册 `EsPersistenceAuditEventListener` 类型的 Bean 可替换默认监听器，其处理逻辑和异常隔离由宿主负责。Persistence 和 Search 审计使用不同的监听器、处理器接口及执行器 Bean 名，可同时引入，各自独立启用；不要直接复用会触发自身事件的审计写入通道。

### 6. 使用受控诊断日志

默认日志处理器负责输出审计记录；SDK 的 DEBUG 日志仅用于诊断转换、分发和主体提取，不替代长期审计存储。排障时可临时开启本模块日志：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.audit.persistence.elasticsearch: DEBUG
```

关注线程池拒绝和处理器失败的 WARN 告警。处理器失败不会自动重试；需要重试和可靠交付时，由独立存储处理器实现，不把业务调用正常返回理解为审计已经落库。

## 兼容性与边界

| 项目 | 范围 |
| --- | --- |
| Spring Boot | 验证基线为 `3.4.2` |
| Java | Java 17 编译目标，Java 17/21 运行验证 |
| Elasticsearch | 随 Persistence 主链支持 ES7/8；实际联调版本为 `7.17.16`、`8.17.0`；不支持 ES6，ES9 不在支持范围 |
| 事件依赖 | `simple-elasticsearch-persistence-core:1.0.3`；不传递引入写入主模块或认证组件 |
| 自动装配 | 使用 Spring Boot 3 自动配置发现，无需手工扫描 SDK 包 |
| 与旧线混用 | 不支持；保留旧线公开包名、接口和配置前缀，新旧制品包含同名 Java 类型 |

本模块只消费持久化事件，不提供审计历史查询、任务状态主动追踪或可靠消息队列。需要这些能力时，由应用自己的存储、查询和交付机制承担。

## 兼容矩阵

| Spring Boot | Java | 验证范围 |
|---|---:|---|
| 3.4.2 | 17 / 21 | 事件模拟链全量测试（基线） |
| 3.3.13 | 17 | 事件模拟链全量测试 |
| 3.2.12 | 17 | 事件模拟链全量测试 |
