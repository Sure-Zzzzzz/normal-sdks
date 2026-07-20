# Changelog - v2.0.0

## [2.0.0] - 2026-07-14

### Breaking Changes

- **Redis Route 原生化**：L2、缓存失效 Pub/Sub、启动预热元数据和缓存击穿相关 Redis 操作统一经 `RedisRouteTemplate` 执行；移除 `smartCacheRedisTemplate` 及对象型 `RedisTemplate` 的生产 L2 执行路径。
- **L2 载荷格式变更**：L2 改为 JSON 字符串信封，包含类型与数据；不能读取 1.x 遗留 Redis 值，升级时必须清理旧 L2 载荷。
- **批量路由边界**：`getAll` / `putAll` 不再隐式按数据源拆分；同一批 key 必须路由到单个数据源，跨数据源请求会失败。
- **强一致性启动校验**：`consistency.mode=strong` 与 `pubsub.mode=disabled` 不再允许同时配置，应用启动失败；`eventual + disabled` 仍然允许。

### Fixed

- **强一致性写后失效**：`put`、`putAll` 成功写入 L2 与写入实例 L1 后，向同组其他实例发布逐 key L1 失效通知；其他实例后续从共享 L2 读取新值，空值批量条目不发布失效通知。
- **前置删除条件**：`@SmartCacheEvict(beforeInvocation = true)` 现在会在方法执行前计算 `condition`；条件为 false 时保留缓存，条件为 true 时仍保留“业务方法失败也已删除缓存”的前置语义。前置条件只能依赖方法参数，不能依赖返回值。
- **预刷新总开关**：`l2.preload.enabled=false` 时不再允许 handler 的 `Optional.of(true)` 绕过开关；关闭状态下不调用 handler、不查询 TTL、不申请预刷新锁或提交异步任务。
- **组隔离补齐**：预刷新锁、预热完成标记和预热 key 列表均纳入 `me`；不同缓存应用组共享相同 Redis 与 `key-prefix` 时，不再互相抑制预刷新或读取对方预热状态。
- **Pub/Sub 配置收口**：删除 `consistency.pubsub-channel-prefix`，统一使用 `pubsub.channel-prefix`，不保留 1.x 配置绑定兼容层。

### Changed

- **依赖升级**：新增 `simple-redis-route-starter:1.1.0`，升级 `simple-redis-lock-starter` 至 `1.2.1`、`task-retry-starter` 至 `2.0.0`。
- **显式 lease 检查点**：缓存击穿、L2 异步预刷新和启动预热使用 Lock 1.2.1 的显式 lease；三个路径均复用 `lock.timeout-seconds` 作为初始及成功续租时长，回调完成后、写共享状态前主动续租一次。
- **失租写入抑制**：续租返回 `false` 或抛出异常时，缓存击穿返回未缓存的计算结果，预刷新丢弃 reload 结果且不发布失效通知，预热不写缓存数据、预热 key 列表或完成标记。
- **L2 存储方式**：路由型 L2 使用 JSON 字符串读写；默认序列化器注册 Java Time 模块，不启用 Jackson 默认类型。通过 `Object.class` 恢复载荷类型时，类型必须通过可信包校验。
- **Pub/Sub 路由范围**：订阅连接由 `{channel-prefix}:{me}:route-probe` 选择，同一 `me` 下所有缓存失效 channel 必须路由至该单一数据源；当前实现不按 channel 自动创建多数据源监听容器。
- **批量写入语义**：`putAll` 使用非事务性的 TTL `SET` pipeline，并为整个批次计算一次实际 TTL；空值不写入 L2。
- **Cluster 批量槽位**：同一缓存命名空间的 L2 key 自动共享 Redis Cluster hash tag，`getAll` / `putAll` 可在同一个槽位执行。
- **扫描默认关闭**：`route.scan-enabled` 默认 `false`。关闭时 L2 `clear` 跳过 SCAN、L2 `size` 返回 0；开启后仅在缓存命名空间路由到的单一数据源执行 SCAN；真实 standalone 与 Redis Cluster E2E 已覆盖当前 `me` 的统计、清理及其他组数据保留。
- **异步任务限流**：L2 异步续期和启动预热使用有界执行器。续期饱和时跳过当前续期并释放锁；预热饱和时对应 order 失败，不再静默执行。

### Validation

- **真实 Route E2E**：使用 standalone 与 Redis 3/5/7 Cluster 覆盖 L2、批量 MGET/pipeline、跨数据源拒绝、Pub/Sub、缓存击穿锁、显式 lease、预刷新锁、启动预热及 SCAN。
- **Spring Boot 矩阵**：Spring Boot 2.2.x、2.3.12、2.4.5、2.7.9 均完成全量验证。2.2.x 在 Redis 3/5 Cluster 实际执行 L2、批量、SCAN、Pub/Sub 与强一致性 `put` / `putAll`；仅 5 条实际操作 Redis 7.2.6 Cluster 锁的 E2E 因 Lettuce 5.2.2.RELEASE 兼容边界明确跳过。2.3.12、2.4.5、2.7.9 的 Redis 3/5/7 Cluster 场景全部执行。

### Configuration

- 新增 `pubsub.mode`（`routed` / `disabled`）和 `pubsub.channel-prefix`；`consistency.pubsub-channel-prefix` 已删除，升级时必须迁移。
- 新增 `route.scan-enabled`、`route.scan-count`，控制 L2 `clear` / `size` 的 SCAN 行为与 count 提示值。
- 新增 `serializer.trusted-packages`，默认只信任 `java.lang`、`java.time`、`java.util`；使用 `Object.class` 读取应用 DTO 时需显式加入对应包前缀。
- 新增 `l2.preload.executor-threads`、`l2.preload.executor-queue-capacity`，控制异步续期执行器。
- 新增 `warm-up.executor-threads`、`warm-up.executor-queue-capacity`，控制启动预热执行器。
- `key-prefix` 作为 L2 key 的根级配置；同一副本组必须使用相同 `me`，以共享 L2 和失效通知。

### Migration

- 将依赖升级至 `smart-cache-starter:2.0.0`，并配置 `simple-redis-route-starter:1.1.0`、`simple-redis-lock-starter:1.2.1`、`task-retry-starter:2.0.0`。
- `lock.timeout-seconds` 同时控制缓存击穿、预刷新和预热的初始 lease 与成功续租时长；不新增独立 watchdog、续租频率或后台任务配置。
- 启用 `io.github.surezzzzzz.sdk.redis.route.enable=true` 与 `io.github.surezzzzzz.sdk.lock.redis.route.enable=true`，为缓存 key、锁 key、Pub/Sub 探测 key / channel 和预热元数据配置路由规则。
- 移除对 `smartCacheRedisTemplate`、旧 L2 直连 RedisTemplate、`l2.key-prefix` 以及 `consistency.pubsub-channel-prefix` 的配置或代码依赖，改用 Route 原生 L2 路径、根级 `key-prefix` 和 `pubsub.channel-prefix`。
- 清理所有 1.x L2 缓存载荷后再切换流量；2.0.0 不提供旧载荷兼容读取。
- 检查 `getAll` / `putAll`，确保每批 key 路由至一个数据源；如依赖 `clear` / `size`，评估 SCAN 成本后再开启 `route.scan-enabled`。
- 强一致性场景保持 `pubsub.mode=routed`；仅在最终一致性场景关闭 Pub/Sub。
- 对使用 `Object.class` 的读取路径补齐 `serializer.trusted-packages`，不要为方便而配置不受限的 `*`。
