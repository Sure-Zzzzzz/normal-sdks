# CHANGELOG - simple-aksk-server-starter 3.2.1

## 发布信息

- 版本：`3.2.1`
- 类型：Patch / 候选目录降级信号（非破坏增量）
- 基线版本：`3.2.0`

## 版本定位

让前端能区分"确实没有可创建的业务应用"与"身份源投影同步暂不可用"：3.2.0 的候选目录在这两种状态下返回完全相同的 `200 + 空数组`，信息在服务端即被压扁，用户与管理员无法分流排障。趁前端尚未发版补上机器可读信号，响应契约形态零变化。

## 主要变更

- `GET /api/me/aksk-clients/candidate-applications` 新增响应头 `X-Aksk-Projection-Degraded`（**仅在降级时出现**，值恒为 `true`）：本地所属人授权投影的同步租约已过期（游标存在但 `synchronizationLeaseUntil` 已过）时置位。判定与 inherited AKU 有效授权失败关闭使用同一租约口径；游标不存在（继承链路从未启用）不算降级。
- `AkskOwnerAuthorizationProjectionService` 新增 `isSynchronizationLeaseExpired()` 只读判定。
- 老前端（3.2.0 契约消费方）完全无感：响应体、状态码、缓存语义均不变，仅多一个可忽略的响应头。

## 依赖变更

无。与 3.2.0 相同（`simple-aksk-core:3.0.2`、`simple-aksk-server-core:3.0.4`、`simple-owner-authorization-collaboration-core:1.0.0`）。

## 新增或扩展测试

- 新增 `AkskSelfServiceCandidatesDegradedHeaderTest`（候选端点此前无直接测试覆盖）：
  - 租约判定三态——游标不存在不算降级 / 宽限内健康 / 过期降级；
  - 响应头仅在租约过期时出现且体透传不变；
  - 匿名主体保持 403 且不带降级头。

## 向后兼容性

- 纯增量：无 API 签名、响应体、状态码或配置变化；不读取该头的消费方行为与 3.2.0 完全一致。
- 契约 `simple-aksk-admin-web.openapi.yaml` 同步登记该响应头（仅 3.2.1+ 出现）。
