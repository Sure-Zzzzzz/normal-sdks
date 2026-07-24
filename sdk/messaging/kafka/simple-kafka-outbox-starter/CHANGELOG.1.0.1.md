# CHANGELOG 1.0.1

- 发布日期：2026-07-24
- 类型：兼容性修复

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| simple-kafka-outbox-core | 1.0.0 |

## 变更内容

- Runtime 改为传递依赖 `simple-kafka-outbox-core:1.0.0`。
- `OutboxStatus`、`OutboxPayloadKind` 和 `KafkaOutboxStringHelper` 由 Core 提供，保留原有 FQN 和公共方法。
- Runtime 不再打包上述重复类型，消除与 Core 同时使用时的重复 FQN 冲突。

## 测试覆盖

- Core 的 `OutboxStatusContractTest`、`OutboxPayloadKindTest` 与 `KafkaOutboxStringHelperTest` 覆盖迁移类型的公共契约。
- Runtime 保留 engine、serializer、repository、worker、事务、重试、清理与 Kafka 端到端测试；仅移除与 Core 重复的枚举契约断言。
- Spring Boot 2.2.13、2.3.12、2.4.5、2.7.9 四条版本线均执行完整 Runtime suite：每条 128 tests、0 skipped、0 failures、0 errors。
- 2.2.13、2.3.12、2.4.5 使用 Java 8 / Gradle 7.6；2.7.9 使用 Java 11 / Gradle 8.5。所有测试均以 `cleanTest test --rerun-tasks` 执行并核对 JUnit XML。

## 向后兼容性

- Runtime 的配置、DDL、写入、领取、投递、重试、清理和 Worker 状态机语义不变。
- `KafkaOutboxStringHelper` 的长度判断改为按 Unicode 码点处理，避免在代理对中间截断；错误摘要仍最多保留 512 个字符，不会超过现有 `VARCHAR(512)` 列容量。
- 调用方升级到 1.0.1 不需要修改 import、配置或表结构。
- 同时使用 Core 或 Management 时，必须从 Runtime 1.0.0 升级至 1.0.1。

## 升级指南

将依赖升级为：

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-outbox-starter:1.0.1'
```

Runtime 已传递依赖 Core，无需为常规 Runtime 使用重复声明 Core。
