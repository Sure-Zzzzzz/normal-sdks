# simple-kafka-route-starter v1.0.4 CHANGELOG

## 发布日期

2026-07-29

## 类型

Feature

## 依赖升级表

| 依赖 | 1.0.3 | 1.0.4 | 说明 |
|------|-------|-------|------|
| Spring Boot | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| Spring Kafka | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| kafka-clients | 跟随 Spring Kafka | 跟随 Spring Kafka | 无依赖升级 |

## 变更内容

### 新增 callback 作用域 AdminClient 资源入口

新增 `KafkaRouteAdminClientFactory` 与 `KafkaRouteAdminClientCallback`，按已配置 datasource key 在 callback 内提供短生命周期 `AdminClient`。

- Factory 复用 Route 已验证的数据源和配置合并语义，不暴露原始 Kafka 配置或安全字段。
- callback 正常返回或抛出异常时，Route 均在 `finally` 中关闭当前 AdminClient。
- callback 必须在返回前完成所有依赖 AdminClient 的异步请求，不能缓存、返回或传递客户端及未完成的依赖对象。
- Factory 在构造期保存不可变配置快照；每次创建客户端使用独立配置副本，不受后续配置对象修改或前一次调用影响。
- Factory 停止后拒绝新调用，但不会抢占已获准 callback 内的客户端；该客户端仍由当前 callback 负责收尾关闭。
- 创建失败仅返回安全的 datasource 级错误，不保留可能包含安全配置的底层异常链。
- 调用方可通过自定义 `KafkaRouteAdminClientFactory` Bean 整体替换默认实现。

### 兼容处理

`KafkaAdminCompatibilityHelper#createAdminClient` 保持既有 `Object` 返回类型，避免已编译调用方因方法描述符变化产生二进制不兼容。

## 新增测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KafkaRouteAdminClientFactoryTest` | callback 正常与异常关闭、关闭 RuntimeException 与 Error 语义、无效 datasource 与空 callback、创建失败安全错误、停止后拒绝新调用、停止竞争、构造期配置快照与单次调用配置隔离 |
| `SimpleKafkaRouteAutoConfigurationTest` | 默认 AdminClientFactory 注册及调用方完整覆盖 |
| `KafkaRouteEndToEndTest` | callback 内只读 `describeCluster` 并在 callback 内等待结果 |

## 验证矩阵

1.0.4 已完成完整模块测试与 Docker Kafka 端到端验证，覆盖 callback 内只读 `describeCluster`、多版本 broker 路由、事务、诊断、listener container 和派生 ConsumerFactory 生命周期。

| Spring Boot | Spring Kafka | 构建环境 | 结果 |
|-------------|--------------|----------|------|
| 2.7.9 | 2.8.x | Java 11 / Gradle 8.5 | 通过 |
| 2.4.5 | 2.6.x | Java 8 / Gradle 7.6 | 通过 |
| 2.3.12.RELEASE | 2.5.x | Java 8 / Gradle 7.6 | 通过 |
| 2.2.13.RELEASE | 2.3.x | Java 8 / Gradle 7.6 | 通过 |

## 向后兼容性

- 不新增 Kafka 配置项，不改变 `KafkaRouteTemplate`、`SimpleKafkaRouteRegistry`、producer/consumer factory 或 diagnostics 的现有语义。
- 原有 `KafkaAdminCompatibilityHelper#createAdminClient` 的公开二进制方法签名保持不变。
- 已有调用方无需修改代码或配置；仅在需要 callback 作用域 AdminClient 时注入新 Factory。

## 升级指南

从 1.0.3 升级到 1.0.4 只需要替换依赖版本：

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.4'
```

需要 AdminClient 时，通过 `KafkaRouteAdminClientFactory#withAdminClient` 在 callback 内完成请求；不要在 callback 外保留客户端或未完成异步结果。
