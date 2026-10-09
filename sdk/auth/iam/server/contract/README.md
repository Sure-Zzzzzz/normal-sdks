# Simple IAM Contract

`contract` 由 IAM Server 维护，是 Login、统一应用门户、IAM Admin、资源验证和开放 API 消费方的唯一接口契约来源。后端 API、登录状态机或门户 API 变更必须先更新这里，再由消费方生成或校验 client 与类型。

## 版本对应关系

### 1.3.6 服务端契约增量

`simple-iam-login-web.openapi.yaml` 与 `simple-iam-admin-web.openapi.yaml` 的 `info.version` 为 `1.3.6`；`simple-iam-open-api.openapi.yaml` 本轮零变化（维持 1.3.5）。增量均为向后兼容的可空新字段：

- 登录响应与 `/me`（AuthUser）：`mustChangePasswordReason`（FIRST_LOGIN / PASSWORD_RESET / PASSWORD_EXPIRED，与 mustChangePassword 同真）与 `passwordExpiresInDays`（临期 1..N、已过期 0、策略关闭或未激活 null）。
- 管理面用户响应（AdminUserResponse）：`passwordUpdatedAt` 与 `passwordExpiresInDays`，前端按两字段渲染密码状态（不设状态枚举）。
- 同轮行为收紧：三处改密/重置点拒绝"新密码=当前密码"（400，与密码策略校验同位）；口令生存期策略默认关闭（`iam.server.password.max-age-days=0`），开启时本地账号超期登录进入既有须改密受限通道。

### 1.3.5 服务端契约增量

`simple-iam-open-api.openapi.yaml` 的 `info.version` 为 `1.3.5`；其他契约不因 Server 版本变化统一改号。增量两件，均为向后兼容新增：

- `GET /iam/api/users/{subjectId}/page-admitted-applications`：按主体返回有页面准入（PAGE 投影非空）的启用应用编码裸数组。仅供服务间拉取（如反馈服务 hint 交集过滤）；最小授权=AKP 勾 `iam:portal:api` 单码，无 DATA 面，勿为此授 `iam:user:api`。清单顺序未定义；不含 Portal 集成启用维度（未挂门户壳但已注册且有页面准入的应用同样在列，与门户侧边栏口径的两处分叉见 server DESIGN.1.3.5 §10.1）。
- `UserRestResponse` 新增 `subjectId` 字段（列表与详情均回传）：对外公开主体标识，消费方以它做关联绑定，不依赖内部数字 id。旧消费方忽略新键，无破坏。

### 1.3.2 服务端契约增量

`simple-iam-admin-web.openapi.yaml` 与 `simple-iam-open-api.openapi.yaml` 的 `info.version` 为 `1.3.2`；其他契约不因 Server 版本变化统一改号。该增量不改变既有人员身份基座和普通角色调用方式，也不构成任何新前端版本已联调的声明。

| 消费方 | 本版接入要求 |
|---|---|
| IAM Admin | 角色详情、总列表、分页、部门角色及用户有效角色均含只读 `openRoleBinding`，普通角色为 null；列表识别边界，写入前读取当前详情版本；受委托角色改名、规则、挂载与删除提交强 If-Match `"open-role:<openRoleId>:<revision>"`，并遵守固定应用/根；缺条件 428、旧版本 412、非法标记 400，冲突后重读并重算差异 |
| 外部治理服务 | 以可信 AKSK `SERVICE` 身份调用 `/iam/api/**` 的受委托角色、目标应用、完整目录和成员事实 API；IAM 准入、八个精确 API 码和五个 DATA 资源分别批准，不以旧组织同步权限替代 |
| IAM 宿主 | 公共资源层接管 `/iam/api/**`，装配 AKSK 在线验证 Provider，治理调用关闭 introspect 本地缓存及旧结果回退 |

八个码为 `iam:open-role:create:api`、`iam:open-role:read:api`、`iam:open-role-rule:read:api`、`iam:open-role-rule:write:api`、`iam:open-department-role:read:api`、`iam:open-department-role:write:api`、`iam:open-directory:read:api`、`iam:open-application:read:api`。五个 DATA 资源为 `iam:open-role`、`iam:open-role-rule`、`iam:open-department-role`、`iam:open-directory`、`iam:open-application`，动作与对象维度按开放契约完整评估，不能跨 grant 拼权。

部门关系 GET 不提供 ETag，返回 `Cache-Control: no-store`，不按 If-None-Match 返回 304；组织及 DATA 变化后仍返回当前完整分页。响应体 revision 仅代表角色修改版本，写入标记从角色详情获取。创建的应用/部门根编号及规则清单版本只接受正数 int64 JSON 整数，不转换字符串或截断小数，格式及范围非法均返回 400。

服务治理凭据与人员授权码登录、资源验证 Basic 客户端、固定内部 reader SERVICE 各自独立，不可互换。角色规则中的目标业务 DATA 不是调用服务的治理 DATA；IAM 不判断商业有效期或产品资格。该增量的消费方版本组合须另有构建与真实联调记录，不能从以下历史基线推定其已适配。

### 1.3.0 集成基线

| 组件 | 版本 | 关系 |
| --- | --- | --- |
| IAM Server | `1.3.0` | 提供浏览器会话、管理 API、Portal API 与 OAuth2 协议端点 |
| IAM Contract | `1.3.0` | 上述浏览器 API 的唯一契约来源 |
| Login Web | `1.1.0` | 消费登录、短信验证、账号安全与 Consent 契约；构建期使用 IAM Theme Contract `1.0.0` |
| Unified Application Portal Web | `1.2.0` | 消费 Portal、站内信和本人手机号绑定契约；构建期使用 IAM Theme Contract `1.0.3` 与 Frontend Contract `1.0.0` |
| IAM Admin Web | `1.2.0` | 消费 `/iam/admin/**`，包括 subjectId 与用户 Excel 导入契约；构建期使用 IAM Theme Contract `1.0.3` 与 Frontend Contract `1.0.0` |

该表是 1.3.0 同轮联调的历史精确组合，不要求组件版本号与 Server 同号，不代表它已经验收 1.3.2 受委托角色。Server/Contract 在 `1.3.x` 内仅新增向后兼容字段或端点时，已发布前端可独立发布 patch；改变既有请求、响应或权限语义时，必须提升 Contract minor，并重新给出完整组合。

前端构建物不通过运行时版本探测决定兼容性：部署记录必须保存本表中的完整版本元组，发布前以对应 OpenAPI、前端 `check` 与真实联调共同确认。这样既避免浏览器把版本检查变成新的可用性依赖，也避免只凭版本号猜测接口可用性。

## 目录

| 路径 | 说明 |
|------|------|
| `openapi/simple-iam-login-web.openapi.yaml` | `simple-iam-login-web` 调用的浏览器会话、品牌、账号安全和 OAuth2 Consent 辅助 API 契约；含外部身份源登录、手机号验证码登录、忘记密码与手机号绑定 |
| `openapi/simple-unified-application-portal-web.openapi.yaml` | `simple-unified-application-portal-web` 调用的应用导航、站内信和 SSE API 契约 |
| `openapi/simple-iam-admin-web.openapi.yaml` | `simple-iam-admin-web` 调用的 `/iam/admin/**` 管理 API 契约；1.3.2 包含受委托角色摘要与共同条件写 |
| `openapi/simple-iam-owner-authorization.openapi.yaml` | 仅受控协作服务以固定内部 reader SERVICE 调用的所属人授权投影读取契约；不向浏览器、Portal 或普通 OAuth Client 暴露 |
| `openapi/simple-iam-resource-token-verification.openapi.yaml` | `simple-iam-resource-server-starter` 调用的受控 IAM Access Token 验证 API 契约 |
| `openapi/simple-iam-open-api.openapi.yaml` | 外部业务系统持 AKSK 凭证调用的开放 API 契约（`/iam/api/**`），含组织与人员同步、1.3.2 服务主体受委托角色治理和只读事实、1.3.5 页面准入查询与 users 投影 `subjectId`；含 AKSK 侧接入准备 |

## 约束

- 同一个 minor 内允许各前端独立发布 patch。
- Contract 的 breaking change 必须提升 IAM minor，并同步更新受影响前端的兼容范围。
- 每条 API 路径只能由一份 Contract 定义：浏览器会话、品牌和 Consent 辅助接口归 Login；Portal 应用导航、站内信和 SSE 接口归 Portal；`/iam/admin/**` 接口归 Admin；`/iam/resource/tokens/verify` 归资源服务调用契约；`/iam/api/**` 归开放 API 契约。
- 后端 Controller 或 API 行为变更必须先更新对应 Contract；前端不得绕过 Contract 调用未定义接口。
- Login、Portal、Admin 不互相依赖源码、分支或复制的 Contract 源文件；跨仓兼容性以本契约、生成或校验产物、联调和发布记录为准。
