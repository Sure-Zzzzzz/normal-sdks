# Simple Redis Lock Starter

基于 Redis 的分布式锁 Spring Boot Starter，提供默认单 Redis 模式，并支持通过 `simple-redis-route-starter` 按锁 key 路由到不同 Redis datasource。

## 功能特性

- 开箱即用：基于 Spring Boot 自动配置，默认接入项目已有 `RedisConnectionFactory`。
- 安全解锁：使用 Lua 脚本按 `lockValue` 原子校验并删除锁。
- 自动过期：加锁时必须设置过期时间，降低死锁风险。
- route 模式：可选按 `lockKey` 路由到不同 Redis datasource，实现锁流量隔离。
- 故障显性暴露：Redis 命令异常不吞掉，调用方可感知释放失败。
- 多版本验证：覆盖 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9。

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:simple-redis-lock-starter:1.2.0'
}
```

`simple-redis-lock-starter:1.2.0` 会传递引入 `simple-redis-route-starter:1.1.0`。默认不开启 route，仍按单 Redis 模式运行。

| 版本 | 定位 | 说明 |
|------|------|------|
| `1.1.0` | route 接入版本 | 接入 `simple-redis-route-starter:1.1.0`，支持可选 route 模式 |
| `1.2.0` | 结构规范化版本 | 对齐 SDK 包结构、常量、异常和测试规范，锁 API 与运行行为不变 |

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

- `tryLock` 和 `unlock` 都使用同一个 `lockKey` 做 route key。
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
- route 模式下 key 路由到指定 datasource，默认 key 与 lock key 数据隔离。
- route 矩阵环境下 Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 的单 key 锁场景。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 自动配置边界。

## 注意事项

1. 当前实现是非可重入锁，同一个 `lockKey` 重复加锁会失败。
2. `lockValue` 必须唯一并妥善保存，解锁时必须传入同一个值。
3. 过期时间应覆盖正常业务耗时，避免业务未完成锁已过期。
4. `unlock` 返回 `false` 时表示没有释放任何锁，调用方不应按成功处理。
5. route 模式必须保证同一个 `lockKey` 加锁和解锁路由规则一致。
