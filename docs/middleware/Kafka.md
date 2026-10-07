# Kafka 工具链

Kafka 生态接入：连接路由 → 消息发布 → Outbox 事务投递 → 管理面。自底向上组织。

## 连接与路由（底层）

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-kafka-route-starter](../../sdk/route/kafka/simple-kafka-route-starter) | 1.0.5 | 1.0.0 | 多数据源路由（按 topic/route key 路由；不注册全局 KafkaTemplate；callback 作用域 AdminClient 与安全诊断） | [README](../../sdk/route/kafka/simple-kafka-route-starter/README.md) |

## 发布与投递

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-kafka-publisher-starter](../../sdk/messaging/kafka/simple-kafka-publisher-starter) | 1.1.0 | 1.0.1 | 消息发布（topic 路由+可选 Envelope+通用 header，不持有 KafkaTemplate） | [README](../../sdk/messaging/kafka/simple-kafka-publisher-starter/README.md) |
| [simple-kafka-outbox-core](../../sdk/messaging/kafka/simple-kafka-outbox-core) | 1.0.0 | — | Outbox 共享领域模型（状态/payload 分类/无 payload 视图/文本安全规则） | [README](../../sdk/messaging/kafka/simple-kafka-outbox-core/README.md) |
| [simple-kafka-outbox-starter](../../sdk/messaging/kafka/simple-kafka-outbox-starter) | 1.0.1 | — | 本地事务 Outbox（业务事务内落库+后台 Worker 至少一次投递） | [README](../../sdk/messaging/kafka/simple-kafka-outbox-starter/README.md) |
| [simple-kafka-outbox-management-starter](../../sdk/messaging/kafka/simple-kafka-outbox-management-starter) | 1.0.0 | — | Outbox 管理页面（状态查询/受控重置单条 POISON） | [README](../../sdk/messaging/kafka/simple-kafka-outbox-management-starter/README.md) |

## publisher vs outbox 选型

| | publisher-starter | outbox-starter |
|---|---|---|
| 适用场景 | 允许极低概率丢失的非关键消息 | 写入与发送必须原子保证的关键消息（订单/支付/状态变更） |
| 发送时机 | 调用即发（同步或异步 Future） | 业务事务提交后后台 Worker 异步投递 |
| 事务保证 | 无 | 有（同事务落库，broker 故障自动重试） |
| 依赖 | kafka-route | kafka-route + publisher（传递）+ [MySQL](MySQL.md) |
| 消费端要求 | 无 | 按 messageId 幂等（at-least-once） |

## 依赖关系

- publisher/outbox 均依赖 kafka-route；outbox 落库走 MySQL 路由
- outbox：ownerToken+version CAS 短事务租约领取，5 态状态机（PENDING/PROCESSING/RETRY_WAIT/SENT/POISON），指数退避，多实例无共享状态

## 版本映射（唯一事实源）

| outbox-core | outbox-starter | outbox-management | publisher | route |
|-------------|----------------|-------------------|-----------|-------|
| 1.0.0 | 1.0.1 | 1.0.0 | 1.1.0 | 1.0.1 |
