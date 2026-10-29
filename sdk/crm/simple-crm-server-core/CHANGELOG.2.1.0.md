# CHANGELOG 2.1.0

## 发布信息

- 类型：Feature
- Java 字节码：Java 8

## 变更内容

新增独立的 `Opportunity` 商机聚合，形成客户建档后的销售管线：

- 阶段固定为 `NEW`、`QUALIFIED`、`PROPOSAL`、`NEGOTIATION`、`WON`、`LOST`；`LOST` 必须保存输单原因。
- `WON` 不能由人工阶段命令推进，只能由关联报价确认成交在同一事务内派生。
- 新增商机创建、阶段推进和归属转移命令及 `OpportunityRepository`；商机列表复用既有游标与页码查询模型。
- `CreateQuotationCommand` 与 `Quotation` 增加可选 `opportunityId`；同一商机可关联多个报价，重议报价沿用来源商机。
- 新增 `OPPORTUNITY` 资源类型、命令类型和审计动作类型，沿用 `CrmOwner` 与客户 DATA 范围，不引入租户或通用属性 JSON。
- 补强终态与身份校验：`MERGED` 客户不可停用/复启，owner 的 IAM/AKSK 主体后缀不能为空，商机归属转移继续校验终态。

## 新增测试

- 商机状态机覆盖阶段推进、输单原因校验、人工推进 WON 拒绝、终态约束和归属转移。
- 报价关联商机覆盖可选关联、重议沿用商机和确认成交派生 WON 的领域契约。

## 向后兼容性

- 既有客户、目录、报价、订单、履约和幂等公开类型保持兼容。
- 既有报价构造器保留；未关联商机的报价继续以 `null` 表示。

## 升级指南

存储适配器增加商机表及报价 `opportunityId` 列；运行时适配器注册新增仓储和命令映射。HTTP 端点、权限码和数据库迁移由 `simple-crm-server-starter:1.1.0` 及其文档说明。
