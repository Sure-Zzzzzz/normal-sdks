# simple-xff-capture-audit-listener-starter 1.1.0 Changelog

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / 可靠性与请求数据投影扩展
- 基线版本：`1.0.0`

## 依赖升级

| 依赖 | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| `simple-xff-capture-starter` | `1.0.0` | `1.1.0` |
| `simple-xff-capture-audit-core` | `1.0.0` | `1.1.0` |

## 主要变更

- Listener 依赖已切换为已发布的 `simple-xff-capture-audit-core:1.1.0`，Capture Starter 使用 `1.1.0`。
- Factory 直接将 `XffCaptureEvent.requestData` 投影到 `XffCaptureAuditDocument.requestData`；Listener 不重新采集 HTTP 请求，不增加第二套 Query、Form、Body 配置。
- 请求数据的采集开关、URI 规则、Content-Type 约束、截断和读取状态仍完全由 Capture 负责。
- 转换失败、队列拒绝、任务提交失败、Context Provider 失败和 Provider 失败均记录完整 Throwable 堆栈；Provider 失败后继续广播后续 Provider。
- 保持同步 Factory、单一有界执行器、Provider 顺序广播和 best-effort 失败边界，不新增重试、持久队列、fallback 或 Elasticsearch 依赖。
- Listener 版本升级为 `1.1.0`。

## 新增验证

- 真实随机端口 HTTP 集成测试覆盖：Capture Filter → `XffCaptureEvent` → Listener Factory → 有界执行器 → 记录 Provider。
- 验证 Controller 收到完整请求体，Event 与 Document 的 `requestData` 为同一不可变快照，并覆盖 Query 多值、Body 文本、XFF 与 URI 投影。
- 覆盖文档转换、队列拒绝、任务提交、Context Provider 和 Provider 异常的完整堆栈日志。
- 覆盖一个 Provider 失败后后续 Provider 继续执行。

## 向后兼容性

- 既有 Listener 配置结构、Context Provider、Persistence Provider SPI 和异步 best-effort 语义保持不变。
- 既有未开启 Capture 请求数据采集的应用会继续收到 `RequestDataSnapshot.disabled()` 默认快照。
- Listener 不依赖 Elasticsearch、Persistence 或 Route；这些模块的接入方式保持独立。

## 升级指南

1. 将 `simple-xff-capture-starter` 升级到 `1.1.0`。
2. 将 `simple-xff-capture-audit-core` 升级到 `1.1.0`。
3. 将本 Listener 升级到 `1.1.0`。
4. 如需请求数据审计，在 Capture Starter 中按需启用 Query、Form、Body 维度和 URI 规则；Listener 无需新增配置。
