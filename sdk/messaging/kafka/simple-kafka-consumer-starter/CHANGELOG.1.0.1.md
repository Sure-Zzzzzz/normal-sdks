# CHANGELOG 1.0.1

> 版本：1.0.1
> 类型：维护版本

## 依赖

| 依赖 | 1.0.0 | 1.0.1 | 说明 |
|------|-------|-------|------|
| simple-kafka-route-starter | 1.0.3 | 1.0.5 | 使用已发布的 route 维护版本 |
| Spring Boot | 跟随调用方 | 跟随调用方 | 无升级 |
| Spring Kafka | 跟随 Spring Boot | 跟随 Spring Boot | 无升级 |
| Java | 8+ | 8+ | 无升级 |

## 变更内容

### Route 依赖升级

- 升级到 `simple-kafka-route-starter:1.0.5`，保持 Kafka datasource、topic 路由和 ConsumerFactory 契约不变。

### 异常契约统一

- 幂等领取结果缺少租约、Redis 领取脚本返回约定外结果、topic 未注册处理器、注解方法反射调用失败，统一返回带错误码的 `KafkaConsumerException`。
- 不再由 Consumer 主代码抛出裸 `IllegalArgumentException` 或 `IllegalStateException`。

### 安全观测收敛

- 消费事件的 `errorSummary` 与死信 `x-error-summary` 保持既有字段名，字段值调整为脱敏异常类别，不再写入异常原始消息。
- 已处理的消费、幂等、容器、死信与事件监听器故障以异常类别记录 WARN 日志，不附带异常堆栈。

### 编译依赖边界

- `spring-kafka` 与 `simple-redis-route-starter` 出现在公开签名中，调整为 `compileOnlyApi`，让调用方编译期获得所需类型；两者仍不作为 Consumer 的运行时传递依赖。

### 兼容性

- 保持 Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5` 和 `2.7.9` 的兼容范围；对应 Java 运行时分别为 `8`、`8`、`8` 和 `11`。
- 保持 Kafka `1.1.0`、`2.8.1`、`3.7.1` 单节点及 `3.7.1` 三节点集群的消费链路兼容范围。

## 新增测试

- 覆盖事件和死信 header 不包含异常原始消息。
- 端到端测试在消费回调、幂等终态和 offset 提交之间使用截止时间轮询验证最终状态。

## 向后兼容性

- Consumer 注解、配置项、事件类型、Kafka 消费语义和公开 SPI 保持不变。
- 调用方只需将依赖升级到 `simple-kafka-consumer-starter:1.0.1`。

## 升级指南

- 将 Consumer 依赖升级至 `1.0.1`，并同步使用 `simple-kafka-route-starter:1.0.5`。
- 如有死信或消费事件的自定义落库逻辑，将 `errorSummary` 视为异常类别，不再依赖异常消息文本。
