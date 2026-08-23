# simple-xff-capture-audit-es-persistence-provider-starter 1.1.1 Changelog

## 发布信息

- 版本：`1.1.1`
- 类型：Feature / 完整 Capture Event Elasticsearch 投影
- 基线版本：`1.1.0`

## 主要变更

- Provider 使用自维护的非 Spring `ObjectMapper` 稳定投影 Audit Document，确保 `xRealIpList` 等缩写字段保持公开契约名称。
- 为 `xffRawHeaderList`、`xRealIpList`、`xForwardedHostList`、`xForwardedPortList`、`xForwardedProtoList` 增加显式 `keyword` mapping。
- 真实 Elasticsearch E2E 覆盖同名 XFF Header 原始边界、五个新增原始 Header 字段的 `_source` 投影、`keyword` mapping 与逐字段 `term` 精确查询。
- README 补充 Legacy Index Template 更新、既有物理日索引 mapping 补齐及不兼容 dynamic 类型的新索引迁移边界。
- 接入示例升级为 Capture Starter `1.1.2`、Audit Listener `1.1.1` 与本 Provider `1.1.1`。

## 未变更边界

- 保持 `xff-capture-audit` 逻辑索引、`eventId` 文档 ID、Route exact rule、物理日索引和 `PersistenceEngine` 同步写入边界。
- 不创建或管理 Elasticsearch Client、模板、物理索引或 Search；测试继续使用部署侧预装的 Legacy Index Template。
