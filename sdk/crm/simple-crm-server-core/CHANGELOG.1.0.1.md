# simple-crm-server-core 1.0.1

## 发布信息

- 类型：Bug Fix
- Java 字节码：Java 8

## 变更内容

修复服务端生成顶级资源 ID 的创建命令无法使用既有目标资源幂等定位的问题。

新增 `CrmCreateIdempotencyPort`，用于创建 Customer、Contact、Offering、Quotation。运行时适配器以 tenant、actor、命令类型、目标资源类型和幂等键定位首次创建；相同请求重放首次顶级资源 ID，不重复生成或写入；相同范围使用不同请求摘要时复用已有错误码拒绝。

既有 `CrmIdempotencyPort` 的类型与方法签名保持不变，继续用于执行前已知目标 ID 的报价签发和确认。

## 新增测试

- `CrmCreateIdempotencyPortContractTest`
  - 覆盖首次创建与精确重放、摘要冲突、范围隔离、非法命令/资源配对、回调失败与空资源 ID、并发首次请求收敛，以及既有 Port 实现兼容性。

## 向后兼容性

- 不修改任何 1.0.0 已发布 public API、错误码、命令类型、资源类型或 ID 类型。
- 仅新增 `CrmCreateIdempotencyPort` 公开接口。

## 升级指南

从 1.0.0 升级后，创建 Customer、Contact、Offering、Quotation 的运行时适配器应改用 `CrmCreateIdempotencyPort`；签发和确认报价继续使用 `CrmIdempotencyPort`。
