# CHANGELOG - simple-iam-client-core 1.0.3

## 发布信息

- 版本：`1.0.3`
- 类型：Feature / 契约对齐（server 1.3.7 C/D 件）
- 基线版本：`1.0.1`

## 发布信息补充

- `1.0.2` 为废号（误发 Central 作废跳号，未形成有效列车，见台账）；基线 `1.0.1`。

## 变更明细

### C 件：页面准入详情（feedback 消费）

- `IamUserClient` 第 13 方法 `listPageAdmittedApplicationDetails(String subjectId)`：页面准入应用详情（code/displayName/description，displayName 与门户侧边栏同源）；同码 `iam:portal:api`、无 DATA 面。模型 `PageAdmittedApplication`。

### D 件：可信应用目录（limiter 消费）

- 新增 `IamApplicationClient`（1 方法 `listApplications(page, size)`）：可信应用目录分页（applicationCode/applicationName/status，createdAt 倒序，Spring Page wire 页码 0 起）；`iam:application:api` 码 + `iam:application` DATA read all（全局目录无部门归属）。模型 `IamApplication`。

### E 件：openapi 六族补全（全覆盖）

| 新接口 | 方法 | 码 |
| --- | --- | --- |
| `IamMessageClient` | 3（发信/按人查信/批次详情） | `iam:message:api` |
| `IamSessionClient` | 2（按人查会话/踢会话） | `iam:session:api` |
| `IamPermissionClient` | 1（权限清单只读目录） | `iam:permission:api` |
| `IamUserGroupClient` | 8（CRUD+成员分页/加/移） | `iam:user-group:api` |
| `IamRoleCatalogClient` | 1（角色目录只读，role-catalog 路径区隔受委托角色族） | `iam:role:api` |
| `IamStatisticsClient` | 1（统计聚合只读） | `iam:dashboard:api` |

- 十个新模型（IamMessage/IamMessageSendRequest/IamMessageSendResult/IamMessageBatch/IamSession/IamPermission/IamUserGroup/IamUserGroupMember/IamRoleCatalogEntry/IamStatistics）；发信请求收件人形态与管理台同构（subjectId 数组/部门/协作组并集）。
- 全 openapi 方法数 **48**（users 13 / departments 5 / open-roles 13 / applications 1 / messages 3 / sessions 2 / permissions 1 / user-groups 8 / role-catalog 1 / statistics 1）——内置码族与端点族全覆盖，外部消费方无需再等业务侧提需求。

## 升级指引

- 对应 server ≥ 1.3.7；旧 server 上新能力 404（其余方法不受影响）。消费方（feedback/limiter adaptor 等）引 1.0.3 + 对应传输件 1.0.3。
