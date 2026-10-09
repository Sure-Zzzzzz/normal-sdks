# CHANGELOG - simple-iam-server-core 1.3.3

## 发布信息

- 版本：`1.3.3`
- 类型：Patch / 内置权限常量补充
- 基线版本：`1.3.2`

## 版本定位

为 1.3.5 openapi 页面准入查询端点（simple-iam-server-starter）提供内置权限编码常量。本版本只在
`SimpleIamServerConstant` 增加一个常量 `BUILT_IN_PERMISSION_PORTAL_API`（`iam:portal:api`，IAM 门户
准入查询接口），不修改既有常量、身份协议、公开方法、异常类型、事件或枚举。

## 变更明细

- `SimpleIamServerConstant` 新增 `BUILT_IN_PERMISSION_PORTAL_API = "iam:portal:api"`：授予后可调用
  `GET /iam/api/users/{subjectId}/page-admitted-applications`（端点随 simple-iam-server-starter 1.3.5 发布）。
  该码为只读查询专用，不携带 users 族任何写能力，AKP 最小授权语义见 starter DESIGN.1.3.5 §10.1 D6。
