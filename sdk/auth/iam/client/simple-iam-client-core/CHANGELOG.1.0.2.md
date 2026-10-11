# CHANGELOG - simple-iam-client-core 1.0.2

## 发布信息

- 版本：`1.0.2`
- 类型：Feature / 契约对齐（server 1.3.7 C/D 件）
- 基线版本：`1.0.1`

## 变更明细

- `IamUserClient` 第 13 方法 `listPageAdmittedApplicationDetails(String subjectId)`：页面准入应用详情（code/displayName/description，与门户侧边栏同源）；同码 `iam:portal:api`、无 DATA 面。模型 `PageAdmittedApplication`。
- 新增 `IamApplicationClient`（1 方法 `listApplications(page, size)`）：可信应用目录分页（applicationCode/applicationName/status，Spring Page wire 页码 0 起）；`iam:application:api` 码 + `iam:application` DATA read all。模型 `IamApplication`。
- 全 openapi 方法数 31+1（users 13 / departments 5 / open-roles 13 / applications 1）。

## 升级指引

- 对应 server ≥ 1.3.7；旧 server 上两个新能力 404（其余方法不受影响）。消费方（feedback/limiter adaptor）引 1.0.2 + 对应传输件 1.0.2。
