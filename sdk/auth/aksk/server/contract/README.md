# Simple AKSK Contract

`contract` 由 AKSK Server 维护，是 AKSK 管理端（`simple-aksk-admin-web`）的唯一接口契约来源。管理 API、Client/Token/应用授权行为变更必须先更新这里，再由前端生成或校验 client 与类型。

## 版本对应关系

| AKSK Server | Contract | AKSK Admin Web |
|-------------|----------|----------------|
| `3.1.x` | `3.1.x` | `v1.0.0` |
| `3.2.0` | `3.2.0` | `v1.0.1+` |
| `3.2.1` | `3.2.1` | `v1.0.1+` |

`3.2.0` 为 breaking：管理 REST `POST /api/client` 的 `type=user` 一律 409，AKU 创建收敛到 `/api/me/aksk-clients` 本人自助；管理台新建页仅保留平台级 AKP。

`3.2.1` 保持既有请求和响应体形态；自助 AKU 候选目录在所属人投影租约过期时额外返回 `X-Aksk-Projection-Degraded`，供前端区分无可创建目标与协作暂不可用。

首次发布以 Server `3.1.x` 对应 `simple-aksk-admin-web` 仓库的 `v1.0.0` tag。后续前端 patch 可独立发布，但 release notes 必须声明其兼容的 Server 与 Contract 范围。

## 目录

| 路径 | 说明 |
|------|------|
| `openapi/simple-aksk-admin-web.openapi.yaml` | `simple-aksk-admin-web` 调用的管理 API，以及 `/api/me/aksk-clients` 当前用户自助 AKU API 契约 |
| `openapi/simple-aksk-resource-introspection.openapi.yaml` | 面向资源服务与内部消费方的令牌内省（RFC 7662）契约，含 OWNER_INHERITED 扩展声明与四纪元失败关闭语义 |

## 认证与协议端点边界

- 管理 API 一律 `Bearer`（IAM 人员 Access Token）。AKSK Server 门户形态须外插 `simple-iam-resource-server-starter` 并配置受控验证，token 经 IAM 回源校验；请求不得同时携带 Cookie 等其他凭据。
- 前端取得 token 走 PKCE 公共客户端授权码链：`GET /oauth2/authorize`（`response_type=code`、S256 `code_challenge`）与 `POST /oauth2/token`。这两个端点属 IAM Server 的标准 OAuth2 协议端点，按协议接入（见 IAM 用户手册 5.3 节登录序），不由本契约重复定义。
- 内嵌管理台（`/admin/**`，Thymeleaf + session）不在本契约范围，归独立模式浏览器操作。
- `/api/me/aksk-clients` 的对象范围由服务端认证主体固定为当前 IAM HUMAN；自助权限 `akskSelfCredential:*` 与管理权限 `akskClient:*` 分离，前端不得用管理权限替代自助权限。
- 自助 AKU 列表和创建、改名、轮换响应会返回 `targetApplicationId`；前端用候选应用目录中的名称展示目标业务应用，不把 owner 字段误作目标应用。

## 约束

- Contract 的 breaking change 必须提升 AKSK Server minor，并同步更新受影响前端的兼容范围。
- 每条 API 路径只能由一份 Contract 定义：`/api/client`、`/api/token`、`/api/application-authorization` 归本契约；外部业务系统持 AKSK 凭证调用的开放接口归 IAM Contract 仓的 `simple-iam-open-api.openapi.yaml`。
- 后端 Controller 或 API 行为变更必须先更新对应 Contract；前端不得绕过 Contract 调用未定义接口。
- 前端不依赖本仓源码、分支或复制的 Contract 源文件；跨仓兼容性以本契约、生成或校验产物、联调和发布记录为准。
