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
implementation "io.github.sure-zzzzzz:simple-crm-server-core:1.0.2"
```

下游模块必须使用已发布的 Maven 版本，不能依赖未发布的 Core 源码工程。

## 最小接入步骤

1. 在入口适配器完成认证后，构造已绑定租户的 `CrmActor`；不要从请求体接收 tenant、actor、履约消费者或数据权限。
2. 为每次写命令生成唯一的 `CrmCommandMetadata`。创建 Customer、Contact、Offering、Quotation 时，运行时适配器使用 `CrmCreateIdempotencyPort` 以 tenant、actor、命令类型、资源类型和幂等键定位：首次回调生成并持久化顶级资源 ID，相同请求重放该 ID，不能重复生成或写入。
3. 对执行前已经确定目标报价 ID 的 `ISSUE_QUOTATION/QUOTATION`、`CONFIRM_QUOTATION/QUOTATION`，实现并使用 `CrmReplayableIdempotencyPort`；其他命令或资源类型配对必须拒绝。
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
- 对 `ISSUE_QUOTATION/QUOTATION`、`CONFIRM_QUOTATION/QUOTATION` 的首次处理，业务事实、审计、两个 Outbox 与幂等成功事实必须原子提交；任一步失败不得留下可重放成功。相同摘要的 replay 只能重建声明结果；replay 失败不得改写既有成功事实，并发竞争方必须进入 replay，不能执行首次回调。
- 确认报价的 replay 先按当前 `CrmActor` 完成授权和数据范围校验，再读取已提交的 Quotation、QuotationVersion、Order，以及 `ReplayableFulfillmentItemRepository.findByOrderId(tenantId, orderId)` 返回的完整履约项集合；结果必须属于当前租户和订单，随后重建新的 `QuotationConfirmation`，不得写入业务、审计或 Outbox 事实。
- 请求摘要由 runtime adapter 规范化并稳定生成；Core 1.0.2 不提供跨 Starter 共用的摘要策略，重试请求必须使用同一 adapter 规则。
- `QuotationDraftService` 只基于已按租户和数据范围加载的 Customer、Offering 快照构造草稿；Customer、Offering 与报价 Owner 一律由已认证 `CrmActor` 派生或校验，不能由请求体伪造。
- `QuotationLifecycleService` 只执行纯领域状态校验及订单/履约快照构造，不访问存储或外部系统；确认时通过 `FulfillmentConsumerSelector` 选择并冻结消费者与协议版本。
- 1.0.x 的领域状态机仅支持报价版本 `DRAFT → ISSUED → CONFIRMED`；`WITHDRAWN`、`EXPIRED` 与 `SUPERSEDED` 是预留状态，不应由 1.0.x 适配器写入。
- 枚举跨边界传输、存储或展示时使用 `getCode()` 返回的稳定代码，并通过 `fromCode()` 读取；不要把 `name()` 作为持久化或协议契约。
- `FIXED_PRICE_FULFILLMENT_V1` 只计算冻结商业结果和履约义务模板，不选择消费者、不发送网络请求。

## 版本选型

| 模块版本 | Java 字节码 | 说明 |
| --- | --- | --- |
| 1.0.2 | Java 8 | 补齐已知目标命令的幂等结果重放契约 |
| 1.0.1 | Java 8 | 补齐服务端生成资源的创建幂等契约 |
| 1.0.0 | Java 8 | 首发纯 Java CRM 商业领域契约 |

## 升级指南

从 1.0.1 升级到 1.0.2 时，既有 `CrmIdempotencyPort` 与 `FulfillmentItemRepository` 的类型和方法签名保持不变；但仅 API 兼容不代表报价签发/确认适配器无需迁移。`ISSUE_QUOTATION/QUOTATION`、`CONFIRM_QUOTATION/QUOTATION` 的 runtime adapter 必须实现 `CrmReplayableIdempotencyPort`，在同一权威事务内保存首次业务事实、审计、两个 Outbox 与幂等成功事实，并在 replay callback 中按当前 tenant 与数据范围重建声明结果；确认 replay 还必须实现 `ReplayableFulfillmentItemRepository`，按订单读取完整履约项集合。创建 Customer、Contact、Offering、Quotation 继续使用 `CrmCreateIdempotencyPort`。相同稳定范围使用不同请求摘要时必须使用已有错误码拒绝；摘要规则仍由 adapter 负责并在重试间保持一致。
