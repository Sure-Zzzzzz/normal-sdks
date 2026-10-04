# Simple Kafka Consumer Jakarta Starter

面向 Spring Boot 3.x 的注解式 Kafka 消费 Starter。它复用 `simple-kafka-route-jakarta-starter` 管理的 Kafka datasource 和 topic 路由，为业务提供受管 listener container、手动 offset 提交、本地重试、死信投递、可选 Redis 幂等和消费事件。

默认交付语义是**至少一次**：业务副作用完成而 offset 尚未确认时发生故障，Kafka 可能重新投递同一消息。Redis 幂等可以减少重复处理，不会把业务副作用自动变成“恰好一次”。

## 版本选择

| 应用运行时 | 使用模块 | 当前版本 |
| --- | --- | --- |
| Spring Boot 2.x | `simple-kafka-consumer-starter` | `1.0.1` |
| Spring Boot 3.x / Java 17+ | `simple-kafka-consumer-jakarta-starter` | `1.0.0` |

同一应用只能选择一条线。两条线保留相同包名、配置键和业务 API，不能通过同时引入来兼容不同的 Spring Boot 基线。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kafka-consumer-jakarta-starter:1.0.0'
    implementation 'org.springframework.kafka:spring-kafka'
}
```

Starter 以公开 API 依赖 `simple-kafka-route-jakarta-starter:1.0.0`；`spring-kafka` 同样以 `compileOnlyApi` 暴露其公开类型，但运行时仍由 Spring Boot 3 应用按自身 BOM 提供。

如需使用内置 Redis 幂等，再引入：

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-redis-route-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.apache.commons:commons-pool2'
}
```

启用 Redis Route 的 Redis 幂等时，`commons-pool2` 是宿主运行时依赖，不由本 Starter 传递；即使未显式启用连接池配置也不能省略。

配置一个 Kafka datasource，并启用 Consumer：

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
                consumer:
                  group-id: sample-event-consumer
                  enable-auto-commit: false
        messaging:
          kafka:
            consumer:
              enable: true
```

应用账号、source topic、DLT topic 和 Kafka ACL 由基础设施交付管理。本模块不会创建 topic 或授权。启用 DLT 时，账号还必须拥有 DLT topic 的生产权限。

## 声明消费 Handler

```java
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumerComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

@SimpleKafkaConsumerComponent
public class SampleEventConsumer {

    @SimpleKafkaConsumer(topic = "sample.event.created")
    public void consume(KafkaConsumerRecord<String, String> record) {
        // 执行业务处理
    }
}
```

- Handler 类必须位于 Spring Boot 应用基础包内。
- `@SimpleKafkaConsumerComponent` 是扫描标记，不要叠加 `@Component`、`@Service`、`@Repository` 或重复声明 `@Bean`。
- 方法必须是 `public`、非 `static`、返回 `void`，且只接收一个 `KafkaConsumerRecord` 参数。
- `topic` 与 `topics` 二选一；注解的 `datasource` 和 `groupId` 均优先于 route datasource 配置。

同一有效 datasource、消费组、offset 策略、`max-poll-records` 和并发度会由一个 container 统一管理；同一有效组内不能重复注册同一个 topic。

## 提交、重试与 DLT

本模块只支持手动提交。Consumer 与 route datasource 的 `enable-auto-commit` 最终都必须为 `false`，否则应用拒绝启动。

- Handler 成功返回后才确认 offset；业务 Handler 不得自行调用 `record.acknowledge()`。
- 可重试异常按本地退避重试；重试期间对应分区不推进。
- 达到最大尝试次数或遇到不可重试异常后，原消息投递到 `<source-topic>.DLT`。
- DLT 投递成功后才确认 source offset；投递失败、中断或异常时不确认 offset，并停止当前 container，等待后续重投。
- Spring Kafka 3 使用 `CommonContainerStoppingErrorHandler`；其 `isAckAfterHandle()` 固定为 `false`，避免框架在失败路径自动确认 source record。

默认重试参数：

| 配置 | 默认值 |
| --- | --- |
| `error.max-attempts` | `3` |
| `error.initial-interval-ms` | `1000` |
| `error.multiplier` | `2.0` |
| `error.max-interval-ms` | `30000` |
| `error.jitter-factor` | `0.2` |
| `error.dead-letter.enable` | `true` |
| `error.dead-letter.suffix` | `.DLT` |

同步退避适合短暂故障，且会占用消费线程以保持分区顺序；长延迟任务或无限重试不属于本模块边界。DLT 是人工处置入口，应对数量、积压和处置时长建立监控与告警。

## 可选 Redis 幂等

默认 `idempotency.enable=false`。启用内置实现时，配置一个 Redis Route datasource：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: idempotency
            sources:
              idempotency:
                mode: standalone
                host: redis.example.test
                port: 6379
        messaging:
          kafka:
            consumer:
              idempotency:
                enable: true
                redis-route-key: idempotency
                ttl-ms: 86400000
                lease-ms: 300000
```

内置实现按 Kafka datasource、消费组和消息标识隔离状态。它优先读取 `x-message-id`；没有该 header 时使用 `topic:partition:offset`。`COMPLETED` 跳过 Handler 并确认 offset，`IN_PROGRESS` 不确认 offset 且停止当前 container，等待租约到期后的重投。Redis 调用异常时采用 fail-open，消息继续进入 Handler；业务仍应使用唯一约束、状态机或去重表保护副作用。

生产消息应携带稳定的 `x-message-id`。同一业务事件的重发、补偿或 producer 切换必须保持该 ID 不变。

## 事件与扩展

注册 `KafkaConsumerEventListener` 可以接入指标、审计或告警：

```java
@Bean
public KafkaConsumerEventListener kafkaConsumerEventListener() {
    return context -> {
        // 处理 context.getEventType()
    };
}
```

事件类型为 `CONSUMED`、`RETRY`、`DEAD_LETTER`、`IDEMPOTENT_REJECT` 和 `ERROR`。事件仅携带脱敏后的最小定位信息；错误摘要只输出异常简单类名，不包含异常消息。DLT 的 `x-error-summary` 使用同一规则。listener 异常不会传播或阻断消费主流程。高频消费路径默认不自动写审计存储，业务可按事件接入独立 listener 模块。

可替换的单一 Bean 包括 `KafkaConsumerIdempotencyChecker`、`KafkaConsumerErrorHandler`、`RetryableExceptionClassifier`、`KafkaConsumerBackoffPolicy`、`DeadLetterPublisher` 和 `KafkaConsumerContainerFactory`。调用方自定义 container 时必须保持手动提交和失败不 ack 的语义。

## 边界与兼容性

本模块提供注解式入口注册、Kafka route 解析、受管容器、手动提交、本地重试、DLT、Redis 幂等 SPI 与消费事件；不提供 retry topic、延迟消息、Kafka 运维、topic/ACL 创建、数据库 outbox、跨资源事务或业务副作用的自动恰好一次保证。

构建基线为 Spring Boot `3.4.2`、Java `17`、Spring Kafka `3.3.2`。兼容范围如下：

| Spring Boot | Java 运行时 | Spring Kafka |
| --- | --- | --- |
| 3.2.12 | 17 / 21 | 3.1.10 |
| 3.3.13 | 17 / 21 | 3.2.13 |
| 3.4.2 | 17 / 21 | 3.3.2 |

Kafka 消费链路适配 Kafka `1.1.0`、`2.8.1`、`3.7.1` 单节点和 `3.7.1` 三节点集群，并支持 Redis 幂等。生产接入仍应结合自身 Kafka ACL、topic 规划、Redis 部署和业务副作用幂等策略完成验证。
