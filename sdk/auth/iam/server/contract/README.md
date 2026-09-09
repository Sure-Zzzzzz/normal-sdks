# Simple IAM Contract

`contract` 由 IAM Server 维护，是 Login、统一应用门户和 IAM Admin 的唯一接口契约来源。后端 API、登录状态机或门户 API 变更必须先更新这里，再由前端生成或校验 client 与类型。

## 版本对应关系

| IAM Server | Contract | Login Web | Portal Web | Admin Web |
|------------|----------|-----------|------------|-----------|
| `1.0.x` | `1.0.x` | `1.0.x` | `1.0.x` | `1.0.x` |

首次独立发布时，以 Server `1.0.0` 对应 Login、Portal、Admin 各自仓库的 `v1.0.0` tag。后续前端 patch 可独立发布，但 release notes 必须声明其兼容的 Server 与 Contract 范围。

## 目录

| 路径 | 说明 |
|------|------|
| `openapi/simple-iam-login-web.openapi.yaml` | `simple-iam-login-web` 调用的浏览器会话、品牌和 OAuth2 Consent 辅助 API 契约；含外部身份源登录（login 携带 `provider`、`providers` 动态列表、authorize/callback 跳转回调） |
| `openapi/simple-unified-application-portal-web.openapi.yaml` | `simple-unified-application-portal-web` 调用的应用导航、站内信和 SSE API 契约 |
| `openapi/simple-iam-admin-web.openapi.yaml` | `simple-iam-admin-web` 调用的 `/iam/admin/**` 管理 API 契约 |
| `openapi/simple-iam-resource-token-verification.openapi.yaml` | `simple-iam-resource-server-starter` 调用的受控 IAM Access Token 验证 API 契约 |
| `openapi/simple-iam-open-api.openapi.yaml` | 外部业务系统持 AKSK 凭证调用的开放 API 契约（`/iam/api/**`，用户与部门两族；含 AKSK 侧接入准备） |

## 约束

- 同一个 minor 内允许各前端独立发布 patch。
- Contract 的 breaking change 必须提升 IAM minor，并同步更新受影响前端的兼容范围。
- 每条 API 路径只能由一份 Contract 定义：浏览器会话、品牌和 Consent 辅助接口归 Login；Portal 应用导航、站内信和 SSE 接口归 Portal；`/iam/admin/**` 接口归 Admin；`/iam/resource/tokens/verify` 归资源服务调用契约；`/iam/api/**` 归开放 API 契约。
- 后端 Controller 或 API 行为变更必须先更新对应 Contract；前端不得绕过 Contract 调用未定义接口。
- Login、Portal、Admin 不互相依赖源码、分支或复制的 Contract 源文件；跨仓兼容性以本契约、生成或校验产物、联调和发布记录为准。
