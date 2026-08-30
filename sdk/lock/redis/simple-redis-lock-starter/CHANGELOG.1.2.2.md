# simple-redis-lock-starter 1.2.2

## 版本定位

`1.2.2` 修复与 `simple-redis-route-starter` 1.2.x 接管模式的同类型 Bean 冲突：route 接管标准 `stringRedisTemplate` 后，lock 默认分支仍自建 `simpleRedisLockRedisTemplate`，两者同为 `StringRedisTemplate` 类型，任何按类型注入 `StringRedisTemplate` 的使用方都会触发 `NoUniqueBeanDefinitionException`。同时将测试与文档配置示例统一为 redis-route 写法，不再使用 `spring.redis`。

## 变更内容

- `simpleRedisLockRedisTemplate` 的注册条件从按 Bean 名判重（`@ConditionalOnMissingBean(name = ...)`）改为按类型让位（`@ConditionalOnMissingBean(StringRedisTemplate.class)`）：容器已存在任何 `StringRedisTemplate`（redis-route 接管的标准 Bean 或 Boot 自动配置的标准 Bean）时不再自建。
- `SimpleRedisLockConfiguration` 增加 `@AutoConfigureAfter`（`SimpleRedisRouteConfiguration`、Boot `RedisAutoConfiguration`），保证让位条件评估时标准 Bean 已注册。
- `simple-redis-route-starter` 依赖声明从 `1.1.0` 升级到 `1.2.2`（API 兼容，仅对齐接管模式行为）。
- 测试与 README 配置示例统一为 redis-route 写法（`sources` / `default-source` 配置域），默认单 Redis 模式示例不再使用 `spring.redis`。
- 测试依赖按 route README 契约补充 `commons-pool2`（testRuntimeOnly）：route 1.2.x 使用方自带 pool2 运行时依赖，不影响本模块对外传递依赖。

## 升级说明

- route 接管（`io.github.surezzzzz.sdk.redis.route.enable=true`）且 lock 自身 route 开关保持缺省 `false` 时：lock 不再自建模板，执行器参数按类型解析到 route 的标准 `stringRedisTemplate`，锁功能不变。
- 宿主未接管（route 关且 Boot 自动配置生效）：Boot 标准 `stringRedisTemplate` 先注册，lock 同样让位不自建，执行器复用 Boot 的标准 Bean。
- 完全隔离场景（route 关且宿主排除 Boot Redis 自动配置）：lock 自建行为与 1.2.1 一致。
- lock 自身 route 模式（`io.github.surezzzzzz.sdk.lock.redis.route.enable=true`）行为不变：使用 `RouteRedisLockExecutor`。
- 应用侧数据源配置建议统一迁移到 redis-route `sources` 写法；宿主保留自有 `RedisConnectionFactory` 的默认模式仍受支持（按类型让位）。

## 兼容性

- 让位仅影响 Bean 装配来源，锁 API（`tryLock` / `unlock` / `tryLockWithLease`）与语义零变化。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 条件注解与自动配置顺序机制均支持。
- 使用方显式 `@Qualifier("simpleRedisLockRedisTemplate")` 注入自建模板的代码：该 Bean 在标准 Bean 存在时不再注册，此类注入需改为按标准 Bean 名或按类型注入（标准 Bean 名恒为 `stringRedisTemplate`）。

## 测试

- 新增 `SimpleRedisLockRouteTakeOverEndToEndTest`（profile `redis-lock-takeover`）：route 接管 + lock 开关缺省时，验证容器内 `StringRedisTemplate` 唯一、`simpleRedisLockRedisTemplate` 让位不存在、执行器复用标准 Bean、加锁/互斥/解锁语义完整。
- 既有默认模式与 route 模式测试保持不变并全部通过。
- Spring Boot 2.7.9 / 2.4.5 / 2.3.12 / 2.2.x 四版本全量各 36 用例（默认锁语义 14、自动配置边界 7、lock route 双库 7、route 多 Redis 矩阵 6、接管让位 2）均 0 失败 0 错误 0 跳过，已核对 JUnit XML。
