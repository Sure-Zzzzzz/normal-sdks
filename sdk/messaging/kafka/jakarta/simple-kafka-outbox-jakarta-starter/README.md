# Simple Kafka Outbox Jakarta Starter

面向 Spring Boot 3 / Jakarta 应用的本地事务 Outbox（发件箱）组件。业务在数据库事务内调用 `KafkaOutboxEngine.save(...)` 保存稳定消息快照，后台 Worker 以短事务租约领取记录，经 `KafkaPublisher`（Kafka 发送组件）投递并回写结果，实现业务写入与消息发送的最终一致。

业务事务回滚时消息不投递；Kafka 确认后、结果回写前发生故障时会重复投递，消费端必须按 `messageId`（消息唯一标识）幂等（重复消费不产生额外结果）。本模块不提供跨库原子性、Kafka producer transaction、消费端幂等、严格顺序、管理页面或自动建表。

## 版本与依赖

| 模块 | 版本 | 运行环境 |
|---|---|---|
| `simple-kafka-outbox-jakarta-starter` | `1.0.0` | Java 17+，Spring Boot `3.4.2` 基线 |
| `simple-kafka-outbox-starter` | `1.0.1` | 原 javax 运行线；不与 Jakarta 包同时引入 |
| `simple-kafka-outbox-core` | `1.0.0` | 两线共用的纯 Java 领域模型，不绑定 Spring Boot |

```groovy
implementation 'io.github.sure-zzzzzz:simple-kafka-outbox-jakarta-starter:1.0.0'
implementation 'org.springframework.kafka:spring-kafka'
```

使用前提：应用已经配置 MySQL 数据源与 `DataSourceTransactionManager`（数据源事务管理器），并按发送组件与路由组件的 README 配置 Kafka 连接（两者经本模块传递引入，坐标无需重复声明）。消息快照不视为完整发送报文冻结：routeKey、datasourceKey、Envelope（信封包装）、默认 header 与路由规则仍按发送时的 publisher 和 route 配置生效。

## 建表

本模块不自动建表，也不集成 Flyway 或 Liquibase（数据库变更管理工具）。首次安装前阅读 `docs/README.md` 并手动执行 `docs/01_schema.sql`；脚本包含 DROP，生产已有表时禁止重复执行。表结构与 javax 线完全一致，两条运行线的应用可以读写同一张表结构定义。

## 最小配置

### 只投递不扩展

在已有 MySQL 数据源与 Kafka 路由的应用中，开启 outbox 即可获得默认的保存、投递、重试与清理链，不需要自定义 Bean：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kafka:
          route:
            enable: true
            default-source: default
            sources:
              default:
                bootstrap-servers:
                  - localhost:9092
        messaging:
          kafka:
            publisher:
              enable: true
              app-name: my-app
            outbox:
              enable: true
```

outbox 默认关闭，必须显式设为 `true`。容器中只有一个 DataSource 和一个 `DataSourceTransactionManager` 时无需指定 Bean 名；存在多个候选时必须显式配置，本模块不猜测 `@Primary`。publisher 未启用或容器中不存在 `KafkaPublisher` 时，outbox 不注册。

### 业务侧保存消息

业务方法与保存调用必须处于同一个可写事务中，只读事务和事务外调用会被拒绝：

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

`save` 不修改传入消息；headers、attributes 在调用线程防御性复制并于返回前完成序列化。`messageId` 未提供时自动生成 UUID，最长 191 字符；重复 `messageId` 拒绝写入。

## 完整配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
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
                timeout-ms: 25000            # Worker 等待发送 Future 的超时，必须 < lease-ms
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

以上 outbox 段包含本模块全部配置项；route 与 publisher 段的完整配置项参见各自 README。发送超时必须小于租约时长（建议租约 ≥ 发送超时 × 2），停机等待不能超过租约时长，否则会出现回写竞争。

## 配置项说明

前缀：`io.github.surezzzzzz.sdk.messaging.kafka.outbox`（与 javax 线一致）

### 根配置

| 配置项 | 默认值 | 含义 |
|---|---|---|
| `enable` | `false` | 是否启用，必须显式设为 `true` |
| `data-source-bean-name` | ""（空） | 业务 DataSource Bean 名；容器中只有一个时可省略，多个时必须指定 |
| `transaction-manager-bean-name` | ""（空） | 事务管理器 Bean 名；规则同上 |
| `table-name` | `simple_kafka_outbox` | Outbox 表名，仅允许字母/数字/下划线，最长 64 字符 |

### worker 配置

| 配置项 | 默认值 | 含义 |
|---|---|---|
| `worker.enable` | `true` | 是否启用默认投递 Worker；设为 `false` 只保留保存与清理 |
| `worker.concurrency` | `1` | 并发投递槽位数，必须 > 0 |
| `worker.batch-size` | `20` | 每轮候选领取上限，必须 > 0 |
| `worker.scan-interval-ms` | `500` | 有候选时的扫描间隔（ms），必须 > 0 |
| `worker.idle-interval-ms` | `2000` | 无候选时的空闲间隔（ms），必须 > 0 |
| `worker.lease-ms` | `30000` | 领取租约时长（ms），转为微秒时不能溢出 Long |
| `worker.shutdown-await-ms` | `20000` | 停机等待槽位释放时长（ms），必须 ≤ lease-ms |

### send 与 retry 配置

| 配置项 | 默认值 | 含义 |
|---|---|---|
| `send.timeout-ms` | `25000` | 单条发送 Future 等待超时（ms），必须 > 0 且 < lease-ms |
| `retry.max-attempts` | `10` | 最大投递总次数（含首次），达到上限后进 POISON |
| `retry.initial-interval-ms` | `1000` | 首次重试间隔（ms） |
| `retry.multiplier` | `2.0` | 退避倍数，必须 ≥ 1.0 |
| `retry.max-interval-ms` | `300000` | 重试间隔上限（ms），必须 ≥ initial-interval-ms |
| `retry.jitter-factor` | `0.2` | 抖动比例 [0.0, 1.0]，防止多实例同时集中重试 |

### cleanup 配置

| 配置项 | 默认值 | 含义 |
|---|---|---|
| `cleanup.enable` | `true` | 是否启用 SENT 记录自动清理 |
| `cleanup.retention-days` | `7` | SENT 记录保留天数，必须 > 0 |
| `cleanup.batch-size` | `500` | 每批清理最大行数，必须 > 0 |
| `cleanup.interval-ms` | `3600000` | 清理任务间隔（ms），默认 1 小时 |

## 状态与重试

状态为 `PENDING`（待投递）、`PROCESSING`（投递中）、`RETRY_WAIT`（等待重试）、`SENT`（已投递）、`POISON`（毒丸，永久保留）。并发控制完全通过 MySQL：Worker 领取候选时以 `ownerToken`（实例持有的领取令牌）+ `version`（乐观锁版本）CAS（比较并交换）更新，同一条记录同时只能被一个实例领取成功；其他实例领取失败后，记录保持 PROCESSING 等租约到期自动恢复。

停机与同步发布入口串行化：未进入发送的已领取任务释放租约；已进入发送的任务保持租约，由确认回写或租约恢复收敛，避免把可能已投递的消息误标记为停机重试。同步准备阶段的确定性错误（消息、序列化、路由参数非法）直接进 POISON；broker、网络、超时等结果未知错误进入 RETRY_WAIT 按退避重试；达到 `retry.max-attempts` 后进 POISON。POISON 默认永久保留，自动清理只删除超过保留期的 SENT 记录。

## 扩展点

可按类型提供单个自定义 Bean 覆盖：`KafkaOutboxEngine`、`KafkaOutboxRepository`、`KafkaOutboxMessageSerializer`、`KafkaOutboxTraceSnapshotResolver`、`KafkaOutboxTraceScope`、`KafkaOutboxRetryPolicy`、`KafkaOutboxJitterGenerator`、`KafkaOutboxEventListener`、`KafkaOutboxWorker`。

提供自定义 `KafkaOutboxEngine` 后，默认 Properties、数据库资源选择、Repository、序列化器、重试策略、监听器、Worker、调度器和清理器整条链路退场；只覆盖其他单个 SPI 时默认链继续工作，仅替换对应实现。默认 Jackson 序列化器使用模块私有静态 `ObjectMapper`，内置 `JavaTimeModule` 并使用 ISO-8601，不读取 Spring 全局 `ObjectMapper`。`KafkaOutboxEventListener` 按领取、投递成功、重试、毒丸、租约丢失五类事件回调，监听器异常只记告警不影响投递。

## 最佳实践

### 容量规划

单实例低吞吐场景用 `concurrency=1`、`batch-size=10` 与默认间隔即可。高吞吐场景按 Kafka broker 写入往返耗时确定 `concurrency`，`concurrency × batch-size` 不宜超出下游 broker 接收能力；Leader 选举或分区卡住时会大量占用槽位，`batch-size` 建议留安全余量。

### 重试与毒丸

不可重试错误（序列化失败、路由参数非法）直接进 POISON，`max-attempts` 不影响该路径。可重试错误按退避策略安排下次投递时间，数据库扫描不会提前执行；`jitter-factor` 取 0.1～0.2 防止多实例同一时刻集中重试。通过 `KafkaOutboxEventListener.onPoison` 监控毒丸消息，生产出现 POISON 应人工排查根因；修复后可将 POISON 状态重置为 PENDING 触发重新投递，操作前确认消费端幂等。

### 多实例部署

Worker 不持有跨进程共享状态，任意数量实例可同时运行，在线扩缩容无需停止其他实例，也不需要外部协调。实例越多 CAS 竞争越激烈，`onLeaseLost`（租约丢失事件）会增加；优先调大单实例 `concurrency` 和 `batch-size` 提升吞吐，而不是水平加实例。需要高可用冗余时保持 2～3 实例合理，再多需结合实际租约丢失监控评估。SENT 记录持续堆积通常意味着清理任务未正常运行，检查 `cleanup.enable` 与调度器状态。

## 常见问题

| 现象 | 检查项 |
|---|---|
| 开启后没有投递 | 确认 `enable=true`、publisher 已启用、容器中存在 `KafkaPublisher`、已按 DDL 建表 |
| 记录停在 PENDING | 检查 `worker.enable` 是否被关闭、`table-name` 与实际表名是否一致 |
| 同一消息被消费两次 | 属预期语义：确认后回写前故障会重复投递，消费端必须按 `messageId` 幂等 |
| 启动报 KAFKA_OUTBOX_001 / 002 | 容器存在多个 DataSource 或事务管理器且未指定 Bean 名，或事务管理器与 DataSource 不是同一实例 |
| `save` 抛未在可写事务中异常 | 业务方法缺少 `@Transactional`，或处于只读事务 |
| 大量租约丢失事件 | 实例数过多或 `lease-ms` 偏短；检查 `send.timeout-ms` 是否逼近租约时长 |
| POISON 持续增长 | 查看记录上的错误码；确定性错误不会因重试自愈，需修正根因后重置 |

## 运行线切换

从 javax 线 `simple-kafka-outbox-starter` 切换时只替换依赖坐标：包名、类名、配置键与表结构完全一致，业务代码与 DDL 可直接平移。本模块自发布起即依赖 `simple-kafka-outbox-core:1.0.0` 承载共享类型，不存在旧线 1.0.0 的重复类名历史问题；同一应用不可同时引入两条运行线。人工排障页面 `simple-kafka-outbox-management-starter` 当前仅在 javax 运行线提供，可对 Jakarta 应用写入的同一张表排障。

## 验证范围

验证基线为 Spring Boot `3.4.2`、Gradle `8.5`。其他 Spring Boot 补丁版本不由下表推定为已验证。

| 验证项 | Java | 用例 | 结果 |
|---|---|---|---|
| 模块完整测试 | 17 | 129 | 无失败、错误或跳过 |
| 模块完整测试 | 21 | 129 | 无失败、错误或跳过 |
| 独立 JAR/POM 消费 | 17 | 2 | 自动装配发现默认链，类型来自 JAR |

测试覆盖租约竞争、至少一次投递与重复回写、事务边界与回滚隔离、重复 `messageId` 拒绝、多路由切换、故障恢复、重试耗尽进 POISON、停机恢复、清理游标，以及宿主自定义 Bean 的让位与整链退场（含宿主配置类声明 `KafkaPublisher` 的装配时序）。MySQL 集成使用本机 8.4.2 真实例，Kafka 使用真实容器 fixture（多套独立 broker 与不可达死集群做故障注入）；fixture 为独立单节点与三节点集群，不代表多节点故障场景的全覆盖。独立制品消费验证不引用模块源码。以上证明组件装配、事务语义与投递状态机，不等同于真实生产 Kafka 集群验收或业务消费端联调。
