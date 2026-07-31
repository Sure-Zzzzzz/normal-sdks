# simple-crm-server-core 1.0.2

## 发布信息

- 类型：Bug Fix
- Java 字节码：Java 8

## 变更内容

补齐执行前已知目标资源命令的幂等成功结果重放契约。

新增 `CrmReplayableIdempotencyPort`，仅允许 `ISSUE_QUOTATION/QUOTATION` 和 `CONFIRM_QUOTATION/QUOTATION`。报价签发和确认的运行时适配器在首次成功时于同一权威事务提交业务事实与幂等成功事实；相同请求重放时只执行重放回调：签发按已提交报价和版本重建，确认按已提交报价、版本、订单以及 `ReplayableFulfillmentItemRepository` 按订单回读的完整履约事实重建，不重复执行状态迁移、订单创建、审计或 Outbox 写入。

既有 `CrmIdempotencyPort` 与 `FulfillmentItemRepository` 的已发布类型和方法签名保持不变，因而已有实现仍具备源码和二进制 API 兼容性；但这不代表报价签发/确认的 Starter/runtime adapter 无需迁移。需要返回可重放签发或确认结果的 adapter 必须实现 `CrmReplayableIdempotencyPort`，确认 replay 还必须实现 `ReplayableFulfillmentItemRepository`。

## 新增测试

- `CrmReplayableIdempotencyPortContractTest`
  - 覆盖签发与确认的首次成功和精确重放。
  - 覆盖重放不执行首次业务回调。
  - 覆盖摘要冲突、非法命令/资源配对、稳定范围隔离、首次失败后重试、重放失败后成功记录保留、并发首次请求收敛，以及确认重放所需履约项的按租户订单回读。

## 向后兼容性

- 不修改任何已发布的 `CrmIdempotencyPort` 方法、错误码、命令类型、资源类型或领域模型。
- 仅新增 `CrmReplayableIdempotencyPort` 与 `ReplayableFulfillmentItemRepository` 公开子接口。

## 升级指南

从 1.0.1 升级后，创建 Customer、Contact、Offering、Quotation 继续使用 `CrmCreateIdempotencyPort`。报价签发和确认的 runtime adapter 改为实现 `CrmReplayableIdempotencyPort`，并提供仅从已提交权威事实重建成功结果的 replay callback；确认 replay 同时实现 `ReplayableFulfillmentItemRepository`，按当前 tenant 和订单读取完整履约项集合。该版本不新增请求摘要策略，摘要规则继续由 adapter 负责并在重试间保持一致。
