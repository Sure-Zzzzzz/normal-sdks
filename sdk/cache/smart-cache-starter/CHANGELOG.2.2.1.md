# Changelog - v2.2.1

## [2.2.1] - 2026-11-27

### Added

- **L2 载荷协议版本**：`SmartCachePayload` 新增 `schemaVersion`。新载荷固定写入 `1`；读取 `2.2.0` 及更早的缺省版本载荷继续兼容；小于 `1` 或大于当前版本的载荷失败关闭。
- **协议版本测试**：覆盖新载荷写入、旧载荷缺省兼容和未知版本拒绝。

### Compatibility

- 公开缓存 API、配置键、Redis Key 格式、TTL、L1/L2 语义和 Pub/Sub 行为不变。
- 只升级 SmartCache 时，存量 L2 JSON 载荷无需清理；首次读取仍按旧协议成功，回写后带 `schemaVersion:1`。
- 宿主自定义 `smartCacheObjectMapper` 与 `SmartCacheTypeValidator` 的覆盖行为不变。

### Validation

- 已使用真实 standalone、Redis 3.2.12 Cluster、Redis 5.0.14 Cluster 和 Redis 7.2.6 Cluster 完成四版本矩阵：2.7.9 / 2.4.5 / 2.3.12 各 `199` 例全绿；2.2.13 为 `199` 例中 5 条 Redis 7 Cluster 锁用例按既有边界明确跳过，无失败。
