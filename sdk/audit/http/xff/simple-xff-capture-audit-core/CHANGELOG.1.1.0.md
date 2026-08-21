# simple-xff-capture-audit-core 1.1.0 Changelog

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / 向后兼容能力扩展
- 基线版本：`1.0.0`

## 主要变更

- `XffCaptureAuditDocument` 新增固定顶层 `requestData` 字段，直接保存 `simple-xff-capture-core:1.1.0` 提供的不可变 `RequestDataSnapshot`。
- Query、Form、Body 的采集开关、URI 规则、Content-Type 约束、截断和读取状态继续完全由 Capture 负责；Audit Core 不增加第二套配置或重新采集请求数据。
- 新增带 `requestData` 的构造器；原有构造器默认写入 `RequestDataSnapshot.disabled()`，保持既有调用方兼容。
- 审计文档 `toString()` 排除 `requestData`，避免 Query、Form 与 Body 文本出现在默认日志中。
- Core 依赖升级为精确坐标 `io.github.sure-zzzzzz:simple-xff-capture-core:1.1.0`。

## 验证范围

- 覆盖带 Query、Form、Body 的请求数据快照完整投影。
- 覆盖旧构造器默认关闭请求数据、空 `requestData` 拒绝与 `toString()` 不泄漏请求数据值。
- 执行完整 Audit Core 模块测试。

## 向后兼容性

- 现有构造器、原有网络事实字段、`extensions` envelope 与 `XffCaptureAuditPersistenceProvider` 方法签名保持不变。
- 已有调用方无需传入 `requestData`；它们会得到完整但全部为 `DISABLED` 的默认快照。
