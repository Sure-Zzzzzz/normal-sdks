# simple-kafka-route-starter v1.0.2 CHANGELOG

## 发布日期

2026-07-23

## 类型

Feature / Maintenance

## 依赖升级表

| 依赖 | 1.0.1 | 1.0.2 | 说明 |
|------|-------|-------|------|
| Spring Boot | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| Spring Kafka | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| kafka-clients | 跟随 Spring Kafka | 跟随 Spring Kafka | 无依赖升级 |

## 变更内容

### 1. 新增调用方持有的派生 ConsumerFactory

**问题背景**：同一 datasource 的多个消费入口可能需要不同的消费组、起始 offset、自动提交开关或单次 poll 条数。复用 registry 的基础 ConsumerFactory 会使这些入口共享配置，无法安全表达独立消费行为。

**根因**：此前 route 只暴露 registry 持有的基础 ConsumerFactory，缺少从 datasource 配置创建独立 consumer 资源的公开入口。

**修复方案**：

- `SimpleKafkaRouteRegistry` 新增 `createConsumerFactory(datasourceKey, override)`。
- 每次调用都创建独立实例；`override` 为 `null` 时同样不复用基础 factory。
- 返回的派生 factory 由调用方负责销毁；route registry 不缓存、不销毁已成功交付的派生 factory；`getConsumerFactory(...)` 的基础 factory 所有权与既有版本一致，仍由 registry 管理。
- 支持按调用覆盖 `groupId`、`autoOffsetReset`、`enableAutoCommit`、`maxPollRecords`；非空 override 优先于 datasource typed consumer 配置，显式 `enableAutoCommit=false` 会生效。
- route 固定 bootstrap servers、安全配置、反序列化器和 client id，避免调用方绕过 datasource 边界。
- registry 关闭前后与 SPI 创建过程使用同一生命周期边界；SPI 重入关闭时，未交付的派生 factory 会被回收并报 `KAFKA_ROUTE_016`。

### 2. 约束 consumer raw properties 与扩展 SPI

- datasource 公共 raw properties 和 `consumer.properties` 不再允许设置 `group.id`、`auto.offset.reset`、`enable.auto.commit`、`max.poll.records`。
- 这些 consumer 受控字段必须分别通过 datasource typed consumer 配置和 `KafkaConsumerFactoryOverride` 设置。
- `KafkaConsumerFactoryFactory` 增加三参数派生创建入口。旧版自定义 SPI 未实现该入口时固定报 `KAFKA_ROUTE_015`，不会回退复用两参数基础 factory。

### 3. 统一 Locale 无关的大小写处理

- consumer override、配置合并、校验和诊断路径的大小写标准化统一使用 `Locale.ROOT`。
- 避免在土耳其 Locale 等默认 Locale 环境下误判消费配置或敏感诊断信息。

## 新增测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KafkaConsumerFactoryFactoryTest` | 四项 override 优先级、显式 false、空 override 独立性、受控 raw key、Locale.ROOT |
| `SimpleKafkaRouteRegistryTest` | 旧 SPI fail-fast、不缓存、调用方与 registry 的销毁边界、SPI 重入 destroy 回收、并发 create/destroy 线性化 |
| `KafkaRouteDiagnosticsTest` | 土耳其 Locale 下 compression 与敏感消息识别 |
| `KafkaRouteEndToEndTest` | 真实 Kafka broker 上同 datasource 两个独立 group 的派生 factory 消费、生效参数与销毁隔离 |

## 向后兼容性

- `getConsumerFactory(...)` 保持原有 registry 持有的基础 factory 语义。
- `KafkaRouteTemplate`、路由规则和 producer 路径不变。
- 新增的三参数 SPI 为可选扩展；未实现它的旧 SPI 仅在调用派生 API 时失败，不影响原有两参数 SPI 和基础 factory 获取。
- consumer raw properties 中此前未受支持的四个受控字段现在会被明确拒绝，调用方应迁移到 typed 配置或 override。

## 升级指南

从 1.0.1 升级到 1.0.2：

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.2'
```

1. 只使用发送、路由或 `getConsumerFactory(...)` 的调用方无需修改代码或配置。
2. 需要独立 consumer 参数时，改用 `createConsumerFactory(...)`；调用方必须在对应消费入口停止或启动失败时销毁返回的 factory，不能销毁 `getConsumerFactory(...)` 的基础 factory。
3. 如在 datasource `properties` 或 `consumer.properties` 写入过 `group.id`、`auto.offset.reset`、`enable.auto.commit`、`max.poll.records`，升级后会报 `KAFKA_ROUTE_005`；迁移到 `consumer.*` typed 配置或 `KafkaConsumerFactoryOverride`。
4. 自定义 `KafkaConsumerFactoryFactory` 需要支持派生 factory 时，实现三参数 `create(datasourceKey, config, override)` 并保证每次返回独占实例；未实现时调用新 API 固定报 `KAFKA_ROUTE_015`，原有两参数 SPI 与 `getConsumerFactory(...)` 不受影响。
