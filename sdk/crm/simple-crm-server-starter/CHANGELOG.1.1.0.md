# CHANGELOG 1.1.0

## 发布信息

- 类型：Feature
- 运行基线：Spring Boot 2.7.9 / Java 8
- 领域内核：`simple-crm-server-core:2.1.0`
- HTTP 契约：OpenAPI v2.1.0

## 变更内容

- 修复商机预计金额读写不对称：数据库 `DECIMAL(20,6)` 读回的金额小数位超出币种默认位数时，读路径重建商机被写校验拒绝（携带 `estimatedAmount` 创建商机必 400）；实体读回按币种归一金额小数位，写校验语义不变。
- 新增商机 REST 资源：创建、点查、列表、阶段推进/输单和归属转移。
- 新增五个 API 权限码：`crm.opportunity.create`、`crm.opportunity.read`、`crm.opportunity.list`、`crm.opportunity.stage`、`crm.opportunity.transfer`。
- 报价创建、查询响应和重议链增加可选 `opportunityId`；同一商机可关联多个报价，报价确认成交时商机同事务进入 `won`。
- 新增 `crm_opportunity` 表及报价关联列，商机查询沿用客户 owner DATA 范围和页码/游标双轨分页。
- 商机写路径复用既有幂等、审计、MySQL Route 和限流装配边界；人工阶段 PATCH 不允许推进 `won`，`lost` 必须带输单原因。
- 商机归属转移按客户根归属同事务同步客户、未完结报价和未终态商机；报价归属转移递增聚合版本并保留并发 CAS 语义。
- 报价归属转移只接受未确认且未过期报价；关联未终态商机同事务同步 owner，过期报价不会被客户/商机治理路径再次转移。
- 报价转移后的新 owner 可继续编辑、签发、确认或重议（客户仍须 active，关联商机仍须未终态）；已终态商机不接受报价转移。
- 同一商机仅允许一份关联报价成交；商机已 `WON` 时第二份报价确认会被拒绝。
- 合并客户目标不得已是 `MERGED`；已合并客户不可再次停用或复启；owner 解析拒绝空主体后缀。
- 报价列表状态过滤覆盖 `draft/issued/confirmed/withdrawn/superseded/expired`，非法状态和非法请求体统一返回 400。
- 增加 `docs/migration/1.0.0-to-1.1.0.sql`，为已有 1.0.0 数据库提供不删除数据的商机结构升级路径。

## 新增测试

- 商机 HTTP 创建、查询、分页、阶段约束和归属转移。
- 报价关联商机、重议沿用商机、确认成交派生 `won`、幂等重放和 DATA 范围。

## 向后兼容性

- 既有 CRM 端点和响应字段保持兼容；直接报价仍可省略 `opportunityId`。
- 既有数据库需按 `docs/schema.sql` 初始化，已有部署按数据库变更流程增加商机表和报价关联列。

## 升级指南

升级时将 server starter 与 core 同步切换到上述版本，登记新增五个 API 码，并按 [docs/README.md](docs/README.md) 执行结构变更。宿主继续显式提供 DATA MVC 运行义务件，Route、限流和 AKSK 快照客户端的依赖边界不变。
