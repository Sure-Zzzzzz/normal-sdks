# Changelog

## [1.1.0]

### ✨ 新增功能

#### 异常体系

新增统一的异常基类，规范化异常体系：

- `SmartRedisLimiterException`：基础异常类，所有业务异常继承它，提供 `errorCode` 字段
- `SmartRedisLimitExceededException`：限流超限异常，继承基础异常类，提供 `key` 和 `retryAfter` 字段

#### 滑动窗口限流支持

为滑动窗口限流功能提供基础支撑：

- `SmartRedisLimiterRedisKeyConstant` 新增滑动窗口相关常量：
  - `SUFFIX_SLIDING_WINDOW = "sw"`：滑动窗口算法标识
- `SmartRedisLimiterConstant` 新增限流算法常量：
  - `ALGORITHM_FIXED = "fixed"`：固定窗口
  - `ALGORITHM_SLIDING = "sliding"`：滑动窗口
  - `ERROR_CODE_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"`：限流超限错误码

#### 事件与记录增强

- `SmartRedisLimiterEvent` 新增 `algorithm` 字段，标识限流算法类型（fixed/sliding）
- `SmartRedisLimiterRecord` 新增 `algorithm` 字段，与 Event 保持一致

### 🔧 重构

#### 常量与枚举统一管理

将以下枚举和常量类统一移入 core 模块，便于 starter 和其他模块复用：

- `SmartRedisLimiterKeyStrategy`：Key 生成策略枚举（method/path/path-pattern/ip）
- `SmartRedisLimiterMode`：限流模式枚举（annotation/interceptor/both）
- `SmartRedisLimiterFallbackStrategy`：降级策略枚举（allow/deny）
- `SmartRedisLimiterContextAttribute`：上下文属性枚举
- `SmartRedisLimiterHttpMethod`：HTTP 方法枚举
- `SmartRedisLimiterRedisKeyConstant`：Redis Key 常量（Key前缀、分隔符、时间单位后缀、滑动窗口标识等）

### 📝 兼容性

完全向后兼容 1.0.1 版本，无破坏性变更。
