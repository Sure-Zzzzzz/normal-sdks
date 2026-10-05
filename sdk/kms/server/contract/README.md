# Simple KMS Contract

`contract` 由 KMS Server 维护，是 KMS 管理端（`smart-kms-admin-web`）与服务调用方（`simple-kms-client-starter`）的唯一接口契约来源。接口、权限码与 DATA 范围行为变更必须先更新这里，再由前端与客户端生成或校验调用。

## 版本对应关系

| KMS Server | Contract | Admin Web |
|---|---|---|
| `2.0.1` | `2.0.1` | `1.0.0`，本人生命周期使用新增写接口 |
| `2.0.0` | `2.0.0` | 共用管理写接口版本，仍要求 DATA |

## 目录

| 路径 | 说明 |
|---|---|
| `openapi/smart-kms-admin-web.openapi.yaml` | 管理端与服务调用方共用的端点契约，含本人四项生命周期写操作和 owner 销毁窗口政策；Error 为安全错误体（message/timestamp/requestId），具体 HTTP 状态以各端点为准 |

## 认证与端点边界

- 全部端点要求 `Bearer`（IAM 人员 Access Token 或 AKSK Token，经公共 Resource Server 验证，kid 前缀路由）。
- 每个端点绑定精确 API 权限码（如 `kms.key.manage`），授权由 IAM 投影驱动；`/admin/**`、销毁任务列表额外受 DATA 范围（`kms-key` 资源、`ownerPrincipalId` 维度）裁剪。
- crypto 端点在 API 权限之上叠加精确 key policy 与密钥/版本状态校验。
- 写命令携带 `Idempotency-Key`；状态变更类携带 `expectedRowVersion`（乐观锁）。
- 本人启停、轮换、安排与取消销毁使用 `/me/keys/{keyRef}/state`、`versions`、`destruction`，固定认证主体归属，不要求 DATA；原 `/keys/**` 管理写仍要求对应 DATA，新旧幂等作用域独立，不根据失败响应切换入口。
- 启停仅允许 ACTIVE 与 DISABLED 互相迁移。待销毁密钥必须通过取消任务恢复，不能用 PATCH 绕过任务校验或标记为已销毁。
- `simple-kms-client-starter` 的服务端调用走同契约（`/api/kms` 基路径，2.0.0 起；1.x 的 `/api/v1/kms` 已移除）。

## 约束

- Contract 的 breaking change 必须提升 KMS Server major，并同步更新受影响前端/客户端的兼容范围。
- 服务端 Controller 或行为变更必须先更新本 Contract；前端与客户端不得绕过契约调用未定义接口。

## PAGE 权限与角色的注册侧说明

PAGE 权限码（4 个）与角色名（kms-admin / kms-self-service / kms-crypto-user）是 IAM 注册侧概念（权限清单、菜单树与角色规则）。统一注册流程见 [KMS Web 可信应用接入手册](https://github.com/Sure-Zzzzzz/smart-kms-admin-web/blob/main/docs/TRUSTED_APPLICATION_ONBOARDING.md)，配套模板由同一 Web 仓的 `deploy/iam/` 维护。
