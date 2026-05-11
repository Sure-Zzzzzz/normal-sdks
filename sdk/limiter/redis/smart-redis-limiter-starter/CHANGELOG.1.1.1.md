# Changelog

## [1.1.1] - 2026-05-11

### Added

- **限流响应头**：`SmartRedisLimiterInterceptor` 和 `DefaultSmartRedisLimiterExceptionHandler` 在每次限流检查后写入标准响应头：
    - `X-RateLimit-Limit`：时间窗口内的限流阈值
    - `X-RateLimit-Remaining`：当前窗口剩余配额
    - `X-RateLimit-Reset`：窗口重置的 Unix 时间戳（秒）
- **`SmartRedisLimiterResult`**：新增限流结果类，包含 `passed` / `limit` / `remaining` / `resetAt` 四个字段
- **`tryAcquireWithResult()`**：算法接口新增 `tryAcquireWithResult` 方法，返回包含限流详情的 `SmartRedisLimiterResult`
- **`SmartRedisLimitExceededException` 详情扩展**：异常新增 `limit` / `remaining` / `resetAt` 字段，注解模式限流时抛出的异常也携带限流详情，供
  ExceptionHandler 写入响应头

### Refactored

- **提取 `AbstractSmartRedisLimiterAlgorithm` 抽象基类**：固定窗口和滑动窗口两个子类共用的超时控制骨架（FutureTask +
  timeoutScheduler + 异常处理）、init/destroy、buildWindowKey 统一到抽象基类，子类从约 217 行精简到约 85 行
- **Lua 脚本改造**：固定窗口和滑动窗口 Lua 脚本返回值从简单 0/1 改为详细 `[passed, limit, remaining, resetAt]` 数组，供响应头使用

### Changed

- **硬编码清理**：`SmartRedisLimiterSlidingWindowAlgorithm` 中的 `1000000000` 替换为
  `SmartRedisLimiterRedisKeyConstant.NANOSECONDS_PER_SECOND` 引用

### Dependencies

- `smart-redis-limiter-core` 升级至 `1.1.3`
