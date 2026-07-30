# simple-kafka-consumer-starter

用注解声明 Kafka 消费入口的 Spring Boot Starter。

它复用 `simple-kafka-route-starter` 的 Kafka datasource 与 topic 路由配置，为业务管理消费容器、可靠的手动 offset 提交、本地重试、死信投递、可选 Redis 幂等和消费事件。业务只需要配置路由并编写 Handler。

> 默认交付语义是**至少一次**：业务处理完成后、offset 确认前发生故障时，Kafka 仍可能重投消息。Redis 幂等用于减少重复处理，不会自动让业务副作用变成事务性“恰好一次”。

## 5 分钟接入

### 1. 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-consumer-starter:1.0.0'
implementation 'org.springframework.kafka:spring-kafka'
```

Consumer 固定依赖 `simple-kafka-route-starter:1.0.3`。Kafka 的 datasource、topic 路由规则和基础 consumer 参数由 route starter 配置。

如需使用内置 Redis 幂等，再额外引入：

```gradle
implementation 'io.github.sure-zzzzzz:simple-redis-route-starter:1.1.0'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

Redis 不是强制依赖；未启用 Redis 或自定义幂等检查器时，仍可正常消费，但语义为至少一次。

### 2. 配置一个 Kafka datasource 并开启 Consumer

下面是最小可运行配置。`group-id` 是该业务消费者的身份；Consumer 只支持手动提交，因此必须保持 `enable-auto-commit: false`。

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
                consumer:
                  group-id: sample-event-consumer
                  enable-auto-commit: false
        messaging:
          kafka:
            consumer:
              enable: true
```

Kafka 的 source topic、DLT topic 与访问权限均由集群运维或基础设施交付管理，Consumer 和 route starter 都不会创建 topic 或授权。应用启动前必须预创建 source topic；运行账号必须拥有 source topic 的消费权限和对应消费组的使用权限。启用 DLT 时，还必须拥有目标 DLT topic 的生产权限。

Consumer 当前固定处理 `String` key/value。route datasource 默认使用 `StringDeserializer`；如自行覆盖 key/value 反序列化器，产物仍必须是 `String`，否则消费容器无法按本 Starter 的契约交付消息。

### 3. 声明消费 Handler

```java
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumerComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

@SimpleKafkaConsumerComponent
public class SampleEventConsumer {

    @SimpleKafkaConsumer(topic = "sample.event.created")
    public void consume(KafkaConsumerRecord<String, String> record) {
        // 根据 record.getValue() 执行业务处理
    }
}
```

应用启动后，Consumer 根据 `sample.event.created` 通过 route starter 解析 datasource，加入 `sample-event-consumer` 消费组，并创建受管理的 listener container。

### Handler 必须满足的规则

- Handler 类必须位于 Spring Boot 应用基础包内。
- `@SimpleKafkaConsumerComponent` 是 Consumer 的**扫描标记**，不要再叠加 `@Component`、`@Service`、`@Repository`，也不要重复声明 `@Bean`。
- 消费方法必须是 `public`、非 `static`、返回 `void`，且只能接收一个 `KafkaConsumerRecord` 参数。
- `@SimpleKafkaConsumer` 的 `topic` 与 `topics` 二选一。

## 选择 topic、datasource 与消费组

### datasource 选择

- `datasource` 为空：交给 `simple-kafka-route-starter` 按 topic 路由规则选择 datasource。
- `datasource` 非空：直接使用指定 datasource，不再走 topic 路由规则。

### 消费组选择

- 注解 `groupId` 非空时，覆盖 route datasource 的 `consumer.group-id`。
- 注解与 route datasource 都未提供 groupId 时，应用启动失败。
- 同一 topic 的不同业务职责应使用不同 groupId；不同 groupId 会独立消费同一条消息。

例如，一个审计消费组同时订阅两个 topic，并显式使用指定 datasource：

```java
@SimpleKafkaConsumer(
        topics = {"sample.event.created", "sample.event.updated"},
        datasource = "audit",
        groupId = "sample-audit-consumer",
        autoOffsetReset = "earliest")
public void audit(KafkaConsumerRecord<String, String> record) {
    // 记录审计信息
}
```

`autoOffsetReset` 可选值为 `earliest`、`latest`、`none`。注解优先于 Consumer 配置，Consumer 配置为空时继承 route datasource；仍为空时使用 `latest`。

有效 datasource、groupId、offset 策略、`max-poll-records` 与并发数完全相同的注册项会共同管理；同一有效组内不能重复注册同一个 topic。业务不需要感知容器实现，只需保证同一 topic/group 的注册语义唯一。

## 可靠消费与 offset 提交

本模块只支持**手动提交**。

- Consumer 的 `container.enable-auto-commit` 与 route datasource 的 `consumer.enable-auto-commit` 最终都必须为 `false`；任一处解析为 `true`，应用会拒绝启动。
- Handler 成功返回后，Consumer 才确认该消息的 offset。业务代码不得调用 `record.acknowledge()`；自行确认会绕过重试、DLT 与幂等完成标记的终态顺序，可能造成消息提前提交。
- 幂等检查判定为 `COMPLETED` 时，消息不会进入 Handler，但会确认 offset。
- 幂等检查返回 `IN_PROGRESS` 时，表示另一投递仍持有未过期的处理租约；此时不进入 Handler、不投递 DLT、也不确认 offset，当前 listener container 会停止，等待租约到期后由刷新或重启触发 Kafka 重投。
- 失败消息只有在终态处理完成后才会确认 offset；重试、DLT 发布失败或中断时都不会提前确认。

因此，业务 Handler 必须能够安全重入：在“业务副作用已完成、offset 尚未确认”的故障窗口内，Kafka 可能再次投递同一消息。

## 重试与死信（DLT）

业务 Handler 抛出异常后，Consumer 先按异常分类决定本地重试或投递死信。

默认重试参数：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `error.max-attempts` | `3` | 总尝试次数，包含首次处理 |
| `error.initial-interval-ms` | `1000` | 首次重试间隔（毫秒） |
| `error.multiplier` | `2.0` | 指数退避倍数 |
| `error.max-interval-ms` | `30000` | 重试间隔上限（毫秒） |
| `error.jitter-factor` | `0.2` | 退避抖动比例 |

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        messaging:
          kafka:
            consumer:
              enable: true
              error:
                max-attempts: 3
                initial-interval-ms: 1000
                multiplier: 2.0
                max-interval-ms: 30000
                jitter-factor: 0.2
                dead-letter:
                  enable: true
                  suffix: .DLT
                  # 留空表示投递到原消息所在 datasource
                  datasource-key: ""
```

重试在当前消费线程内本地退避，期间该分区不会继续推进。它适合数据库短暂抖动、下游瞬时超时等短故障；不适合长时间延迟处理、跨进程调度或无限重试。

默认情况下，`RetriableException`、`TimeoutException` 等被视为可重试；序列化/反序列化异常、`IllegalArgumentException`、`ClassCastException`、`NullPointerException` 等被视为不可重试。可以在自定义异常类上标记：

```java
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.FatalConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.RetryableConsumerException;

@RetryableConsumerException
public class SampleRemoteException extends RuntimeException {
}

@FatalConsumerException
public class SampleInvalidMessageException extends RuntimeException {
}
```

`@FatalConsumerException` 优先于可重试分类。

### DLT 行为

达到最大尝试次数或遇到不可重试异常后，原消息默认投递到 `<源 topic>.DLT`。死信消息保留原始 key、value、headers，并追加原 topic、partition、offset、错误码、错误摘要和尝试次数等溯源 header。

- DLT 发布成功后，才确认源消息 offset。
- DLT 发布失败时，不确认源消息 offset，并停止当前 listener container；后续刷新或重启后可重新投递。
- Consumer 只负责向 DLT topic 发布消息，不负责创建 Kafka topic 或授予 Kafka ACL。生产环境必须由集群运维或基础设施交付在上线前预创建 source topic 和 `<源 topic><suffix>`；不要依赖 broker 自动创建 topic。DLT topic 不存在、无生产权限或不可用时，同步投递会失败，源消息不会确认 offset。
- DLT 是人工排障入口，不是自动修复机制。生产环境应为 DLT 数量、积压和处置时长建立监控与告警。

## 可选 Redis 幂等

`idempotency.enable=false` 是默认值。未启用时，`NoOpKafkaConsumerIdempotencyChecker` 放行所有消息，业务必须自行处理重复投递。

启用内置 Redis 幂等前，必须引入并启用 `simple-redis-route-starter`，并将 `idempotency.redis-route-key` 指向一个已配置的 Redis datasource。缺少 Redis Route Bean 时应用拒绝启动；key 为空或未配置时，Redis 调用会失败并按 fail-open 继续处理消息，因此不会获得 Redis 去重保护。

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
                host: localhost
                port: 6379
                database: 0
        messaging:
          kafka:
            consumer:
              enable: true
              idempotency:
                enable: true
                redis-route-key: idempotency
                # 成功处理或成功投递 DLT 后的去重保留窗口
                ttl-ms: 86400000
                # 未完成处理的租约与故障恢复窗口
                lease-ms: 300000
```

内置实现按 **Kafka datasource + consumer group + messageId** 隔离状态：

- 优先读取消息 header `x-message-id`；不存在时使用 `topic:partition:offset` 作为兜底标识。
- 首次领取的消息写入带 owner 的 `PROCESSING` 处理租约并进入 Handler。
- Handler 成功或 DLT 成功后，当前 owner 原子转换为 `COMPLETED`；`ttl-ms` 内的后续投递会跳过 Handler 并确认 offset。
- Handler 中断、退避中断或 DLT 失败时，只释放当前 owner 的 `PROCESSING` 租约。释放请求异常时消息仍保持未确认，后续投递会等待 `lease-ms` 到期，而不会被误判为已完成重复消息。
- `IN_PROGRESS` 不是重复消息：它不会确认 offset，也不会再次执行 Handler 或 DLT。
- Redis 调用发生运行时异常时采用 fail-open：消息继续进入 Handler，避免 Redis 短暂故障阻塞所有消费。

> 生产消息应始终携带稳定的 `x-message-id`，同一个业务事件重发时必须使用相同 ID。Redis fail-open 和业务副作用与 Redis 不在同一事务中，业务仍应使用唯一约束、状态机或去重表兜底幂等。

### 自定义幂等检查器

注册自己的 `KafkaConsumerIdempotencyChecker` Bean 可替代内置 NoOp 或 Redis 实现。下面的 `businessIdempotencyStore` 代表业务已实现的幂等存储（例如带唯一约束的业务表或独立去重表）；请替换为项目实际的 Bean，而不是直接复制该变量名。

```java
@Bean
public KafkaConsumerIdempotencyChecker kafkaConsumerIdempotencyChecker(
        BusinessIdempotencyStore businessIdempotencyStore) {
    return (messageId, datasourceKey, groupId) -> {
        BusinessIdempotencyLease lease = businessIdempotencyStore.acquire(
                messageId, datasourceKey, groupId);
        if (lease.isCompleted()) {
            return KafkaConsumerIdempotencyAcquireResult.completed();
        }
        if (lease.isInProgress()) {
            return KafkaConsumerIdempotencyAcquireResult.inProgress();
        }
        return KafkaConsumerIdempotencyAcquireResult.acquired(new KafkaConsumerIdempotencyLease() {
            @Override
            public boolean complete() {
                return lease.complete();
            }

            @Override
            public boolean release() {
                return lease.release();
            }
        });
    };
}
```

自定义实现必须原子区分三种状态：`ACQUIRED` 仅在当前投递拥有处理租约时返回，并提供只影响当前 owner 的 `complete`、`release`；`COMPLETED` 才表示可以跳过并确认 offset；其他未完成 owner 返回 `IN_PROGRESS`，不得伪装为完成。应按 datasource 和消费组隔离记录，并让 `lease-ms` 覆盖最长 Handler、本地重试、同步 DLT 与预期恢复窗口。

## 生产最佳实践

1. **稳定消息标识**：生产者为每个业务事件携带 `x-message-id`；重发、补偿或切换 producer 时保持同一个业务事件 ID 不变。
2. **按职责划分 groupId**：同一 topic 的审计、通知、索引等不同业务使用不同 groupId；同一职责的实例使用同一个 groupId 共同分摊分区。
3. **Handler 可重入且短小**：用业务唯一约束、状态机或幂等表保护副作用；不要在 Handler 内执行不可控的长阻塞任务。
4. **匹配分区设置并发**：`container.concurrency` 不应超过可利用的 topic 分区并行度；结合单条处理耗时设置 `max-poll-records`，避免单次 poll 积压过多 in-flight 消息。
5. **按最长处理时间配置保护窗口**：`shutdown-await-ms` 应覆盖正常停机时的 in-flight Handler 时长；`idempotency.lease-ms` 必须覆盖最长 Handler、本地重试、同步 DLT 与预期恢复窗口；`idempotency.ttl-ms` 定义终态去重保留期。
6. **短故障本地重试，毒消息进入 DLT**：不要用长本地退避充当延迟任务或无限重试；DLT 需要明确责任人、告警和人工重放/修复流程。
7. **建立可观测性**：监控 consumer lag、重试、DLT、消费错误和幂等拒绝，及时识别下游故障、毒消息和异常重复投递。

## 消费事件与扩展

注册 `KafkaConsumerEventListener` 可以接入指标、审计或告警：

```java
@Bean
public KafkaConsumerEventListener kafkaConsumerEventListener() {
    return context -> {
        // 根据 context.getEventType() 记录指标或告警
    };
}
```

事件包括：`CONSUMED`、`RETRY`、`DEAD_LETTER`、`IDEMPOTENT_REJECT` 和 `ERROR`。可以注册多个 listener，按 Spring `Ordered` 或 `@Order` 排序执行；某个 listener 自身异常不会影响消费主流程。

如需替换默认策略，可注册自定义 `KafkaConsumerIdempotencyChecker`、`KafkaConsumerErrorHandler`、`KafkaConsumerBackoffPolicy`、`DeadLetterPublisher` 或 `KafkaConsumerContainerFactory` Bean。

## 完整配置参考

配置前缀：`io.github.surezzzzzz.sdk.messaging.kafka.consumer`

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `enable` | `false` | 是否启用 Consumer Starter |
| `container.auto-offset-reset` | 继承 route，最终默认 `latest` | 新消费组 offset 策略：`earliest` / `latest` / `none` |
| `container.enable-auto-commit` | 继承 route，最终必须为 `false` | 任何有效 `true` 均拒绝启动 |
| `container.max-poll-records` | 继承 route，最终默认 `500` | 单次 poll 最大记录数 |
| `container.concurrency` | `1` | 消费并发数，必须大于 0 |
| `container.shutdown-await-ms` | `30000` | 停机等待 in-flight Handler 完成的时长（毫秒） |
| `error.max-attempts` | `3` | 总尝试次数，包含首次 |
| `error.initial-interval-ms` | `1000` | 首次重试间隔（毫秒） |
| `error.multiplier` | `2.0` | 退避倍数，必须大于等于 1 |
| `error.max-interval-ms` | `30000` | 重试间隔上限（毫秒） |
| `error.jitter-factor` | `0.2` | 抖动比例，范围 `[0, 1]` |
| `error.dead-letter.enable` | `true` | 是否启用 DLT |
| `error.dead-letter.suffix` | `.DLT` | DLT topic 后缀 |
| `error.dead-letter.datasource-key` | 空 | DLT datasource；为空时使用原消息 datasource |
| `idempotency.enable` | `false` | 是否启用内置 Redis 幂等 |
| `idempotency.redis-route-key` | `default` | Redis route datasource key |
| `idempotency.ttl-ms` | `86400000` | `COMPLETED` 终态标记的去重保留期（毫秒） |
| `idempotency.lease-ms` | `300000` | `PROCESSING` 处理租约与故障恢复窗口（毫秒），必须大于 0 |

## 模块边界

本模块提供：

- 注解式消费入口注册与 topic/datasource/group 选择。
- 可靠手动提交、本地退避重试和 DLT 投递。
- 可选 Redis 幂等、自定义幂等 SPI 与消费事件。

本模块不提供：

- retry topic、延迟消息或跨进程重试协调。
- Kafka broker/topic 创建、集群运维或跨应用消费组编排。
- 数据库 outbox、跨资源事务或事务消息。
- 自动保证业务副作用幂等或全局顺序。

## 版本与兼容性

| 组件 | 版本 |
|------|------|
| simple-kafka-consumer-starter | 1.0.0 |
| simple-kafka-route-starter | 1.0.3 |
| Java 编译 API | 8 |
| Kafka Broker | 1.1.0 / 2.8.1 / 3.7.1 |

本模块的兼容目标如下；实际接入前应按自身 Spring Boot、JDK、Kafka Broker 与 Redis 部署组合完成验证：

| Spring Boot | Spring Kafka | Java 运行时 | Kafka Broker |
|-------------|-------------|-------------|--------------|
| 2.2.13 | 2.3.13.RELEASE | 8 | 1.1.0 单节点、2.8.1 单节点、3.7.1 单节点、3.7.1 三节点集群 |
| 2.3.12 | 2.5.14.RELEASE | 8 | 1.1.0 单节点、2.8.1 单节点、3.7.1 单节点、3.7.1 三节点集群 |
| 2.4.5 | 2.6.7 | 8 | 1.1.0 单节点、2.8.1 单节点、3.7.1 单节点、3.7.1 三节点集群 |
| 2.7.9 | 2.8.11 | 11 | 1.1.0 单节点、2.8.1 单节点、3.7.1 单节点、3.7.1 三节点集群 |
