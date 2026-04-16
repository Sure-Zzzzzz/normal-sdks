# Changelog - v1.0.3

## [1.0.3] - 2026-04-16

### Fixed

- **L2 空值序列化失败**：`NULL_PLACEHOLDER` 是匿名内部类，Jackson 无法序列化导致 L2 写入报错。改为空值仅写 L1，不写 L2。L1 短
  TTL 已足够防缓存穿透，L2 不存空值不影响正确性

- **Java 8 时间类型序列化失败**：`smartCacheRedisTemplate` 的 `ObjectMapper` 未注册 `JavaTimeModule`，导致 `Instant`、
  `LocalDateTime` 等类型写入 Redis 报错。现已注册 `JavaTimeModule` 并禁用 `WRITE_DATES_AS_TIMESTAMPS`，时间类型序列化为
  ISO 字符串（如 `"2026-04-16T10:30:00Z"`）

- **Pub/Sub 消息反序列化失败**：`smartCacheRedisTemplate` 的 `ObjectMapper` 未启用 `DefaultTyping`，序列化时不写入
  `@class` 字段，导致 `CacheInvalidationListener` 反序列化时得到 `LinkedHashMap` 而非 `CacheInvalidationMessage`，Pub/Sub
  失效消息无法处理。现已启用 `activateDefaultTyping(NON_FINAL)`

- **`@ActiveProfiles` 场景下 Pub/Sub 不生效**：`RedisMessageListenerContainer` 和 `CacheInvalidationListener` 上的
  `@ConditionalOnProperty` 在 profile-specific 配置文件场景下不可靠，导致 `strong` 模式下 Pub/Sub 组件未被创建。改为去掉
  `@ConditionalOnProperty`，在运行时判断 `consistency.mode` 决定是否启动订阅和发布

- **硬编码一致性模式字符串**：`"strong"`、`"eventual"` 散落在多处代码中。提取为
  `SmartCacheConstant.CONSISTENCY_MODE_STRONG` 和 `SmartCacheConstant.CONSISTENCY_MODE_EVENTUAL`

### Added

- `SmartCacheConstant` 新增 `CONSISTENCY_MODE_STRONG`、`CONSISTENCY_MODE_EVENTUAL` 常量
- `build.gradle` 新增 `api 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'`，使用方无需手动引入 JSR310 模块
