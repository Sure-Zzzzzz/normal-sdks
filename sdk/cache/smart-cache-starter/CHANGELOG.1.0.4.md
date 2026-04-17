# Changelog - v1.0.4

## [1.0.4] - 2026-04-17

### Fixed

- **多副本 Pub/Sub 消息被错误忽略**：`CacheInvalidationListener` 用 `me`（应用名）作为消息 sender 并以此判断"是否为自己发送"，导致同一应用的多个副本收到彼此的 invalidation 消息后全部忽略，L1 缓存无法被清除。改为在 JVM 启动时生成 UUID 作为实例唯一标识（`instanceId`），sender 和自我判断均使用 `instanceId`，`me` 仅保留用于 channel 命名空间

### Changed

- `CacheInvalidationListener` 收到 evict/clear 消息及发布消息的日志级别由 `TRACE` 提升为 `DEBUG`
- 启动日志新增 `instanceId` 输出，便于多副本场景下追踪消息来源
