# CHANGELOG 1.0.1

> 版本：1.0.1
> 类型：Bug Fix

## 变更内容

- 将 `simple-kafka-route-jakarta-starter` 从 Gradle `project` 依赖改为已发布坐标 `io.github.sure-zzzzzz:simple-kafka-route-jakarta-starter:1.0.0`。
- 保持 `api` 依赖范围，使调用方继续获得 Publisher 公开 API 中涉及的 route 类型。

## 依赖

| 依赖 | 版本 | 变化 |
|------|------|------|
| simple-kafka-route-jakarta-starter | 1.0.0 | 改为已发布坐标 |
| Spring Boot | 3.4.2 | 不变 |
| Spring Kafka | 3.x | 不变 |
| Java | 17+ | 不变 |

## 测试结果

- 16 个测试类、96 个测试。
- 0 skipped、0 failures、0 errors。
- 使用已发布 route 坐标完成完整真实 Kafka E2E。

## 向后兼容性

- `KafkaPublisher` 公共 API、配置项、错误码和消息路由语义保持不变。
- 调用方只需将依赖升级到 `simple-kafka-publisher-jakarta-starter:1.0.1`。
