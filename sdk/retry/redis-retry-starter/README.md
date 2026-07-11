# Redis Retry Starter

`redis-retry-starter` 是基于 Redis 的 simple 分布式重试计数组件，提供跨实例重试次数记录、重试间隔控制、重试信息查询和成功后清理能力。

## 版本信息

当前版本：`1.1.0`

`1.1.0` 是 simple 维护线版本，主要目标是统一 normal-sdks 代码规范、补强测试，并在 Redis Key 中新增 `me` 应用实例标识。

后续更完整的业务型分布式重试决策、策略治理、生命周期监听和多 Redis 路由能力，会由 `smart-redis-retry-starter` 承载。

## 依赖引用

### Gradle

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:redis-retry-starter:1.1.0"

    implementation "org.springframework.boot:spring-boot-starter-data-redis"
    implementation "org.springframework.boot:spring-boot-autoconfigure"
    implementation "com.fasterxml.jackson.core:jackson-databind"
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>redis-retry-starter</artifactId>
    <version>1.1.0</version>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## 引包即用

默认启用，无需额外配置；只要应用已有 Redis 连接配置，即可直接注入使用。

```java
@Autowired
private RedisRetryService redisRetryService;
```

## 可选配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          redis:
            enable: true
            key-prefix: sure-redis-retry
            me: default
            max-retry-count: 3
            retry-record-ttl-seconds: 86400
            base-delay-ms: 1000
            max-delay-ms: 30000
            force-hash-tag: null
```

配置说明：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `enable` | `true` | 是否启用组件 |
| `key-prefix` | `sure-redis-retry` | Redis Key 前缀 |
| `me` | `default` | 应用实例标识，用于区分多个调用方共用 Redis 的场景 |
| `max-retry-count` | `3` | 旧 fullKey API 的默认最大重试次数 |
| `retry-record-ttl-seconds` | `86400` | 旧 fullKey API 的记录 TTL |
| `base-delay-ms` | `1000` | 指数退避基础延迟 |
| `max-delay-ms` | `30000` | 指数退避最大延迟 |
| `force-hash-tag` | `null` | 是否强制使用 Redis Cluster hash tag，`null` 表示自动检测 |

## Redis Key

1.1.0 新写入记录使用标准 Key：

```text
普通：{keyPrefix}:retry:{retryType}:{me}::{identifierHash}
Cluster hash tag：{keyPrefix}:retry:{retryType}:{me}::{{identifierHash}}
```

`identifierHash` 是 `retryKey` 的 SHA-1 大写摘要。

1.1.0 兼容读取和清理 1.0.0 legacy Key：

```text
普通：{retryType}:retry:{identifierHash}
Cluster hash tag：{{retryType}}:retry:{identifierHash}
```

当调用新式 API 继续记录 legacy Key 时，会把记录迁移到 1.1.0 标准 Key，并清理旧 Key。

## 核心 API

### 判断是否可以重试

```java
boolean canRetry = redisRetryService.canRetry("test-context", "test-key");
```

### 记录失败

```java
redisRetryService.recordRetry("test-context", "test-key", 5, 60, TimeUnit.SECONDS);
```

### 带上下文记录失败

```java
Map<String, Object> context = new HashMap<>();
context.put("extraField", "value");
redisRetryService.recordRetry("test-context", "test-key", 5, 60, TimeUnit.SECONDS, context);
```

### 查询重试信息

```java
RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo("test-context", "test-key");
```

### 清除重试记录

```java
redisRetryService.clearRetry("test-context", "test-key");
```

### 指数退避延迟

```java
long delayMs = redisRetryService.calculateRetryDelay(3);
```

### 旧 fullKey API

1.1.0 保留旧 fullKey API，已有调用方无需因为 Key 规范升级而立即改造。

```java
String fullKey = "legacy-full-key";

if (redisRetryService.canRetry(fullKey)) {
    try {
        doProcess();
    } catch (Exception e) {
        redisRetryService.recordFailure(fullKey, e);
        throw e;
    }
}
```

## 使用示例

```java
@Service
public class TestRetryService {

    @Autowired
    private RedisRetryService redisRetryService;

    public void process(String testId) {
        String retryType = "test-process";
        String retryKey = testId;

        if (!redisRetryService.canRetry(retryType, retryKey)) {
            return;
        }

        try {
            doProcess(testId);
            redisRetryService.clearRetry(retryType, retryKey);
        } catch (Exception e) {
            redisRetryService.recordRetry(retryType, retryKey, 5, 60, TimeUnit.SECONDS);
            throw e;
        }
    }

    private void doProcess(String testId) {
        // 执行处理逻辑
    }
}
```

## 测试参考

测试包路径：`io.github.surezzzzzz.sdk.retry.redis.test.cases`

核心测试类：`RedisRetryServiceTest`

覆盖场景：

- 首次执行允许重试
- 失败次数累加
- 最大重试次数限制
- 重试间隔控制
- 清除后恢复，并断言实际 Redis Key 被删除
- 上下文写入和更新
- 不同 `retryKey` 独立，并断言 `getRetryKeys` 返回精确 Key 集合
- 不同 `retryType` 独立
- 标准 Key 精确包含 `keyPrefix`、`retryType`、`me` 和 `SHA1(retryKey)`
- Redis JSON 与 service 查询结果字段级一致
- TTL 大于一小时缓冲时间
- `nextRetryTime = lastFailTime + retryIntervalMs`
- legacy Key 可读取、可继续记录、迁移后清理旧 Key
- 旧 fullKey API 记录、查询和清理兼容
