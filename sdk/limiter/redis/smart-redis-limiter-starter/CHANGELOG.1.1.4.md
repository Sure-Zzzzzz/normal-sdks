# Changelog

## [1.1.4] - 2026-06-17

### Added

- **SmartRedisLimiterKeyProvider 扩展点**：新增 `SmartRedisLimiterKeyProvider` 函数式接口，支持业务方从 `HttpServletRequest`、`SmartRedisLimiterContext` 或外部上下文（如 SecurityContextHolder、ThreadLocal）提取自定义限流 key。
- **拦截器规则新增 `key-provider` 配置**：`SmartInterceptorRule` 新增 `keyProvider` 字段，优先级高于 `keyStrategy`。配置 `key-provider` 后，限流 key 优先由对应 Bean 生成；未配置时保持原有 `key-strategy` 行为。
- **启动期 KeyProvider 校验**：拦截器初始化时校验并缓存所有 rule 引用的 KeyProvider Bean。配置了 `key-provider` 但 Bean 不存在或类型不匹配时启动失败，避免运行期请求才暴露问题。
- **自定义 key 审计标识**：KeyProvider 命中时，限流事件 `keyStrategy` 字段记录为 `custom:` + `keyProviderName`，便于审计日志识别实际 key 来源。
- **KeyProvider 端到端测试覆盖**：新增 KeyProvider 集成测试和启动校验测试，覆盖 provider 命中、null/空字符串回退、异常 fallback=allow/deny、keyProvider 优先级、事件审计字段、启动失败、缓存去重等场景。

### Changed

- **算法与事件构建复用预计算 key**：通过 core 1.1.7 的 `SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART` 在拦截器、算法层、事件构建器之间传递 KeyProvider 生成的 key 片段，保证 Redis 实际限流 key 与事件 `limitKey` 一致。
- **依赖升级**：`smart-redis-limiter-core` 从 `1.1.6` 升级到 `1.1.7`，使用新增的 `EVENT_KEY_STRATEGY_CUSTOM_PREFIX` 与 `PRECOMPUTED_KEY_PART` 公共契约。

### Behavior

- 业务方不配置 `key-provider`：保持原行为，继续按 `rule.key-strategy` 或 `interceptor.default-key-strategy` 生成限流 key。
- 业务方配置 `key-provider` 且 provider 返回非空字符串：使用 provider 返回值作为限流 key 片段。
- provider 返回 `null` 或空字符串：回退到 `keyStrategy`。
- provider 抛异常：不回退到 `keyStrategy`，按 fallback 策略处理；`allow` 直接放行且不计数，`deny` 直接拒绝（429）。
