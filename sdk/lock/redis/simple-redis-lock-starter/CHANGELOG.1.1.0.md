# Changelog

## [1.1.0] - 2026-07-12

### 功能新增

#### 接入 redis-route

- `simple-redis-lock-starter` 通过 `api` 固定引入 `simple-redis-route-starter:1.1.0`。
- 新增 `io.github.surezzzzzz.sdk.lock.redis.route.enable` 开关，默认 `false`。
- 默认模式继续使用单 Redis；开启 route 后按 `lockKey` 通过 `RedisRouteTemplate` 选择 datasource。
- route 模式下不注册 `simpleRedisLockRedisTemplate`，避免强制要求全局 `RedisConnectionFactory`。
- `route.enable=true` 但缺少 `RedisRouteTemplate` 时启动失败，避免锁 key 静默漂移到默认 Redis。

### 行为修正

#### unlock 返回释放结果并显性暴露异常

- `unlock(lockKey, lockValue)` 改为返回 `boolean`。
- `true` 表示当前锁 value 匹配并删除成功。
- `false` 表示锁已过期或持有者不匹配。
- Redis 命令执行异常不再被吞掉，调用方可以感知释放失败。

### 内部重构

- 新增 `RedisLockExecutor` 抽象。
- 新增 `DefaultRedisLockExecutor` 和 `RouteRedisLockExecutor`。
- Lua 解锁脚本下沉到 `RedisLockScripts`，默认模式和 route 模式复用同一份脚本。
- `SimpleRedisLock` 改为构造器注入 `RedisLockExecutor`。
- 自动配置拆分为默认配置、route 配置、route 缺失失败配置。

### 测试验证

- 默认单 Redis 模式覆盖加锁、重复加锁、过期、正确解锁、错误 value 解锁、并发互斥。
- route 模式覆盖 key 路由到 lock/default datasource、route executor 装配、错误 value 解锁。
- 复用 redis-route 的 Docker 矩阵环境，覆盖 Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 下的 lock route 场景。
- 自动配置覆盖默认模式、route 模式、route 缺失失败、业务自定义 `RedisLockExecutor` 退让。
- 已通过 Spring Boot 2.7.9 / 2.4.5 / 2.3.12 / 2.2.x 全量 `test`。

### 升级说明

- 默认单 Redis 使用方式不变。
- 需要 route 时配置 `io.github.surezzzzzz.sdk.lock.redis.route.enable=true`，并启用 redis-route datasource/rule。
- 如果业务代码依赖旧版 `void unlock(...)` 签名，需要改为接收或忽略新的 boolean 返回值。
