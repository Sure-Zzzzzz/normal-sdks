# Simple IAM Contract

`contract` 由 IAM Server 维护，是 Login、统一应用门户和 IAM Admin 的唯一接口契约来源。后端 API、登录状态机或门户 API 变更必须先更新这里，再由前端生成或校验 client 与类型。

## 版本对应关系

### 1.3.0 集成基线

| 组件 | 版本 | 关系 |
| --- | --- | --- |
| IAM Server | `1.3.0` | 提供浏览器会话、管理 API、Portal API 与 OAuth2 协议端点 |
| IAM Contract | `1.3.0` | 上述浏览器 API 的唯一契约来源 |
| Login Web | `1.1.0` | 消费登录、短信验证、账号安全与 Consent 契约；构建期使用 IAM Theme Contract `1.0.0` |
| Unified Application Portal Web | `1.2.0` | 消费 Portal、站内信和本人手机号绑定契约；构建期使用 IAM Theme Contract `1.0.3` 与 Frontend Contract `1.0.0` |
| IAM Admin Web | `1.2.0` | 消费 `/iam/admin/**`，包括 subjectId 与用户 Excel 导入契约；构建期使用 IAM Theme Contract `1.0.3` 与 Frontend Contract `1.0.0` |

该表是同轮联调的精确组合，不要求组件版本号与 Server 同号。Server/Contract 在 `1.3.x` 内仅新增向后兼容字段或端点时，已发布前端可独立发布 patch；改变既有请求、响应或权限语义时，必须提升 Contract minor，并重新给出完整组合。

前端构建物不通过运行时版本探测决定兼容性：部署记录必须保存本表中的完整版本元组，发布前以对应 OpenAPI、前端 `check` 与真实联调共同确认。这样既避免浏览器把版本检查变成新的可用性依赖，也避免只凭版本号猜测接口可用性。

## 目录

| 路径 | 说明 |
|------|------|
| `openapi/simple-iam-login-web.openapi.yaml` | `simple-iam-login-web` 调用的浏览器会话、品牌、账号安全和 OAuth2 Consent 辅助 API 契约；含外部身份源登录、手机号验证码登录、忘记密码与手机号绑定 |
| `openapi/simple-unified-application-portal-web.openapi.yaml` | `simple-unified-application-portal-web` 调用的应用导航、站内信和 SSE API 契约 |
| `openapi/simple-iam-admin-web.openapi.yaml` | `simple-iam-admin-web` 调用的 `/iam/admin/**` 管理 API 契约 |
| `openapi/simple-iam-owner-authorization.openapi.yaml` | 仅受控协作服务以固定内部 reader SERVICE 调用的所属人授权投影读取契约；不向浏览器、Portal 或普通 OAuth Client 暴露 |
| `openapi/simple-iam-resource-token-verification.openapi.yaml` | `simple-iam-resource-server-starter` 调用的受控 IAM Access Token 验证 API 契约 |
| `openapi/simple-iam-open-api.openapi.yaml` | 外部业务系统持 AKSK 凭证调用的开放 API 契约（`/iam/api/**`，用户与部门两族；含 AKSK 侧接入准备） |

## 约束

- 同一个 minor 内允许各前端独立发布 patch。
- Contract 的 breaking change 必须提升 IAM minor，并同步更新受影响前端的兼容范围。
- 每条 API 路径只能由一份 Contract 定义：浏览器会话、品牌和 Consent 辅助接口归 Login；Portal 应用导航、站内信和 SSE 接口归 Portal；`/iam/admin/**` 接口归 Admin；`/iam/resource/tokens/verify` 归资源服务调用契约；`/iam/api/**` 归开放 API 契约。
- 后端 Controller 或 API 行为变更必须先更新对应 Contract；前端不得绕过 Contract 调用未定义接口。
- Login、Portal、Admin 不互相依赖源码、分支或复制的 Contract 源文件；跨仓兼容性以本契约、生成或校验产物、联调和发布记录为准。
