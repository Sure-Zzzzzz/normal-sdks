# CHANGELOG - simple-aksk-server-audit-listener-starter 3.0.0

## 版本类型

Major Release - Server 3.0 Token 生命周期审计适配

## 依赖升级

| 依赖 | 2.x | 3.0.0 |
|---|---|---|
| `simple-aksk-server-core` | `2.0.3` | `3.0.1` |
| `simple-aksk-server-starter` | `2.0.3` | `3.0.0`（测试与组合验证基线） |

## 新增

- 对齐 `simple-aksk-server-core:3.0.1` 的 `TokenEventCause` 契约，审计 record 新增 `cause`。
- `REVOKED` 审计可区分 OAuth2 撤销、Token 管理、应用授权完整替换、应用授权撤销、Client 删除和 Client Secret 重置。
- `CLIENT_DISABLED` 保留为 Core 契约原因，但 Server 3.0 当前禁用流程不发布该撤销事件，监听器不虚构该运行时场景。
- 监听器改为 `AFTER_COMMIT` 投递：事务成功后才调用 Handler，非事务事件仍可兼容消费。

## 安全变更

- 3.0 listener 不再向 `ServerTokenAuditHandler` 下发 Token 原文；`tokenValue` 仅为二进制兼容保留字段且始终为 `null`。
- 默认日志仅输出事件类型、原因和非敏感 Client/用户标识，不再输出完整审计记录。
- 测试与示例不再输出完整 OAuth 或 introspection 响应。

## 可靠性边界

- Handler 异常不影响已提交业务操作或后续 Handler。
- 本模块是提交后尽力消费适配器；可靠持久化、重试和 Outbox 由具体 Handler 的专用实现负责。

## 新增测试

- `ServerTokenAuditListenerTest`：生命周期事件、全部撤销原因、事务提交/回滚、非事务 fallback、Handler 异常隔离及 Token 原文不下发。
- `ServerTokenAuditListenerIntegrationTest`：基于已发布 Server Starter 3.0.0 的真实签发、撤销、自省、Token 管理、应用授权、Client 删除和 Secret 重置场景。
- 自动配置测试：默认日志 Handler、自定义 Handler、关闭默认日志 Handler 及无 Handler 条件。
- 完整模块测试结果：19 tests，0 skipped，0 failures，0 errors。

## 兼容性说明

- `ServerTokenAuditHandler#handle(ServerTokenAuditRecord)` 方法签名保持不变。
- `tokenValue` 字段保留用于二进制兼容，但 3.0 listener 始终传递 `null`。
- 2.0.1 保持冻结，3.0.0 需与 Server 3.0 组合使用。

## 升级指南

- 2.0.1 使用方升级到 3.0.0 时，将 listener 依赖升级为 `simple-aksk-server-audit-listener-starter:3.0.0`，并将 Server 依赖升级为 `simple-aksk-server-starter:3.0.0`。
- 复核自定义 Handler 是否依赖 `tokenValue`；3.0 起不得从审计记录读取、记录或重建 Token 原文。
- 如果需要可靠持久化、重试、Outbox 或 MQ，由具体 Handler 实现，不由本 listener 提供。
