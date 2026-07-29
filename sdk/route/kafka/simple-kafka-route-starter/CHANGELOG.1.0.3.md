# simple-kafka-route-starter v1.0.3 CHANGELOG

## 发布日期

2026-07-24

## 类型

Bug Fix / Compatibility

## 依赖升级表

| 依赖 | 1.0.2 | 1.0.3 | 说明 |
|------|-------|-------|------|
| Spring Boot | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| Spring Kafka | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| kafka-clients | 跟随 Spring Kafka | 跟随 Spring Kafka | 无依赖升级 |

## 变更内容

### 修复 ConsumerFactory 的 Spring Kafka listener 兼容性

**问题背景**：route 创建的 `DefaultKafkaConsumerFactory` 保留了 `key.deserializer` 和 `value.deserializer` 的类名配置，原生 `Consumer.poll()` 可以正常使用；但 Spring Kafka 2.3.x 的 listener container 启动还会读取 factory 级反序列化器实例，因此无法满足该启动路径。

**根因**：factory 仅通过配置 Map 构造，未向 `DefaultKafkaConsumerFactory` 提供 key/value deserializer 实例。

**修复方案**：

- 基础和派生 ConsumerFactory 在保留 Kafka 配置 Map 中反序列化器类名的同时，分别创建并传入 factory 级 key/value deserializer 实例。
- 每次 factory 创建使用独立反序列化器实例，不在调用方持有的派生 factory 间共享状态。
- 反序列化器为空、类不存在、类型不匹配、无可访问无参构造器或构造失败时，统一以 `KAFKA_ROUTE_011` 拒绝；构造失败保留原始异常原因。
- 不改变 `SimpleKafkaRouteRegistry` API、派生 factory 所有权、override 边界、producer/transaction、publisher 或 outbox 行为。

## 新增测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KafkaConsumerFactoryFactoryTest` | factory 级 deserializer 实例与配置类名并存、默认与显式类型、不同 factory 实例隔离、类不存在、类型不匹配、无无参构造器、构造失败及根因保留 |
| `KafkaRouteEndToEndTest` | 真实 Kafka 上派生 ConsumerFactory 启动 Spring Kafka listener container、接收独立消息、先完成停止再销毁 factory |

## 兼容验证

- Spring Boot 2.2.x / Spring Kafka 2.3.x：完整模块测试通过，真实 listener container 路径通过。
- Spring Boot 2.3.12 / Spring Kafka 2.5.x：完整模块测试通过，真实 listener container 路径通过。
- Spring Boot 2.4.5 / Spring Kafka 2.6.x：完整模块测试通过，真实 listener container 路径通过。
- Spring Boot 2.7.9 / Spring Kafka 2.8.x：完整模块测试通过，真实 listener container 路径通过；Kafka 1.1.0 / 2.8.1 / 3.7.1 单节点和 3 broker cluster 场景均覆盖。

## 向后兼容性

- 不新增或修改公开 API、配置项和依赖版本。
- `getConsumerFactory(...)` 的 registry 持有语义不变；`createConsumerFactory(...)` 的调用方持有和销毁责任不变。
- 已有调用方无需修改配置或代码；升级后 route 创建的基础和派生 ConsumerFactory 可直接用于 Spring Kafka listener container。

## 升级指南

从 1.0.2 升级到 1.0.3 只需要替换依赖版本：

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.3'
```

无需修改配置或业务代码。调用方仍应先停止 listener container，再销毁 `createConsumerFactory(...)` 返回的派生 factory。
