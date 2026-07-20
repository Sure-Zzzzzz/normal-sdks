# Smart Cache Starter

基于 Spring Boot 的二级缓存组件，提供 L1（Caffeine）与 L2（Redis）协同缓存、缓存击穿防护、失效通知、批量读写、L2 异步续期和启动预热能力。`2.0.0` 的 L2、Pub/Sub、预热元数据和分布式锁全部通过 Redis Route 执行。

## 版本与兼容性

当前版本：`2.0.0`

| 项目 | 支持版本 |
| --- | --- |
| Java | 8 |
| Spring Boot | 2.2.x、2.3.12、2.4.5、2.7.9 |

本版本基于 `javax` 体系实现，不支持 Spring Boot 3、Spring Framework 6 或 Jakarta API。

### 兼容性验证

`2.0.0` 已使用真实 standalone、Redis 3.2.12 Cluster、Redis 5.0.14 Cluster 和 Redis 7.2.6 Cluster 完成全量测试验证：

| Spring Boot | JUnit 结果 | Redis 7 Cluster 锁边界 |
| --- | --- | --- |
| 2.2.x | 178 tests，5 skipped，0 failures，0 errors | Spring Boot 2.2.x 的 Lettuce 5.2.2.RELEASE 无法解析 Redis 7.2.6 Cluster 节点 metadata；仅缓存击穿锁、显式 lease、预刷新锁与启动预热相关的 5 条 E2E 跳过。L2、批量、SCAN、Pub/Sub 及强一致性 `put` / `putAll` 仍在 Redis 3/5 Cluster 实际执行。 |
| 2.3.12 | 178 tests，0 skipped，0 failures，0 errors | Redis 3/5/7 Cluster 全部实际执行。 |
| 2.4.5 | 178 tests，0 skipped，0 failures，0 errors | Redis 3/5/7 Cluster 全部实际执行。 |
| 2.7.9 | 178 tests，0 skipped，0 failures，0 errors | Redis 3/5/7 Cluster 全部实际执行，并完成最终干净回归。 |

`1.x` 已封版，历史使用说明见 [README.1.x.md](README.1.x.md)。

## 核心行为

- L1 使用 Caffeine，L2 将缓存值序列化为 JSON 字符串后通过 `RedisRouteTemplate` 写入 Redis。
- L2、Pub/Sub、预热完成标记和预热 key 列表均按实际 Redis key 路由；生产路径不再创建或使用 `smartCacheRedisTemplate`，也不再使用对象型 `RedisTemplate` 作为 L2 底座。
- 读路径依次查询 L1、L2；缓存未命中时使用 Redis 分布式锁防止击穿，锁不可用时仅回退到当前进程本地锁。
- 缓存击穿、L2 异步预刷新与启动预热均使用显式 lease；业务回调完成、准备写入共享状态前会主动续租一次。续租失败时，缓存击穿仅返回未缓存的计算结果，预刷新丢弃 reload 结果，预热不写缓存数据或完成元数据。
- `put`、`putAll`、`evict`、`clear` 在强一致性模式下发布 L1 失效通知。写入实例保留刚更新的本地 L1；`put` / `putAll` 向其他实例发布逐 key `evict`，其他实例仅删除旧 L1，下一次读取从共享 L2 获取新值。该通知不是分布式读写事务。
- loader 返回 `null` 时仅在 L1 写入短期空值占位，避免将匿名对象写入 L2。
- 注解 `@SmartCacheable`、`@SmartCachePut`、`@SmartCacheEvict` 和编程式 `SmartCacheManager` API 均可使用；`@SmartCacheEvict(beforeInvocation = true)` 会在执行原方法前计算 `condition`，条件只能依赖方法参数，不能依赖尚不存在的返回值。需要从 L2 恢复具体类型时，优先使用带 `Class<T>` 的编程式重载。

## 依赖

### Gradle

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-cache-starter:2.0.0'

    // L1 与注解式 API 由使用方按需提供
    implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
    implementation 'org.springframework.boot:spring-boot-starter-aop'

    // L2、路由、锁和续期重试所需依赖
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'io.github.sure-zzzzzz:simple-redis-route-starter:1.1.0'
    implementation 'io.github.sure-zzzzzz:simple-redis-lock-starter:1.2.1'
    implementation 'io.github.sure-zzzzzz:task-retry-starter:2.0.0'
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.sure-zzzzzz</groupId>
        <artifactId>smart-cache-starter</artifactId>
        <version>2.0.0</version>
    </dependency>

    <!-- L1 与注解式 API 按需提供 -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
        <version>2.9.3</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>

    <!-- L2、路由、锁和续期重试所需依赖 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.sure-zzzzzz</groupId>
        <artifactId>simple-redis-route-starter</artifactId>
        <version>1.1.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.sure-zzzzzz</groupId>
        <artifactId>simple-redis-lock-starter</artifactId>
        <version>1.2.1</version>
    </dependency>
    <dependency>
        <groupId>io.github.sure-zzzzzz</groupId>
        <artifactId>task-retry-starter</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```

`smart-cache-starter` 已传递 `spring-boot-starter-data-redis`、`jackson-datatype-jsr310`、Redis Route、Redis Lock 和 task-retry；上方显式列出相关依赖，便于锁定使用版本。Caffeine 与 AOP 在模块中为 `compileOnly`，使用 L1 或注解式 API 时需要由应用自行提供。

在旧版 Spring Boot 运行时，如 Lettuce 连接工厂需要 Apache Commons Pool 相关类，可按运行环境补充：

```gradle
runtimeOnly 'org.apache.commons:commons-pool2'
```

这只解决旧运行环境的类路径需求；是否启用 Redis 连接池仍由 Spring Boot / Lettuce 的连接池配置决定，添加该依赖本身不会开启连接池。

## 配置

### 最小路由配置

L2 默认开启，启动时必须存在 `RedisRouteTemplate`；强一致性 Pub/Sub 同样依赖 `RedisRouteTemplate`。因此启用缓存的默认配置需要同时启用 Redis Route；只有 `l2.enabled=false` 且 `consistency.mode=eventual` 的 L1-only 组合不要求 Route。以下示例使用中性数据源和中性 key 前缀：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: cache-data
            sources:
              cache-data:
                mode: standalone
                host: localhost
                port: 6379
                database: 0
                timeout-ms: 3000
                connect-timeout-ms: 3000
            rules:
              - pattern: "demo-cache:"
                type: prefix
                datasource: cache-data
                priority: 1
                enable: true
        lock:
          redis:
            route:
              enable: true
        cache:
          enabled: true
          key-prefix: demo-cache
          me: sample-group
          l1:
            enabled: true
            max-size: 10000
            expire-seconds: 300
            refresh-seconds: 270
          l2:
            enabled: true
            expire-seconds: 3600
            ttl-random-offset-ratio: 0.1
            key-format: "{keyPrefix}:{cacheName}:{me}::{key}"
          consistency:
            mode: strong
          pubsub:
            mode: routed
            channel-prefix: cache:pubsub
```

`me` 是同一缓存应用组的标识。共享同一 L2 与失效通知的所有实例必须使用相同的 `me`；不同组使用不同的 `me`，会隔离 L2、缓存击穿锁、预刷新锁、预热 lease、预热完成标记、预热 key 列表和 Pub/Sub channel。

缓存 L2 key 默认格式为 `{keyPrefix}:{cacheName}:{me}::{key}`，同一缓存命名空间自动使用 `{keyPrefix:cacheName:me}` 作为 Redis Cluster hash tag。路由规则还应覆盖缓存锁 key、预刷新锁 key、Pub/Sub 探测 key / channel 以及启用预热时的预热元数据 key。若它们需要路由至不同数据源，请在 Redis Route 中分别配置对应前缀规则；缓存组件不会自行定义数据源或路由规则。

### 常用配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 缓存总开关。 |
| `key-prefix` | `sure-cache` | L2、锁和预热相关 key 的基础前缀。 |
| `me` | `default` | 缓存应用组标识；同组副本必须一致。 |
| `l1.enabled` | `true` | 是否启用 Caffeine L1。L1、L2 都关闭时会自动开启 L1。 |
| `l2.enabled` | `true` | 是否启用路由型 L2；启用时必须有 `RedisRouteTemplate`。 |
| `l2.expire-seconds` | `3600` | L2 基础 TTL（秒）。 |
| `l2.ttl-random-offset-ratio` | `0.1` | L2 TTL 的随机偏移比例，范围为 0 到 1。 |
| `l2.preload.enabled` | `false` | 异步预刷新总开关；关闭后不调用 handler、不查询 TTL、不申请预刷新锁。 |
| `l2.preload.before-expire-seconds` | `300` | 开启预刷新后，L2 剩余 TTL 小于该值时可触发预刷新。 |
| `lock.timeout-seconds` | `30` | 缓存击穿、预刷新和预热 lease 的初始及成功续租时长，范围为 5–300。 |
| `consistency.mode` | `strong` | `strong` 或 `eventual`。 |
| `pubsub.mode` | `routed` | `routed` 或 `disabled`。 |
| `pubsub.channel-prefix` | `cache:pubsub` | Pub/Sub channel 前缀。 |
| `route.scan-enabled` | `false` | 是否允许 `clear` / `size` 对 L2 使用 SCAN。 |
| `route.scan-count` | `100` | SCAN 的 count 提示值。 |

## 一致性与 Pub/Sub

### 强一致性与最终一致性

- `consistency.mode=strong`：`put`、`putAll`、`evict`、`clear` 通过路由型 Redis Pub/Sub 通知同组其他实例清理 L1。写入仅让其他实例精确失效旧 L1；写入实例保留新 L1，其他实例在后续读取时从共享 L2 获取新值。`strong` 必须提供 `RedisRouteTemplate`，且与 `pubsub.mode=disabled` 的组合非法；两者都会在启动时失败，避免以强一致性名义静默运行。
- `consistency.mode=eventual`：不初始化 Pub/Sub 订阅；`pubsub.mode=disabled` 合法。该模式依赖 L1 过期和后续读取收敛，不提供跨实例即时 L1 失效保证。

路由型 Pub/Sub 使用 `{channel-prefix}:{me}:route-probe` 选择一个连接工厂，并在 `{channel-prefix}:{me}:*` 上订阅。实现只有一个监听容器，因而同一 `me` 下的全部 Pub/Sub channel 必须路由到该探测 key 选出的同一个数据源；不支持按 channel 自动创建多数据源订阅容器。发布也经由探测 key 的路由执行。

失效消息处理使用独立的有界线程池（核心 2、最大 4、队列 1000）。队列满时，新到的失效消息会被记录警告并丢弃；应结合实例数、失效峰值和一致性要求评估容量。

`2.0.0` 不再支持 `consistency.pubsub-channel-prefix`。升级时必须将该旧配置迁移为 `pubsub.channel-prefix`；未配置新字段时使用默认值 `cache:pubsub`。

## 批量操作与扫描

### `getAll` / `putAll`

`SmartCacheManager.getAll` 会先批量读取 L1，再将未命中 key 一次性交给 L2；`putAll` 先写 L2 后写 L1。

L2 的 `getAll` 使用单次 `MGET`，`putAll` 使用非事务性的 TTL `SET` pipeline。二者都要求本次参与的最终 Redis key 路由到同一个数据源：组件不会按数据源拆分请求。跨数据源时会抛出路由异常；同一缓存命名空间的 L2 key 自动共享 Redis Cluster hash tag，批量操作可落在同一个槽位。`putAll` 会对整个批次计算一次实际 TTL，空值条目不会写入 L2。

### `clear` / `size`

`route.scan-enabled` 默认 `false`。关闭时，`clear` 只清理 L1 并跳过 L2 扫描，`size` 的 L2 部分返回 0；这是为了避免无意执行高成本 Redis 扫描。

开启后，`clear` 和 `size` 仅在缓存命名空间路由到的单个数据源上执行 `SCAN MATCH`，并受 `route.scan-count` 影响，不会跨数据源汇总。SCAN 仍会遍历目标数据源的 key 空间并造成 Redis 负载，`clear` 的匹配 key 也会逐个删除；仅在确实需要按缓存名称清理或统计且已评估成本时启用。

## 序列化安全边界

L2 使用 JSON 字符串信封保存类型与数据，默认 `ObjectMapper` 注册 Java Time 模块且不启用 Jackson 默认类型。读取时若调用方未提供具体类型而使用 `Object.class`，载荷中的类型名必须经 `SmartCacheTypeValidator` 校验后才会加载。

默认可信包为：

- `java.lang`
- `java.time`
- `java.util`

使用业务对象的 `Object.class` 读取时，需要显式加入中性 DTO 包前缀：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          serializer:
            trusted-packages:
              - java.lang
              - java.time
              - java.util
              - com.example.cache.dto
```

`trusted-packages` 是反序列化安全边界，不应为方便配置而使用 `*`；只有在完全受控的本地测试环境才可考虑该值。应用可通过自定义 `SmartCacheSerializer` 或 `SmartCacheTypeValidator` Bean 覆盖默认实现。

## 异步续期与启动预热

### L2 异步续期

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l2:
            preload:
              enabled: true
              before-expire-seconds: 300
              executor-threads: 4
              executor-queue-capacity: 1024
```

`l2.preload.enabled` 是全部异步预刷新的总开关。关闭时，L2 命中直接返回，既不调用 `CachePreloadHandler`，也不查询 TTL、申请预刷新锁或提交异步任务。开启后，`CachePreloadHandler.needPreload` 的 `Optional.of(true/false)` 可替代 TTL 判断；返回 `Optional.empty()` 时才按剩余 TTL 与预刷新窗口决定。

L2 命中后，若启用状态下的 `CachePreloadHandler` 判断需要续期，或剩余 TTL 落入预刷新窗口，当前请求仍返回旧值；持有 lease 的实例在后台重载。reload 返回非空结果后，只有写入前续租成功才会写回 L2、L1 并发布失效通知；续租返回 `false` 或抛出异常时丢弃 reload 结果。重载使用 `task-retry-starter`，最多重试 6 次，退避比为 1.5。

预刷新线程池是固定大小的有界队列执行器，拒绝策略为 `AbortPolicy`。队列饱和时本次预刷新被跳过并关闭 lease，当前读请求不会失败；应结合重载耗时和峰值访问量设置线程数与队列容量。

### 启动预热

使用 `@SmartCacheWarmUp` 标记返回 `Map<String, Object>` 的方法。组件仅在根 `ContextRefreshedEvent` 执行预热，按 `order` 顺序运行：相同 `order` 并行，不同 `order` 串行。返回 `List` 或其他类型的预热方法会被跳过并记录警告。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          warm-up:
            completion-mark-ttl-seconds: 600
            executor-threads: 4
            executor-queue-capacity: 1024
```

启用 L2 和 Redis 锁时，一个实例获取预热 lease 后执行预热方法；只有在写入前续租成功时才写入 L2/L1、预热完成标记及 key 列表。续租返回 `false` 或抛出异常时丢弃本次预热数据，不发布完成信号；未获取 lease 的实例会等待其他实例的完成标记，再从 L2 回填 L1。缺少 L2 或分布式锁时，预热仅在本地执行。预热线程池同样有界并使用 `AbortPolicy`；线程池饱和会使对应 `order` 的预热失败并抛出 `IllegalStateException`，不会静默丢弃任务。

lease 不是 watchdog：组件不会创建后台线程、调度器或自动续租。loader、reload 和预热方法是不可中断的调用方回调，lease 在回调执行期间仍可能到期并与新 owner 并行；本组件只保证重新获得控制权后，续租失败时不再写入共享缓存或预热元数据。该机制不提供 fencing token、跨实例写入排序或分布式事务，调用方回调需要能接受独立重跑。

## 从 1.x 升级

1. 将依赖升级为 `smart-cache-starter:2.0.0`，并使用 Redis Route `1.1.0`、Redis Lock `1.2.1`、task-retry `2.0.0`。
2. 配置并启用 `io.github.surezzzzzz.sdk.redis.route.enable=true`，同时启用 `io.github.surezzzzzz.sdk.lock.redis.route.enable=true`；为 L2 key、锁 key、Pub/Sub 探测 key / channel 和预热元数据配置匹配的路由规则。
3. 移除对 `smartCacheRedisTemplate`、应用主对象型 `RedisTemplate` 以及 1.x L2 直连路径的依赖；生产 L2 统一使用 Route 原生的 JSON 字符串路径。
4. 将旧的 `l2.key-prefix` 配置迁移为根级 `key-prefix`，将 `consistency.pubsub-channel-prefix` 迁移为 `pubsub.channel-prefix`，并确认同一副本组的 `me` 一致。
5. 检查所有批量 `getAll` / `putAll` 调用，保证一批 key 路由至同一数据源；组件不会自动按数据源分组。
6. 若依赖 `clear` 或 L2 `size`，显式评估 SCAN 成本后再开启 `route.scan-enabled`。
7. 为 `Object.class` 读取涉及的 DTO 添加 `serializer.trusted-packages` 白名单，避免放宽到通配符。
8. 强一致性场景必须保持 `pubsub.mode=routed`；如选择 `eventual`，才可以关闭 Pub/Sub。
9. 清理 1.x 遗留的全部 L2 缓存载荷后再切换流量。2.0.0 使用 JSON 字符串信封，不能读取 1.x 的旧 L2 值，也不提供双格式兼容读取器。

## 许可证

Apache License 2.0
