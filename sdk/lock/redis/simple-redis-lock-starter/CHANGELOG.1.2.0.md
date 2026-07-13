# simple-redis-lock-starter 1.2.0

## 版本定位

`1.2.0` 是结构规范化版本，不新增分布式锁能力，不改变 `1.1.0` 已验证的加锁、解锁、默认单 Redis 模式和 route 模式行为。

## 变更内容

- 规范化主代码结构：新增 `annotation`、`constant`、`exception`、`support` 包。
- 规范化命名：
  - `LockPackage` 调整为 `SimpleRedisLockPackage`。
  - `LockComponent` 调整为 `SimpleRedisLockComponent`。
  - 默认与 route 自动配置类统一使用 `SimpleRedisLock` 模块名前缀。
  - Redis Lua 脚本持有类调整为 `support/RedisLockScriptHelper`。
- 常量治理：配置前缀、route 属性名、Bean name、条件类名统一收敛到 `SimpleRedisLockConstant`。
- 异常治理：新增 `SimpleRedisLockException`、`ConfigurationException`、`ErrorCode`、`ErrorMessage`，route 开启但缺少 `RedisRouteTemplate` 时抛出模块配置异常。
- 测试结构治理：测试启动类调整为 `SimpleRedisLockTestApplication`，测试用例移动到 `io.github.surezzzzzz.sdk.lock.redis.test.cases` 包。
- 文档更新：README 增加版本选型说明，依赖示例升级到 `1.2.0`。

## 兼容性

- `SimpleRedisLock` 包名不变。
- `tryLock` 方法签名不变。
- `unlock` 方法签名不变。
- 配置前缀和配置项不变。
- 默认单 Redis 模式不变。
- route 模式不变。
- `simple-redis-lock-starter:1.2.0` 仍传递引入 `simple-redis-route-starter:1.1.0`。

## 测试要求

发布前需要完成以下验证：

- Spring Boot 2.7.9 全量 `test`。
- Spring Boot 2.4.5 全量 `test`。
- Spring Boot 2.3.12 全量 `test`。
- Spring Boot 2.2.x 全量 `test`。
- 恢复主版本后执行 `compileJava`。
