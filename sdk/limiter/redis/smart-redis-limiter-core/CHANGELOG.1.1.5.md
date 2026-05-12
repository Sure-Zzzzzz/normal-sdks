# Changelog

## [1.1.5] - 2026-05-12

### Changed

- **SmartRedisLimiterConstant**：由 `interface` 改为 `final class`（符合SDK开发规范）
- **SmartRedisLimiterRedisKeyConstant**：由 `interface` 改为 `final class`（符合SDK开发规范）
- **SmartRedisLimiterException**：新增 `@Getter` 注解（符合SDK开发规范）
- **SmartRedisLimiterHttpMethod**：字段 `method` 重命名为 `code`，方法 `fromMethod()` 重命名为 `fromCode()`，新增 `isValid()`、`getAllCodes()`、`toString()` 方法（符合枚举规范）
- **SmartRedisLimiterContextAttribute**：字段 `key` 重命名为 `code`，新增 `fromCode()`、`isValid()`、`getAllCodes()`、`toString()` 方法（符合枚举规范）
- **SmartRedisLimiterKeyStrategy**：新增 `isValid()`、`getAllCodes()`、`toString()` 方法
- **SmartRedisLimiterMode**：新增 `isValid()`、`getAllCodes()`、`toString()` 方法
- **SmartRedisLimiterFallbackStrategy**：新增 `isValid()`、`getAllCodes()`、`toString()` 方法，移除冗余的 `ALLOW_CODE`/`DENY_CODE` 常量

### Added

- **SmartRedisLimiterConstant 新增常量**：
  - 配置相关：`CONFIG_PREFIX`、`DEFAULT_ENABLE`、`MAX_ME_LENGTH`、`DEFAULT_LOG_ON_PASS`、`DEFAULT_INTERCEPTOR_ENABLED`、`DEFAULT_EXCEPTION_HANDLER_ENABLED`、`DEFAULT_COMMAND_TIMEOUT`、`COMMAND_TIMEOUT_MIN_WARNING`、`COMMAND_TIMEOUT_MAX_WARNING`、`MAX_COUNT_WARNING`、`MAX_WINDOW_SECONDS_WARNING`
  - Key 模板：`TEMPLATE_KEY_IP`、`TEMPLATE_KEY_METHOD`、`TEMPLATE_KEY_PATH`、`TEMPLATE_KEY_PATH_WITH_METHOD`、`TEMPLATE_KEY_PATH_PATTERN`、`TEMPLATE_KEY_PATH_PATTERN_WITH_METHOD`
  - 其他：`TEMPLATE_SLIDING_WINDOW_MEMBER`、`RULE_SEPARATOR`、`TEMPLATE_RULE_FORMAT`、`DEFAULT_EXCLUDE_PATTERNS`、`MILLIS_PER_SECOND`
