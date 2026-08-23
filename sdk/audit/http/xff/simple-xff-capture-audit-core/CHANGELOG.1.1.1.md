# simple-xff-capture-audit-core 1.1.1 Changelog

## 发布信息

- 版本：`1.1.1`
- 类型：维护 / 完整 Capture Event 投影
- 基线版本：`1.1.0`

## 主要变更

`XffCaptureAuditDocument` 新增以下不可变原始事实字段：

- `xffRawHeaderList`：保留同名 XFF Header 的原始边界与顺序；
- `xRealIpList`；
- `xForwardedHostList`；
- `xForwardedPortList`；
- `xForwardedProtoList`。

完整构造器用于直接投影 Capture Event。既有构造器仍保留，新增字段使用空列表，保持原有调用方兼容。新字段均完成防御性复制并排除在 `toString()` 之外；它们不参与 XFF IP 分类、客户端身份推断或 Header 回填。

## 未变更边界

- 保留现有 `hostList`、`xffPresent`、`xffRawList`、`xffIpList`、`publicIpList`、应用远端地址、`extensions` 与 `requestData` 契约。
- Provider SPI、线程模型、请求数据采集和外部存储语义不变。
