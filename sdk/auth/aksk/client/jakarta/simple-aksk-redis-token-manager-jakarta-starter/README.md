# Simple AKSK Redis Token Manager Jakarta Starter

`simple-aksk-redis-token-manager-jakarta-starter` 为 Spring Boot 3.x 应用提供 AKSK 调用侧的 `TokenManager`。它通过 Client Credentials 获取 Access Token，并用 Caffeine L1、Redis L2、分布式锁和 Pub/Sub 失效控制多实例并发与缓存一致性。

适合需要通过 AKSK 调用受保护接口、且应用已有 Redis 的服务。它不认识 IAM，不推断目标接口权限，也不替代资源服务的入站校验。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-aksk-redis-token-manager` | `3.0.2` |
| Spring Boot 3.x（Jakarta） | `simple-aksk-redis-token-manager-jakarta-starter` | `1.0.0` |

两条线不可同时引入。Jakarta 线要求 Java 17 或更高版本、Spring Boot 3.x 与 Spring Framework 6.x。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-redis-token-manager-jakarta-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-redis-route-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

应用还需配置 Redis Route，并启用 Smart Cache。L2 缓存或强一致模式下缺少 `RedisRouteTemplate` 会在启动期失败，不会静默退化为 `spring.redis.*` 直连。Token Manager 只在 `io.github.surezzzzzz.sdk.auth.aksk.client.enable=true`，且容器已有 `RedisConnectionFactory`、`SmartCacheManager`、`SimpleRedisLock` 与 `TaskRetryExecutor` 时装配。

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

        auth:
          aksk:
            client:
              enable: true
              server-url: https://aksk.example.test
              token-endpoint: /oauth2/token
              client-id: example-client-id
              client-secret: ${AKSK_CLIENT_SECRET}
              redis:
                token:
                  cache-name: aksk-client-token
        cache:
          enabled: true
          key-prefix: my-app
          me: my-aksk-app
          l1:
            enabled: true
            expire-seconds: 2
          l2:
            enabled: true
            preload:
              enabled: true
              before-expire-seconds: 60
          consistency:
            mode: strong
```

`cache.me` 是应用组标识，不是实例标识。同一应用的多个实例必须使用相同值，彼此独立的应用必须使用不同值；它参与 Token 缓存、分布式锁和失效频道隔离。

通过注入 `TokenManager` 获取或清理当前安全上下文的 Token：

```java
private final TokenManager tokenManager;

String accessToken = tokenManager.getToken();
tokenManager.clearToken();
```

AKU 场景可由调用方注册 `SecurityContextProvider` 提供上下文；未提供时使用默认实现。调用方也可注册 `TokenRefreshExecutor` 覆盖默认 Token 获取实现。

## 行为边界

- 首次请求从 AKSK Token 端点换取 Token，后续请求优先命中 L1 或 Redis L2。
- 同一 JVM 的缓存未命中先由固定分片本地锁收敛，再以分布式锁协调多实例；锁不可用或 L2 等待超时仅由当前分片执行刷新。
- `clearToken()` 通过 Smart Cache 失效链路清理当前上下文，强一致配置下会广播 L1 失效。
- Redis TTL 不超过 Token 服务端有效期，正常情况下提前 30 秒失效；有效期不足 30 秒时最多缓存 1 秒。预刷新写回沿用相同规则，不会回退使用全局 L2 TTL。
- 空白 Token、无有效期、Token 获取失败与缓存读写失败都会抛出 `TokenFetchException`，不会返回空 Token。
- Token、Client Secret、安全上下文、缓存键、Authorization、Cookie 和请求响应体不写入本模块日志或异常消息。

目标资源服务最终允许哪些 API，仍由目标服务的 OpenAPI、API 权限和数据权限共同决定；Token Manager 不持有该映射。

## 扩展点

- 未注册 `SecurityContextProvider` 时，使用默认空上下文，适合平台级 Client Credentials。
- AKU 等需要按主体隔离 Token 的场景，调用方注册 `SecurityContextProvider`；返回值仅用于 Token 端点的 `security_context` 和本地缓存隔离，不应包含不必要的敏感字段。
- 调用方可注册 `TokenRefreshExecutor` 覆盖默认换取方式；注册自定义 `TokenManager` 时，Starter 不再创建 `RedisTokenManager`。

## 验证范围

首轮已在 JDK 17 下完成真实 Redis、AKSK Server、并发、L1/L2、预刷新、Pub/Sub 和多安全上下文 E2E，共 49 项测试全绿。Spring Boot 3.2、3.3、3.4 的 JDK 17 编译与 JDK 21 运行兼容矩阵尚未完成，因此不将首轮结果表述为完整 Boot 3.x 矩阵。
