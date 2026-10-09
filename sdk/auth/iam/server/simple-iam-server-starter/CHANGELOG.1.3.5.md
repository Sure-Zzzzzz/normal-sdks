# CHANGELOG - simple-iam-server-starter 1.3.5

## 发布信息

- 版本：`1.3.5`
- 类型：Patch / 缺陷修复 + openapi 能力补齐
- 基线版本：`1.3.4`
- 关联制品：`simple-iam-server-core:1.3.3`（新内置权限码常量）、`simple-iam-client-core:1.0.1` 与四传输件（契约镜像）

## 版本定位

三件事同发一版：① 平台管理员特权全量分支收口（1.3.1 特权域定案在门户读模型上的漏网补齐）；
② openapi 新增页面准入应用清单查询端点（feedback 等服务间消费方的提交下拉交集过滤前置能力）；
③ openapi users 族响应投影回传 `subjectId`。数据库形状与既有端点行为零变化。

## 变更明细

### 1. 平台管理员特权全量分支收口（缺陷修复）

- 症状：平台管理员取消某非内置（业务）应用的全部自定义角色后（授权行 `page_permissions_json=[]`），
  门户中该应用页面仍然全量可见。
- 根因：`IamPortalApplicationService#resolvePagePermissions` 以 `manifest != null` 当"内置应用"的代理判断——
  业务应用注册同样登记 manifest，等价关系破裂后平台管理员 + 非内置应用命中清单全量分支，授权行被无视。
- 修复：取值策略收敛为包私有枚举 `PagePermissionSource`（判定与取值分离）——特权全量
  （PLATFORM_ADMIN_MANIFEST）的成立条件是三条件合取：平台管理员 ∧ 内置应用 ∧ 已申报清单，
  缺一即回退授权行投影（AUTHORIZATION_ROW）。取值异常仍按既有语义返回空集并记 debug。
- 影响面：仅门户读模型一处；令牌投影 / 资源校验（1.3.1 已对）/ 管理面投影经核实无同源缺陷；
  导航上下文与登录落地应用复用裁剪结果自动修复。本版补 `buildPrivilegedContext` 非内置应用返回空的直测钉住。

### 2. openapi 新端点：页面准入应用清单

```
GET /iam/api/users/{subjectId}/page-admitted-applications
授权：AKP 持新内置 API 码 iam:portal:api（方法级 @RequireApiPermission）；无 DATA 面
响应：200 应用编码裸数组（顺序未定义）；subjectId 不存在 404；无码 403
```

- 口径：准入门放行（授权行 admitted 有效，或平台管理员对内置应用特权直通）∧ `PagePermissionSource`
  取值非空 ∧ 应用启用；**不含 Portal 集成启用维度**——未挂门户壳但已注册且有页面准入的应用同样在列
  （与门户侧边栏口径的两处分叉见 DESIGN.1.3.5 §10.1 D5 对照表，均为"页面准入"字面口径的忠实结果）。
- 新内置权限码 `iam:portal:api` 经 `IamBootstrapService` 幂等登记，存量部署升级重启后 AKP 授权面自动可见；
  不复用 `iam:user:api` 是为保持消费方（如 feedback hint adaptor）最小授权——复用会带出用户全族写能力。
- 消费场景：feedback SDK 提交下拉交集过滤（DESIGN.1.0.0 D11/D12）；就绪前 adaptor 只能降级全量。

### 3. openapi users 投影回传 subjectId

- `UserRestResponse`（列表与详情）新增 `subjectId` 字段（置于 `id` 后）。`subjectId` 是对外公开主体标识
  （`/{subjectId}` 路径参数即用它），此前响应体不回传，消费方（limiter-management 等）列表场景无法关联绑定。

## 测试

- 门户服务层：分裂数据用例（清单申报 ⊋ 授权行授予，两分支结果必须分裂——钉死按行裁剪）、零权限行不可见、
  特权兜底非内置返回空直测、页面准入/门户口径分叉（未挂 Portal 集成的应用进清单不进侧边栏）。
- 真 AKP HTTP 组合验收（新 `IamPageAdmissionApiTest`，发布版 AKSK 独立进程 + 正式 introspect）：
  码控三段（401/403/200）、口径分叉实证、零权限内置不外溢、404、users 列表与详情 subjectId 回传。
- client 契约：方法数 29→30、新方法 URL/解析、subjectId 解析（javax/jakarta 四传输件）。
- 组合验收去浏览器化：`IamOpenRoleAkpHttpTest` 删除依赖仓外 qiankun 验收脚本的浏览器全链段
  （脚本不可再生导致本机全量永久带 1 已知失败），保留真 AKSK 独立进程回源的 HTTP 全链断言，
  用例更名 `realAkpPreservesAuthorizationRevisionAndConditionalWrites`——全量在本机首次干净全绿。

## 升级指引

- 依赖"特权膨胀"（平台管理员靠缺陷看到业务应用全页面）的部署升级后入口会"消失"——该行为本就违反
  1.3.1《角色边界定案》，需补显式授权（应用自宣告管理角色并分配）。
- `iam_trusted_application.built_in` 误标为 1 的业务应用仍会走特权——内置判定唯一口径即该列（部署自查）。
- AKP 授权面出现新码 `iam:portal:api`：需要页面准入查询的消费方授此单码即可，无需 `iam:user:api`。
