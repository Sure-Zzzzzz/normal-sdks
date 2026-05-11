# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2026-05-09

### Added

- **滑动窗口算法**：新增 `SmartRedisLimiterSlidingWindowAlgorithm`，基于 Redis ZSET + Lua
  脚本，纳秒级精度，支持多规则复合限流。与固定窗口的核心区别：任意时间段内请求数都不超过阈值，不存在窗口边界突刺问题
- **算法工厂**：新增 `SmartRedisLimiterAlgorithmFactory` 接口和 `DefaultSmartRedisLimiterAlgorithmFactory`
  实现，通过工厂模式统一管理算法实例，支持扩展自定义算法
- **拦截器算法选择**：`SmartInterceptorRule` 新增 `algorithm` 字段，拦截器规则支持按规则独立配置算法（`fixed` / `sliding`
  ），与注解模式保持一致
- **ClientIP 解析**：拦截器模式新增客户端 IP 解析，优先级：`X-Forwarded-For`（多级代理取第一个）→ `X-Real-IP` →
  `getRemoteAddr()`，解析结果写入审计事件
- **`SmartRedisLimiterEventHelper`**：新增公共工具类，提取 Interceptor 和 Aspect 共用的 `buildLimitKey` /
  `serializeLimitRules` 逻辑，消除重复代码
- **测试覆盖**：新增 `SmartRedisLimiterFixedWindowTest`（5 个用例）和 `SmartRedisLimiterSlidingWindowTest`（6
  个用例），覆盖基本限流、窗口重置、并发、Key 结构、边界突刺对比等场景；新增 `SmartRedisLimiterInterceptorAlgorithmTest`（13
  个用例），覆盖拦截器+滑动窗口、混用算法、ClientIP 获取、Redis 异常降级等场景

### Fixed

- **`RuleMatchCacheHelper` 优先级匹配 bug**：修复优先级 2（模式路径 + 精确方法）会错误匹配 `method = null`
  规则的问题，导致精确方法规则被通配规则覆盖。现在优先级 2 只匹配非空 method 规则，优先级 3 只匹配 method 为空的通配规则
- **拦截器 `getRequestUri` 重复**：删除拦截器内部的 `getRequestUri` 方法，统一调用
  `SmartRedisLimiterWebContextHelper.getRequestPath()`，消除两处相同逻辑的维护风险
- **拦截器事件 `algorithm` 字段**：`SmartRedisLimiterInterceptor` 发布审计事件时，`algorithm` 字段现在正确反映实际使用的算法（之前固定为
  `fixed`）

### Changed

- **`SmartRedisLimiterWebContextHelper.getRequestPath`**：从 `private` 改为 `public static`，供拦截器直接调用

### Validation

- **`algorithm` 字段校验**：`SmartRedisLimiterProperties` 启动校验新增对拦截器规则 `algorithm` 字段的合法性检查，配置非法值（如
  `"slide"`）时启动直接报错，不再静默 fallback 到固定窗口

### Dependencies

- `smart-redis-limiter-core` 升级至 `1.1.2`

---

## [1.0.3] - 2026-05-08

### Added

- 事件驱动审计能力：限流触发/通过时发布 `SmartRedisLimiterEvent`，支持与 Kafka、Elasticsearch、数据库等外部系统集成
- `SmartRedisLimiterUserProvider` / `SmartRedisLimiterTraceIdProvider` 接口，支持注入用户信息和链路追踪 ID

## [1.0.2] - 2026-05-08

### Added

- 路径匹配规则缓存（`RuleMatchCacheHelper`），减少重复匹配开销
- 配置验证：启动时校验限流规则合法性，提前暴露配置错误

## [1.0.1] - 2026-05-08

### Added

- 细粒度降级策略：支持规则级别、模式级别、全局级别三层降级策略配置，优先级依次降低

## [1.0.0]

- 初始版本：双模式限流（注解 + 拦截器）、多时间窗口复合规则、固定窗口算法、智能降级（allow/deny）
