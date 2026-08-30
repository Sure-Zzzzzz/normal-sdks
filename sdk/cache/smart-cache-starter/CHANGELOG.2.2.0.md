# Changelog - v2.2.0

## [2.2.0] - 2026-08-30

### Changed

- **依赖对齐**：`simple-redis-route-starter` 从 `1.1.0` 升级到 `1.2.2`；`simple-redis-lock-starter` 从 `1.2.1` 升级到 `1.2.2`。缓存装配、L1/L2 通道、序列化信封格式与配置契约零变化。
- **依赖口径收紧**：`simple-redis-route-starter`、`simple-redis-lock-starter`、`task-retry-starter` 从 `api` 改为 `implementation`——L2 通道、击穿防护、预热锁与续期重试均为内部实现，公开缓存 API 不暴露这三者类型；运行时仍随本模块传递（`RedisRouteTemplate` 装配、lock route 联动开关与续期重试照常生效），编译期直接使用 `RedisRouteTemplate`、lock API 或 `TaskRetryExecutor` 的使用方需自行声明对应依赖。
- **文档修正**：README 依赖说明中"使用方按运行环境自行补充 `commons-pool2`"改为如实描述——本模块自 `2.0.0` 起以 `runtimeOnly` 传递 `commons-pool2`，使用方无需额外声明。

### Notes

- **版本号说明**：本次包含编译期依赖可见性变化（`api` 收紧为 `implementation`）与传递依赖行为变化（route 接管模式、lock 模板让位），超出 patch 的"可无脑升级"语义，按语义化版本递增 minor 至 `2.2.0`；模块公开缓存 API 与配置契约零变化，正常运行时行为与开箱即用不受影响。
- **升级迁移**：从 `2.1.0` 及之前版本升级、且编译期直接 import `RedisRouteTemplate`、lock API 或 `TaskRetryExecutor` 的使用方，升级到 `2.2.0` 后需自行声明对应依赖（此前经 `api` 传递进编译类路径，现仅运行时传递）；只注入缓存 API、写 YAML 配置的使用方零动作。
- `simple-redis-lock-starter:1.2.2` 的模板让位语义会传递到本模块使用方：容器中已存在标准 `stringRedisTemplate`（route 接管或 Boot 自动配置）时，lock 不再自建 `simpleRedisLockRedisTemplate`。本模块不注入 lock 自建模板，装配不受影响；使用方若有显式 `@Qualifier("simpleRedisLockRedisTemplate")` 注入需参照 lock 1.2.2 CHANGELOG 调整。
- 测试资源配置清理：删除 5 个测试 yaml 中 route 接管下不生效的 `spring.redis` 死配置段（route `enable=true` 时 Boot Redis 自动配置让位）。负面守卫用例仍保留 `spring.redis` 属性作为 route 关闭场景的功能输入，不受影响。
- 测试：Spring Boot `2.7.9` / `2.4.5` / `2.3.12.RELEASE` / `2.2.13.RELEASE` 四版本全量测试通过，每版本 196 用例——前三个版本 0 失败、0 错误、0 跳过；`2.2.13.RELEASE` 下 5 条 Redis 7 Cluster 锁边界 E2E 用例按基线 assumption 跳过（`SmartCacheMultiDatasourceRouteClusterIntegrationTest`，与 2.1.0 基线一致），其余 191 用例全绿。测试基于真实 Redis 拓扑：本机 standalone 与 Redis `3.2.12` / `5.0.14` / `7.2.6` Cluster。
