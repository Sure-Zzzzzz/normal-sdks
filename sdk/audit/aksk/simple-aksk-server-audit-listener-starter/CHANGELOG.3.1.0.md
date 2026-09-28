# CHANGELOG - simple-aksk-server-audit-listener-starter 3.1.0

## 发布信息

- 版本：`3.1.0`
- 类型：Minor / 新增 AKU 生命周期审计消费能力
- 基线版本：`3.0.0`

## 版本定位

与 `simple-aksk-server-starter` 3.2.0 的 OWNER_INHERITED AKU 自助生命周期配套：Server 侧按 AFTER_COMMIT 发布脱敏的 `AkskClientLifecycleEvent`，本模块提供对应的审计消费 SPI、默认日志 Handler 与自动装配。

## 主要变更

- 新增 `ServerClientLifecycleAuditHandler` SPI 与 `ServerClientLifecycleAuditRecord` 记录模型：字段为 eventType（CREATED/RENAMED/SECRET_ROTATED/TERMINATED）、eventTime、clientId、ownerSourceId、ownerSubjectId、targetApplicationId、lifecycleVersion——全部为稳定标识与版本号，不含 Secret、Token、权限集合或 DATA。
- 新增 `ServerClientLifecycleAuditEventListener`：`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)`，与 Token 审计同一提交后语义；单 Handler 异常只记日志，不阻断后续 Handler。
- 新增默认日志 Handler `LogServerClientLifecycleAuditHandler`，与 Token 日志 Handler 共用开关 `io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log.enabled`（默认开）。
- 自动配置扩展：存在任意 `ServerClientLifecycleAuditHandler` Bean 时装配生命周期监听器；Token 审计装配逻辑不变。
- 既有 Token 审计监听与 Handler 契约零变化（Server 3.2.0 线未修改 AbstractTokenEvent 家族）。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
| --- | --- | --- |
| `simple-aksk-server-core` | `3.0.1` | `3.0.4` |

测试依赖 `simple-aksk-server-starter` 同步升至 `3.2.0`（真实宿主发布版，验证 3.2.0 线的完整事件链）。

## 新增或扩展测试

- 新增 `ServerClientLifecycleAuditListenerTest`：监听器装配唯一性、事件 7 字段到记录的逐字段映射、单 Handler 异常隔离。
- 新增 `ServerClientLifecycleAuditListenerNoHandlerAutoConfigurationTest`：关闭默认日志 Handler 且业务不提供 Handler 时监听器不装配的反证。

## 向后兼容性

- 仅新增：既有 Token 审计行为、配置键与 Handler 契约不变；不提供生命周期 Handler 的应用无任何装配变化。
