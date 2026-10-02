# Smart Cache Jakarta Starter

`smart-cache-jakarta-starter` 为 Spring Boot 3.x 应用提供两级缓存：进程内 L1（Caffeine）和 Redis L2。它支持缓存击穿保护、缓存失效通知、批量读写、异步预刷新与启动预热。

适用于多实例应用需要共享缓存且希望保留本地缓存读性能的场景。

## 版本选择

| 应用运行时 | 使用模块 | 版本 | 是否可同时引入 |
| --- | --- | --- | --- |
| Spring Boot 2.x（javax） | `smart-cache-starter` | `2.2.0` | 否 |
| Spring Boot 3.x（Jakarta） | `smart-cache-jakarta-starter` | `1.0.0` | 否 |

两条线保持相同的包名、配置键、注解和缓存 API。升级到 Spring Boot 3.x 时只替换依赖坐标；同一应用不能同时引入两条线。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-cache-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

L2、跨实例失效、预刷新和预热通过 Redis Route（Redis 多数据源路由组件）执行。启用 L2 或强一致性时，应用还需要启用 `simple-redis-route-jakarta-starter`；Route、Lock 与 Task Retry 已由本模块运行时传递，只有调用方直接使用其 API 时才需显式声明对应坐标。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: cache
            sources:
              cache:
                mode: standalone
                host: localhost
                port: 6379
                database: 0
        lock:
          redis:
            route:
              enable: true
        cache:
          key-prefix: sample-cache
          me: sample-group
          l2:
            enabled: true
          consistency:
            mode: strong
```

`me` 是缓存应用组标识：共享同一 L2 和失效通知的实例必须配置相同值；不同值的实例彼此隔离。

预热竞争实例未取得租约时，默认最多等待 `warm-up.completion-wait-timeout-seconds=60` 秒读取完成标记。该值可按预热 SLA 调整，必须为正数；超时后的 continue 或 fail-fast 行为由 `warm-up.failure-policy` 决定。

## 使用方式

```java
@SmartCacheable(cacheName = "product", key = "#productId")
public Product findProduct(String productId) {
    return loadProduct(productId);
}
```

也可直接注入 `SmartCacheManager` 使用编程式缓存 API。读取 L2 中的业务对象时优先传入具体 `Class<T>`；使用 `Object.class` 时，应通过 `serializer.trusted-packages` 明确允许的 DTO 包前缀，不能使用通配符放宽反序列化边界。

## 行为边界

- L2 默认开启；启用 L2 或 `consistency.mode=strong` 但不存在 `RedisRouteTemplate` 时，应用启动失败，不会静默降级。
- 强一致性通过 Redis Pub/Sub（Redis 的发布订阅通知）让其他实例失效 L1，不是分布式事务，也不提供离线消息重放。
- 缓存击穿、预刷新与预热使用 Redis 分布式锁；业务回调执行期间锁仍可能自然过期，续租失败时组件不再写入共享缓存。
- 不记录缓存值、Redis 认证信息、Token、Authorization 或业务对象到日志和异常消息。

## 运行时边界

- Java 17 或更高版本。
- Spring Boot 3.x、Spring Data Redis 3.x 与 Jakarta Annotation API。
- 应用启用 Lettuce 连接池时，应由宿主提供 `org.apache.commons:commons-pool2` 运行时依赖。
