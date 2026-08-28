# Changelog - simple-aksk-resource-audit-listener-starter 3.0.0

## 变更概述

适配 3.0 公共资源服务架构：监听事件由 `AkskAccessEvent`（simple-aksk-resource-core，随 2.x 封版）切换为公共资源层 `ResourceAccessEvent`，并按身份来源标识（`AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID`）过滤，只处理 AKSK 来源的事件；`AkskAuditRecord` 字段对齐 3.0 服务身份模型。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-resource-core` | 2.0.0 | 移除（随 2.x 封版） |
| `simple-resource-server-core` | - | 1.1.1（新增，`implementation`，`ResourceAccessEvent` 事件契约） |
| `simple-aksk-core` | - | 3.0.0（新增，`implementation`，来源常量） |
| `simple-resource-server-starter` | - | 1.1.0（新增，`testImplementation`，链路 E2E 的公共安全链） |
| `simple-aksk-resource-server-starter` | 2.0.0（测试依赖） | 3.0.1（测试依赖，链路 E2E 的 AKSK Provider） |

main 的 `simple-resource-server-core` 与 `simple-aksk-core` 是本模块的实现细节（事件契约与来源过滤常量），用 `implementation` 收紧：runtime 自动传递不影响开箱即用，使用方不耦合公共资源层 API；公共安全链与身份来源 Provider（`aksk-resource-server-starter` / `iam-resource-server-starter`）由使用方按需组装，测试侧按同一组装形态引入公共 Starter 与 AKSK Provider。

## 行为说明

- 只处理 AKSK 来源的 `ResourceAccessEvent`（`authenticationSourceId == aksk`），其他来源（如 IAM）的事件直接忽略——同一资源服务组合多身份来源时互不串扰。
- 事件转换后的处理链路不变：`@Async` 异步分发所有 `AkskAuditHandler`，单个 Handler 异常不影响其他 Handler 和主流程。
- `AkskAuditTraceIdProvider` 链路追踪机制不变：未提供时 `traceId` 为 null。
- 初始化与异常日志收敛为中文。

## 兼容性（破坏性变更）

- **`AkskAuditRecord` 字段变更**：移除 `clientId` / `clientType` / `userId` / `username` / `roles` / `scope` / `source` / `context`；新增 `authenticationSourceId` / `subjectType` / `subjectId` / `applicationCode` / `requestId`。AKSK 来源事件中 `subjectType` 恒为 `SERVICE`、`subjectId` 为 Client ID。业务 `AkskAuditHandler` 实现需同步适配取值字段。
- **事件前提变更**：审计事件契约在 `simple-resource-server-core`（纯模型对象，经 `publishEvent(Object)` 发布），由公共安全链在认证通过后统一发布（AKSK 身份经其 Provider 接入），不再依赖 `simple-aksk-resource-server-starter` 直发事件。
- `AkskAuditHandler` / `AkskAuditTraceIdProvider` 接口签名、自动装配机制、配置前缀 `io.github.surezzzzzz.sdk.audit.aksk.resource.listener` 均不变。
- 默认日志 Handler 输出的记录内容随字段模型变化。

## 测试

- Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本矩阵（完整链路端到端验证：MockMvc → 公共安全链 → AKSK Provider → stub 内省 → AKSK 访问记录 12 字段审计 / 未认证请求不产生记录 / 非 AKSK 来源事件忽略）。
- stub 只剪 introspect 网络边界；introspect 协议本身的真实性（回退策略、本地缓存、拒绝路径）由 `simple-aksk-resource-server-starter` 模块自己的测试承担。
- 移除旧 E2E 的真实 INTROSPECT 认证链路测试（`AkskAuditIntegrationTest`、`OAuth2TokenHelper`）及相应凭据配置，测试不再依赖外部 AKSK Server。
