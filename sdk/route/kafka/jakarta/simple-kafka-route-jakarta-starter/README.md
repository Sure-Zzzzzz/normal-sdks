# Simple Kafka Route Jakarta Starter

面向 Spring Boot 3.x 的 Kafka 多数据源路由组件。调用方按 topic、route key 或显式数据源选择 Kafka 集群，组件只提供独立的 Route Bean，不创建或替换应用已有的全局 Kafka Bean。

## 适用范围

- Spring Boot 3.x、Java 17 及以上。
- 需要在同一应用中隔离多个 Kafka 数据源，并按规则选择发送目标。
- 需要保持原生 Spring Kafka 的 `KafkaTemplate`、事务与 ConsumerFactory 使用方式。

本模块与 javax 线 `simple-kafka-route-starter:1.0.5` 二选一。同一应用不要同时引入两者；两条线的包名和配置键保持一致。Spring Kafka 3 将发送结果改为 `CompletableFuture`，因此本模块的 `send`、`sendByRouteKey` 和 `sendOn` 返回 `CompletableFuture<SendResult<K, V>>`，不再使用 `ListenableFuture`。

## 版本选型

| 宿主 | 选择的模块 | 当前版本 | 发送 API |
|---|---|---|---|
| Spring Boot 2.x | `simple-kafka-route-starter` | 1.0.5 | `ListenableFuture<SendResult<K, V>>` |
| Spring Boot 3.x | `simple-kafka-route-jakarta-starter` | 1.0.0 | `CompletableFuture<SendResult<K, V>>` |

每个应用只能选择其中一条线。两条线的包名与配置键相同，不能通过同时引入来兼容不同 Spring Boot 基线。

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kafka-route-jakarta-starter:1.0.0'
    implementation 'org.springframework.kafka:spring-kafka'
}
```

Starter 对 Spring Kafka 使用 `compileOnly`。调用方应按 Spring Boot 3.x 依赖治理自行提供 `spring-kafka`；不要依赖 javax 线的传递依赖。

## 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            enable: true
            default-source: primary
            sources:
              primary:
                bootstrap-servers:
                  - kafka.example.test:9092
                producer:
                  acks: all
                consumer:
                  group-id: sample-route-group
                  auto-offset-reset: earliest
                  enable-auto-commit: false
              event:
                bootstrap-servers:
                  - kafka-event.example.test:9092
            rules:
              - pattern: "event."
                type: prefix
                datasource: event
                priority: 1
```

## 使用方式

```java
@Service
public class SampleEventService {

    private final KafkaRouteTemplate kafkaRouteTemplate;

    public SampleEventService(KafkaRouteTemplate kafkaRouteTemplate) {
        this.kafkaRouteTemplate = kafkaRouteTemplate;
    }

    public void publish(String value) {
        kafkaRouteTemplate.send("event.created", "sample-key", value);
    }
}
```

也可使用 `sendByRouteKey` 按 route key 选择数据源，使用 `sendOn` 显式指定数据源。`execute` 和 `executeOn` 提供回调内的 `KafkaTemplate`；`KafkaRouteAdminClientFactory` 提供回调内短生命周期的 `AdminClient`。回调结束后不得保留或继续使用由 Route 管理的短生命周期客户端。

## Bean 与扩展边界

启用后组件只注册 `SimpleKafkaRouteRegistry`、`KafkaRouteResolver`、`KafkaRouteTemplate`、`KafkaRoutePatternMatcher`、`KafkaRouteDiagnostics` 和 `KafkaRouteAdminClientFactory`。它不会注册或覆盖 `KafkaTemplate`、`ProducerFactory`、`ConsumerFactory`、`KafkaAdmin`、事务管理器或监听器容器工厂。

调用方可以注册同类型 Bean 替换默认的 Resolver、ProducerFactoryFactory、ConsumerFactoryFactory、PropertiesValidator、Diagnostics 或 AdminClientFactory。派生 ConsumerFactory 的生命周期由调用方负责：停止对应消费入口后调用 `KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(...)`；Registry 返回的基础 ConsumerFactory 由 Route 管理，调用方不得销毁。

## 配置和安全约束

配置前缀为 `io.github.surezzzzzz.sdk.kafka.route`。`bootstrap.servers`、序列化器、`group.id` 等受控键不能写入 raw properties；安全配置、认证材料和密码不会写入 `toString`、日志或异常消息。运行诊断时可按包名开启 DEBUG，日志仅包含数据源标识、耗时和受控故障分类。

## Spring Boot 3 自动配置

本模块使用 Spring Boot 3 的 `AutoConfiguration.imports` 注册自动配置，不包含 `spring.factories`。当前基线为 Spring Boot `3.4.2`、Java `17`；兼容性验证应以调用方实际使用的 Spring Boot 3 精确补丁版本为准。

## 从 Spring Boot 2.x 升级

1. 将依赖坐标替换为本模块，并移除 `simple-kafka-route-starter`。
2. 将宿主升级至 Spring Boot 3.x 和 Java 17 及以上，并由调用方提供 `spring-kafka`。
3. 保持原有配置键和 Route Bean 注入方式不变；将发送结果的 `ListenableFuture` 调整为 `CompletableFuture`。
4. 在目标 broker 拓扑执行发送、事务、消费和启动诊断回归，不要同时装载两条 Route 自动配置。

## 发布前验证

本版本使用 Kafka 1.1、2.8、3.7 单节点及三节点集群，覆盖路由发送、事务、派生 ConsumerFactory、短生命周期 AdminClient、自动配置、启动诊断和敏感配置脱敏。
