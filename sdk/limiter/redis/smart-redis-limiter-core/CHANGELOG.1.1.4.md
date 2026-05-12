# Changelog

## [1.1.4] - 2026-05-12

### Added

- **SmartRedisLimiterEvent 新增限流详情字段**：`limit`（限流阈值）、`remaining`（剩余配额）、`resetAt`（窗口重置时间 Unix 秒）、`durationNanos`（限流检查耗时纳秒）
- **SmartRedisLimiterRecord 新增限流详情字段**：`limit`、`remaining`、`resetAt`、`durationNanos`，与 Event 对齐
- **SmartRedisLimiterContextAttribute 新增常量**：`DURATION_NANOS`、`FALLBACK`、`FALLBACK_STRATEGY`
