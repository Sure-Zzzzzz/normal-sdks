# CHANGELOG - simple-iam-client-core 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Patch / 契约对齐（server 1.3.5）
- 基线版本：`1.0.0`

## 版本定位

对齐 simple-iam-server-starter 1.3.5 的两项 openapi 增补：users 族响应投影回传 `subjectId`，
新增页面准入应用清单查询方法。全部为向后兼容的新增（接口加方法、模型加字段），wire 旧消费方不受影响。

## 变更明细

- `IamUserClient` 新增第 12 个方法 `listPageAdmittedApplications(String subjectId)`：返回用户有页面准入
  （PAGE 投影非空）的启用应用编码列表。**此方法单独持 `iam:portal:api` API 码（非 `iam:user:api`），
  无 DATA 面约束**；wire 为字符串数组，顺序未定义。未挂 Portal 集成但有页面准入的应用同样在列
  （与门户侧边栏口径的差异见 server DESIGN.1.3.5 §10.1）。
- `IamUser` 模型新增 `subjectId` 字段（置于 `id` 之后）：users 列表与详情响应回传对外公开主体标识，
  消费方以 subjectId 做关联绑定，不再依赖内部数字 id。
- 三接口方法数契约 29 → 30（`IamClientCoreContractTest` 同步）。

## 兼容性说明

- 接口新增方法对"自行 implements IamUserClient 的外部实现方"是编译级不兼容——契约接口的消费形态
  为使用传输件实现（resttemplate / feign / jakarta 两线四件同号 1.0.1 随后批次发布）；自实现属非常规用法。
- `IamUser` 为不可变模型，builder 消费方完全兼容。
