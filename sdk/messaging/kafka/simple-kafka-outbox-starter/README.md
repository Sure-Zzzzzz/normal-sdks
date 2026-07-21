# simple-kafka-outbox-starter

基于 `simple-kafka-publisher-starter:1.1.0` 的本地事务 Outbox SDK。业务事务内保存稳定消息快照，后台 Worker 以短事务租约领取并通过 `KafkaPublisher.publish(message)` 至少一次投递。

## 语义与边界

- 业务写入和 `KafkaOutboxEngine.save(...)` 必须处于同一个 `DataSourceTransactionManager` 管理的可写事务中。
- Kafka ack 后、SENT 回写前发生故障时会重复投递。消费端必须按稳定 `messageId` 幂等。
- 不提供跨库原子性、Kafka producer transaction、消费端幂等、严格顺序、管理页面或自动建表。
- 消息保存时要求显式非空 topic。routeKey、datasourceKey、Envelope、默认 header 和路由规则仍按发送时 publisher/route 配置生效，不能视为完整 wire payload 冻结。
- POISON 默认永久保留；自动清理只删除超过保留期的 SENT 记录。

## 兼容性

| 组件 | 版本 |
|---|---|
| simple-kafka-outbox-starter | 1.0.0 |
| simple-kafka-publisher-starter | 1.1.0 |
| simple-kafka-route-starter | 1.0.1 |
| Spring Boot | 2.2.13 / 2.3.12 / 2.4.5 / 2.7.9 |
| Java | 8+ |
| MySQL | 5.7+ InnoDB |

## 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-kafka-outbox-starter:1.0.0'
implementation 'org.springframework.kafka:spring-kafka'
```

应用还需提供 Spring JDBC、MySQL 驱动、DataSource 和对应的 `DataSourceTransactionManager`。

## 建表

starter 不自动建表。首次安装前阅读 `docs/README.md` 并手动执行 `docs/01_schema.sql`。脚本包含 DROP，生产已有表时禁止重复执行。

## 配置示例

outbox 依赖 `simple-kafka-publisher-starter`（transitive）和 `simple-kafka-route-starter`（transitive via publisher），三者均需配置。以下是可直接参考的全量配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:

        # ── simple-kafka-route-starter：Kafka broker 连接与路由 ──────────
        kafka:
          route:
            enable: true
            default-source: default           # 未命中路由规则时的 fallback 数据源
            sources:
              default:
                bootstrap-servers:
                  - localhost:9092
                producer:
                  acks: all
                  retries: 3
                  delivery-timeout-ms: 30000
                # 多集群时继续追加，例：
                # cluster-b:
                #   bootstrap-servers:
                #     - broker-b:9092
            rules:                            # 路由规则（无规则时全部走 default-source）
              - pattern: "order.*"
                type: WILDCARD
                datasource: default
                priority: 100
                enable: true
            diagnostics:
              enable: true
              startup-check: true             # 启动时探测 broker 连通性
              fail-fast: false                # true 时 broker 不可达会阻断启动
              timeout-ms: 3000
              log-summary: true

        # ── simple-kafka-publisher-starter：消息发送 ────────────────────
        messaging:
          kafka:
            publisher:
              enable: true
              app-name: my-app               # 写入默认 header x-source 的值
              send:
                timeout-ms: 3000             # publisher 层内部 Future 超时（与下方 outbox.send.timeout-ms 独立）
              envelope:
                enable: true
                include-null-payload: false
                enable-default-headers: true
                allow-header-override: false

            # ── simple-kafka-outbox-starter：本地事务 Outbox ─────────────
            outbox:
              enable: true
              data-source-bean-name: dataSource
              transaction-manager-bean-name: transactionManager
              table-name: simple_kafka_outbox
              worker:
                enable: true
                concurrency: 1
                batch-size: 20
                scan-interval-ms: 500        # 有候选时的扫描间隔
                idle-interval-ms: 2000       # 无候选时的空闲间隔
                lease-ms: 30000
                shutdown-await-ms: 20000
              send:
                timeout-ms: 25000            # outbox worker 调用 future.get() 的超时，必须 < lease-ms
              retry:
                max-attempts: 10
                initial-interval-ms: 1000
                multiplier: 2.0
                max-interval-ms: 300000
                jitter-factor: 0.2
              cleanup:
                enable: true
                retention-days: 7
                batch-size: 500
                interval-ms: 3600000
```

outbox 默认关闭。publisher 未启用或容器中不存在 `KafkaPublisher` 时不注册。单个 DataSource/`DataSourceTransactionManager` 时名称可省略；存在多个候选时必须显式配置，starter 不猜测 `@Primary`。route 和 publisher 的完整配置项分别参见各自 README。


## 保存消息

```java
@Transactional(transactionManager = "transactionManager")
public void createMockRecord() {
    // 在此写入业务表
    KafkaPublishMessage<MockPayload> message = KafkaPublishMessage.<MockPayload>builder()
            .topic("mock.event.created")
            .messageId("mock-message-id")
            .messageType("mock.event.created")
            .payload(mockPayload)
            .build();
    OutboxSaveResult result = kafkaOutboxEngine.save(message);
}
```

`save` 不修改传入消息；headers、attributes 在调用线程防御性复制并于返回前完成序列化。messageId 未提供时自动生成 UUID，最大 191 字符；重复 messageId 拒绝写入。

## 状态与重试

状态为 `PENDING`、`PROCESSING`、`RETRY_WAIT`、`SENT`、`POISON`。Worker 合并待投递/重试候选和租约到期 PROCESSING 候选，以 ownerToken + version CAS 领取和回写；网络等待发生在领取事务之外。

`worker.concurrency` 控制并发槽位，候选领取数不会超过空闲槽位，执行器零排队。停机与同步发布入口串行化：未进入 `KafkaPublisher.publish` 的已领取任务释放租约；已进入发布入口的任务保持租约，由 ACK 回写或租约恢复收敛，避免将可能已投递的消息误标记为停机重试。同步准备阶段的确定性消息/序列化/路由参数错误进入 POISON；broker、网络、Future 超时和结果未知进入 RETRY_WAIT；达到 `retry.max-attempts` 后进入 POISON。

## 扩展点

可按类型提供单个自定义 Bean 覆盖：

- `KafkaOutboxEngine`
- `KafkaOutboxRepository`
- `KafkaOutboxMessageSerializer`
- `KafkaOutboxTraceSnapshotResolver`
- `KafkaOutboxTraceScope`
- `KafkaOutboxRetryPolicy`
- `KafkaOutboxJitterGenerator`
- `KafkaOutboxEventListener`
- `KafkaOutboxWorker`

提供自定义 `KafkaOutboxEngine` 后，默认 Properties、数据库资源选择、Repository、Serializer、RetryPolicy、Listener、Worker、调度器和清理器整条链路退场。只覆盖单个其他 SPI 时，默认 Engine 继续工作，仅替换对应实现。`worker.enable=false` 只关闭默认投递 Worker；`cleanup.enable` 独立控制清理器。

默认 Jackson serializer 使用模块私有静态 `ObjectMapper`，内置 `JavaTimeModule` 并使用 ISO-8601，不读取 Spring 全局 `ObjectMapper`。

## 完整配置参数

前缀：`io.github.surezzzzzz.sdk.messaging.kafka.outbox`

### 根配置

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enable` | boolean | false | 是否启用，必须显式设为 true |
| `data-source-bean-name` | String | ""（空） | 业务 DataSource Bean 名；容器中只有一个 DataSource 时可省略，多个时必须指定 |
| `transaction-manager-bean-name` | String | ""（空） | 事务管理器 Bean 名；容器中只有一个 DataSourceTransactionManager 时可省略，多个时必须指定 |
| `table-name` | String | simple_kafka_outbox | Outbox 表名，仅允许字母/数字/下划线，最长 64 字符 |

### worker 配置

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `worker.enable` | boolean | true | 是否启用默认 Worker |
| `worker.concurrency` | int | 1 | 并发投递槽位数，必须 > 0 |
| `worker.batch-size` | int | 20 | 每轮候选领取上限，必须 > 0 |
| `worker.scan-interval-ms` | long | 500 | 有候选时的扫描间隔（ms），必须 > 0 |
| `worker.idle-interval-ms` | long | 2000 | 无候选时的空闲间隔（ms），必须 > 0 |
| `worker.lease-ms` | long | 30000 | 领取租约时长（ms），转为微秒时不能溢出 Long |
| `worker.shutdown-await-ms` | long | 20000 | 停机等待槽位释放时长（ms），必须 ≤ lease-ms |

### send 配置

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `send.timeout-ms` | long | 25000 | 单条 Future.get() 等待超时（ms），必须 > 0 且 < lease-ms |

### retry 配置

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `retry.max-attempts` | int | 10 | 最大投递总次数（含首次），必须 > 0；达到上限后进 POISON |
| `retry.initial-interval-ms` | long | 1000 | 首次重试间隔（ms） |
| `retry.multiplier` | double | 2.0 | 退避倍数，必须 ≥ 1.0 |
| `retry.max-interval-ms` | long | 300000 | 重试间隔上限（ms），必须 ≥ initial-interval-ms |
| `retry.jitter-factor` | double | 0.2 | 抖动比例 [0.0, 1.0]，防止重试风暴 |

### cleanup 配置

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `cleanup.enable` | boolean | true | 是否启用 SENT 记录自动清理 |
| `cleanup.retention-days` | int | 7 | SENT 记录保留天数，必须 > 0 |
| `cleanup.batch-size` | int | 500 | 每批清理最大行数，必须 > 0 |
| `cleanup.interval-ms` | long | 3600000 | 清理任务间隔（ms），默认 1 小时 |

### 时序约束

- `send.timeout-ms` 必须小于 `worker.lease-ms`：发送超时后 Future 还未返回，如果此时租约已到期被其他 Worker 领走，回写会因 CAS 版本不匹配而失败；send 超时与租约之间应留有足够余量，建议租约 ≥ 发送超时 × 2。
- `worker.shutdown-await-ms` 不能超过 `worker.lease-ms`：停机等待时间超过租约时长没有意义，并会导致恢复任务和停机等待并发竞争同一条记录。

## 最佳实践

**容量规划**

- 单实例低吞吐场景：`concurrency=1`，`batch-size=10`，默认 scan/idle 间隔即可。
- 高吞吐场景：按照 Kafka broker 写入 RTT 来确定 `concurrency`，`concurrency × batch-size` 不宜超出下游 broker 的接收能力。Leader 选举或分区 hang 时会大量消耗槽位，`batch-size` 建议留出安全余量。

**重试参数**

- 不可重试错误（payload 序列化失败、路由参数非法）直接进 POISON，`max-attempts` 不影响此路径。
- 可重试错误（broker 超时、连接失败）按退避策略安排下次投递时间，数据库扫描不会提前执行。`jitter-factor` 取 0.1～0.2 防止多实例在同一时刻大量集中重试同一批消息。

**监控与告警**

- 通过 `KafkaOutboxEventListener.onPoison` 监控进入 POISON 的消息，生产中出现 POISON 应及时人工介入排查原因并补投。
- 通过 `onLeaseLost` 监控租约竞争情况，频繁出现表示实例数过多或租约时间设置偏短。
- SENT 记录量持续堆积（`retention-days` 期内未清理）通常意味着清理任务未正常运行，需排查 `cleanup.enable` 配置和调度器状态。

**POISON 记录处理**

POISON 记录默认永久保留，不会被自动清理。排查根因修复后，可通过直接更新数据库将 POISON 状态的记录重置为 PENDING 触发重新投递；操作前请确认幂等性。

## 分布式部署

**多实例安全**

Outbox Worker 不持有任何跨进程共享状态。并发控制完全通过 MySQL：Worker 领取候选时以 `ownerToken`（UUID）+ `version`（乐观锁）进行 CAS 更新，同一条记录同时只能被一个实例领取成功；其他实例尝试领取同一行时 CAS 失败，该记录保持 PROCESSING 等到租约到期后自动恢复。

**多实例启动**

任意数量的应用实例可同时运行，每个实例各自独立启动 Worker，互不干扰。在线扩缩容无需停止其他实例，也无需外部协调。

**JVM 内部状态**

`lifecycleGeneration`、`slots`（Semaphore）、`scanFuture` 等字段均为进程级内存状态，重启或新增实例时自然初始化，不需要清理或同步。

**多实例竞争调优**

实例数量越多，CAS 竞争越激烈，`leaseLost` 事件（竞争失败）会增加。建议优先通过调大单实例的 `concurrency` 和 `batch-size` 来提升吞吐，而不是水平加实例。如果业务要求高可用冗余，保持 2～3 实例是合理的；实例数量再多时需结合实际 `leaseLost` 监控数据评估是否有价值。

## 升级说明

1.0.0 为首发版本，无历史版本升级步骤。后续版本如变更快照协议或 DDL，将单独提供迁移说明；不要用本版本包含 DROP 的首次安装脚本升级已有表。
