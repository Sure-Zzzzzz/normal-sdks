# smart-redis-limiter-jakarta-starter

面向 Spring Boot 3 应用的 Redis 限流运行端。调用方可用方法注解或 Spring MVC 拦截器声明限额；模块通过 Redis Route 执行固定窗口（fixed）和滑动窗口（sliding）限流，并在超限时返回 HTTP 429 或抛出限流异常。

| 应用运行时 | 运行端 | 版本 |
| --- | --- | --- |
| Spring Boot 2（javax） | `smart-redis-limiter-starter` | `2.1.0` |
| Spring Boot 3（Jakarta） | `smart-redis-limiter-jakarta-starter` | `1.0.0` |

两条运行端保留同名配置和限流能力，不能在同一应用中同时引入。Jakarta 运行端已在 Spring Boot `3.4.2`、Java `17` 和 `21` 下验证；其他 Spring Boot 3 版本未纳入本模块测试矩阵。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-redis-limiter-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

只用本地规则的注解模式时可不引入 Web；拦截器模式或默认 HTTP 远程策略客户端需要 Web。只用拦截器模式时可不引入 AOP。宿主至少使用 Java 17。`simple-redis-route-jakarta-starter:1.0.0` 由本模块作为运行时依赖引入；直接编程使用 Redis Route API 的宿主应显式声明该依赖。

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
                host: ${REDIS_HOST}
                port: ${REDIS_PORT:6379}
        limiter:
          redis:
            smart:
              enable: true
              me: example-service
              mode: both
              fallback:
                on-redis-error: deny
```

`me` 是服务编码，也是远程策略快照请求的 `serviceCode`。本模块不创建 Redis 连接，也不会退回到宿主的另一套 Redis 配置。Redis Route 缺失时启动失败。

## 限流规则

```java
@SmartRedisLimiter(
        resourceCode = "query-data",
        rules = @SmartRedisLimitRule(
                count = 10L,
                window = 1L,
                unit = SmartRedisLimiterTimeUnit.SECONDS),
        algorithm = "sliding",
        fallback = "deny")
public String queryData(String id) {
    return id;
}
```

拦截器模式在 `io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules` 下按 HTTP 方法和路径声明规则。两种模式共用 fixed/sliding 算法、KeyProvider（自定义限流对象取值器）、Redis Route 和执行事件。多窗口用单次 Lua 执行，任一窗口拒绝时不写入其他窗口。Web 模式默认返回 HTTP 429 和 `Retry-After` 响应头；宿主可按需替换异常处理。

## 远程策略

远程管理端可按 `serviceCode + resourceCode + subject` 下发完整限额窗口列表；命中时只替换 `limits`，不改变算法、路径、KeyProvider、fallback 或 Redis 路由。`subject` 是被限流对象的标识。刷新通过 ETag/304（内容未变化时不重复传输）与 revision（策略版本）校验，失败时保留最后一次已接受快照；请求执行链路不直接访问管理端。未启用远程策略时不创建 HTTP 客户端和刷新线程。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              remote-policy:
                enable: true
                snapshot-url: https://management.example.test/api/v1/policy/snapshot
                refresh-interval-millis: 60000
```

默认客户端只支持可选的固定 `policy-token` 请求头，不会自动获取 AKSK 凭据。需要已认证的快照请求时，由宿主注册 `SmartRedisLimiterPolicyClient` Bean；可使用 `HttpSmartRedisLimiterPolicyClient(properties, jsonCodec, authenticatedRestTemplate)` 复用宿主提供的客户端，SDK 不修改其拦截器、错误处理器或连接配置。认证拒绝、网络失败和非法快照均不替换已接受策略。`policy-token` 只适用于管理端明确配置的固定令牌模式，不应与资源服务器认证混用。

## 边界

- 复用 `smart-redis-limiter-core:2.1.0` 的模型与事件契约；Servlet 扩展接口使用 `jakarta.servlet`，旧 `javax.servlet` 实现需重新编译。
- 限流运行端不依赖 Management、IAM、AKSK、MySQL 或审计监听器。事件由 Core 定义，监听和持久化由宿主另行装配。
- 动态策略的原始 subject 在生成 Redis key 前计算摘要；本地策略的自定义 key 由宿主负责避免放入敏感原文。SDK 不在诊断日志中打印完整限流 key。
- 执行事件的 `routeKey`、`limitKey` 为实际路由 key 的 SHA-256 摘要，可用于关联，不可作为 Redis 物理 key 使用；这与 Spring Boot 2 运行端的事件值不同。摘要不等于加密，低熵 key 仍可能被猜测；宿主写入的自定义事件属性须自行避免敏感原文。
- 默认 HTTP 客户端限制声明的 Content-Length 和实际读取字节数，使用独立 JSON 编解码器，不注入宿主的 ObjectMapper。
- 远程快照的 HTTP 状态与内容边界已在模拟服务端测试中验证；与可信应用管理端、AKSK 认证链路的真实联调不属于本模块 `1.0.0` 的已验证范围。

## 兼容矩阵

| Spring Boot | Java | 验证范围 |
|---|---:|---|
| 3.4.2 | 17 / 21 | 全量测试（基线） |
| 3.3.13 | 17 | 全量测试 |
| 3.2.12 | 17 | 全量测试 |
