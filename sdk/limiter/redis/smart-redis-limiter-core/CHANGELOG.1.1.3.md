# Changelog

## [1.1.3] - 2026-05-11

### Added

- **限流响应头常量**：`SmartRedisLimiterConstant` 新增 `HEADER_X_RATELIMIT_LIMIT`、`HEADER_X_RATELIMIT_REMAINING`、
  `HEADER_X_RATELIMIT_RESET` 三个标准限流响应头常量
- **异常详情扩展**：`SmartRedisLimitExceededException` 新增 `limit`（限流阈值）、`remaining`（剩余配额）、`resetAt`（窗口重置
  Unix 时间戳）字段，支持 ExceptionHandler 写入标准限流响应头；兼容原有两参数构造器
