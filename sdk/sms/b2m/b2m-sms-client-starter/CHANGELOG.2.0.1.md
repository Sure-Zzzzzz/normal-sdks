# CHANGELOG 2.0.1

- 发布日期：2026-09-26
- 类型：优化（错误码表补齐与测试断言增强，零行为改动）

## 依赖变化

无。

## 变更内容

1. **错误码表补齐**：`ErrorCode` 新增 `CONFIG_CONTENT_INVALID = "SMS_CONFIG_003"`（配置内容非法——话术/形态校验拒绝）。IAM 侧 b2m 适配器（`simple-iam-sms-b2m-adapter-starter`）在配置话术/形态校验拒绝时已使用该错误码语义，本次在 SDK 码表中落常量，调用方可统一以 `ErrorCode.CONFIG_CONTENT_INVALID` 定位与判断；
2. **测试断言增强**：`SmsClientPipelineTest`、`B2mManualSendTest` 补齐成功/失败判定断言（`assertTrue` / `assertFalse` 静态导入），测试编排不变、断言口径更严。

## 新增测试

| 测试类 | 说明 |
| --- | --- |
| `SmsClientPipelineTest`（增强） | 全链路 mock 用例补成功态与 `customSmsId` 非空、非法响应体失败态断言 |
| `B2mManualSendTest`（增强） | 手跑联调用例补真实平台受理成功断言 |

## 向后兼容性

完全兼容：仅新增常量与测试增强，无任何方法签名、异常、装配与配置变化。

## 升级指南

无必做动作；需要按错误码定位配置内容被拒绝的场景时，改用 `ErrorCode.CONFIG_CONTENT_INVALID` 判断。
