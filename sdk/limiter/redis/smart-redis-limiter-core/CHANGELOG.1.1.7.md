# Changelog

## [1.1.7] - 2026-06-17

### Added

- **`SmartRedisLimiterConstant.EVENT_KEY_STRATEGY_CUSTOM_PREFIX`**：新增事件审计前缀常量 `"custom:"`。当限流命中由自定义 `SmartRedisLimiterKeyProvider` 生成的 key 时，事件 `keyStrategy` 字段值为 `EVENT_KEY_STRATEGY_CUSTOM_PREFIX + keyProviderName`（例如 `custom:aksClientIdKeyProvider`），便于审计日志识别 key 来源。
- **`SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART`**：新增上下文属性枚举值 `precomputedKeyPart`，用于承载自定义 KeyProvider 预先计算好的 key 片段（拦截器写入，算法层和事件构建器读取），避免重复执行 KeyGenerator 逻辑。

### Notes

- 仅 core 层契约扩展，无破坏性变更，starter 1.1.4 起依赖本版本。
