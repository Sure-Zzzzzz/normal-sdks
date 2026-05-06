# Changelog - v1.0.1

## [1.0.1] - 2026-05-06

### Fixed

- **executeWithRetry 多执行一次**：循环条件 `attempt <= totalAttempts`（totalAttempts = retryTimes + 1），但内部判断用的是 `attempt < retryTimes`，导致最后一次 retry 后还会多跑一次，日志出现 "Attempt N+1/N failed, no more retries"。改为 `attempt < totalAttempts`，同时日志中的分母也从 `retryTimes` 改为 `totalAttempts`，语义更清晰（如 "Attempt 7/7 failed, no more retries" 表示共 7 次全部失败）
