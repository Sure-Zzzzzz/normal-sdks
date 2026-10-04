# CHANGELOG 1.1.1

> 版本：1.1.1
> 类型：Bug Fix / 测试稳定性优化

## 依赖

| 依赖 | 版本 | 变化 |
|------|------|------|
| simple-kafka-route-starter | 1.0.5 | 升级至已发布的 1.0.5 |
| Spring Boot | 2.2.13.RELEASE / 2.3.12.RELEASE / 2.4.5 / 2.7.9 | 不变 |
| Spring Kafka | 2.x | 不变 |
| Java | 8+ | 不变 |

## 变更内容

### 发送失败语义保持一致

- route 未返回发送 Future（异步结果对象），或异步结果缺少 broker metadata（Kafka 服务端返回的发送位点）时，继续返回 `KAFKA_PUBLISHER_007`。
- 移除该受控失败分支中仅用于构造异常的 `IllegalStateException`。
- 在 route 调用失败、空 Future、异步失败和空 metadata 分支补充默认关闭的 DEBUG（诊断）日志；日志只记录固定状态或异常类型，不记录 topic、消息内容、key、header 或连接地址。

### 真实 Kafka E2E 消费稳定性

- consumer（Kafka 消费者）订阅 topic 后先等待 consumer group（协调分配分区的消费者组）完成分区分配，再开始读取消息。
- 分区分配完成后从分区起始位置读取，避免协调阶段的 poll 消耗实际读取窗口。
- 跨 datasource 的负向断言中，目标集群没有该 topic 时返回“未读取到消息”，不将未发生分区分配误判为测试异常。

## 测试结果

- 16 个测试类、96 个测试。
- 0 skipped、0 failures、0 errors。
- Spring Boot 2.2.13.RELEASE、2.3.12.RELEASE、2.4.5、2.7.9 全量矩阵通过。
- Kafka 1.1.0、2.8.1、3.7.1 单节点和 Kafka 3.7.1 三 Broker 集群真实 E2E 通过。

## 向后兼容性

- `KafkaPublisher` 公共 API、配置项、错误码和路由语义保持不变；内置 route starter 依赖升级至 1.0.5。
- 调用方只需将依赖升级到 `simple-kafka-publisher-starter:1.1.1`。
