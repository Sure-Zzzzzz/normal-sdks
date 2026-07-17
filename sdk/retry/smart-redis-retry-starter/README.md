# Smart Redis Retry Starter

`smart-redis-retry-starter` 是基于 `simple-redis-route-starter` 的分布式重试决策与生命周期管理组件，用 Redis Hash + Lua 原子记录失败次数、首末失败时间、下次可重试时间、最近错误和上下文。

## 版本信息

当前版本：`1.0.0`

`1.0.0` 是 smart 线首发版本，独立于 `redis-retry-starter` 的 simple 维护线。旧模块使用 String JSON，smart-retry 使用 Redis Hash + Lua 原子操作，两者记录格式不兼容；本模块不会读取或自动迁移旧记录，迁移需由调用方显式处理。

## 兼容性

| Spring Boot | Redis standalone | Redis Cluster |
|---|---|---|
| 2.3.12 / 2.4.5 / 2.7.9 | 3 / 5 / 7 | 3 / 5 / 7 |
| 2.2.x（默认依赖） | 3 / 5 / 7 | 3 / 5；不支持 Redis 7 Cluster |

## 核心特性

- 基于 `simple-redis-route-starter` 复用多 Redis 数据源、standalone / cluster、连接生命周期和路由规则。
- `SmartRedisRetryEngine` 返回完整 `RetryDecision`，区分允许、等待、耗尽等状态。
- `RetryScene` 绑定 `retryType`，减少业务侧重复传参和传错场景。
- `policy.default-policy` + `policy.scene` + 单次 `RetryFailure.policy` 支持多层策略覆盖。
- Lua 原子记录失败次数和下一次可重试时间，避免非原子计数。
- Redis Key 默认使用 Cluster hash tag，保证单条记录 Hash / Lua 操作落在同一 slot。
- 使用 SCAN 分页扫描重试记录，不使用阻塞式 KEYS。
- 支持上下文 JSON 大小限制、Redis 故障策略、生命周期监听和扩展点覆盖。
- 兼容 Spring Boot 2.3.12 / 2.4.5 / 2.7.9 的 Redis 3 / 5 / 7 standalone + cluster 矩阵；Spring Boot 2.2.x 使用默认依赖时不支持 Redis 7 Cluster。

## 依赖引用

### Gradle

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:smart-redis-retry-starter:1.0.0"
    implementation "io.github.sure-zzzzzz:simple-redis-route-starter:1.1.0"
    implementation "org.springframework.boot:spring-boot-starter-data-redis"
    implementation "com.fasterxml.jackson.core:jackson-databind"
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>smart-redis-retry-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-redis-route-starter</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## 快速开始

### 1. 配置 redis-route

smart-retry 不维护第二套 Redis 数据源配置，所有数据源和路由规则都由 redis-route 管理。应用必须先引入并配置 `simple-redis-route-starter`，使 `RedisRouteTemplate` 可用；否则 smart-retry 不会创建 `SmartRedisRetryEngine`。

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
              retryCluster:
                mode: cluster
                nodes:
                  - localhost:6379
                  - localhost:6380
                  - localhost:6381
                timeout-ms: 3000
                connect-timeout-ms: 3000
            rules:
              - pattern: "sure-smart-redis-retry:retry:test-compensation:"
                type: prefix
                datasource: retryCluster
                priority: 1
```

smart-retry 生成的 Redis Key 本身就是 route key，因此要按实际 Key 前缀配置 route rule。

### 2. 启用 smart-retry

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          redis:
            smart:
              enable: true
              redis:
                key-prefix: sure-smart-redis-retry
                me: default
                use-hash-tag: true
                scan-count: 500
                record-ttl-seconds: 86400
                retain-exhausted: true
              guard:
                max-retry-key-length: 512
                max-context-json-length: 4096
                redis-failure-strategy: fail_closed
              policy:
                default-policy:
                  max-retry-times: 3
                  retry-interval-millis: 1000
                  max-interval-millis: 30000
                  backoff-multiplier: 1.5
                  jitter-ratio: 0.1
                scene:
                  test-compensation:
                    max-retry-times: 20
                    retry-interval-millis: 60000
                    max-interval-millis: 1800000
                    backoff-multiplier: 2.0
                    jitter-ratio: 0.2
```

## 配置说明

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `enable` | `true` | 是否启用 smart-retry；只有 `RedisRouteTemplate` 存在时才会生效 |
| `redis.key-prefix` | `sure-smart-redis-retry` | Redis Key 前缀 |
| `redis.me` | `default` | 应用实例标识，用于区分多个调用方共用 Redis 的场景 |
| `redis.use-hash-tag` | `true` | 是否用 `{identifierHash}` 包裹 hash tag |
| `redis.scan-count` | `500` | SCAN 每批建议数量 |
| `redis.record-ttl-seconds` | `86400` | 重试记录 TTL |
| `redis.retain-exhausted` | `true` | 重试耗尽后是否保留记录直到 TTL 到期 |
| `guard.max-retry-key-length` | `512` | 业务 retryKey 最大长度 |
| `guard.max-context-json-length` | `4096` | 上下文 JSON 最大长度 |
| `guard.redis-failure-strategy` | `fail_closed` | Redis 操作失败策略：`fail_closed` / `fail_open` / `throw` |
| `policy.default-policy` | 内置默认策略 | 所有场景的兜底重试策略 |
| `policy.scene` | 空 | 按 `retryType` 配置场景策略 |

## Redis Key

```text
普通：{keyPrefix}:retry:{retryType}:{me}::{identifierHash}
Cluster hash tag：{keyPrefix}:retry:{retryType}:{me}::{{identifierHash}}
```

`identifierHash` 是业务 `retryKey` 的 SHA-1 大写摘要。默认启用 hash tag，只包裹摘要部分，保证单条记录的 Hash / Lua 命令在 Redis Cluster 下落到同一 slot。

## 核心 API

### 判断是否允许重试

```java
@Autowired
private SmartRedisRetryEngine retryEngine;

RetryDecision decision = retryEngine.decide("test-compensation", "test-key");
if (decision.isAllowed()) {
    // 执行业务补偿
}
```

### 决策结果

`canRetry(retryType, retryKey)` 是 `decide(retryType, retryKey).isAllowed()` 的便利入口。需要判断原因或等待时间时，应使用完整的 `RetryDecision`。

| 类型 | `allowed` | 含义 |
|---|---:|---|
| `ALLOW` | `true` | 没有记录，或已到下一次允许重试时间 |
| `WAITING` | `false` | 存在记录但尚未到下一次允许重试时间，可读取 `waitMillis` |
| `EXHAUSTED` | `false` | 失败次数已达到策略的 `maxRetryTimes` |

### 记录失败

```java
RetryInfo info = retryEngine.recordFailure("test-compensation", "test-key");
```

### 带错误信息和上下文记录失败

```java
Map<String, Object> context = new HashMap<>();
context.put("extraField", "mock-value");

RetryFailure failure = RetryFailure.builder()
        .retryType("test-compensation")
        .retryKey("test-key")
        .errorCode("TEST_ERROR")
        .errorMessage("mock error message")
        .context(context)
        .build();

RetryInfo info = retryEngine.recordFailure(failure);
```

### 查询和清理记录

```java
RetryInfo current = retryEngine.getInfo("test-compensation", "test-key");
RetryInfo beforeClear = retryEngine.clear("test-compensation", "test-key");
```

### 使用场景门面

```java
RetryScene scene = retryEngine.scene("test-compensation");

if (scene.canRetry("test-key")) {
    try {
        // 执行业务补偿
    } catch (Exception e) {
        scene.recordFailure("test-key");
        throw e;
    }
}
```

### 扫描记录

```java
RetryScanResult result = retryEngine.scan(
        RetryScanRequest.builder()
                .routeKey("sure-smart-redis-retry:retry:test-compensation:default::mock-route")
                .retryType("test-compensation")
                .cursor("0")
                .count(500)
                .includeInfo(true)
                .build());
```

`routeKey` 只用于选择一个 datasource；实际扫描 pattern 由 `retryType`、`key-prefix` 和 `me` 共同决定。每次调用只返回一页；应持续将 `nextCursor` 原样传回，直到 `finished=true`。Cluster 模式下 `nextCursor` 是引擎生成的不透明值，不能自行解析或修改。

单次 `scan` 不会跨 datasource 或 Cluster 汇总记录。多个 retryType 路由到不同 datasource 时，调用方应对每个 datasource 独立完成分页扫描，再自行合并和去重结果。

## 策略优先级

重试策略解析顺序：

1. `RetryFailure.policy`
2. `policy.scene.{retryType}`
3. `policy.default-policy`
4. 内置默认值

## 运行边界

smart-retry 负责重试决策和状态记录，不执行线程内重试、不调度或投递任务，也不提供分布式锁、single-flight 或幂等保证。调用方应根据 `RetryDecision` 自行执行实际操作，并在失败时调用 `recordFailure`。

## Redis 故障策略

| 策略 | 行为 |
|------|------|
| `fail_closed` | Redis 操作失败时返回不允许重试，避免故障时放大下游压力 |
| `fail_open` | Redis 操作失败时返回允许重试，适合更看重业务继续执行的场景 |
| `throw` | Redis 操作失败时抛出 `RetryOperationException` |

## 扩展点

以下 Bean 均可由业务侧覆盖：

- `RetryPolicyResolver`
- `SmartRedisRetryListener`
- `RetryClock`
- `RetryKeyHelper`
- `RetryInfoConvertHelper`
- `RetryContextSerializer`
- `RetryRequestValidatorChain`
- `RedisRetryScriptExecutor`
- `SmartRedisRetryEngine`

默认实现均通过 `@ConditionalOnMissingBean` 注册。
