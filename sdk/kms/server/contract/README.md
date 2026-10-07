# Simple KMS Contract

`contract` 由 KMS Server 维护，是 KMS 管理端（`smart-kms-admin-web`）与服务调用方（`simple-kms-client-starter`）的唯一接口契约来源。接口、权限码与 DATA 范围行为变更必须先更新这里，再由前端与客户端生成或校验调用。

## 版本对应关系

| KMS Server | Contract | Admin Web |
|---|---|---|
| `2.0.2` | `2.0.2` | `1.0.0`，本人公钥和两模式销毁明细使用新增查询接口 |
| `2.0.1` | `2.0.1` | 本人四项生命周期写接口基线，不支持当前 Web 的新增查询 |
| `2.0.0` | `2.0.0` | 共用管理写接口版本，仍要求 DATA |

## 目录

| 路径 | 说明 |
|---|---|
| `openapi/smart-kms-admin-web.openapi.yaml` | 管理端与服务调用方共用的端点契约，含本人公钥、本人及治理销毁明细、本人四项生命周期写操作和 owner 销毁窗口政策；Error 为安全错误体（message/timestamp/requestId），具体 HTTP 状态以各端点为准 |

## 认证与端点边界

- 全部端点要求 `Bearer`（IAM 人员 Access Token 或 AKSK Token，经公共 Resource Server 验证，kid 前缀路由）。
- 每个端点绑定精确 API 权限码（如 `kms.key.manage`），授权由 IAM 投影驱动；`/admin/**`、销毁任务列表额外受 DATA 范围（`kms-key` 资源、`ownerPrincipalId` 维度）裁剪。
- crypto 端点在 API 权限之上叠加精确 key policy 与密钥/版本状态校验。
- `GET /me/keys/{keyRef}/public-keys` 要求已验证 HUMAN 人员身份、`kms.read-public-key`、本人归属和合法算法/状态，不要求额外 `READ_PUBLIC_KEY` 使用策略。SERVICE、旧公钥接口与密码学调用继续使用原策略授权。
- 本人 `GET /me/keys/{keyRef}/destruction` 使用 `kms.key.read`，无 DATA；治理 `GET /admin/keys/{keyRef}/destruction` 加 `kms-key:read` DATA。返回密钥状态、版本、取消资格和每版计划/完成时间，不暴露材料或领取内部信息。
- `GET /admin/keys` 与兼容入口 `GET /keys` 支持可选 `ownerPrincipalId` 精确筛选，条目含 `ownerPrincipalId` 与可空 `ownerDisplayName`；归属筛选只在 DATA 范围内收窄，空白参数视为未筛选。
- `GET /admin/policies` 跨钥策略分页查询使用 `kms.key.policy` API 加 `kms-key` DATA，支持密钥别名片段、被授权主体精确、操作精确筛选，条目附带密钥别名、双方主体显示名与创建时间；策略写命令仍在按钥入口。
- `GET /destruction-jobs` 支持可选 `ownerPrincipalId` 精确筛选，条目含归属主体与可空显示名；自定义查询仓储未覆盖新重载时携带筛选返回 400。
- `cancelEligible` 是读取时的业务资格，不包含销毁授权；历史领取过即使释放回 PENDING 或租约过期也为 false。取消命令仍最终复核；未排程/取消后为空，查询失败为 503。
- 写命令携带 `Idempotency-Key`；状态变更类携带 `expectedRowVersion`（乐观锁）。
- 本人启停、轮换、安排与取消销毁使用 `/me/keys/{keyRef}/state`、`versions`、`destruction`，固定认证主体归属，不要求 DATA；原 `/keys/**` 管理写仍要求对应 DATA，新旧幂等作用域独立，不根据失败响应切换入口。
- 启停仅允许 ACTIVE 与 DISABLED 互相迁移。待销毁密钥必须通过取消任务恢复，不能用 PATCH 绕过任务校验或标记为已销毁。
- `simple-kms-client-starter` 的服务端调用走同契约（`/api/kms` 基路径，2.0.0 起；1.x 的 `/api/v1/kms` 已移除）。

## 约束

- Contract 的 breaking change 必须提升 KMS Server major，并同步更新受影响前端/客户端的兼容范围。
- 服务端 Controller 或行为变更必须先更新本 Contract；前端与客户端不得绕过契约调用未定义接口。
- `2.0.1` 升级到 `2.0.2` 无数据库迁移，标准自助角色无需增权、重新注册或补历史使用策略。自定义主体解析器须在可信认证器证明 HUMAN 后显式建立人员上下文；自定义存储须实现新增销毁查询端口。当前 Web 不能先于 Server 部署，回滚先恢复旧 Server 兼容的 Web 制品。

## PAGE 权限与角色的注册侧说明

PAGE 权限码（4 个）与角色名（kms-admin / kms-self-service / kms-crypto-user）是 IAM 注册侧概念（权限清单、菜单树与角色规则）。统一注册流程见 [KMS Web 可信应用接入手册](https://github.com/Sure-Zzzzzz/smart-kms-admin-web/blob/main/docs/TRUSTED_APPLICATION_ONBOARDING.md)，配套模板由同一 Web 仓的 `deploy/iam/` 维护。
