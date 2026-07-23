# simple-kafka-outbox-core

Kafka Outbox 的纯 Java 领域模型模块。

## 版本选择

| 使用场景                                            | 推荐依赖                                              |
|-------------------------------------------------|---------------------------------------------------|
| 需要复用 Outbox 状态、payload 分类、无 payload 记录视图或文本安全规则 | `simple-kafka-outbox-core:1.0.0`                  |
| 需要写入、扫描和投递 Kafka 消息                             | 使用适配 core 的 `simple-kafka-outbox-starter:1.0.1+`  |
| 需要查询 Outbox 状态或受控重置 POISON 消息                   | 使用 `simple-kafka-outbox-management-starter:1.0.0` |

## 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-outbox-core:1.0.0'
```

## 包含能力

- `OutboxStatus`：投递状态值域。
- `OutboxPayloadKind`：payload 快照分类。
- `OutboxRecord`：不含 payload 内容的只读 Outbox 记录视图。
- `KafkaOutboxStringHelper`：文本归一化、安全展示和错误摘要截断规则。

`OutboxRecord` 使用 `Instant`、`OutboxStatus` 与 `OutboxPayloadKind` 表达领域语义；它不包含
payload、headers、attributes、worker 所有权令牌或 JDBC 乐观锁版本。因此读侧可以将查询结果映射为该模型，而不会经由该模型暴露消息内容。

## 配置

无配置项。core 不提供 Spring Bean、配置绑定、数据源或事务选择、JDBC、SQL、DDL、Kafka 投递、Worker、Repository、自动配置或管理接口，也不能替代
runtime starter。

## 组合约束

`simple-kafka-outbox-core:1.0.0` 不能与已发布的 `simple-kafka-outbox-starter:1.0.0` 同时放入 classpath：旧 runtime 仍包含同
FQN 的共享类型。需要组合 runtime 时，必须使用适配 core 的 `simple-kafka-outbox-starter:1.0.1+`。

runtime starter 与 management starter 是仅依赖 core 的兄弟模块，各自负责基础设施和持久化边界：

```text
simple-kafka-outbox-core
            ↑                         ↑
            │                         │
outbox runtime starter      outbox management starter
```

## 升级说明

`1.0.0` 为首发版本，没有旧 core 版本的升级路径，也不提供 CHANGELOG。
