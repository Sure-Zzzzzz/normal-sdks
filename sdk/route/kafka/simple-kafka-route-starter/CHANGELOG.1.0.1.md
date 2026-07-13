# simple-kafka-route-starter v1.0.1 CHANGELOG

## 发布日期

2026-07-13

## 类型

Bug Fix / Maintenance

## 依赖升级表

| 依赖 | 1.0.0 | 1.0.1 | 说明 |
|------|-------|-------|------|
| Spring Boot | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| Spring Kafka | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| kafka-clients | 跟随 Spring Kafka | 跟随 Spring Kafka | 无依赖升级 |

## 变更内容

### 1. 修复 broker capability 诊断误报

**问题背景**：低版本 broker 或当前 client classpath 不支持 feature API 时，诊断层无法确认事务、幂等 producer、zstd 压缩等能力。1.0.0 中部分 capability 字段被固定写成 `SUPPORTED`，会让调用方误以为能力已确认支持。

**根因**：`DefaultKafkaRouteDiagnostics` 中 `idempotenceSupported`、`zstdSupported` 没有随 feature API 探测结果变化，诊断口径与实际探测证据不一致。

**修复方案**：

- `describeCluster` 成功后，`adminApiLevel` 表示基础 Admin API 可用，返回 `SUPPORTED`。
- `transactionSupported`、`idempotenceSupported`、`zstdSupported` 统一基于 feature API 可达性推断：feature API 返回结果时为 `SUPPORTED`，不可用或探测失败时为 `UNKNOWN`。
- 对显式启用事务、幂等 producer、zstd 压缩且 capability 不是 `SUPPORTED` 的 datasource 返回 `WARN`，不把未知能力静默包装成成功。
- warning 判断按实际生效 producer 配置解析 typed 配置、datasource raw properties、producer raw properties 的覆盖顺序，避免 raw properties 配置 zstd 或幂等时漏诊。

### 2. 修复 Spring Kafka transactionIdPrefix 能力探测口径

**问题背景**：Spring Kafka 2.3.x 中 `ProducerFactory` 接口未声明 `getTransactionIdPrefix`，但 `DefaultKafkaProducerFactory` 实现类存在 protected 方法，route 兼容层可以通过反射访问。

**根因**：`KafkaSpringVersionHelper.supportsGetTransactionIdPrefix()` 探测的是 `ProducerFactory` 接口声明，不是 route 实际使用的 `DefaultKafkaProducerFactory` 可访问能力。

**修复方案**：将探测目标改为 `DefaultKafkaProducerFactory.class`，与 route 默认 producer factory 和反射兼容层保持一致。

## 新增测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KafkaRouteDiagnosticsTest` | 覆盖 feature API 可用/不可用时 capability 精确值；覆盖事务、幂等、zstd 在 UNKNOWN 能力下的 WARN；覆盖 raw properties 覆盖 typed 配置后的 warning 行为 |
| `KafkaCompatibilityHelperTest` | 覆盖 `DefaultKafkaProducerFactory#getTransactionIdPrefix` 在支持版本中必须可反射访问 |
| `KafkaRouteEndToEndTest` | 覆盖 Kafka 1.1.0 / 2.8.1 / 3.7.1 / 3 broker cluster 的路由隔离、事务边界、诊断结果和 capability 精确断言；移除测试跳过开关，默认跑全量 |

## 向后兼容性

- 不新增配置项。
- 不改变 `KafkaRouteTemplate`、resolver、registry、factory SPI 等路由核心 API。
- 不改变 producer / consumer / routing 主路径。
- 诊断报告中低版本 broker 或 feature API 不可达场景的 capability 会从 1.0.0 的误报 `SUPPORTED` 收敛为 `UNKNOWN`，这是诊断准确性修复。
- 显式配置事务、幂等 producer、zstd 压缩但 capability 无法确认时，诊断状态可能从 `SUCCESS` 变为 `WARN`，不阻断启动，除非 broker 完全不可达且 `diagnostics.fail-fast=true`。

## 升级指南

从 1.0.0 升级到 1.0.1 只需要替换依赖版本：

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.1'
```

无需修改配置文件或业务代码。
