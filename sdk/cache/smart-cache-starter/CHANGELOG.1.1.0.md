# Changelog - v1.1.0

## [1.1.0] - 2026-05-05

### Added

- **L2 异步续期（Preload）**：新增 `CachePreloadHandler` 接口，业务侧实现并注册为 Spring Bean 后，当 L2 条目剩余 TTL 进入预刷新窗口时，框架异步调用 `reload()` 提前续期，当前请求返回旧值不阻塞，续期完成后写回 L2 和 L1，提供容错窗口
  - `needPreload()` 默认返回 `Optional.empty()`，框架查 L2 TTL 决定是否触发；业务侧可覆盖返回 `Optional.of(true/false)` 完全替代 TTL 查询，避免额外 Redis IO
  - 使用分布式锁防止多实例重复触发同一 key 的 preload
  - 失败时使用指数退避重试（默认 6 次，初始间隔由 `before-expire-seconds` 反推，保证总等待时间不超过预刷新窗口）
  - `reload()` 全部失败时旧值仍可返回（容错窗口内 L2 key 尚未过期）
  - 无 handler 注册时静默跳过，不影响现有行为
- **`L2Cache.getTtl()`**：新增查询 L2 条目剩余 TTL 的方法，供 preload 判断使用
- **新配置项**：
  ```yaml
  io.github.surezzzzzz.sdk.cache.l2.preload:
    enabled: false              # 是否启用异步续期，默认关闭
    before-expire-seconds: 300  # 提前多少秒触发，需 < l2.expire-seconds
  ```

### Changed

- **包重构**：`cache.cache` 子包改为 `cache.layer`（`L1Cache`、`L2Cache` 移入 `layer` 包），原包名与根包同名，语义不清晰
- **包重构**：`SmartCacheAspect` 从 `cache.support` 移入新建的 `cache.aspect` 包，职责分离更清晰
- **`SmartCacheManager`**：`get(cacheName, key)` 无 loader 重载补充 preload 检查，与 `get(cacheName, key, loader)` 行为对齐；提取 `handleL2Hit()` 私有方法消除三处 L2 命中路径的重复代码
- **`L2Cache`**：提取 `getKeyPrefix()`、`getMe()`、`getKeyFormat()` 私有方法，消除 `buildKey()` 与 `buildKeyPattern()` 中重复的 properties 取值逻辑
- **`SpELExpressionHelper`**：移除 `createEvaluationContext()` 中无意义的 `withRootObject()` 循环调用（每次调用都覆盖上一次，最终只有最后一个参数生效，而参数实际通过 `setVariable()` 绑定）
- **`SmartCacheProperties`**：`l1.refreshSeconds` 调整逻辑中的硬编码 `30` 提取为常量 `L1_REFRESH_EXPIRE_BUFFER_SECONDS`
- **`SmartCacheConstant`**：删除已无引用的冗余常量 `WARMUP_COMPLETE_MARK_TTL_SECONDS`（与 `DEFAULT_WARMUP_COMPLETION_MARK_TTL_SECONDS` 语义重叠）；新增 `L1_REFRESH_EXPIRE_BUFFER_SECONDS`、`PRELOAD_LOCK_KEY_SUFFIX`、`PRELOAD_MAX_RETRIES`、`PRELOAD_RETRY_BACKOFF_RATIO`
- **`SmartCacheWarmUpProcessor`**：日志统一为英文
- **`SmartCacheAspect`**：异常消息中的中文 `原始异常` 改为英文 `caused by`

### Fixed

- **`SmartCacheAspect`**：异常包装时 `SmartCacheException` 的 import 使用全限定名，改为正常 import，代码更整洁

### Breaking Changes

- `cache.cache` 包已删除，`L1Cache`、`L2Cache` 移至 `cache.layer`，接入方需更新 import
- `cache.support.SmartCacheAspect` 移至 `cache.aspect.SmartCacheAspect`，直接引用该类的接入方需更新 import（通过注解使用不受影响）
