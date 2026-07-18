# Simple Redis Lock Starter

基于 Redis 的分布式锁 Spring Boot Starter，提供默认单 Redis 模式，并支持通过 `simple-redis-route-starter` 按锁 key 路由到不同 Redis datasource。

## 功能特性

- 开箱即用：基于 Spring Boot 自动配置，默认接入项目已有 `RedisConnectionFactory`。
- 安全解锁：使用 Lua 脚本按 `lockValue` 原子校验并删除锁。
- 自动过期：加锁时必须设置过期时间，降低死锁风险。
- 显式租约：调用方可获取 `RedisLockLease`，按需续租并安全释放，无后台 watchdog。
- route 模式：可选按 `lockKey` 路由到不同 Redis datasource，实现锁流量隔离。
- 故障显性暴露：Redis 命令异常不吞掉，调用方可感知释放失败。
- 多版本验证：覆盖 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9。

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:simple-redis-lock-starter:1.2.1'
}
```

`simple-redis-lock-starter:1.2.1` 会传递引入 `simple-redis-route-starter:1.1.0`。默认不开启 route，仍按单 Redis 模式运行。

| 版本 | 定位 | 说明 |
|------|------|------|
| `1.1.0` | route 接入版本 | 接入 `simple-redis-route-starter:1.1.0`，支持可选 route 模式 |
| `1.2.0` | 结构规范化版本 | 对齐 SDK 包结构、常量、异常和测试规范，锁 API 与运行行为不变 |
| `1.2.1` | 显式租约版本 | 新增调用方显式续租的 lease API，保持旧锁 API 兼容 |

### 2. 默认单 Redis 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 2000ms
```

### 3. 使用分布式锁

```java
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ResourceService {

    private final SimpleRedisLock simpleRedisLock;

    public ResourceService(SimpleRedisLock simpleRedisLock) {
        this.simpleRedisLock = simpleRedisLock;
    }

    public void process(String resourceId) {
        String lockKey = "lock:resource:" + resourceId;
        String lockValue = UUID.randomUUID().toString();

        if (!simpleRedisLock.tryLock(lockKey, lockValue, 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("资源正在处理中");
        }

        try {
            doBusiness(resourceId);
        } finally {
            simpleRedisLock.unlock(lockKey, lockValue);
        }
    }
}
```

## API

```java
boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit)
```

| 参数 | 说明 |
|------|------|
| `lockKey` | 锁 key，同一个资源必须使用同一个 key |
| `lockValue` | 锁持有者标识，解锁时必须传入同一个值 |
| `expireTime` | 过期时间 |
| `timeUnit` | 过期时间单位 |

返回 `true` 表示加锁成功，返回 `false` 表示锁已被持有。

```java
boolean unlock(String lockKey, String lockValue)
```

只有 Redis 中当前 value 与传入 `lockValue` 匹配时才删除 key。返回 `true` 表示释放成功，返回 `false` 表示锁已过期或持有者不匹配；Redis 命令执行异常会向外抛出。

### 显式租约

```java
Optional<RedisLockLease> tryLockWithLease(String lockKey, long leaseTime, TimeUnit timeUnit)
```

成功时返回由 SDK 管理 owner token 的租约句柄；锁已被持有时返回 `Optional.empty()`。owner token 不提供构造参数、getter、日志或 `toString` 暴露口子。

```java
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public void processWithLease(String resourceId) {
    String lockKey = "lock:resource:" + resourceId;
    Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
            lockKey, 30, TimeUnit.SECONDS);
    if (!optionalLease.isPresent()) {
        throw new IllegalStateException("资源正在处理中");
    }

    try (RedisLockLease lease = optionalLease.get()) {
        processFirstStep(resourceId);
        if (!lease.renew(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("锁租约已失效");
        }
        processSecondStep(resourceId);
    }
}
```

- `renew` 使用 owner-CAS Lua：仅当前 owner 能更新同一 key 的 TTL。请在当前 TTL 到期前、业务检查点之间主动调用；返回 `false` 表示锁已过期、已被释放或 owner 已改变，调用方不得继续假定自己持有锁。
- `release` 使用 owner-CAS 删除；`close` 委托 `release`，重复调用不会重复执行解锁。`try-with-resources` 仅负责释放，不能替代业务过程中的主动续租。
- 未调用 `renew` 时，租约会按初始 TTL 自然过期；starter 不创建线程、调度器或自动续租 watchdog。
- `leaseTime` 换算后必须至少为 1 毫秒，且 `timeUnit` 不能为空。未释放租约的获取或续租参数不合法时抛出 `ValidationException`：`VALIDATION_001` 表示时间单位为空，`VALIDATION_002` 表示租约时长不足 1 毫秒。已释放句柄的 `renew` 直接返回 `false`，不再校验参数或访问 Redis。
- `RedisLockExecutor` 的旧自定义实现仍可用于固定租约；若未覆写新增的 `renew`，调用显式续租会明确抛出“不支持租约续租”异常，不会伪装为锁失效。

## route 模式

当锁流量需要与默认 Redis 隔离，或不同锁域需要落到不同 Redis 时，可以开启 lock route。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        lock:
          redis:
            route:
              enable: true
        redis:
          route:
            enable: true
            default-datasource: default
            datasources:
              default:
                host: localhost
                port: 6379
                database: 0
              lock:
                host: localhost
                port: 6379
                database: 1
            rules:
              - pattern: "lock:"
                type: prefix
                datasource: lock
                priority: 1
```

开启后：

- `tryLock`、`tryLockWithLease`、`renew` 和 `release` 都使用完全相同的原始 `lockKey` 做 route key。
- 命中 `lock:` 前缀的锁会路由到 `lock` datasource。
- 未命中规则的锁走 route 默认 datasource。
- SDK 不再注册 `simpleRedisLockRedisTemplate`，避免 route-only 项目被迫提供全局 `RedisConnectionFactory`。
- 如果 `lock.redis.route.enable=true` 但缺少 `RedisRouteTemplate`，启动会失败并明确提示配置 route。

## Cluster 语义

当前锁脚本只操作单个 key：

```text
KEYS[1] = lockKey
ARGV[1] = lockValue
```

因此 Redis Cluster 下不涉及多 key cross-slot。SDK 不修改业务传入的 `lockKey`，如果业务需要 hash tag，应直接在锁 key 中声明。

## 自动配置边界

- 默认模式：注册 `simpleRedisLockRedisTemplate` 和 `DefaultRedisLockExecutor`。
- route 模式：存在 `RedisRouteTemplate` 时注册 `RouteRedisLockExecutor`。
- 业务自定义 `RedisLockExecutor` 时，SDK 默认 executor、route executor、失败型 executor 都会退让。
- route 模式不会回退默认 Redis，避免同一个锁 key 在不同 Redis 间漂移导致互斥失效。

## 测试

本模块测试依赖真实 Redis，不使用 embedded-redis。

```bash
./gradlew :sdk:lock:redis:simple-redis-lock-starter:test
```

测试覆盖：

- 默认单 Redis 模式加锁、重复加锁、过期、解锁与并发互斥。
- 显式租约的续租、自然过期、旧 owner 防护、release/close 幂等、无效租约参数与 renew/release 并发串行化。
- route 模式下租约获取、续租、释放均落在同一目标 datasource，且不存在默认 Redis 回退。
- route 矩阵环境下 Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 的单 key 租约场景。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 自动配置边界。

## 注意事项

1. 当前实现是非可重入锁，同一个 `lockKey` 重复加锁会失败。
2. `lockValue` 必须唯一并妥善保存，解锁时必须传入同一个值。
3. 过期时间应覆盖正常业务耗时，避免业务未完成锁已过期。
4. `unlock` 返回 `false` 时表示没有释放任何锁，调用方不应按成功处理。
5. `renew` 返回 `false` 后，调用方不得继续假定自己持有锁。
6. route 模式必须保证同一个 `lockKey` 的获取、续租和释放路由规则一致。
