# smart-redis-limiter-starter

智能 Redis 限流器，提供基于注解和拦截器的双模式限流方案，支持固定窗口和滑动窗口算法，内置降级保护和事件审计能力。

## 功能特性

- **双模式**：注解模式（`@SmartRedisLimiter`）+ 拦截器模式（yaml 规则配置）
- **双算法**：固定窗口（Fixed Window）+ 滑动窗口（Sliding Window），可按规则独立配置
- **多 Key 策略**：方法级、路径级、路径模式级、IP 级
- **多时间窗口**：同一接口可同时配置多个时间窗口的复合规则
- **智能降级**：Redis 异常时支持放行（allow）或拒绝（deny），三层优先级（规则 > 模式 > 全局）
- **超时保护**：Redis 命令超时自动触发降级，不阻塞业务线程
- **集群支持**：自动检测 Redis 集群模式，使用 Hash Tag 保证 Lua 脚本原子性
- **事件审计**：限流触发/通过时发布 Spring 事件，支持与外部系统集成
- **ClientIP 解析**：拦截器模式自动解析客户端真实 IP（支持多级代理）
- **标准响应头**：限流检查后写入 `X-RateLimit-Limit/Remaining/Reset`，客户端可据此实现重试

## 快速开始

### 1. 添加依赖

```gradle
implementation 'io.github.surezzzzzz:smart-redis-limiter-starter:1.1.3'
// 必须显式引入以下依赖
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-aop'
implementation 'org.springframework.boot:spring-boot-starter-web'
```

### 2. 基本配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              enable: true
              me: my-service          # 服务标识，用于区分多实例
              mode: both              # annotation | interceptor | both

              redis:
                command-timeout: 3000 # Redis 命令超时（毫秒），超时后触发降级

              fallback:
                on-redis-error: deny  # 全局降级策略：allow | deny
```

### 3. 注解模式

在方法上添加 `@SmartRedisLimiter`：

```java

@SmartRedisLimiter(
        rules = {
                @SmartRedisLimitRule(count = 10, window = 1, unit = TimeUnit.SECONDS)
        },
        algorithm = "sliding",   // fixed（默认）| sliding
        fallback = "allow"       // 覆盖全局降级策略
)
public String queryData(String id) {
    // ...
}
```

### 4. 拦截器模式

在 yaml 中配置规则：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              interceptor:
                default-key-strategy: path
                default-fallback: allow
                rules:
                  # 查询接口：滑动窗口，每个路径独立限流
                  - path-pattern: /api/query/**
                    method: GET
                    key-strategy: path
                    algorithm: sliding
                    fallback: allow
                    limits:
                      - count: 100
                        window: 1
                        unit: seconds

                  # 写操作：固定窗口，所有路径共享限流
                  - path-pattern: /api/write/**
                    method: POST
                    key-strategy: path-pattern
                    algorithm: fixed
                    fallback: deny
                    limits:
                      - count: 50
                        window: 10
                        unit: seconds
```

## 配置参考

### 核心配置

| 属性                        | 类型      | 默认值     | 说明                                         |
|---------------------------|---------|---------|--------------------------------------------|
| `enable`                  | Boolean | `false` | 是否启用                                       |
| `me`                      | String  | -       | 服务标识（必填）                                   |
| `mode`                    | String  | `both`  | 限流模式：`annotation` / `interceptor` / `both` |
| `redis.command-timeout`   | Long    | `3000`  | Redis 命令超时（毫秒）                             |
| `fallback.on-redis-error` | String  | `deny`  | 全局降级策略：`allow` / `deny`                    |

### 限流规则

| 属性       | 类型       | 默认值       | 说明                                            |
|----------|----------|-----------|-----------------------------------------------|
| `count`  | Integer  | -         | 时间窗口内允许的最大请求数                                 |
| `window` | Integer  | -         | 时间窗口大小                                        |
| `unit`   | TimeUnit | `SECONDS` | 时间单位：`SECONDS` / `MINUTES` / `HOURS` / `DAYS` |

### 拦截器规则专属

| 属性             | 类型     | 默认值                       | 说明                            |
|----------------|--------|---------------------------|-------------------------------|
| `path-pattern` | String | -                         | 路径模式（Ant 风格，必填）               |
| `method`       | String | -                         | HTTP 方法（GET / POST 等，不填则匹配所有） |
| `key-strategy` | String | 继承 `default-key-strategy` | Key 生成策略                      |
| `algorithm`    | String | `fixed`                   | 限流算法：`fixed` / `sliding`      |
| `fallback`     | String | 继承 `default-fallback`     | 规则级降级策略                       |

### Key 生成策略

| 策略             | 说明       | 适用场景                                                        |
|----------------|----------|-------------------------------------------------------------|
| `method`       | 基于方法签名   | 注解模式默认，同一方法共享计数                                             |
| `path`         | 基于完整请求路径 | 每个路径独立计数（`/api/user/123` 和 `/api/user/456` 各自独立）            |
| `path-pattern` | 基于路径模式   | 路径模式下所有路径共享计数（`/api/user/**` 下所有路径共享）                       |
| `ip`           | 基于客户端 IP | 按 IP 独立计数，IP 解析优先级：X-Forwarded-For → X-Real-IP → RemoteAddr |

### 规则匹配优先级

拦截器规则按以下优先级匹配，先匹配到的规则生效：

1. **精确路径 + 精确方法**：`path = /api/user/123`，`method = GET`
2. **模式路径 + 精确方法**：`path = /api/user/**`，`method = GET`
3. **模式路径 + 通配方法**：`path = /api/user/**`，`method` 为空

同一优先级内，按 yaml 中的配置顺序匹配，先配置的先生效。

### 算法对比

| 特性         | 固定窗口（fixed）        | 滑动窗口（sliding）   |
|------------|--------------------|-----------------|
| 精度         | 窗口边界存在突刺（最多 2 倍流量） | 精确滑动，任意时间段不超过阈值 |
| Redis 数据结构 | String（INCR）       | ZSET            |
| 内存占用       | 低                  | 较高（与请求量成正比）     |
| 适用场景       | 对精度要求不高、高吞吐        | 对精度要求高、需要防突刺    |

### 降级策略优先级

```
规则级 fallback > 模式级 default-fallback > 全局 fallback.on-redis-error
```

## 最佳实践

### 算法选择

**用固定窗口的场景：**

- 内部接口、对精度要求不高的限流
- 高吞吐场景，Redis 内存敏感

**用滑动窗口的场景：**

- 短信/邮件发送：防止用户在窗口边界连续发送（如 59s 发 5 条、61s 再发 5 条）
- 支付接口：严格防止突刺，保护下游系统
- 对外暴露的 API：需要严格保证任意时间段内的请求量

**固定窗口的突刺问题示例：**

```
限流：5次/秒
T=0.9s：发 5 次 → 全部通过（窗口 1 配额耗尽）
T=1.1s：发 5 次 → 全部通过（窗口 2 配额重置）
结果：0.2 秒内通过了 10 次请求（2 倍限流阈值）
```

**滑动窗口无此问题：**

```
限流：5次/秒
T=0.9s：发 5 次 → 全部通过
T=1.1s：发第 6 次 → 被拒绝（0.9s 的 5 条记录仍在窗口内）
T=1.9s：发第 6 次 → 通过（0.9s 的记录已过期）
```

### Key 策略选择

```yaml
# 按用户/资源独立限流 → path
- path-pattern: /api/user/**
  key-strategy: path        # /api/user/123 和 /api/user/456 各自独立

# 全局共享限流 → path-pattern
- path-pattern: /api/sms/**
  key-strategy: path-pattern  # 所有短信接口共享同一个计数器

# 按 IP 防刷 → ip
- path-pattern: /api/login
  key-strategy: ip
```

### 降级策略选择

```yaml
# 查询接口：Redis 故障时放行，不影响读
- path-pattern: /api/query/**
  fallback: allow

# 写操作：Redis 故障时拒绝，保护下游
- path-pattern: /api/write/**
  fallback: deny
```

### 多时间窗口复合限流

同一接口同时配置多个时间窗口，所有窗口都通过才放行：

```java

@SmartRedisLimiter(
        rules = {
                @SmartRedisLimitRule(count = 10, window = 1, unit = TimeUnit.SECONDS),   // 每秒 10 次
                @SmartRedisLimitRule(count = 100, window = 1, unit = TimeUnit.MINUTES)   // 每分钟 100 次
        },
        algorithm = "sliding"
)
public String queryData(String id) { ...}
```

### 事件审计

限流触发时（`passed=false`）自动发布 `SmartRedisLimiterEvent`。实现 `SmartRedisLimiterUserProvider` 和
`SmartRedisLimiterTraceIdProvider` 可将用户信息和链路 ID 注入事件。

```java

@Component
public class MyUserProvider implements SmartRedisLimiterUserProvider {
    @Override
    public String getClientId() {
        return SecurityContext.getClientId();
    }

    @Override
    public String getClientType() {
        return SecurityContext.getClientType();
    }

    @Override
    public String getUserId() {
        return SecurityContext.getUserId();
    }

    @Override
    public String getUsername() {
        return SecurityContext.getUsername();
    }
}

@Component
public class MyTraceIdProvider implements SmartRedisLimiterTraceIdProvider {
    @Override
    public String getTraceId() {
        return MDC.get("traceId");
    }
}

@Component
public class MyAuditListener {
    @Autowired
    private MyUserProvider userProvider;
    @Autowired
    private MyTraceIdProvider traceIdProvider;

    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        SmartRedisLimiterRecord record = SmartRedisLimiterRecord.builder()
                .clientId(userProvider.getClientId())
                .userId(userProvider.getUserId())
                .traceId(traceIdProvider.getTraceId())
                .limitKey(event.getLimitKey())
                .passed(event.isPassed())
                .source(event.getSource())
                .requestUri(event.getRequestUri())
                .clientIp(event.getClientIp())
                .algorithm(event.getAlgorithm())
                .timestamp(event.getTimestamp())
                .build();
        // 写入 Kafka / Elasticsearch / 数据库等
    }
}
```

审计配置：

```yaml
log-on-pass: false   # 是否在限流通过时也发布事件（默认 false，仅限流触发时发布）
```

### 限流响应头

限流检查后自动写入以下标准响应头（无论通过还是拒绝），便于客户端实现重试逻辑：

| 响应头 | 说明 | 示例 |
|-------|------|------|
| `X-RateLimit-Limit` | 时间窗口内的限流阈值 | `5` |
| `X-RateLimit-Remaining` | 当前窗口剩余配额 | `0` |
| `X-RateLimit-Reset` | 窗口重置的 Unix 时间戳（秒） | `1715635200` |
| `Retry-After` | 被限流时建议重试等待时间（秒） | `1` |

被限流时的 HTTP 响应示例：

```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1715635200
Retry-After: 1
Content-Type: application/json

{"code":429,"message":"Rate limit exceeded","data":{"limit":5,"remaining":0,"resetAt":1715635200}}
```

> 注意：注解模式和拦截器模式均会写入响应头。被限流时抛出的 `SmartRedisLimitExceededException` 也携带限流详情，可供自定义 `ExceptionHandler` 使用。

## Redis Key 结构

```
smart-limiter:{me}:{key-strategy}:{key-part}:{window}s    # 固定窗口
smart-limiter:{me}:{key-strategy}:{key-part}:{window}sw   # 滑动窗口
```

示例：

```
smart-limiter:my-service:method:UserController.getUser:60s
smart-limiter:my-service:path:/api/query/list:1sw
smart-limiter:my-service:path-pattern:/api/write/**:10s
```

## 性能建议

**连接池配置：**

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
```

**超时配置：** Redis 命令超时建议设置为 `500~1000ms`，超时后触发降级，避免 Redis 故障拖垮业务线程。生产环境不建议设置过大（默认
3000ms 偏高）。
