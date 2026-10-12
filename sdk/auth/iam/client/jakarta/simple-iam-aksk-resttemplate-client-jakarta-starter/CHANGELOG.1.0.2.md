# CHANGELOG - simple-iam-aksk-resttemplate-client-jakarta-starter 1.0.2

## 发布信息

- 版本：`1.0.2`
- 类型：Feature / 契约对齐（simple-iam-client-core 1.0.3，server 1.3.7 C/D/E 件）
- 基线版本：`1.0.1`

## 变更明细

### C 件：页面准入详情（feedback 消费）

- `IamUserClient` 第 13 方法实现 `listPageAdmittedApplicationDetails`：对象数组直读（code/displayName/description），与 getUserRoles 同款 JsonNode 解析形态。

### D 件：可信应用目录（limiter 消费）

- `IamApplicationClient` 实现：可信应用目录分页（Spring Page wire 解析，createdAt 倒序，页码 0 起）。

### E 件：openapi 六族补全

- `IamMessageClient`（发信 body 直配/按人查信/批次详情）、`IamSessionClient`（按人查会话/踢会话）、`IamPermissionClient`（权限清单目录）、`IamUserGroupClient`（CRUD+成员管理 8 方法）、`IamRoleCatalogClient`（角色目录）、`IamStatisticsClient`（统计聚合）各实现落地。
- 依赖：`simple-iam-client-core:1.0.3`（十接口 48 方法契约源）。

### 契约对位修复（端到端全方法验证中发现）

- users `updateUser`、departments `updateDepartment` 动词 PATCH→PUT；users `assignRole` PUT→POST（对位 server openapi 契约，四线同修）；openrole 挂载请求体改空体（契约要求）；删除 RT 支撑层废料 patch 方法。
- openrole 写操作 If-Match 语义钉死：必填、带引号强 ETag（`"open-role:<id>:<revision>"`，版本源=角色行 revision）；`dataGrantTemplate` null=不携带（空对象服务端拒收）——契约 javadoc 同步修正。

## 测试

- 模块单测通过（含六族+applications wire 契约断言）；四线端到端 48 方法全覆盖全绿（SB 2.7 与 SB 3.x，断言链与复跑方式见 `../E2E-TEST-MATRIX.1.0.2.md`）。

## 升级指引

- 对应 server ≥ 1.3.7（六族端点+目录端点）；旧 server 上新能力 404（其余方法不受影响）。AKP 按各端点最小码授权。
