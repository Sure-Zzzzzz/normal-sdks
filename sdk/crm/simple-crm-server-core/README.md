# simple-crm-server-core

`simple-crm-server-core` 提供可独立发布的 CRM 商业领域内核：业务事实、类型化命令、纯领域服务、错误码和外部适配 Port。它帮助业务应用在自身的认证、存储、审计和履约实现之上，建立可追溯的商业事实。

## 适用场景

当应用需要在自身的存储、身份、审计、消息或履约适配器之上实现以下商业闭环时，可以依赖本模块：

```text
客户 → 商品/服务 → 报价 → 订单 → 履约义务
```

本模块不提供 Spring Boot 自动配置、REST API、数据库表、认证实现、HTTP 履约投递、Kafka、Redis 或 Elasticsearch。

## 依赖

```groovy
implementation "io.github.sure-zzzzzz:simple-crm-server-core:1.0.1"
```

下游模块必须使用已发布的 Maven 版本，不能依赖未发布的 Core 源码工程。

## 最小接入步骤

1. 在入口适配器完成认证后，构造已绑定租户的 `CrmActor`；不要从请求体接收 tenant、actor、履约消费者或数据权限。
2. 为每次写命令生成唯一的 `CrmCommandMetadata`。创建 Customer、Contact、Offering、Quotation 时，运行时适配器使用 `CrmCreateIdempotencyPort` 以 tenant、actor、命令类型、资源类型和幂等键定位：首次回调生成并持久化顶级资源 ID，相同请求重放该 ID，不能重复生成或写入。
3. 对执行前已经确定目标 ID 的签发、确认报价，使用 `CrmIdempotencyPort`；不要将未生成或临时生成的创建资源 ID 传给该 Port。
4. 由存储适配器按租户、授权和数据范围加载 Customer、Offering、Quotation 等领域事实；实现 `CrmQueryFacade` 时，对不可见资源使用安全 not-found 语义。创建首次成功和重放后均按返回 ID 回读；报价还必须回读初始 `QuotationVersion` 以重建 `QuotationDraft`。
5. 注册业务所需的 `CommercialCapability`。使用 `QuotationDraftService` 基于已加载的 Customer 与 Offering 快照构造报价草稿；使用 `QuotationLifecycleService` 签发或确认报价。
6. 确认报价后，将 `QuotationConfirmation` 中的报价、版本、订单和履约项作为一个完整事实集合，在同一权威事务中连同审计与两个 Outbox 事实持久化；通过 `port` 接口对接实际的安全、存储、时钟、标识生成和投递实现。

## 使用入口

- 命令与查询契约位于 `io.github.surezzzzzz.sdk.crm.server.core.api`。
- 命令输入与命令元数据位于 `io.github.surezzzzzz.sdk.crm.server.core.command`。
- 业务事实、状态机与商业能力位于 `io.github.surezzzzzz.sdk.crm.server.core.domain`。
- 外部实现契约按安全、仓储、系统、审计、幂等与 Outbox 收敛在 `io.github.surezzzzzz.sdk.crm.server.core.port`。

## 使用边界

- 入口适配器负责认证，构造 tenant-bound `CrmActor`；调用方不得从请求体指定 tenant、actor、消费者或数据权限。
- 命令必须携带独立的 `CrmCommandMetadata`，其中包含关联 ID 与幂等键。
- `CrmCommandFacade` 与 `CrmQueryFacade` 是运行时实现契约，不是本模块自带的 HTTP、认证或存储实现。
- 存储适配器负责在同一权威事务内完成授权、数据范围、幂等、CAS、审计、业务事实以及领域事件和履约命令 Outbox 的持久化。
- `QuotationDraftService` 只基于已按租户和数据范围加载的 Customer、Offering 快照构造草稿；Customer、Offering 与报价 Owner 一律由已认证 `CrmActor` 派生或校验，不能由请求体伪造。
- `QuotationLifecycleService` 只执行纯领域状态校验及订单/履约快照构造，不访问存储或外部系统；确认时通过 `FulfillmentConsumerSelector` 选择并冻结消费者与协议版本。
- 1.0.0 仅支持报价版本 `DRAFT → ISSUED → CONFIRMED`；`WITHDRAWN`、`EXPIRED` 与 `SUPERSEDED` 是预留状态，不应由 1.0.0 适配器写入。
- 枚举跨边界传输、存储或展示时使用 `getCode()` 返回的稳定代码，并通过 `fromCode()` 读取；不要把 `name()` 作为持久化或协议契约。
- `FIXED_PRICE_FULFILLMENT_V1` 只计算冻结商业结果和履约义务模板，不选择消费者、不发送网络请求。

## 版本选型

| 模块版本 | Java 字节码 | 说明 |
| --- | --- | --- |
| 1.0.1 | Java 8 | 补齐服务端生成资源的创建幂等契约 |
| 1.0.0 | Java 8 | 首发纯 Java CRM 商业领域契约 |

## 升级指南

从 1.0.0 升级到 1.0.1 时，既有 `CrmIdempotencyPort` 的类型与方法签名保持不变。创建 Customer、Contact、Offering、Quotation 的运行时适配器改用新增 `CrmCreateIdempotencyPort`，并在同一权威事务中保存首次顶级资源 ID 与幂等成功结果；相同稳定范围使用不同请求摘要时必须使用已有错误码拒绝。后续版本会保持已发布类型和枚举语义的兼容性；涉及状态机、错误码或公开模型语义变更时，将以新的兼容策略发布。
