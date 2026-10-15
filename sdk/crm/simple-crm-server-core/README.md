# simple-crm-server-core

`simple-crm-server-core` 提供可独立发布的 CRM 商业领域内核：业务事实、类型化命令、纯领域服务、错误码和外部适配 Port。它帮助业务应用在自身的认证、存储、审计和履约实现之上，建立可追溯的商业事实。

## 适用场景

当应用需要在自身的存储、身份、审计或履约适配器之上实现以下商业闭环时，可以依赖本模块：

```text
客户 → 商品/服务 → 报价（草稿/编辑/签发/确认/撤回/重议） → 订单 → 履约闭环
```

本模块不提供 Spring Boot 自动配置、REST API、数据库表、认证实现、HTTP 履约投递、Kafka、Redis 或 Elasticsearch。

## 依赖

```groovy
implementation "io.github.sure-zzzzzz:simple-crm-server-core:2.0.0"
```

下游模块必须使用已发布的 Maven 版本，不能依赖未发布的 Core 源码工程。

## 身份协作底座对接（2.0.0 起）

2.0.0 起归属维度为 `CrmOwner`（owner 字符串 + 主体类型）：人=`iam:{subjectId}`（`CrmOwner.human(...)`）、服务凭证=`aksk:{clientId}`（`CrmOwner.service(...)`）、持久化值经 `CrmOwner.of(...)` 解析。调用主体的 owner 只能由入口适配器从已验证请求上下文派生，不能由请求体指定；归属转移命令另行接收目标 owner，由入口检查操作权限和数据范围。全链无租户概念。

## 最小接入步骤

1. 在入口适配器完成认证后，从已验证上下文构造 `CrmOwner`；不要从请求体接收 owner、履约消费者或数据权限。
2. 为每次写命令生成唯一的 `CrmCommandMetadata`。创建 Customer、Contact、Offering、Quotation 时，运行时适配器使用 `CrmCreateIdempotencyPort` 以 owner、命令类型、资源类型和幂等键定位：首次回调生成并持久化顶级资源 ID，相同请求重放该 ID，不能重复生成或写入。
3. 对执行前已经确定目标报价 ID 的 `ISSUE_QUOTATION/QUOTATION`、`CONFIRM_QUOTATION/QUOTATION`，实现并使用 `CrmReplayableIdempotencyPort`；其他命令或资源类型配对必须拒绝。签发按已提交的 Quotation 与 QuotationVersion 重建重放结果；确认按已提交的 Quotation、QuotationVersion、Order 与 `ReplayableFulfillmentItemRepository.findByOrderId(orderId)` 回读的 FulfillmentItem 完整集合重建重放结果。
4. 由存储适配器按 owner 授权和数据范围加载 Customer、Offering、Quotation 等领域事实；实现 `CrmQueryFacade` 时，对不可见资源使用安全 not-found（不存在或不可访问）语义。机器续读使用固定排序游标，管理台使用真实页码；页码总数与当页行必须应用相同过滤和授权范围，并在同一读取快照内查询。创建首次成功和重放后均按返回 ID 回读；报价还必须回读初始 `QuotationVersion` 以重建 `QuotationDraft`。
5. 注册业务所需的 `CommercialCapability`。使用 `QuotationDraftService` 构造或编辑报价草稿（编辑仅限当前草稿版本，整单替换行项，版本号不变聚合版本递增）；使用 `QuotationLifecycleService` 签发、撤回、确认报价或自已签发快照重议派生新草稿。签发、撤回、确认报价时聚合 CAS 递增；确认生成订单与 PENDING_DISPATCH 履约项并同结果冻结派发消费者。
6. 确认报价后，将 `QuotationConfirmation` 中的报价、版本、订单和履约项作为一个完整事实集合，在同一权威事务中连同审计持久化；重议派生的报价确认时结果集合同时携带被 SUPERSEDED 的旧报价与版本事实，必须一并落库。通过 `port` 接口对接实际的存储、时钟、标识生成和投递实现。

## 使用入口

- 命令与查询契约位于 `io.github.surezzzzzz.sdk.crm.server.core.api`。
- 命令输入与命令元数据位于 `io.github.surezzzzzz.sdk.crm.server.core.command`。
- 业务事实、状态机与商业能力位于 `io.github.surezzzzzz.sdk.crm.server.core.domain`。
- 提交后业务审计事件契约位于 `io.github.surezzzzzz.sdk.crm.server.core.domain.event.CrmAuditEvent`；事件由 server 装配件发布，审计 listener 只依赖本模块契约。
- 外部实现契约按仓储、系统、审计与幂等收敛在 `io.github.surezzzzzz.sdk.crm.server.core.port`。

## 列表查询

`CrmQueryFacade` 为客户、联系人、目录、报价、订单、履约项和审计分别声明两种查询方法：`list*` 使用游标续读，`page*` 使用 `CrmPageRequest` 的真实页码。游标是不透明的续读位置，调用方只能原样传回，不能将其解析为页码；过滤条件、归属主体或授权范围变化后须重新从首页查询。

```java
CrmPage<Customer> customers = queries.pageCustomers(owner,
        new CrmListCriteria(null, null, null, null), new CrmPageRequest(1, 20));
long total = customers.getTotal();
boolean hasNext = customers.hasNext();
```

页码从 1 开始，每页条数为 1～100；默认值常量为页码 1、条数 20，由入口适配器补齐。`CrmPage` 页码结果携带 `items/page/pageSize/total`，`nextCursor` 为空；游标结果携带 `items/nextCursor`，三个页码字段为空。超出末页返回空列表，保留请求页码和真实总数。`hasNext()` 在页码模式按当前偏移量、当页条数和总数判断，在游标模式按续读游标判断。

## 使用边界

- 入口适配器负责认证，构造 `CrmOwner`；调用方不得从请求体指定 owner、消费者或数据权限。
- 命令必须携带独立的 `CrmCommandMetadata`，其中包含关联 ID 与幂等键。
- `CrmCommandFacade` 与 `CrmQueryFacade` 是运行时实现契约，不是本模块自带的 HTTP、认证或存储实现。
- 存储适配器负责在同一权威事务内完成授权、数据范围、幂等、CAS、审计与业务事实的持久化。
- 首次处理中，业务事实、审计与幂等成功事实必须原子提交；任一步失败不得留下可重放成功。相同摘要的 replay 只能重建声明结果；replay 失败不得改写既有成功事实，并发竞争方必须进入 replay，不能执行首次回调。
- 已知目标命令的 replay 先按当前 `CrmOwner` 完成授权和数据范围校验，再恢复首次成功声明结果；后续迁移不得覆盖首响应。适配器可持久化结果快照；若使用回调重建确认结果，须包含首个成功的 Quotation、QuotationVersion、Order，以及 `ReplayableFulfillmentItemRepository.findByOrderId(orderId)` 对应的完整履约项集合，不得写入业务或审计事实。
- 请求摘要由 runtime adapter 规范化并稳定生成；Core 不提供跨 Starter 共用的摘要策略，重试请求必须使用同一 adapter 规则。
- `QuotationDraftService` 只基于已按数据范围加载的 Customer、Offering 快照构造草稿；报价 owner 一律由已验证 `CrmOwner` 派生，不能由请求体伪造。创建与编辑草稿引用校验=Customer 未停用且 Offering 为 PUBLISHED（三态 `DRAFT→PUBLISHED→DISABLED`，下架可重新上架）；确认时复检 Customer 未停用，Offering 状态不复检（行项快照已冻结，下架只拦新报价）。
- 同一商品可在报价中出现多个业务行，例如为不同授权对象或履约范围分别报价；每行独立保留数量、价格、授权对象和履约范围，不能按商品合并或删除重复行。存储适配器对重复商品引用只加载一次目录事实，领域服务再按原始业务行逐行计算与冻结快照。
- `QuotationLifecycleService` 只执行纯领域状态校验及订单/履约快照构造，不访问存储或外部系统；确认时通过 `FulfillmentConsumerSelector` 选择并冻结消费者与协议版本。
- 报价状态机：聚合 `DRAFT→ISSUED→{CONFIRMED|WITHDRAWN|SUPERSEDED}`；`EXPIRED` 为惰性派生态（`isExpired(version, now)` 按 `QuotationVersion.validUntil` 判定，不落库）；编辑草稿不改版本号；签发、撤回、确认、被取代均同事务迁移聚合与版本事实（版本终态时间与操作人在 `terminalAt/terminalBy` 槽位）。
- 履约项状态机 `PENDING_DISPATCH→IN_PROGRESS→COMPLETED/FAILED`：首次回写 `in_progress` 即认领（仓储以状态+版本条件写实现原子认领）；回写序号必须严格大于当前水位（防乱序与重复）；终态回写必带结果数据；`COMPLETED` 终态不可再报不可重置（误报走线下修正，防重复履约）；`FAILED` 可人工重置回 `PENDING_DISPATCH` 且水位保留。履约项携带 owner（人员查询维度）与冻结的 consumerId（消费者回写授权维度=派发归属）。
- 客户档案治理：停用/复启（不进新报价/新联系人，历史不受影响）；合并=源客户置 `MERGED` 终态（不物理删除，记录指向目标）+联系人/未完结报价/已确认事实由调用方同事务改挂（改挂关联 ID，冻结快照不重写）；归属转移=客户及未完结报价 owner 同事务改指，已确认历史事实不追改；报价转移仅限未确认。
- `CrmPage`/`CrmPageRequest`/`CrmListCriteria` 是列表契约模型：机器游标绑定排序位置与查询范围，管理台页码返回真实总数。DATA（行级数据授权）计划由实现并入查询条件，总数不得包含调用方不可见的数据。
- 枚举跨边界传输、存储或展示时使用 `getCode()` 返回的稳定代码，并通过 `fromCode()` 读取；不要把 `name()` 作为持久化或协议契约。
- `FIXED_PRICE_FULFILLMENT_V1` 只计算冻结商业结果和履约义务模板，不选择消费者、不发送网络请求。

## 版本选型

| 模块版本 | Java 字节码 | 说明 |
| --- | --- | --- |
| 2.0.0 | Java 8 | 破坏性重设计：owner 维度替代租户、报价全生命周期（编辑/撤回/重议/取代/惰性过期）、Offering 三态、履约四态回写状态机、档案治理（停用/合并/转移）、机器游标与管理台页码契约；剥离安全 Port 与双 Outbox Port |
| 1.0.2 | Java 8 | 补齐已知目标命令的幂等结果重放契约 |
| 1.0.1 | Java 8 | 补齐服务端生成资源的创建幂等契约 |
| 1.0.0 | Java 8 | 首发纯 Java CRM 商业领域契约 |

## 升级指南（1.0.x → 2.0.0）

2.0.0 是破坏性版本，详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)。核心迁移点：

1. `CrmActor`（tenantId+actorId）→ `CrmOwner`（`iam:{subjectId}`/`aksk:{clientId}` 前缀 owner 字符串+主体类型）；全链移除 tenant 概念。
2. 四个安全 Port（`CrmActorProvider`/`CrmCommandAuthorizer`/`CrmDataPermissionProvider`/`CrmOperationsAuthorizer`）与双 Outbox Port（`DomainEventOutboxPort`/`FulfillmentCommandOutboxPort`）移除——三权由身份协作底座在 HTTP 层完成；履约不再使用跨服务 Outbox 或消息事件，server 仍可将业务审计事实投影为提交后的进程内 `CrmAuditEvent`。
3. `OfferingState` 由两态改三态（`DRAFT→PUBLISHED→DISABLED`）；`FulfillmentState` 由七态改四态（`pending_dispatch/in_progress/completed/failed` 稳定 code 变化）。
4. `QuotationVersion` 确认时间/操作人槽位更名 `terminalAt`/`terminalBy`（三终态共用）；`Quotation` 新增 `supersedesQuotationId`（重议链）。
5. `FulfillmentItem` 新增 `owner`/`reportSequence`/`result` 字段与 `report()`/`reset()` 迁移方法；`FulfillmentConsumer` 新增 `enabled` 启停。
6. `Customer` 新增 `mergedIntoCustomerId` 与停用/复启/合并/转移迁移方法。
7. 命令门面与查询门面扩展：12 个新命令（编辑/撤回/重议/停用/复启/上下架/合并/双转移/履约回写/重置）；查询门面新增履约点查、六资源列表族与审计查询，并分别声明游标 `list*` 与页码 `page*` 方法（`CrmPage`/`CrmPageRequest`/`CrmListCriteria`/`CrmAuditFact`）。
8. 错误码修剪：`UNAUTHENTICATED`/`ACTOR_DISABLED`/`TENANT_MISMATCH`/`ACTION_FORBIDDEN`/`RETRYABLE_DEPENDENCY_UNAVAILABLE` 移除（401/403 由底座统一给出）。
