# Changelog

## [1.1.1] - 2026-05-08

### Fixed

- **常量丢失修复**：`SmartRedisLimiterFallbackStrategy` 恢复 `ALLOW_CODE` = "allow" / `DENY_CODE` = "deny" 静态常量，避免在注解中硬编码字符串，与历史版本行为保持一致
