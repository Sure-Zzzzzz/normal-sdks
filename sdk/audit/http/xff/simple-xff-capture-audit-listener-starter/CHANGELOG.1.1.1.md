# simple-xff-capture-audit-listener-starter 1.1.1 Changelog

## 发布信息

- 版本：`1.1.1`
- 类型：Feature / 完整 Capture Event 投影
- 基线版本：`1.1.0`

## 主要变更

- Factory 直接从 `XffCaptureEvent` 的 `XffChain.rawHeaderList`、`rawList` 与完整 `ForwardedContext` 投影 Audit Document。
- 审计文档不再丢失 X-Real-IP、X-Forwarded-Host、X-Forwarded-Port、X-Forwarded-Proto 或同名 XFF Header 的原始边界。
- 保持 Host、XFF IP 分类、应用远端地址、可选上下文与 `requestData` 的既有投影语义。
- 传递依赖升级为 `simple-xff-capture-audit-core:1.1.1`；接入示例同步 Capture Starter `1.1.2`。

## 测试与兼容性验证

- Factory 测试覆盖多同名 XFF Header 的原始边界与顺序、原始 XFF 值链、全部 `ForwardedContext` 原始 Header 列表，以及规范化去重后的 XFF IP 和公网 IP 投影。
- 增加 XFF 缺失但 Host、X-Real-IP 与全部 X-Forwarded-* Header 存在的负向场景，验证这些独立事实仍分别投影，同时不得补造任何 XFF 原始值或类型化 IP。
- 按完整测试集通过 Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5` 和 `2.7.9` 精确版本矩阵；每项均为 `BUILD SUCCESSFUL`，最终报告为 30 tests、0 failures、0 errors、0 skipped。

## 未变更边界

- 不重新读取 Servlet 请求或 Header。
- 保持同步上下文快照、唯一有界异步广播、Provider 顺序和失败隔离语义。
