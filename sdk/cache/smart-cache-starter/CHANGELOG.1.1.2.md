# Changelog - v1.1.2

## [1.1.2] - 2026-05-06

### Changed

- **task-retry-starter 升级**：1.0.0 → 1.0.1，修复 `executeWithRetry` 多执行一次的 bug（循环条件 `attempt < retryTimes` 改为 `attempt < totalAttempts`，日志分母也从 `retryTimes` 改为 `totalAttempts`）
