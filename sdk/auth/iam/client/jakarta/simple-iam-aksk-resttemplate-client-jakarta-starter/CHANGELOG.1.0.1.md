# CHANGELOG - simple-iam-aksk-resttemplate-client-jakarta-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Patch / 契约对齐（simple-iam-client-core 1.0.1）
- 基线版本：`1.0.0`

## 版本定位

随 simple-iam-client-core 1.0.1 同步：实现新契约方法并补齐响应字段解析，无装配与依赖变化。

## 变更明细

- 实现第 12 个契约方法 `listPageAdmittedApplications(String subjectId)`（GET `/iam/api/users/{subjectId}/page-admitted-applications`，
  字符串数组直读，与 getUserRoles 同款解析形态；此端点单独持 `iam:portal:api` 码）。
- 用户模型解析补 `subjectId` 字段回传。
- 契约测试补两端断言（新方法 URL/解析、subjectId 解析）。
