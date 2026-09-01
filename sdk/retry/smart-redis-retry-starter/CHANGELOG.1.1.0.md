# CHANGELOG 1.1.0

类型：Dependency Alignment

## 依赖升级

| 依赖 | 原版本 | 新版本 | 口径变化 |
|---|---|---|---|
| simple-redis-route-starter | 1.1.0 | 1.2.2 | `api` → `implementation` |

## 变更内容

- `simple-redis-route-starter` 从 `1.1.0` 升级到 `1.2.2`，对齐 route 1.2.x 版本线。本模块重试决策、Lua 脚本、Redis Key 语义、策略优先级与配置契约零变化。
- **依赖口径收紧**（按模块自闭环口径）：route 收为 `implementation`——公开接口（`SmartRedisRetryEngine` / facade / policy / model）零暴露 route 类型，`RedisRouteTemplate` 仅在 `DefaultSmartRedisRetryEngine` 实现与自动配置装配内部使用；运行时仍随本模块传递，开箱即用不变。
- 源码零改动：本模块不自建任何 RedisTemplate / connectionFactory，route 1.2.x 接管语义下无 Bean 冲突面，无让位适配需求。
- 测试依赖补充 `commons-pool2`（testRuntimeOnly）：route 1.2.x 自动配置类加载即需要 `GenericObjectPoolConfig`，与 yaml 是否启用连接池无关；仅本模块测试类路径，不影响传递依赖。

## 版本号说明

本次包含编译期依赖可见性变化（route `api` 收紧）与传递行为变化（route 1.2.x 接管模式），超出 patch 的"可无脑升级"语义，按语义化版本递增 minor 至 `1.1.0`；重试公开 API 与配置契约零变化。

## 升级迁移

- 只使用 `SmartRedisRetryEngine` / facade、写 YAML 配置、catch 本模块异常的使用方零动作。
- 编译期直接注入 `RedisRouteTemplate` 等 route API 的使用方需自行声明 `simple-redis-route-starter`（此前经 `api` 传递进编译类路径，现仅运行时传递）。
- 应用若启用 `lettuce.pool.enabled=true`，按 `simple-redis-route-starter` README 自行提供 `commons-pool2` 运行时依赖。

## 兼容性

- route 1.1.0 → 1.2.2 对本模块使用的 API（`RedisRouteTemplate` 执行通道）完全兼容。
- route 1.2.x 接管语义会传递到本模块使用方：`redis.route.enable=true` 时 route 接管标准 `stringRedisTemplate`、Boot Redis 自动配置让位——存量应用的 `spring.redis.*` 连接配置不再生效，需迁移到 route 数据源配置（本模块自 1.0.0 起即基于 route 多数据源通路，重试执行不受影响）。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 兼容矩阵不变；2.2.x 默认 Lettuce 对 Redis 7 Cluster 的既有探测边界（`known=false`）保持。

## 测试

- 四版本矩阵（SDK starter 基线）：Spring Boot `2.7.9`（Gradle 8.5 + Zulu 11）/ `2.4.5` / `2.3.12.RELEASE` / `2.2.13.RELEASE`（三低档 Gradle 7.6 + JDK 8），每版本 `--rerun-tasks --no-daemon` 全量重跑，11 个测试类 82 个用例全部通过（0 failures / 0 errors / 0 skipped），已核对 JUnit XML 计数。
- 含真实 Docker Redis 矩阵 E2E（复用 route 的 redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 六 datasource）：recordFailure / decide / clear / scan / cluster 多页聚合 / cursor 越界 / 多集群业务路由与分区隔离；2.2.13 默认 Lettuce 对 Redis 7 Cluster 的 `known=false` 探测边界断言保持。
- 测试依赖补 `commons-pool2`（testRuntimeOnly）：route 1.2.x 自动配置类加载需要 `GenericObjectPoolConfig`，与 yaml 是否启用连接池无关。
