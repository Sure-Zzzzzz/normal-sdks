# Changelog

## [1.1.3] - 2026-05-12

### Changed

- **核心依赖升级**：`smart-redis-limiter-core` 从 `1.1.4` 升级至 `1.1.6`

### Code Quality

- **SmartRedisLimiterConstant / SmartRedisLimiterRedisKeyConstant**：由 `interface` 改为 `final class`（符合 SDK 开发规范）
- **SmartRedisLimiterProperties**：内部类使用 `@Data` 替代 `@Getter @Setter`，移除手动 `toString()`
- **SmartRedisLimiterException**：新增 `@Getter` 注解（符合 SDK 开发规范）
- **Key 生成器**（`SmartRedisLimiterIpKeyGenerator` 等）：硬编码字符串拼接替换为模板常量 `String.format()`，消除 magic string
- **滑动窗口/固定窗口算法**：除法常量 `/1000` 替换为 `/ MILLIS_PER_SECOND`，前缀硬编码替换为 `CONFIG_PREFIX`
- **SmartRedisLimiterContext**：`.getKey()` 统一替换为 `.getCode()`，完整重写并补充 Javadoc
- **SmartRedisLimiterRuleMatchCacheHelper**：`fromMethod()` 替换为 `fromCode()`，前缀硬编码替换为 `CONFIG_PREFIX`
- **SmartRedisLimiterAspect / SmartRedisLimiterInterceptor**：补充完整类 Javadoc 和字段注释，`CONFIG_PREFIX` 替换硬编码前缀
- **`@ConditionalOnProperty`**：所有注解前缀统一使用 `SmartRedisLimiterConstant.CONFIG_PREFIX`
- **`logOnPass`**：从 `AuditConfig` 内配置提升至顶层配置项，`application.yaml` 中 `audit.log-on-pass` 替换为 `log-on-pass`

### Fixed

- **TestService.java**：恢复使用 `ALLOW_CODE`/`DENY_CODE` 常量，避免注解中硬编码字符串
