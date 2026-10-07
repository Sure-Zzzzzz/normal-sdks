# simple-elasticsearch-search-audit-listener-jakarta-starter

将 Elasticsearch 查询事件转换为审计记录，交给 Handler（审计处理器）异步处理。适用于已经接入 Search Jakarta、需要记录查询结果、耗时或关联调用主体的 Spring Boot 应用。

支持查询、计数、聚合的成功/失败审计，覆盖表达式搜索进入底层查询引擎后的操作；可选接入宿主身份和 traceId（调用链追踪标识）。本模块不执行业务查询，也不提供审计存储、检索或 Metrics（指标采集）。

## 最小配置

审计模块只传递依赖 Core（公共事件契约），不会自动启用查询主链。以下配置适用于已接入 Search Jakarta、已有 Route（连接路由）和查询配置的应用：

```groovy
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-audit-listener-jakarta-starter:1.0.0"
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-jakarta-starter:1.0.0"
```

保留主模块已有 Route 和查询配置，追加：

```yaml
io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener:
  enable: true
  handler:
    log:
      enabled: true
```

总开关和默认日志处理器均默认关闭。上例将后续查询事件转换为记录，以 INFO 级别写入日志，日志前缀为 `ES_AUDIT`。它不回补历史事件，也不自动写入审计数据库。

需要自定义存储时，将 `handler.log.enabled` 设为 `false`，并注册自己的 `EsAuditHandler`。没有任何处理器时，不注册监听器；仅设置 `enable=true` 不能代替处理器注册。

## 完整配置

```yaml
io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener:
  enable: true
  executor:
    core-size: 4
    max-size: 20
    queue-capacity: 2000
    keep-alive-seconds: 60
    reject-policy: CALLER_RUNS
  handler:
    log:
      enabled: false
```

上例列出本模块全部配置项，使用自定义处理器而非默认日志处理器。身份与追踪标识 Provider（提供器）是可选的 Java 扩展接口，不是额外 YAML 配置项；不配置时对应记录字段为空，不引入 IAM、AKSK 或其他认证依赖。

## 配置说明

以下配置相对于 `io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener`：

| 配置 | 默认值 | 含义 |
| --- | --- | --- |
| `enable` | `false` | 审计总开关；关闭时不装配 SDK 默认组件，不删除宿主自定义 Bean |
| `executor.core-size` | `4` | 核心线程数，必须大于 0 |
| `executor.max-size` | `20` | 最大线程数，不得小于核心线程数 |
| `executor.queue-capacity` | `2000` | 等待任务容量，不得为负；0 表示不排队 |
| `executor.keep-alive-seconds` | `60` | 超出核心数量的空闲线程存活秒数，不得为负 |
| `executor.reject-policy` | `CALLER_RUNS` | 饱和时策略，见下表；不区分大小写，非法值使启动失败 |
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

公开类型位于 `io.github.surezzzzzz.sdk.audit.search.elasticsearch`。处理器使用 `EsAuditHandler`，记录使用 `EsAuditRecord`，主体与追踪标识使用 `EsAuditUserProvider` 和 `EsAuditTraceIdProvider`。

```java
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SampleSearchAuditConfiguration {
    /** 接入宿主提供的审计存储。 */
    @Bean
    public EsAuditHandler sampleSearchAuditHandler(AuditSink sink) {
        return sink::append;
    }

    public interface AuditSink {
        /** 记录一条操作摘要；持久化和去重由实现负责。 */
        void append(EsAuditRecord record);
    }
}
```

`AuditSink` 由应用实现并注册为 Bean。本模块会依次调用所有处理器；单个处理器修改记录或抛出异常，不影响其他处理器。每个处理器拿到独立记录快照，不修改原事件。

处理器应在宿主配置中按接口类型注册。若处理器由另一个自动配置提供，需要让它先于 `SimpleElasticsearchAuditListenerConfiguration` 注册，确保装配时能识别该处理器。日志处理器与自定义处理器可以并存，但它们收到的是同一操作的独立快照，不是两次业务查询。

## 记录说明

### 操作范围

| 操作 | 监听的 Core 事件 | 记录结果 |
| --- | --- | --- |
| 查询、仅计数 | `EsQueryEvent`、`EsQueryErrorEvent` | `success` / `failure`；`countOnly` 区分查询和计数 |
| 聚合 | `EsAggEvent`、`EsAggErrorEvent` | `success` / `failure`；`total`、`countOnly` 为 `null` |
| 表达式查询、计数、聚合 | 委托底层 SearchEngine（查询引擎）后的同类事件 | 使用相同记录结构，不单独制造表达式来源标记 |

本模块消费已有事件，不主动执行查询。`result=success` 表示主链发布了成功事件，不表示审计记录已经持久化。

### 字段如何使用

| 字段 | 含义与使用方式 |
| --- | --- |
| `timestamp` | 事件时间，Unix 毫秒时间戳；不是处理器落库完成时间 |
| `clientId`、`clientType`、`userId`、`username`、`traceId` | 由可选 Provider 提供，不从查询参数或凭据推导 |
| `result` | `success` 或 `failure`，以主链事件为准 |
| `datasource`、`downgradeLevel` | 数据源标识与事件报告的降级级别；未知值不补造 |
| `sourceType` | 事件发布者提供的来源类型；正式主链包含表达式委托操作在内使用 `structured` |
| `total` | 查询/计数返回的命中总数；聚合为 `null` |
| `countOnly` | 查询是否仅计数；聚合为 `null` |
| `returnedSize` | 有结果集合时为文档数，聚合则为顶层聚合项数；集合未发布时为 `null` |
| `took` | 事件报告的查询耗时，单位毫秒；不包含审计排队及落库耗时 |
| `errorMessage` | 失败事件提供的错误摘要；不存在的值不补造 |
| `indexAlias`、`actualIndices` | 仅从事件请求和上下文提取，正式主链不提供，不使用配置值补填 |
| `queryCondition` | 本监听器不采集原始查询条件，保持 `null` |

正式 Search Jakarta 事件包含成功/失败、数据源、降级级别、耗时、查询总数与 `countOnly` 标记；不发布原始条件、文档、聚合内容、游标或真实索引。对应字段为 `null`，`returnedSize=null` 表示未知，不是 0。聚合事件的 `total` 和 `countOnly` 为 `null`。

表达式查询、计数和聚合进入底层 SearchEngine 后，会产生相同的审计事件。主链当前将这些事件标记为 `structured`，监听器不会伪造 `EXPRESSION_API`。表达式解析/校验、提示、字段元数据查询和游标关闭未发布这些事件，不属于本监听器的操作审计范围。不包含自然语言搜索。

## 最佳实践

### 1. 关联宿主已有身份

实现 `EsAuditUserProvider`，返回已认证的客户端或用户信息；实现 `EsAuditTraceIdProvider`，返回当前调用链标识。不要返回原始凭据、Authorization、Cookie 或令牌。缺少 Provider 或 Provider 某一字段提取失败时，仅该字段为空，不阻断审计。

```java
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SampleSearchAuditIdentityConfiguration {
    /** 从宿主已有认证上下文读取主体，不在此解析或验证凭据。 */
    @Bean
    public EsAuditUserProvider sampleSearchAuditUserProvider(CurrentAuditContext context) {
        return new EsAuditUserProvider() {
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
    public EsAuditTraceIdProvider sampleSearchAuditTraceIdProvider(CurrentAuditContext context) {
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

身份在监听方法中、投递专用审计线程池之前提取。默认同步事件广播时，这就是事件发布线程；宿主改用异步事件广播器时，需要自行处理上下文传递。不要把 ThreadLocal（线程本地上下文）自动传递到审计线程池作为接入前提。

### 2. 对未知值保留未知语义

不要将 `returnedSize=null` 写成 0，不用配置的索引别名补填未发布的实际索引，也不从日志重新拼出查询条件。表达式前置解析失败需要其他审计事件，不能靠本监听器推断。

### 3. 正确接入表达式搜索审计

表达式查询、计数、聚合通过 Search 主模块进入底层引擎后，正常消费同一组审计事件即可，不需要另一套监听器或手工重复发布事件。`sourceType=structured` 不表示用户一定直接调用了结构化接口，也不能据此区分表达式与结构化调用。

表达式解析失败发生在引擎执行之前，不会产生本模块监听的查询/聚合事件。表达式校验、提示、字段元数据和游标关闭也不属于此事件范围；若应用需要记录这些操作，应在自己的调用边界使用独立事件，不通过补填原始表达式或伪造查询失败记录实现。

### 4. 根据可靠性要求选择处理器

日志适合接入初期或诊断。需要长期保存时实现独立处理器，并自行处理写入失败、去重、保留期与存储权限。若写入审计的处理器又触发被监听查询，应隔离事件来源或使用不触发同类事件的存储路径，避免递归审计。

审计处理失败仅记录处理器类型和异常类型，不输出异常消息、正文或堆栈；默认日志处理器会输出完整审计记录，启用前确认主体与扩展事件字段符合日志数据政策。

### 5. 按峰值调整队列与线程

先量测处理器延迟，再配置线程和队列。默认 `CALLER_RUNS` 可将压力反馈到提交任务的线程，同步事件广播时会增加业务延迟；延迟敏感应用可以选择丢弃策略，但应监控拒绝告警并接受记录缺失。不要把内存队列当作可靠审计存储。

### 6. 按模块隔离处理器和执行器

同名 `esAuditExecutor` Bean 可替换专用执行器，类型必须实现 `java.util.concurrent.Executor`；宿主自行负责其资源生命周期和拒绝策略。此时 `executor.*` 不再配置宿主执行器。

注册 `EsAuditEventListener` 类型的 Bean 可替换默认监听器，其处理逻辑和异常隔离由宿主负责。Search 和 Persistence 审计使用不同的监听器、处理器接口及执行器 Bean 名，可同时引入，各自独立启用；记录查询操作不代表自动记录了审计存储的写入操作。

### 7. 使用受控诊断日志

默认日志处理器负责输出审计记录；SDK 的 DEBUG 日志仅用于诊断转换、分发和主体提取，不替代长期审计存储。排障时可临时开启本模块日志：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.audit.search.elasticsearch: DEBUG
```

关注线程池拒绝和处理器失败的 WARN 告警。处理器失败不会自动重试；需要重试和可靠交付时，由独立存储处理器实现，不把业务调用正常返回理解为审计已经落库。

## 兼容性与边界

| 项目 | 范围 |
| --- | --- |
| Spring Boot | 验证基线为 `3.4.2` |
| Java | Java 17 编译目标，Java 17/21 运行验证 |
| Elasticsearch | 随 Search 主链支持 ES7/8；实际联调版本为 `7.17.16`、`8.17.0`；不支持 ES6，ES9 不在支持范围 |
| 事件依赖 | `simple-elasticsearch-search-core:1.0.12`；不传递引入查询主模块或认证组件 |
| 自动装配 | 使用 Spring Boot 3 自动配置发现，无需手工扫描 SDK 包 |
| 与旧线混用 | 不支持；保留旧线公开包名、接口和配置前缀，新旧制品包含同名 Java 类型 |

本模块只消费查询/聚合事件，不提供审计历史查询、自然语言搜索或可靠消息队列。需要保存和查询审计记录时，由应用自己的存储、查询和交付机制承担。

## 兼容矩阵

| Spring Boot | Java | 验证范围 |
|---|---:|---|
| 3.4.2 | 17 / 21 | 事件模拟链全量测试（基线） |
| 3.3.13 | 17 | 事件模拟链全量测试 |
| 3.2.12 | 17 | 事件模拟链全量测试 |
