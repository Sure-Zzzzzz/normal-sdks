# Simple Redis Lock Jakarta Starter

`simple-redis-lock-jakarta-starter` 为 Spring Boot 3.x 应用提供基于 Redis 的分布式锁。它可使用宿主已有的标准 Redis 连接；启用 Redis Route（Redis 多数据源路由组件）后，也可按锁 key 将锁隔离到不同数据源。

适用于需要互斥执行、避免并发重复处理的场景。

## 版本选择

| 应用运行时 | 使用模块 | 版本 | 是否可同时引入 |
| --- | --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-redis-lock-starter` | `1.2.2` | 否 |
| Spring Boot 3.x（Jakarta） | `simple-redis-lock-jakarta-starter` | `1.0.0` | 否 |

两条线保持相同的包名、配置键和锁 API。升级到 Spring Boot 3.x 时只替换依赖坐标；同一应用不能同时引入两条线。

## 已验证版本

以下组合均执行完整测试集，使用真实 Redis 及 Redis 3/5/7 standalone、Cluster 路由场景。每轮为 `37` 项测试，`0` failure、`0` error、`0` skipped：

| Spring Boot | JDK 17 | JDK 21 |
| --- | --- | --- |
| `3.4.2` | 通过（最终基线） | 通过 |
| `3.3.13` | 通过 | 通过 |
| `3.2.12` | 通过 | 通过 |

## 最小接入

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'io.github.sure-zzzzzz:simple-redis-lock-jakarta-starter:1.0.0'
}
```

宿主提供 Spring Data Redis；Lock 传递依赖 `simple-redis-route-jakarta-starter:1.0.0`，无需重复声明 Route。启用 Route 连接池时，宿主还需提供 `org.apache.commons:commons-pool2` 运行时依赖。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: localhost
                port: 6379
                database: 0
                timeout-ms: 3000
                connect-timeout-ms: 3000
```

## 使用锁

```java
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public void process(String resourceId, SimpleRedisLock simpleRedisLock) {
    String lockKey = "lock:resource:" + resourceId;
    String lockValue = UUID.randomUUID().toString();

    if (!simpleRedisLock.tryLock(lockKey, lockValue, 30, TimeUnit.SECONDS)) {
        return;
    }
    try {
        processResource(resourceId);
    } finally {
        simpleRedisLock.unlock(lockKey, lockValue);
    }
}
```

`tryLock` 返回 `false` 表示锁已被其他持有者占用。`unlock` 通过 Redis Lua 脚本（由 Redis 服务端原子执行的脚本）确认 `lockValue` 与当前持有者匹配后才会删除 key；Redis 命令异常会向调用方抛出，不会被吞掉。

## 显式租约

需要在长任务中主动续租时，使用 `RedisLockLease`。SDK 自行生成并保管持有者标识，调用方无需传递或记录该值。

```java
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public void processWithLease(String resourceId, SimpleRedisLock simpleRedisLock) {
    Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
            "lock:resource:" + resourceId, 30, TimeUnit.SECONDS);
    if (!optionalLease.isPresent()) {
        return;
    }

    try (RedisLockLease lease = optionalLease.get()) {
        processFirstStep(resourceId);
        if (!lease.renew(30, TimeUnit.SECONDS)) {
            return;
        }
        processSecondStep(resourceId);
    }
}
```

- 未主动续租时，租约按初始有效期自然过期；starter 不创建后台线程或自动续租任务。
- `renew` 返回 `false` 时，锁已过期、已释放或持有者已改变，调用方不得继续假定自己仍持有锁。
- `release` 与 `close` 在 Redis 解锁调用正常返回后终结租约，后续不再执行释放；Redis 命令抛出异常时租约保持可用，调用方可以续租或重试释放。
- `try-with-resources` 负责退出时释放，不能替代业务过程中的主动续租。
- 未释放的租约调用 `renew` 时，时间单位不能为空且换算后必须至少为 1 毫秒。

## Route 模式

启用 Lock 的 route 开关后，锁使用 Redis Route 的 `default-source`。需要将特定锁域路由到独立 Redis 数据源时，配置 Route 规则：

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
            default-source: default
            sources:
              default:
                mode: standalone
                host: localhost
                port: 6379
                database: 0
              lock:
                mode: standalone
                host: localhost
                port: 6379
                database: 1
            rules:
              - pattern: "lock:"
                type: prefix
                datasource: lock
                priority: 1
```

同一 `lockKey` 的获取、续租与释放始终使用相同的 Route key，因此会命中同一数据源。`lock.redis.route.enable=true` 但没有 `RedisRouteTemplate` 时，应用启动会失败，不会静默回退到默认 Redis。

## 运行时边界

- Java 17 或更高版本。
- Spring Boot 3.x 与 Spring Data Redis 3.x。
- Lock 只执行单 key 操作。Redis Cluster（Redis 集群）下如业务自身需要多 key 或 Lua 操作，应在业务 lock key 中使用 hash tag。
- Redis 密码、认证信息、锁 key、锁持有者标识和业务数据不得写入配置仓库、日志或异常消息。
