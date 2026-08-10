# Simple Kafka Route Starter

基于 Spring Boot 的 Kafka 多数据源路由组件，支持按 topic 或 route key 将消息路由到不同 Kafka 数据源。

## 核心特性

- 多数据源：支持同一路由实例同时管理多个 Kafka 集群。
- 三类显式路由入口：按 topic 路由、按 route key 路由、显式指定 datasource，互不干扰。
- 多匹配模式：支持 exact / prefix / suffix / wildcard / regex。
- 规则优先级：priority 数字越小越优先，同 priority 按配置声明顺序匹配。
- Broker 诊断层：核心 Bean 就绪后可选探测 broker 可达性、cluster 信息和 capability；探测失败默认只 warn，不阻断启动；`WARN` 结果通过 `diagnosticReason` 提供固定且脱敏的告警原因。
- 事务透传：`transaction-id-prefix` 通过 `setTransactionIdPrefix()` 设置，保留原生 Spring Kafka 事务语义。
- 安全默认：保留键（bootstrap.servers、serializer、group.id 等）禁止写入 raw properties；敏感字段（JAAS、SSL 密码等）不进 toString、日志、异常 message。
- 不污染业务上下文：不注册全局 `KafkaTemplate` / `ProducerFactory` / `ConsumerFactory` / `KafkaAdmin`。
- Spring Kafka 全版本兼容：跨版本差异集中在 helper 层，业务路径不感知版本细节。
- 可扩展：`KafkaRouteResolver`、`KafkaProducerFactoryFactory`、`KafkaConsumerFactoryFactory`、`KafkaRouteDiagnostics`、`KafkaRouteAdminClientFactory` 均可调用方侧覆盖。
- 兼容 Spring Boot 2.2.13.RELEASE / 2.3.12.RELEASE / 2.4.5 / 2.7.9。

## 依赖配置

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.5'
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.kafka:spring-kafka'
}
```

## 快速开始

### 1. 启用路由

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            enable: true
            default-source: default
```

### 2. 配置多数据源

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            enable: true
            default-source: default
            sources:
              default:
                bootstrap-servers:
                  - localhost:9092
                client-id: kafka-route-default
                producer:
                  client-id: kafka-route-default-producer
                  acks: all
                consumer:
                  client-id: kafka-route-default-consumer
                  group-id: kafka-route-default-group
                  auto-offset-reset: earliest
                  enable-auto-commit: false
              event:
                bootstrap-servers:
                  - localhost:9093
                client-id: kafka-route-event
                producer:
                  client-id: kafka-route-event-producer
                  acks: all
                consumer:
                  client-id: kafka-route-event-consumer
                  group-id: kafka-route-event-group
                  auto-offset-reset: earliest
                  enable-auto-commit: false
```

### 3. 配置路由规则

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            rules:
              - pattern: "event."
                type: prefix
                datasource: event
                priority: 1
                enable: true
              - pattern: "tenant-*"
                type: wildcard
                datasource: event
                priority: 2
                enable: true
```

## 使用示例

### 按 topic 自动路由

```java
@Service
public class DemoEventService {

    private final KafkaRouteTemplate kafkaRouteTemplate;

    public DemoEventService(KafkaRouteTemplate kafkaRouteTemplate) {
        this.kafkaRouteTemplate = kafkaRouteTemplate;
    }

    public void sendEvent(String topic, String key, String value) {
        // topic 以 "event." 开头，命中规则，自动路由到 event datasource
        kafkaRouteTemplate.send(topic, key, value);
    }

    public void sendWithRecord(ProducerRecord<String, String> record) {
        // topic / partition / timestamp / key / value / headers 原样透传
        kafkaRouteTemplate.send(record);
    }
}
```

### 按 route key 路由

```java
// routeKey 只用于选 datasource，最终发送 topic 不变
kafkaRouteTemplate.sendByRouteKey("tenant-a", "mock.order.topic", "mock-key", "mock-value");
```

### 显式指定 datasource

```java
// 绕过规则解析，直发指定 datasource
kafkaRouteTemplate.sendOn("event", "mock.topic", "mock-key", "mock-value");
```

### callback 模式

```java
// execute 返回已选 datasource 的 KafkaTemplate，callback 内发送不会二次路由
kafkaRouteTemplate.execute("event.order.created", kafkaTemplate -> {
    kafkaTemplate.send("event.order.created", "mock-key", "mock-value");
    return null;
});

// 显式 datasource callback
kafkaRouteTemplate.executeOn("event", kafkaTemplate -> {
    kafkaTemplate.send("mock.topic", "mock-key", "mock-value");
    return null;
});
```

### 使用短生命周期 AdminClient

通过 `KafkaRouteAdminClientFactory` 按 datasource key 获取仅在 callback 内有效的 `AdminClient`。所有异步 Admin 请求必须在 callback 内等待完成并转换为业务结果；callback 返回后 Route 自动关闭客户端，调用方不得缓存、返回或传递该客户端及依赖它的未完成异步对象。

```java
Integer nodeCount = adminClientFactory.withAdminClient("event", adminClient -> {
    try {
        return adminClient.describeCluster().nodes().get(30, TimeUnit.SECONDS).size();
    } catch (Exception e) {
        throw new IllegalStateException("读取 Kafka 集群信息失败", e);
    }
});
```

默认 Factory 不暴露合并后的 Kafka 配置，也不转移客户端关闭责任。调用方如需替换创建或关闭策略，可注册自己的 `KafkaRouteAdminClientFactory` Bean；替换后由调用方实现完整生命周期边界。

### 获取 KafkaTemplate

```java
KafkaTemplate<Object, Object> defaultTemplate = kafkaRouteTemplate.kafkaTemplate();
KafkaTemplate<Object, Object> eventTemplate  = kafkaRouteTemplate.kafkaTemplate("event");
KafkaTemplate<Object, Object> routedTemplate = kafkaRouteTemplate.kafkaTemplateByTopic("event.order.created");
```

### 事务

```java
// tx37 datasource 配置了 transaction-id-prefix，保留原生 Spring Kafka 事务语义
kafkaRouteTemplate.executeOn("tx37", kafkaTemplate ->
    kafkaTemplate.executeInTransaction(operations -> {
        operations.send("mock.topic", "mock-key", "mock-value");
        return Boolean.TRUE;
    })
);
```

### 为独立消费入口创建 ConsumerFactory

同一 datasource 下的不同消费入口需要不同消费组或 poll 参数时，调用 `SimpleKafkaRouteRegistry#createConsumerFactory`。每次调用都会创建独立实例；即使 `override` 为 `null`，也不会复用 `getConsumerFactory(...)` 返回的 registry 基础 factory。基础和派生 factory 均同时保留 Kafka 配置中的反序列化器类名，并提供独立的 factory 级反序列化器实例，可直接用于 Spring Kafka listener container。

```java
ConsumerFactory<Object, Object> consumerFactory = registry.createConsumerFactory("event",
        KafkaConsumerFactoryOverride.builder()
                .groupId("mock-consumer-group")
                .autoOffsetReset("earliest")
                .enableAutoCommit(false)
                .maxPollRecords(100)
                .build());

try {
    // 将 consumerFactory 交给当前消费入口创建 Consumer 或 listener container
} catch (RuntimeException e) {
    // 当前消费入口启动失败时，立即回收尚未交付给业务容器的 factory
    KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(consumerFactory);
    throw e;
}
```

派生 factory 的所有权属于调用方：调用方停止对应消费入口时必须调用 `KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(...)`；route registry 不缓存、也不会在关闭时销毁已经成功返回的派生 factory。反之，`getConsumerFactory(...)` 返回 registry 持有的基础 factory，调用方不得销毁。

`groupId`、`autoOffsetReset`、`enableAutoCommit`、`maxPollRecords` 的生效顺序为：datasource typed consumer 配置，再由本次 override 的非空字段覆盖。四项 override 均为可选；`enableAutoCommit=false` 是有效显式值。`bootstrap.servers`、安全配置、反序列化器和 client id 仍由 route 固定，不允许调用方改写。

> `group.id`、`auto.offset.reset`、`enable.auto.commit`、`max.poll.records` 不能写入 datasource `properties` 或 `consumer.properties`；请分别使用 `consumer.*` typed 配置和 `KafkaConsumerFactoryOverride`。无效 override 或受控 raw key 会以 `KAFKA_ROUTE_005` 拒绝。

## 配置说明

### 顶层配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enable` | `false` | 是否启用 Kafka route |
| `default-source` | `default` | 默认 datasource key |
| `sources` | 空 | datasource 配置 |
| `rules` | 空 | 路由规则配置 |
| `diagnostics` | 见下 | Broker 诊断配置 |

### 数据源配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `bootstrap-servers` | 空 | Kafka broker 地址列表 |
| `client-id` | 空 | datasource 级公共 client id |
| `properties` | 空 | datasource 级公共 raw properties（不允许保留键，也不允许 `group.id`、`auto.offset.reset`、`enable.auto.commit`、`max.poll.records`） |
| `security.security-protocol` | 空 | 安全协议：PLAINTEXT / SSL / SASL_PLAINTEXT / SASL_SSL |
| `security.sasl-mechanism` | 空 | SASL 机制 |
| `security.sasl-jaas-config` | 空 | JAAS 配置（不进 toString） |
| `security.ssl-truststore-location` | 空 | TrustStore 路径 |
| `security.ssl-truststore-password` | 空 | TrustStore 密码（不进 toString） |
| `security.ssl-keystore-location` | 空 | KeyStore 路径 |
| `security.ssl-keystore-password` | 空 | KeyStore 密码（不进 toString） |
| `security.ssl-key-password` | 空 | Key 密码（不进 toString） |

### Producer 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `producer.client-id` | 空 | producer client id |
| `producer.key-serializer` | `StringSerializer` | key 序列化器类名 |
| `producer.value-serializer` | `StringSerializer` | value 序列化器类名 |
| `producer.acks` | 空 | acks 配置：0 / 1 / all / -1 |
| `producer.retries` | 空 | 重试次数 |
| `producer.compression-type` | 空 | 压缩类型：none / gzip / snappy / lz4 / zstd |
| `producer.transaction-id-prefix` | 空 | 事务 ID 前缀（通过 setTransactionIdPrefix 设置） |
| `producer.properties` | 空 | producer raw properties（不允许保留键） |

### Consumer 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `consumer.client-id` | 空 | consumer client id |
| `consumer.key-deserializer` | `StringDeserializer` | key 反序列化器类名 |
| `consumer.value-deserializer` | `StringDeserializer` | value 反序列化器类名 |
| `consumer.group-id` | 空 | 消费组 ID |
| `consumer.auto-offset-reset` | 空 | 消费起点：earliest / latest / none |
| `consumer.enable-auto-commit` | 空 | 是否自动提交 offset |
| `consumer.max-poll-records` | 空 | 单次 poll 最大条数 |
| `consumer.properties` | 空 | consumer raw properties（不允许保留键，也不允许 `group.id`、`auto.offset.reset`、`enable.auto.commit`、`max.poll.records`；这些字段分别通过 typed 配置或本次 override 设置） |

### 路由规则配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pattern` | 空 | 匹配表达式 |
| `type` | `exact` | 匹配类型：exact / prefix / suffix / wildcard / regex |
| `datasource` | 空 | 命中的 datasource key |
| `priority` | `1000` | 优先级，数字越小越优先 |
| `enable` | `true` | 是否启用规则 |

### 诊断配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `diagnostics.enable` | `true` | 是否启用诊断能力 |
| `diagnostics.startup-check` | `true` | 是否在核心 Bean 就绪后执行启动探测 |
| `diagnostics.fail-fast` | `false` | broker 不可达时是否阻断启动 |
| `diagnostics.timeout-ms` | `3000` | 单 datasource 探测超时时间（毫秒） |
| `diagnostics.log-summary` | `true` | 探测成功时是否打印摘要日志 |

## 路由语义

- `exact`：topic/routeKey 与 pattern 完全一致。
- `prefix`：topic/routeKey 以 pattern 开头。
- `suffix`：topic/routeKey 以 pattern 结尾。
- `wildcard`：支持 `*` 和 `?` 通配符。
- `regex`：按 Java 正则表达式匹配。
- 没有命中任何启用规则时，使用 `default-source`。

## Bean 边界

模块启用后只注册 Kafka route 自身 Bean：

- `SimpleKafkaRouteRegistry`
- `KafkaRouteResolver`
- `KafkaRouteTemplate`
- `KafkaRoutePatternMatcher`
- `KafkaRouteDiagnostics`
- `KafkaRouteAdminClientFactory`

模块不会注册或替换业务项目的全局 Kafka Bean：

- `ProducerFactory`
- `ConsumerFactory`
- `KafkaTemplate`
- `KafkaAdmin`
- `KafkaTransactionManager`
- `ConcurrentKafkaListenerContainerFactory`

如果业务项目已经使用 Spring Boot 默认 Kafka 自动配置，可以继续保留；Kafka route 通过 `KafkaRouteTemplate` 独立使用。

## 扩展点

业务侧可以通过自定义 Bean 覆盖默认实现：

| 扩展点 | 默认实现 | 说明 |
|--------|----------|------|
| `KafkaRouteResolver` | `DefaultKafkaRouteResolver` | 自定义 topic/routeKey 到 datasource 的解析逻辑 |
| `KafkaProducerFactoryFactory` | `DefaultKafkaProducerFactoryFactory` | 自定义 ProducerFactory 创建逻辑 |
| `KafkaConsumerFactoryFactory` | `DefaultKafkaConsumerFactoryFactory` | 自定义 ConsumerFactory 创建逻辑；需要支持派生 factory 时必须实现三参数 `create`，旧 SPI 调用新 API 会固定报 `KAFKA_ROUTE_015`，不会回退到共享基础 factory |
| `KafkaRoutePropertiesValidator` | `DefaultKafkaRoutePropertiesValidator` | 自定义或增强配置校验 |
| `KafkaRouteDiagnostics` | `DefaultKafkaRouteDiagnostics` | 自定义 Broker 诊断实现 |
| `KafkaRouteAdminClientFactory` | `DefaultKafkaRouteAdminClientFactory` | 自定义 callback 作用域内的 AdminClient 创建与关闭策略 |

## 测试

单元测试不依赖真实 broker；端到端测试通过 Docker 启动 Kafka 1.1.0 / 2.8.1 / 3.7.1 单节点与 3 broker cluster，验证多版本 broker 路由隔离、事务边界、诊断 capability 准确性，以及同一 datasource 的派生 ConsumerFactory 独立 group、生效参数和销毁边界。端到端测试还会真实启动 Spring Kafka listener container，验证 factory 级反序列化器、消息消费和停止后销毁顺序；1.0.4 额外在 callback 内执行只读 `describeCluster` 并等待结果，1.0.5 继续验证 `WARN` 结果携带安全的 `diagnosticReason`。端到端测试默认随 `test` 任务执行，运行前需要先启动本地 Docker Kafka 矩阵。

## 升级说明

### 1.0.5 诊断告警原因

1.0.5 新增 `KafkaRouteBrokerDiagnosticResult#diagnosticReason`，无需增加配置。其为 Route 对 `WARN` 的唯一详细解释来源：

| 诊断状态 | `diagnosticReason` | 调用方处理方式 |
|----------|--------------------|----------------|
| `SUCCESS` | `null` | 不展示告警原因 |
| `WARN` | 下表中的固定脱敏短消息之一 | 直接展示该字段，不要根据 capability 字段自行推断 |
| `FAILED` | `null` | 读取既有 `failureReason`，不要将失败信息混入告警原因 |

| WARN 触发条件 | 固定 `diagnosticReason` |
|---------------|----------------------------|
| 已配置事务生产者，broker Feature API 未确认事务能力 | `已配置事务生产者，但 broker Feature API 未确认事务能力` |
| 已启用幂等生产者，broker Feature API 未确认幂等能力 | `已启用幂等生产者，但 broker Feature API 未确认幂等能力` |
| 已配置 zstd 压缩，broker Feature API 未确认 zstd 能力 | `已配置 zstd 压缩，但 broker Feature API 未确认 zstd 能力` |

同时命中多项时固定按事务生产者、幂等生产者、zstd 压缩取第一项。该字段不包含 bootstrap 地址、认证信息、JAAS、SSL 配置或原始异常。自定义 `KafkaRouteDiagnostics` 实现可以继续返回未设置该字段的旧版结果；使用方仅在 `WARN` 且原因为空时展示中性提示，不得反推具体能力原因。

1.0.4 新增 `KafkaRouteAdminClientFactory`，不改变既有 `KafkaRouteTemplate`、Registry、ProducerFactory、ConsumerFactory 或 diagnostics 的使用方式。需要 AdminClient 的调用方在 callback 内完成所有请求，callback 结束后不再保留客户端或未完成异步结果；不需要该能力的调用方无需修改代码或配置。

1.0.3 修复 `DefaultKafkaConsumerFactory` 仅保留反序列化器类名、未提供 factory 级实例的问题。已有使用 `KafkaRouteTemplate`、`getConsumerFactory(...)` 或 `createConsumerFactory(...)` 的代码和配置均无需修改；升级后，route 创建的基础和派生 ConsumerFactory 可直接交给 Spring Kafka listener container。调用方继续遵循既有生命周期边界：停止 listener container 后，再销毁 `createConsumerFactory(...)` 返回的派生 factory。

## 版本兼容

1.0.5 已在以下 Spring Boot 基线完成完整模块测试与 Docker Kafka 端到端验收；每个基线覆盖 callback 内只读 `describeCluster`、多版本 broker 路由、事务、诊断及 `diagnosticReason`、listener container 和派生 ConsumerFactory 生命周期。

| Spring Boot | Spring Kafka | 构建环境 | Kafka Broker 矩阵 | 状态 |
|-------------|--------------|----------|-------------------|------|
| 2.7.9 | 2.8.x | Java 11 / Gradle 8.5 | 1.1.0 / 2.8.1 / 3.7.1 + 3 broker cluster | 已验证 |
| 2.4.5 | 2.6.x | Java 8 / Gradle 7.6 | 1.1.0 / 2.8.1 / 3.7.1 + 3 broker cluster | 已验证 |
| 2.3.12.RELEASE | 2.5.x | Java 8 / Gradle 7.6 | 1.1.0 / 2.8.1 / 3.7.1 + 3 broker cluster | 已验证 |
| 2.2.13.RELEASE | 2.3.x | Java 8 / Gradle 7.6 | 1.1.0 / 2.8.1 / 3.7.1 + 3 broker cluster；`getTransactionIdPrefix` 为 protected，兼容层通过反射覆盖 | 已验证 |
