# Changelog - v2.1.0

## [2.1.0] - 2026-07-20

### Fixed

- **批量负缓存边界**：`SmartCacheManager.getAll` 不再向业务返回 L1 内部空值占位；负缓存仍作为 L1 命中，且不会被重复查询 L2。
- **强一致性失效背压**：本地失效处理队列饱和时，由 Redis listener 回调线程同步处理已送达 JVM 的消息，不再由本地队列主动丢弃。

### Added

- **预热失败策略**：新增 `warm-up.failure-policy`。默认 `continue` 记录单任务失败并继续后续预热；`fail-fast`
  在方法调用、返回类型、lease、预热元数据、L1 回填、等待完成标记或执行器提交失败时，以 `CacheWarmUpException`（
  `SMART_CACHE_009`）阻断启动。

### Changed

- **预热返回契约**：`null` 或空 Map 表示无数据预热；非 Map 返回视为预热失败。
- **关闭阶段边界**：失效监听器关闭后跳过新到消息，避免与 L1 销毁竞争。

### Notes

- Redis Pub/Sub 不提供离线重放、持久化或 exactly-once 承诺；本版本仅消除消息已送达 JVM 后由本地有界队列造成的主动丢弃。
- `fail-fast` 不会中断同一 order 中已经开始的调用方预热回调，只保证当前 order 完成后不再进入后续 order。
