# CHANGELOG 2.0.0

## 破坏性变更（相对 1.0.2）

本版本是同坐标破坏性升级，基于身份协作底座重设计：归属维度由「租户+操作者」收敛为 `CrmOwner`（`iam:{subjectId}` 人员 / `aksk:{clientId}` 服务凭证前缀 owner 字符串），全链移除租户概念。

### 移除

- `CrmActor`（tenantId+actorId+displayName）→ `CrmOwner`（owner+主体类型 `CrmSubjectType`，`human`/`service`/`of` 工厂）。
- 四个安全 Port：`CrmActorProvider`、`CrmCommandAuthorizer`、`CrmDataPermissionProvider`、`CrmOperationsAuthorizer`——三权由身份协作底座组件在 HTTP 层完成。
- 双 Outbox Port：`DomainEventOutboxPort`、`FulfillmentCommandOutboxPort` 及 `domain.event` 记录族、`FulfillmentCommandOutboxRecord`——零事件形态（履约消费走 HTTP 拉取回写）下无存在必要。
- 错误码：`UNAUTHENTICATED`、`ACTOR_DISABLED`、`TENANT_MISMATCH`、`ACTION_FORBIDDEN`（401/403 由底座统一给出）、`RETRYABLE_DEPENDENCY_UNAVAILABLE`（无触发入口）。

### 领域状态机扩展

- 报价：`DRAFT` 可编辑（`QuotationDraftService.edit`，整单替换行项、版本号不变、聚合版本递增）；`ISSUED` 可撤回（`withdraw`→`WITHDRAWN` 终态）、可重议（`renegotiate` 自己签发快照复制派生新草稿并记录 `supersedesQuotationId`）；重议派生报价确认时旧报价同结果自动 `SUPERSEDED`（`confirm` 携带取代事实对）；`EXPIRED` 为惰性派生态（`isExpired(version, now)` 按版本有效截止时间判定，不落库、无定时任务）。`QuotationVersion` 终态时间/操作人统一为 `terminalAt`/`terminalBy` 槽位（确认/撤回/取代共用）。
- Offering：两态改三态 `DRAFT→PUBLISHED→DISABLED`（`published()`/`disabled()`/`isPublished()`；下架可重新上架）；报价创建/编辑引用校验=Customer 未停用且 Offering 为 `PUBLISHED`；确认时复检 Customer、Offering 快照不复检。
- 履约：运行状态机收敛为四态 `PENDING_DISPATCH→IN_PROGRESS→COMPLETED/FAILED`；旧 `REVOKED` 枚举仅保留数据解析，不提供撤销、回写或重置动作。`FulfillmentItem` 新增 `owner`（人员查询维度）、`reportSequence`（回写序号水位）、`result`（`FulfillmentResult`）；`report(sequence, target, result)` 序号严格递增防乱序、终态必带结果数据、首次 `in_progress` 即认领（仓储条件写实现原子认领）；`COMPLETED` 终态不可再报不可重置；`reset()` 仅 `FAILED` 可重置回待执行且水位保留。`FulfillmentConsumer` 新增 `enabled` 启停。
- 客户档案治理：`CustomerState` 新增 `MERGED` 终态；`Customer` 新增 `mergedIntoCustomerId`、`deactivated()`/`activated()`/`mergedInto(target)`/`transferredTo(newOwner)`；`Quotation.transferredTo` 仅未确认可转（已确认历史归属不追改）。

### 契约扩展

- 命令：`EditQuotationCommand`、`WithdrawQuotationCommand`、`RenegotiateQuotationCommand`、`DeactivateCustomerCommand`、`ActivateCustomerCommand`、`PublishOfferingCommand`、`DisableOfferingCommand`、`MergeCustomerCommand`、`TransferCustomerCommand`、`TransferQuotationCommand`、`ReportFulfillmentResultCommand`、`ResetFulfillmentItemCommand`；`CrmCommandType` 对应新增 12 个稳定 code。
- 门面：`CrmCommandFacade` 新增 12 个命令方法（合并返回 `CustomerMergeResult`、客户转移返回 `CustomerTransferResult`）；`CrmQueryFacade` 新增履约点查、六资源列表族与审计查询，列表分别声明机器游标 `list*` 与真实页码 `page*` 方法。
- 模型：`CrmPage` 支持游标结果 `items/nextCursor` 与页码结果 `items/page/pageSize/total`；`CrmPageRequest` 校验从 1 开始的页码及 1～100 的条数，以长整数计算偏移量；`hasNext()` 按所选模式判断续页。页码总数与当页行使用相同过滤、授权范围和读取快照。`CrmListCriteria` 提供状态/关联资源/时间窗/关键字结构化过滤，`CrmAuditFact` 为审计读取模型。
- 幂等：创建幂等与已知目标幂等稳定范围由（tenant, actor, …）改为（owner, …）；`ReplayableFulfillmentItemRepository.findByOrderId(orderId)` 去租户参数。创建重放复用首次顶级资源 ID 并按当前权限回读；已知目标命令在当前权限校验后恢复首次成功声明结果，后续迁移不覆盖首次响应，重放不修改业务事实或订单派生态。

### 迁移要点

1. 适配器将 `CrmActor` 构造替换为 `CrmOwner.human(subjectId)`/`CrmOwner.service(clientId)`；持久化层 owner 列存 owner 字符串。
2. 安全与数据范围裁决移交身份协作底座组件（`@RequireApiPermission`+`@CurrentDataAccessPlan`）；本模块命令门面首参改为 `CrmOwner`。
3. `OfferingState`/`FulfillmentState` 稳定 code 变化，存量数据需按新 code 迁移（offering: active→published/draft 语义重判；fulfillment: 七态映射四态）。
4. 事件类 Outbox 适配器整体移除；履约消费方改为持码拉取+回写。
