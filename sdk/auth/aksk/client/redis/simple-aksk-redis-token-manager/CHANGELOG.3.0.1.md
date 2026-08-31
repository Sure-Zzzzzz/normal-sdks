# Changelog - simple-aksk-redis-token-manager 3.0.1

## 变更概述

对齐 `smart-cache-starter` 2.2.0（`simple-redis-route-starter` 1.2.2 版本线）：升级依赖版本、按自闭环原则补显式依赖声明、移除 lock 接管开关改验让位语义。公开 API、配置键、运行时行为零变化，属 Patch Release。

## 依赖变更

| 依赖 | 旧版本 | 新版本 | 声明方式 |
|------|--------|--------|----------|
| `smart-cache-starter` | 2.1.0 | 2.2.0 | `implementation`（不变） |
| `simple-redis-lock-starter` | （经 cache api 传递） | 1.2.2 | `implementation`（新增显式声明） |
| `task-retry-starter` | （经 cache api 传递） | 2.0.0 | `implementation`（新增显式声明） |
| `simple-redis-route-starter` | （经 cache api 传递） | 1.2.2 | `testImplementation`（新增显式声明） |

## 背景：为什么需要补声明

- cache 2.2.0 将其运行时配套件 `simple-redis-route-starter` / `simple-redis-lock-starter` / `task-retry-starter` 从 `api` 收为 `implementation`，不再覆盖下游编译期。
- 本模块 main 源码直接 import `SimpleRedisLock`（`RedisTokenManager` 构造）与 `TaskRetryExecutor`（自动装配的 `tokenRefreshExecutor` @Bean 参数注入），不补声明即编译失败。
- `RedisRouteTemplate` 仅测试源码 import（断言 route 接管形态），main 零 route 类型引用，故 route 声明为 `testImplementation`。
- 按依赖口径自闭环原则：api/impl 只看自身公开 API 是否暴露类型，本模块公开 API 零暴露三件类型，均取 `implementation`（`@Bean` 参数注入类型非返回类型，`implementation` 足够）。

## 行为说明

- lock 1.2.2 让位语义：容器存在 route 接管的标准 `stringRedisTemplate` 时，lock 不再自建 `simpleRedisLockRedisTemplate`，直接复用接管模板——`lock.redis.route.enable` 配置不再需要（配置残留不报错，仅失效）。
- cache 2.2.0 启动校验（`smartCacheRouteConfigurationValidator`）：容器无 `RedisRouteTemplate` bean 且 `cache.l2.enabled=true` 或 `consistency.mode=strong` 时抛 `CacheConfigurationException` 拒绝启动。本模块默认配置即命中（L2 + strong），使用方必须启用 redis-route 接管。
- 缓存读写、分布式锁键构造、Pub/Sub 一致性、L2 预刷新的既有 3.0.0 行为全部不变。

## 兼容性

- 公开 API（`TokenManager` / `SecurityContextProvider` 契约）、`auth.aksk.client.redis.*` 与 `cache.*` 配置键零变化，业务代码无需修改（使用方 compile classpath 不含新增三件，无传递面变化）。
- 使用方必须启用 `io.github.surezzzzzz.sdk.redis.route` 接管（`enable: true` + 数据源配置）：`spring.redis.*` 直连形态在 L2 / strong 配置下启动失败（cache 2.2.0 启动校验，见上）。
- 使用方可从配置中删除 `lock.redis.route.enable`（让位语义下无意义）。
- `simple-redis-lock-starter` / `task-retry-starter` 随本模块运行时自动传递，使用方无需自加。

## 测试

- Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本矩阵，每版本 41 个测试全绿（真实 Redis + AKSK Server E2E，含并发抢锁、Pub/Sub 双实例、L2 预刷新、多 securityContext 隔离），命令与记录见 [LOCAL_TEST_COMMANDS.md](LOCAL_TEST_COMMANDS.md)。
- 依赖面核对：`dependencies --configuration compileClasspath` 确认 lock 1.2.2 / task-retry 2.0.0 进入编译类路径且零 FAILED；route 1.2.2 仅出现在 `testCompileClasspath`（testImplementation），main 编译类路径零 route 条目。
