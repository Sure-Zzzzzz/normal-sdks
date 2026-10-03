# Simple KMS Contract

`contract` 由 KMS Server 维护，是 KMS 管理端（`smart-kms-admin-web`）与服务调用方（`simple-kms-client-starter`）的唯一接口契约来源。接口、权限码与 DATA 范围行为变更必须先更新这里，再由前端与客户端生成或校验调用。

## 版本对应关系

| KMS Server | Contract | Admin Web |
|---|---|---|
| `2.0.0` | `2.0.0` | 首版随发 |

## 目录

| 路径 | 说明 |
|---|---|
| `openapi/smart-kms-admin-web.openapi.yaml` | 管理端与服务调用方共用的端点契约（22 条路径 / 26 个端点，含 owner 销毁窗口政策三端点）；非 2xx 统一为 Error 错误体（message/timestamp/requestId）：主体（/me）、自助（/me/keys）、管理查询（/admin/keys）、密钥生命周期、精确策略、密码学四操作、公钥分发、销毁任务与 worker 健康 |

## 认证与端点边界

- 全部端点要求 `Bearer`（IAM 人员 Access Token 或 AKSK Token，经公共 Resource Server 验证，kid 前缀路由）。
- 每个端点绑定精确 API 权限码（如 `kms.key.manage`），授权由 IAM 投影驱动；`/admin/**`、销毁任务列表额外受 DATA 范围（`kms-key` 资源、`ownerPrincipalId` 维度）裁剪。
- crypto 端点在 API 权限之上叠加精确 key policy 与密钥/版本状态校验。
- 写命令携带 `Idempotency-Key`；状态变更类携带 `expectedRowVersion`（乐观锁）。
- `simple-kms-client-starter` 的服务端调用走同契约（`/api/kms` 基路径，2.0.0 起；1.x 的 `/api/v1/kms` 已移除）。

## 约束

- Contract 的 breaking change 必须提升 KMS Server major，并同步更新受影响前端/客户端的兼容范围。
- 服务端 Controller 或行为变更必须先更新本 Contract；前端与客户端不得绕过契约调用未定义接口。

## PAGE 权限与角色的注册侧说明

PAGE 权限码（4 个）与角色名（kms-admin / kms-self-service / kms-crypto-user）不在本契约内——它们是 IAM 注册侧概念（manifest + 菜单树 + 角色规则），见 KMS Server `DESIGN.2.0.0` 第 6 节与 IAM 领域文档《权限与授权投影》。
