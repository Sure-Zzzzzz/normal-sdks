# CHANGELOG 1.1.0

> 发布日期：2026-07-17  
> 类型：Feature / Bug Fix / 优化

## 依赖

| 依赖 | 版本 | 变化 |
|------|------|------|
| simple-kafka-route-starter | 1.0.1 | 不变 |
| Spring Boot | 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 | 不变 |
| Spring Kafka | 2.x | 不变 |
| Jackson Databind | 随根依赖管理 | 继续作为运行时依赖 |
| Java | 8+ | 不变 |

publisher 从 1.0.0 直接升级到 1.1.0，不发布 1.0.1。

## 变更内容

### 默认序列化器与全局 ObjectMapper 隔离

- `JacksonKafkaPublishSerializer` 改为使用类内私有静态 `ObjectMapper`，只提供无参构造。
- publisher 不再读取、修改或注册 Spring 全局 `ObjectMapper` Bean。
- 应用没有全局 `ObjectMapper` Bean 时，默认 publisher 仍可正常启动。
- 应用全局命名策略和模块配置不再改变 publisher 默认消息协议。
- 私有 mapper 内置 `JavaTimeModule` 并关闭时间戳数组格式，Java 8 时间类型固定输出 ISO-8601 字符串。
- 需要其他 Jackson 模块、命名策略或协议定制时，通过自定义 `KafkaPublishSerializer` 完整接管。

### SPI 包结构整理

- `KafkaPublishTraceResolver` 与默认实现迁移到 `resolver` 包。
- `KafkaPublishMessageIdGenerator` 与默认实现迁移到 `generator` 包。
- 不保留旧 `support` 包桥接类型。

### traceId 标准化

- 默认 resolver 按 `traceId`、`trace-id`、`X-B3-TraceId` 顺序读取，每个候选都执行 trim 和 blank fallback。
- 发布引擎再次标准化自定义 resolver 返回值。
- Envelope 与默认 trace header 使用同一个标准化 traceId；blank 不再生成 trace header。

### Header 安全与一致性

- header key 在 trim 前检查原始控制字符和 Unicode 分隔字符，防止清洗后绕过。
- 默认 header 配置在启动期和每次发送前校验原始值、blank 和大小写重复。
- message/customizer 产生的 trim 后重复 header 在覆盖前拒绝。
- 默认禁止覆盖时，customizer 删除默认 header 后以大小写变体写回也会被拒绝。
- 不安全或超长的 header key 使用 `<unsafe>` 展示，不回显 header value。

### 错误消息安全边界

- header key、topic、messageType、messageId 统一通过安全展示 Helper。
- ISO 控制字符、Unicode FORMAT/LINE_SEPARATOR/PARAGRAPH_SEPARATOR 或长度超过 256 的值统一显示 `<unsafe>`。
- payload、record key、header value、完整 headers 和 attributes 继续禁止进入错误消息。

### Serializer 契约防御

- 自定义 serializer 返回 null 时，在构造 `ProducerRecord` 和调用 route 前同步抛 `KAFKA_PUBLISHER_006`。
- 空字符串仍是合法 Kafka value。
- serializer 抛出的 `KafkaPublishException` 原样传播。
- 其他运行时异常统一包装为 `KAFKA_PUBLISHER_006`，不拼接原异常 message，也不保留不可信 cause。

### 自定义 KafkaPublisher 完整接管

- 默认发布链收敛到受 `@ConditionalOnMissingBean(KafkaPublisher.class)` 控制的嵌套配置。
- 调用方提供自定义 `KafkaPublisher` 后，默认 publisher、properties、serializer、resolver、generator、clock、validator 和校验触发器全部退场。
- 仅覆盖单个 SPI 时仍创建默认 publisher，且只替换对应 SPI。

## 新增或调整测试

- 新增默认 trace resolver 的 MDC 优先级、trim 和 blank fallback 测试。
- 新增字符串安全展示 Helper 的控制字符、Unicode 类型和 256/257 长度边界测试。
- 新增 topic、messageType、messageId 在序列化失败、发送失败、同步超时和中断路径的安全展示测试。
- 补齐 serializer null 对六个公共发布入口的发送前阻断，并覆盖空字符串合法、异常包装和原样传播。
- 补齐 header 原始控制字符、trim 后重复、运行期默认配置变更和 customizer 大小写重命名防线。
- 补齐全局 ObjectMapper 隔离、Java 8 时间类型 ISO-8601 序列化和自定义 KafkaPublisher 完整接管测试。
- E2E 覆盖真实 traceId 在 Envelope/header 中一致。
- E2E 新增同一 Publisher 按 Kafka 1.1.0 → 2.8.1 → 3.7.1 → 三 Broker 集群 → Kafka 1.1.0 往返切换；四套 Kafka 使用同名 topic，精确断言每个 datasource 实际收到的消息 key 集合。

## 测试结果

- 16 个测试类、96 个测试。
- 0 skipped、0 failures、0 errors。
- Spring Boot 2.2.13、2.3.12、2.4.5、2.7.9 四套全量矩阵通过。
- Kafka 1.1.0、2.8.1、3.7.1 单节点和 Kafka 3.7.1 三 Broker 集群真实 E2E 通过。
- 每套 Spring Boot 矩阵均实际执行完整多 Kafka 集群 E2E，不使用测试跳过开关。

## 向后兼容性

- `KafkaPublisher` 六个公共方法、消息模型、Envelope、结果模型和路由语义保持不变。
- ErrorCode 集合保持不变。
- `JacksonKafkaPublishSerializer(ObjectMapper)` 构造方式删除，改为无参构造。
- trace resolver 和 messageId generator 的旧 `support` 包路径不再保留，属于源码不兼容调整。
- 默认序列化协议不再继承应用全局 Jackson 配置；依赖该行为的调用方必须自定义 serializer。

## 升级指南

1. 依赖版本升级到 `simple-kafka-publisher-starter:1.1.0`。
2. 将 `new JacksonKafkaPublishSerializer(objectMapper)` 改为无参构造，或注册自定义 `KafkaPublishSerializer`。
3. 将 trace resolver import 改到 `io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver`。
4. 将 messageId generator import 改到 `io.github.surezzzzzz.sdk.messaging.kafka.publisher.generator`。
5. Java 8 时间类型已由默认 mapper 按 ISO-8601 支持；若需要其他模块、命名策略或自定义协议，请注册自定义 serializer，不要依赖 Spring 全局 `ObjectMapper`。
6. 若已提供自定义 `KafkaPublisher`，确认默认 publisher properties 和 validator 退场符合预期。
