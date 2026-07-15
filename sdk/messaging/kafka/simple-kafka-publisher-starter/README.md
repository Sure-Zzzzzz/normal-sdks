# simple-kafka-publisher-starter

基于 `simple-kafka-route-starter` 的 Kafka 消息发布 SDK。

调用方提供消息内容和最终 topic，publisher 负责：

- 解析 topic、record key 和路由方式。
- 可选地为 payload 增加统一 JSON 外包装。
- 补充通用 Kafka header。
- 将 payload 转换为 Kafka 可发送的 String。
- 将 Spring Kafka `SendResult` 转换为稳定的 `KafkaPublishResult`。

Kafka 集群连接仍由 `simple-kafka-route-starter` 创建和管理。publisher 只选择 datasource，不创建、不持有 `KafkaTemplate`、`ProducerFactory` 或 Kafka datasource。

## 模块边界

本模块提供：

- 统一 `KafkaPublisher` 发布入口。
- topic 规则路由、routeKey 规则路由和显式 datasource 发布。
- 可选消息外包装（Envelope）和默认 Kafka header。
- serializer、resolver、generator、clock、validator 和 customizer 扩展点。
- 异步发送与显式同步等待。

本模块不提供：

- Spring Event 到 Kafka 的自动桥接。
- 本地事务 outbox、可靠投递、后台重试或状态机。
- 消费端、DLQ、retry topic 或延迟消息。
- topic 自动创建或 broker capability 诊断。
- Kafka producer transaction 编排。
- Kafka datasource、ProducerFactory 或 KafkaTemplate 生命周期管理。

## 版本与兼容性

| 组件 | 版本 |
|------|------|
| simple-kafka-publisher-starter | 1.0.0 |
| simple-kafka-route-starter | 1.0.1 |
| Spring Boot | 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 |
| Spring Kafka | 2.x（随 Spring Boot 依赖管理） |
| Java | 8+ |

1.0.0 已在上述四套 Spring Boot 版本下完成全量测试，并使用 Kafka 1.1.0、2.8.1、3.7.1 单节点和 Kafka 3.7.1 三 Broker 集群完成真实发送、消费与隔离验证。

## 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-publisher-starter:1.0.0'
implementation 'org.springframework.kafka:spring-kafka'
```

publisher 已固定依赖 `simple-kafka-route-starter:1.0.1`。调用方仍需引入 Spring Kafka 运行时依赖，并按 route starter 的方式配置 Kafka datasource 和路由规则。

## 配置示例

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
        messaging:
          kafka:
            publisher:
              enable: true
              app-name: mock-app
              default-topic: mock.default.topic
              envelope:
                enable: true
                include-null-payload: false
              headers:
                enable-default-headers: true
                allow-header-override: false
                message-id-header: x-message-id
                message-type-header: x-message-type
                trace-id-header: x-trace-id
                source-header: x-source
                published-at-header: x-published-at
              send:
                timeout-ms: 3000
```

publisher 默认关闭。route 未启用或容器中不存在 `KafkaRouteTemplate` 时，publisher 不会注册。

### Publisher 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enable` | `false` | 是否注册 publisher 相关 Bean |
| `app-name` | `default` | Envelope source 和默认 source header 的值 |
| `default-topic` | 空 | message、topic resolver 均未提供 topic 时的最后兜底 |
| `envelope.enable` | `true` | 是否默认启用消息外包装 |
| `envelope.include-null-payload` | `false` | 是否允许 null payload |
| `headers.enable-default-headers` | `true` | 是否写入五个默认 Kafka header |
| `headers.allow-header-override` | `false` | 是否允许调用方或 customizer 覆盖默认 header |
| `headers.*-header` | 见上方示例 | 五个默认 header 的名称 |
| `send.timeout-ms` | `3000` | 仅供 `publishAndWait` 同步等待使用，必须大于 0 |

配置约束：

- 启用 Envelope 或默认 header 时，`app-name` 必须非 blank。
- 全局关闭 Envelope 后，单条消息仍可设置 `envelopeEnabled=true`；此时发送前仍会校验 `app-name`。
- 启用默认 header 时，五个 header 名必须非 blank、不能包含控制字符，且大小写不敏感地互不重复。
- 关闭默认 header 后，这些名称配置不再生效，也不再作为保留 header 名。
- publisher 不提供默认 routeKey 或默认 datasourceKey；默认 datasource 语义只由 route starter 负责。

## 发送与路由概念

- **topic**：消息最终写入的 Kafka topic。无论使用哪种发布方法，最终都必须确定一个 topic。
- **key**：Kafka record key，参与分区选择；相同 key 通常进入同一分区，用于维持该分区内的消息顺序。
- **routeKey**：只交给 route starter 匹配规则并选择 datasource，不会写入消息，也不会替换 topic。
- **datasource**：由 route starter 管理的一套 Kafka 集群连接配置。publisher 只选择 datasource，不创建或管理连接。
- **header**：Kafka record 自带的小型键值元数据，与消息 value 分开存储。
- **发送结果**：broker 确认后返回的 topic、partition、offset 和 timestamp 等元数据。

## 发布 API

### 1. 指定最终 topic

```java
ListenableFuture<KafkaPublishResult> future = kafkaPublisher.publish(
        "mock.event.created",
        "mock-key",
        mockPayload
);
```

该入口只指定最终 topic（以及可选 key），由 route 按 topic 规则选择 datasource；不调用 routeKey resolver。

### 2. 按消息字段自动选择路由方式

```java
KafkaPublishMessage<MockPayload> message = KafkaPublishMessage.<MockPayload>builder()
        .topic("mock.event.created")
        .key("mock-key")
        .messageType("mock.event.created")
        .payload(mockPayload)
        .build();

ListenableFuture<KafkaPublishResult> future = kafkaPublisher.publish(message);
```

`publish(message)` 的 datasource 选择顺序固定为：

1. `message.datasourceKey`：显式 datasource。
2. `KafkaPublishRouteKeyResolver` 的非空结果：按 routeKey 规则选择 datasource。
3. 都没有：由 route 按最终 topic 规则选择 datasource。

默认 routeKey resolver 只返回 `message.routeKey`，不会反射 payload。

### 3. 显式指定 routeKey

```java
ListenableFuture<KafkaPublishResult> future =
        kafkaPublisher.publishByRouteKey("mock-route-a", message);
```

该入口忽略 `message.datasourceKey`，不调用 routeKey resolver。routeKey 只决定 datasource，`message.topic` 仍是最终消息 topic。

### 4. 显式指定 datasource

```java
ListenableFuture<KafkaPublishResult> future =
        kafkaPublisher.publishOn("mock-source", message);
```

该入口绕过 route 的 topic/routeKey 规则，忽略 `message.routeKey`，直接使用指定 datasource；`message.topic` 仍是最终消息 topic。

### 5. 同步等待 broker 结果

```java
KafkaPublishResult result = kafkaPublisher.publishAndWait(message);
```

路由语义与 `publish(message)` 一致。只有该方法使用 `send.timeout-ms`；其他 API 始终异步返回 `ListenableFuture<KafkaPublishResult>`。

## Topic 与 key 解析顺序

Topic 解析顺序：

1. `publish(topic, ...)` 的 API topic。
2. `message.topic`。
3. `KafkaPublishTopicResolver`。
4. `default-topic`。
5. 仍为空则抛 `KAFKA_PUBLISHER_004`。

Key 解析顺序：

1. `publish(topic, key, payload)` 的 API key。
2. `message.key`。
3. `KafkaPublishKeyResolver`。
4. 仍为空也允许发送，由 Kafka producer 决定分区。

API 入参只影响本次发送，不会回写原 `KafkaPublishMessage`。

## KafkaPublishMessage 字段

| 字段 | 说明 |
|------|------|
| `topic` | 最终消息 topic |
| `key` | Kafka record key，可为空 |
| `routeKey` | 用于 route 规则匹配，不进入 Kafka value |
| `datasourceKey` | 显式 datasource，`publish(message)` 中优先级最高 |
| `partition` | 可选目标分区，非空时必须大于等于 0 |
| `timestamp` | 可选 ProducerRecord 时间戳，非空时必须大于等于 0 |
| `messageId` | 消息 ID；为空时由 `KafkaPublishMessageIdGenerator` 生成 |
| `messageType` | 调用方定义的消息类型 |
| `payload` | 原始消息内容；是否允许 null 由配置控制 |
| `headers` | 调用方提供的字符串 Kafka header |
| `attributes` | Envelope 扩展属性，不自动写入 Kafka header |
| `envelopeEnabled` | `null` 跟随全局配置，`true/false` 覆盖本次发送 |

`key`、`payload`、`headers` 和 `attributes` 不进入 `KafkaPublishMessage.toString()`。

## 消息外包装（Envelope）

Envelope 是 publisher 为原始 payload 增加的一层通用 JSON 外包装，便于消费方统一获得消息 ID、消息类型、来源、发布时间和 traceId。它不是 Kafka 自带协议，也不是额外发送一条消息。

例如原始 payload 是字符串 `hello`，启用 Envelope 后实际发送的 Kafka value 类似：

```json
{
  "messageId": "mock-message-id",
  "messageType": "mock.message.created",
  "source": "mock-app",
  "timestamp": 1700000000000,
  "traceId": "mock-trace-id",
  "payload": "hello",
  "attributes": {}
}
```

字段含义：

- `messageId`：本条消息的唯一标识。
- `messageType`：调用方定义的消息类型。
- `source`：来源应用，取自 `app-name`。
- `timestamp`：publisher 构造消息时的毫秒时间戳。
- `traceId`：默认依次从 MDC 的 `traceId`、`trace-id`、`X-B3-TraceId` 获取。
- `payload`：调用方原始消息内容。
- `attributes`：通用扩展属性，不写入 Kafka header。

关闭 Envelope 后：

- String payload 原样作为 Kafka value 发送，不额外增加 JSON 引号。
- 非 String payload 使用 Jackson 转为 JSON。
- 不再增加上述外包装。

## 默认 Kafka Header

默认写入：

| Header | 值 |
|--------|----|
| `x-message-id` | 最终 messageId |
| `x-message-type` | messageType（非 null 时写入） |
| `x-trace-id` | traceId（非 null 时写入） |
| `x-source` | `app-name` |
| `x-published-at` | publisher 构造消息的毫秒时间戳 |

Header 名称可配置，value 统一按 UTF-8 转为 Kafka byte array。

`allow-header-override=false`（默认）时：

- 调用方和 `KafkaPublishHeaderCustomizer` 不能修改或删除已启用的默认 header。
- 保留名比较大小写不敏感，`X-Message-Id` 也视为 `x-message-id`。
- 普通 header 名大小写不敏感重复时抛 `KAFKA_PUBLISHER_009`。
- header value 不允许为 null；空字符串允许，会编码为长度为 0 的字节数组。

关闭默认 header 后，默认名称不再保留，可作为普通调用方 header 使用。

## 异步、取消与失败语义

异步 API 分为两个阶段：

1. **发送准备阶段**：消息校验、resolver、customizer 和序列化在调用线程内完成；该阶段失败会直接抛异常，不会返回 Future。
2. **底层发送阶段**：route send 已被调用后，broker 发送失败通过返回 Future 的异常状态传播，统一包装为 `KAFKA_PUBLISHER_007`。

取消 publisher 返回的 Future 会向底层 route Future 传播取消请求。

`publishAndWait`：

- broker 明确发送失败：`KAFKA_PUBLISHER_007`。
- 等待超时：`KAFKA_PUBLISHER_008`。
- 等待被线程中断：`KAFKA_PUBLISHER_011`，并恢复线程中断标记。

超时和中断都只表示“等待没有取得最终结果”。消息可能已经到达 broker，因此调用方不应盲目重试，以免造成重复投递。

## KafkaPublishResult

成功结果包含：

| 字段 | 说明 |
|------|------|
| `messageId` | 本次发布使用的最终 messageId |
| `topic` | broker metadata 返回的 topic |
| `key` | 本次发送使用的 record key，不进入 `toString()` |
| `partition` | broker metadata 返回的 partition |
| `offset` | broker metadata 返回的 offset |
| `timestamp` | broker metadata 返回的 timestamp |
| `datasourceKey` | 仅显式 `publishOn` 模式回填；topic/routeKey 模式为 null |

`KafkaPublishResult` 只表示成功，不提供 `success=false`。失败统一通过异常语义表达。

## 错误码

| 错误码 | 含义 |
|--------|------|
| `KAFKA_PUBLISHER_001` | 配置非法 |
| `KAFKA_PUBLISHER_002` | message 或 messageId 非法 |
| `KAFKA_PUBLISHER_003` | payload 非法 |
| `KAFKA_PUBLISHER_004` | 无法确定最终 topic |
| `KAFKA_PUBLISHER_005` | partition 或 timestamp 非法 |
| `KAFKA_PUBLISHER_006` | 默认 Jackson 序列化失败 |
| `KAFKA_PUBLISHER_007` | 底层发送失败 |
| `KAFKA_PUBLISHER_008` | 同步等待超时，发送状态未知 |
| `KAFKA_PUBLISHER_009` | Kafka header 非法 |
| `KAFKA_PUBLISHER_010` | routeKey 或 datasourceKey 非法 |
| `KAFKA_PUBLISHER_011` | 同步等待被中断，发送状态未知 |

错误消息不打印 payload、key、header value、完整 headers 或完整 attributes。

## 扩展点

### 可替换的单一 Bean

| 接口 | 默认行为 |
|------|----------|
| `KafkaPublishSerializer` | 使用 Spring `ObjectMapper` 序列化 Envelope 或非 String payload |
| `KafkaPublishTopicResolver` | 默认返回 null，交给 `default-topic` 兜底 |
| `KafkaPublishKeyResolver` | 默认返回 `message.key` |
| `KafkaPublishRouteKeyResolver` | 默认返回 `message.routeKey` |
| `KafkaPublishMessageIdGenerator` | 默认生成 UUID |
| `KafkaPublishTraceResolver` | 默认从 MDC 获取 traceId |
| `KafkaPublishClock` | 默认调用 `System.currentTimeMillis()` |
| `KafkaPublishPropertiesValidator` | 启动期校验嵌套配置、timeout、默认 header 名和 app-name |

默认 Bean 均使用 `@ConditionalOnMissingBean`，调用方可直接注册自定义实现覆盖。

示例：按 messageType 解析 topic。

```java
@Bean
public KafkaPublishTopicResolver kafkaPublishTopicResolver() {
    return message -> {
        if ("mock.message.created".equals(message.getMessageType())) {
            return "mock.event.created";
        }
        return null;
    };
}
```

### 可注册多个的 Customizer

- `KafkaPublishHeaderCustomizer`
- `KafkaPublishEnvelopeCustomizer`

多个 customizer 支持 `Ordered` / `@Order`，按 Spring 顺序执行。

Header customizer 示例：

```java
@Bean
public KafkaPublishHeaderCustomizer kafkaPublishHeaderCustomizer() {
    return context -> context.getHeaders().put("x-mock-tag", "mock-value");
}
```

Envelope customizer 示例：

```java
@Bean
public KafkaPublishEnvelopeCustomizer kafkaPublishEnvelopeCustomizer() {
    return context -> context.getAttributes().put("mockAttribute", "mock-value");
}
```

Envelope customizer 只能修改 attributes；上下文不暴露 payload。publisher 会在所有 customizer 执行完毕后显式回写 attributes，再进行序列化。

## ObjectMapper

默认 `JacksonKafkaPublishSerializer` 使用 Spring 容器现有的 `ObjectMapper`，不会自行创建或注册全局 ObjectMapper，因此会保留调用方配置的 JavaTime、枚举和命名策略等模块。

若应用排除了 Jackson 自动配置，需要自行提供 `ObjectMapper` 或自定义 `KafkaPublishSerializer`；否则 publisher 启动时抛 `KAFKA_PUBLISHER_001`。

## 安全边界

- payload、key、headers、attributes 不进入消息模型 `toString()`。
- key 不进入发布结果 `toString()`。
- 序列化和发送错误不打印 payload、key、header value 或 attributes 内容。
- header 校验错误只打印 header key。
- Header customizer 不直接接触 Kafka 原生 byte array。
- Envelope customizer 不接触 payload。

## Outbox 与可靠投递边界

本模块保留 `messageId`、`messageType`、`timestamp`、`attributes`、`datasourceKey` 和 `routeKey` 等通用字段，供后续独立 outbox 模块复用，但不实现 outbox 表、扫描任务、重试状态机、poison message 或 checkpoint。

普通 Kafka producer ack 不能解决本地数据库事务与 Kafka 投递的一致性，也不能消除“broker 已收到消息但调用方未拿到确认”造成的重复投递窗口。需要可靠投递时，应使用后续独立的 `simple-kafka-outbox-starter`，消费方仍应按 messageId 设计幂等处理。

## 测试基线

1.0.0 封版前完成：

- 13 个测试类、70 个测试。
- 0 skipped、0 failures、0 errors。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 全量矩阵通过。
- Kafka 1.1.0 / 2.8.1 / 3.7.1 单节点和 Kafka 3.7.1 三 Broker 集群真实 E2E 通过。
- E2E 直接复用 `simple-kafka-route-starter/docker-compose.kafka-e2e.yml`，publisher 不维护重复 Kafka compose。

首发 1.0.0 不提供测试跳过开关，也不使用 `@EnabledIfSystemProperty` 静默跳过 E2E。
