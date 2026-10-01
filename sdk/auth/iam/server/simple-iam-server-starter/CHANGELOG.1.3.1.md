# CHANGELOG - simple-iam-server-starter 1.3.1

## 发布信息

- 版本：`1.3.1`
- 类型：Patch / 平台管理员特权域收窄（越界行为收口）
- 基线版本：`1.3.0`

## 版本定位

落地 2026-09-30 角色边界定案：平台管理员（内置 `iam_admin` 角色）的解析层特权合不再覆盖非内置（业务）应用——iam_admin 的特权域收敛为内置应用集合，业务应用治理回归"自宣告管理角色 + 显式授权"。这是对越界行为的收口而非承诺变更：README 与契约从未声明平台特权包含业务应用数据。

## 主要变更

- `IamPlatformAdminPrivilegeService.buildPrivilegedContext` 增加前置校验：目标应用非内置（`TrustedApplicationBuiltInResolver` 判定）时直接返回 `null`，不再合成特权上下文；**无 manifest 的"仅准入"分支同步收窄**（非内置无清单应用同样返回 null，不留隐性准入通道）。内置应用（模块默认内置域仅 `iam`；部署方可经引导配置追加，追加项行为不变）与 1.3.0 完全一致。
- 门户可达列表（`IamPortalApplicationService`）：特权直通从"全部启用应用"收窄为"内置应用"；非内置应用对平台管理员同样要求真实授权行。特权 manifest 批量读取仅为内置直通服务。
- `resolvePagePermissions` 分支对齐：非内置应用一律按授权行裁剪页面权限；platformAdmin 标记语义更新为"该用户是平台管理员（特权域=内置应用）"，该标记为纯展示字段（无逻辑消费）。
- 注释修正：原"撤销单应用授权不改变解析结果"的表述更新为仅对内置应用成立；管理面"按标记禁用撤销入口"的描述与实现不符（从未实现），已按实况改写。

## 行为变化（upgrade note）

- 平台管理员访问非内置应用（无真实授权行）时：门户侧栏不可见、PKCE 发码被拒（"当前用户未获应用资源授权"）、资源 verify 拒绝。**此前依赖隐性特权访问业务应用的部署，需为业务应用显式授权**（管理台授予或业务自宣告角色投影）——这正是本次收口的目的。
- AKSK 协作链零影响（实码确认）：reader / VC 是 SERVICE 凭据不走 platformAdmin；owner-authorization reader 在 loadActiveContext 前有自身授权行 isActive 守门，特权兜底在该链路本就不可达。
- 内置应用（默认 `iam` 及部署方追加项）全链路行为不变。

## 不变式

授权行优先级不变（有有效授权行永远走授权行，特权只是兜底）；isPlatformAdmin 判定不变（变的是作用域）；bootstrap 补授权行逻辑不变（仅内置域）；SERVICE 主体不受影响。

## 新增或扩展测试

- `IamAdminUserApplicationAuthorizationApiTest`：特权用例迁移为双断言——非内置应用（含无清单形态）对平台管理员不兜底（null）；内置应用（iam）无授权行仍合成清单全量；摘角色后内置授权行仍有效（bootstrap 行语义）。
- `IamPortalApplicationServiceTest`：特权直通用例迁移为"直通内置 iam、不直通非内置 A/B"；菜单裁剪用例改为"平台管理员经显式授权获得完整菜单"；manifest 批量读取用例改为"仅为内置直通服务、非内置不触发"；摘角色用例补内置直通失效断言。
