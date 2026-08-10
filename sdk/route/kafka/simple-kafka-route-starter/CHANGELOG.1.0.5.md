# simple-kafka-route-starter v1.0.5 CHANGELOG

## 类型

维护版本

## 变更内容

### 新增安全诊断告警原因

`KafkaRouteBrokerDiagnosticResult` 新增 nullable 的 `diagnosticReason`：

- 仅当诊断状态为 `WARN` 时返回固定、脱敏的中文短消息。
- 同时存在多个未确认的已启用能力时，原因优先级固定为事务生产者、幂等生产者、zstd 压缩。
- `SUCCESS` 与 `FAILED` 时该字段为 `null`；`FAILED` 继续仅使用已有的安全 `failureReason`。
- 原因不包含 bootstrap、认证信息、JAAS、SSL 配置或原始异常，下游应直接读取该字段，不要根据 capability 自行推断。

| WARN 场景 | 固定原因 |
|-----------|----------|
| 事务生产者能力未确认 | `已配置事务生产者，但 broker Feature API 未确认事务能力` |
| 幂等生产者能力未确认 | `已启用幂等生产者，但 broker Feature API 未确认幂等能力` |
| zstd 压缩能力未确认 | `已配置 zstd 压缩，但 broker Feature API 未确认 zstd 能力` |

## 依赖升级表

| 依赖 | 1.0.4 | 1.0.5 | 说明 |
|------|-------|-------|------|
| Spring Boot | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| Spring Kafka | 跟随调用方 | 跟随调用方 | 无依赖升级 |
| kafka-clients | 跟随 Spring Kafka | 跟随 Spring Kafka | 无依赖升级 |

## 新增测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KafkaRouteDiagnosticsTest` | 事务、typed/raw/datasource raw 幂等与 zstd 告警原因；事务优先级；SUCCESS/FAILED 空原因；公开原因不含敏感配置 |
| `KafkaRouteEndToEndTest` | tx37 在 Feature API 未确认时返回事务 WARN 原因，已确认时保持 SUCCESS 空原因 |

## 验证矩阵

已按模块 `LOCAL_TEST_COMMANDS.md` 完成完整 starter test 与 Docker Kafka E2E；每个基线均覆盖 callback 内只读 `describeCluster`、多版本 broker 路由、事务、诊断及 `diagnosticReason`、listener container 和派生 ConsumerFactory 生命周期。

| Spring Boot | Spring Kafka | 构建环境 | 结果 |
|-------------|--------------|----------|------|
| 2.7.9 | 2.8.x | Java 11 / Gradle 8.5 | 已验证 |
| 2.4.5 | 2.6.x | Java 8 / Gradle 7.6 | 已验证 |
| 2.3.12.RELEASE | 2.5.x | Java 8 / Gradle 7.6 | 已验证 |
| 2.2.13.RELEASE | 2.3.x | Java 8 / Gradle 7.6 | 已验证 |

## 向后兼容性

- 新增字段和 Lombok builder 方法均为增量 API；既有诊断访问方式、配置项和状态语义不变。
- 未新增或升级生产依赖，未新增配置项，`KafkaRouteDiagnostics` SPI 不变。
- `WARN` 仍不触发 fail-fast，fail-fast 继续只处理 `FAILED`。
- 自定义 `KafkaRouteDiagnostics` 可以继续返回未设置 `diagnosticReason` 的结果；下游应仅展示中性提示，不能反推 Route 的具体能力原因。

## 升级指南

从 1.0.4 升级到 1.0.5 只需要替换依赖版本：

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-route-starter:1.0.5'
```

需要解释 `WARN` 时读取 `KafkaRouteBrokerDiagnosticResult#getDiagnosticReason()`；不要展示或拼接 Route 内部配置、broker 异常或认证信息。
