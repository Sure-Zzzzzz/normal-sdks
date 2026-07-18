# simple-redis-lock-starter 1.2.1

## 版本定位

`1.2.1` 新增调用方显式控制的 Redis 锁续租能力，解决固定 TTL 无法覆盖长任务的问题；不引入后台 watchdog、自动续租或新的锁模型。

## 变更内容

- 新增 `tryLockWithLease(String lockKey, long leaseTime, TimeUnit timeUnit)`：SDK 内部生成并私有保存 owner token，调用方只获得 `RedisLockLease`。
- 新增 `RedisLockLease`：提供 `renew`、`release` 和 `close`；`close` 委托 `release`，同一租约的释放只执行一次。
- 新增 owner-CAS 续租 Lua：仅当 Redis 当前 owner token 匹配时才执行单 key `PEXPIRE`，旧 owner 无法续租或释放新 owner 的锁。
- route 模式下获取、续租、释放始终使用完全相同的原始 `lockKey` 路由；不回退默认 Redis。
- 新增 `ValidationException` 与参数校验错误码：时间单位为空使用 `VALIDATION_001`，有效租约不足 1 毫秒使用 `VALIDATION_002`；校验失败不会写入 Redis 或执行 `PEXPIRE`。
- 保持旧自定义 `RedisLockExecutor` 的二进制和源码兼容：新增 `renew` Java 8 default 方法。未实现续租的旧执行器会明确提示不支持，不会误报锁失效。

## 升级说明

- 既有 `tryLock` 与 `unlock` 的签名、固定 TTL 语义保持不变；仅在需要业务主动续租时切换到 `tryLockWithLease`。
- `RedisLockLease` 不会自动续租。调用方应在当前 TTL 到期前的业务检查点调用 `renew`，并在返回 `false` 后停止依赖当前锁的互斥语义。
- 使用旧自定义 `RedisLockExecutor` 时，固定 TTL API 无需改造；如需使用 lease API，执行器必须覆写 `renew`。
- `try-with-resources` 可确保 lease 在代码块结束时释放，但不替代业务过程中的主动续租。

## 兼容性

- Java 8、Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 均已验证。
- 不新增 Redisson、重入锁、公平锁、读写锁、线程、调度器或自动续租行为。
- `smart-cache-starter` 本次未升级对 Redis Lock 的依赖，后续独立评估接入。

## 测试

- 默认 Redis：租约显式续租、自然过期、旧 owner 防护、释放幂等、参数边界及并发 renew/release 串行化。
- route Redis：租约获取、续租、释放的物理 datasource 一致性与无默认 datasource 回退。
- Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 单 key 租约场景。
- 模块级 `clean test` 已通过：Spring Boot 2.7.9 → 2.4.5 → 2.3.12 → 2.2.x → 最终 2.7.9。
