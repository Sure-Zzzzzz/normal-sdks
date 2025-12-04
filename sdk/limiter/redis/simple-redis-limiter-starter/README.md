# Simple Redis Limiter Starter

基于Redis的分布式限流器Spring Boot Starter，提供Token桶和Set集合两种限流模式，支持多实例环境下的分布式限流控制。

## 功能特性

- 🚀 **双模式限流**：Token 令牌桶（控制总量）+ Set 集合（防重复）
- 🔒 **分布式安全**：基于 Redis 分布式锁，多实例环境数据一致
- 🔄 **自动重置**：Cron 定时任务自动恢复令牌和清理去重集合
- 🛡️ **重试机制**：指数退避重试，应对网络抖动
- ⚙️ **灵活配置**：自定义 Key 前缀、令牌数量、重置周期等

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation "io.github.surezzzzzz:simple-redis-limiter-starter:1.0.0"
}
```

### 2. 启用限流器

在`application.yml`中添加配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            enable: true  # 启用限流器
            me: myapp     # 应用标识，用于区分不同应用实例
```

## 配置详解

### 基础配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `enable` | 是否启用限流器 | `false` |
| `me` | 应用标识，用于区分不同实例 | `default` |
| `lockKey` | 分布式锁的Redis Key | `surezzzzzz_redis_limiter_lock` |
| `lockValue` | 分布式锁的Redis Value | 随机UUID |
| `lockExpiryTime` | 锁的过期时间（秒） | `300` |
| `maxRetries` | 最大重试次数 | `20` |
| `retryInterval` | 重试间隔（毫秒） | `30000` |

### Token桶配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `token.enable` | 是否启用Token桶 | `true` |
| `token.reset` | 是否启用定时重置 | `true` |
| `token.cron` | 重置Cron表达式 | `0 0 0 * * ?` |
| `token.size` | 令牌数量 | `800000000` |
| `token.bucket` | Token桶的Redis Key | `surezzzzzz_redis_limiter_token_bucket` |

### Set桶配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `set.enable` | 是否启用Set桶 | `true` |
| `set.reset` | 是否启用定时重置 | `true` |
| `set.cron` | 重置Cron表达式 | `0 0 0 * * ?` |
| `set.bucket` | Set桶的Redis Key | `surezzzzzz_redis_limiter_set_bucket` |
| `set.compressBucket` | 压缩Set桶的Redis Key | `surezzzzzz_redis_limiter_compress_set_bucket` |

## 高级用法

### 自定义Key前缀

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            enable: true
            me: payment-service  # 所有Key都会包含这个标识
            lockKey: custom_payment_limiter_lock
            initializedKey: custom_payment_limiter_initialized
```

### 调整重试策略

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            enable: true
            maxRetries: 30        # 增加重试次数
            retryInterval: 10000  # 缩短重试间隔到10秒
            lockExpiryTime: 600  # 延长锁过期时间到10分钟
```

### 定时重置配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            enable: true
            token:
              reset: true
              cron: "0 0 8,20 * * ?"  # 每天8点和20点重置Token桶
            set:
              reset: true
              cron: "0 30 2 * * ?"    # 每天凌晨2:30重置Set桶
```

## @SimpleRedisRateLimiter 注解详解

### 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | `""` | 限流标识，支持SpEL表达式 |
| `useHash` | boolean | `false` | 是否使用哈希存储（适合长字符串） |
| `message` | String | `"系统繁忙，请稍后重试"` | 限流失败时的错误消息 |
| `fallback` | FallbackStrategy | `EXCEPTION` | 限流失败处理策略 |
| `fallbackMethod` | String | `""` | 自定义降级方法名 |

### FallbackStrategy 枚举

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| `EXCEPTION` | 抛出 RateLimitException 异常 | 需要统一异常处理的场景 |
| `RETURN_NULL` | 返回 null | 适用于返回对象类型的方法 |
| `CUSTOM` | 调用自定义降级方法 | 需要特定降级逻辑的场景 |

### SpEL 表达式支持

注解的 `key` 参数支持丰富的 SpEL 表达式：

```java
// 单参数
@SimpleRedisRateLimiter(key = "#userId")

// 对象属性
@SimpleRedisRateLimiter(key = "#request.orderId")

// 组合表达式
@SimpleRedisRateLimiter(key = "#userId + ':' + #ip")

// 固定前缀 + 动态参数
@SimpleRedisRateLimiter(key = "'user:' + #userId + ':action:' + #action")

// 调用方法
@SimpleRedisRateLimiter(key = "#user.getId()")
```

## 使用场景示例

### 1. API 接口限流
```java
@RestController
public class OrderController {
    
    // 创建订单接口 - 按用户限流
    @PostMapping("/orders")
    @SimpleRedisRateLimiter(key = "#userId", message = "下单过于频繁，请稍后重试")
    public Result createOrder(@RequestParam String userId, @RequestBody OrderRequest request) {
        return orderService.createOrder(userId, request);
    }
    
    // 支付接口 - 按订单去重（防止重复支付）
    @PostMapping("/orders/{orderId}/pay")
    @SimpleRedisRateLimiter(key = "#orderId", message = "订单正在支付中")
    public Result payOrder(@PathVariable String orderId, @RequestBody PaymentRequest request) {
        return paymentService.processPayment(orderId, request);
    }
}
```

### 2. 消息处理去重
```java
@Service
public class MessageService {
    
    // 处理消息 - 按消息ID去重
    @SimpleRedisRateLimiter(key = "#message.id", message = "消息重复")
    public void handleMessage(Message message) {
        // 处理消息逻辑
    }
    
    // 发送通知 - 按用户+通知类型组合去重
    @SimpleRedisRateLimiter(key = "'notify:' + #userId + ':' + #type")
    public void sendNotification(String userId, String type, String content) {
        // 发送通知逻辑
    }
}
```

### 3. 定时任务保护
```java
@Component
public class ScheduledTasks {
    
    // 数据同步任务 - 防止多实例重复执行
    @Scheduled(cron = "0 0 2 * * ?")
    @SimpleRedisRateLimiter(key = "'sync:data:' + #date")
    public void syncData(String date) {
        // 数据同步逻辑
    }
}
```

### 4. 自定义降级处理
```java
@Service
public class BusinessService {
    
    @SimpleRedisRateLimiter(
        key = "#request.userId",
        fallback = FallbackStrategy.CUSTOM,
        fallbackMethod = "handleRateLimit"
    )
    public BusinessResult processBusiness(BusinessRequest request) {
        return doProcess(request);
    }
    
    // 降级方法：返回缓存数据或默认值
    public BusinessResult handleRateLimit(BusinessRequest request) {
        log.warn("触发限流，返回降级数据: userId={}", request.getUserId());
        return BusinessResult.fromCache(request.getUserId());
    }
}
```

## 最佳实践

### 1. Key 设计原则
- **简洁明确**：使用清晰的命名规范，如 `user:123`、`order:ABC123`
- **避免冲突**：不同业务使用不同的前缀，如 `api:`、`task:`、`notify:`
- **长度控制**：过长的 key 建议使用 `useHash = true`

### 2. 降级策略选择
- **EXCEPTION**：适合有统一异常处理的系统
- **RETURN_NULL**：适合返回对象类型的简单接口
- **CUSTOM**：适合需要复杂降级逻辑的场合

### 3. 性能优化
- **批量操作**：避免在循环中调用限流方法
- **合理配置**：根据业务 QPS 调整 Token 桶大小
- **监控告警**：监控限流触发频率，及时调整策略

### 4. 注意事项
- Token 桶是**全局共享**的，所有注解方法共用
- key 用于**去重检查**，不是独立的限流配额
- 注解方法必须在 Spring 管理的 Bean 中才能生效

## API说明

### SimpleRedisLimiter

#### getToken()
获取一个简单令牌，不存储任何标识信息。

**返回值**：`boolean` - 是否获取成功

#### getToken(String something)
为特定字符串获取令牌，并将该字符串存储到Set集合中。

**参数**：
- `something` - 要限流的标识字符串

**返回值**：`int` - 结果代码
- `1` - 获取成功
- `0` - 令牌不足（限流）
- `2` - 标识已存在（去重）

#### getToken(String something, boolean hash)
为特定字符串获取令牌，可选择是否使用哈希存储。

**参数**：
- `something` - 要限流的标识字符串
- `hash` - 是否使用哈希存储（节省内存）

**返回值**：`int` - 结果代码（同上）

#### initializeBuckets()
手动初始化存储桶，通常在应用启动时自动调用。

### 3. 使用限流器

#### 3.1 编程式使用

```java
@Autowired
private SimpleRedisLimiter limiter;

// 获取简单令牌
if (limiter.getToken()) {
    // 执行业务逻辑
}

// 为特定字符串获取令牌（带存储）
int result = limiter.getToken("user_123");
if (result == 1) {
    // 获取成功，执行业务逻辑
} else if (result == 0) {
    // 令牌不足，限流
} else if (result == 2) {
    // 已存在该标识，去重
}
```

#### 3.2 声明式注解使用（推荐）

使用 `@SimpleRedisRateLimiter` 注解，支持 SpEL 表达式和多种降级策略：

```java
// 仅消耗令牌（无去重）
@SimpleRedisRateLimiter
public void simpleOperation() {
    // 执行业务逻辑
}

// 按用户ID去重
@SimpleRedisRateLimiter(key = "#userId")
public Result userOperation(String userId) {
    return processUser(userId);
}

// 按订单去重
@SimpleRedisRateLimiter(key = "#request.orderId", message = "订单正在处理中")
public Result orderOperation(OrderRequest request) {
    return createOrder(request);
}

// 组合去重（用户 + IP）
@SimpleRedisRateLimiter(key = "#userId + ':' + #ip")
public Result sensitiveOperation(String userId, String ip) {
    return doSensitiveOperation(userId, ip);
}

// 使用哈希存储（适合长字符串）
@SimpleRedisRateLimiter(key = "#token", useHash = true)
public Result longTokenOperation(String token) {
    return processToken(token);
}

// 自定义降级策略
@SimpleRedisRateLimiter(
    key = "#userId", 
    fallback = FallbackStrategy.CUSTOM,
    fallbackMethod = "handleRateLimit"
)
public Result customFallback(String userId) {
    return processUser(userId);
}

// 降级方法（必须在同一个类中）
public Result handleRateLimit(String userId) {
    return Result.fail("系统繁忙，请稍后重试");
}
```

## 工作原理

### Token桶算法
1. 初始化时设置固定数量的令牌
2. 每次请求消耗一个令牌
3. 令牌消耗完毕后触发限流
4. 根据Cron表达式定时重置令牌数量

### Set集合算法
1. 结合Token桶和Set集合存储
2. 请求时先检查标识是否已存在于Set中
3. 如果存在则返回去重结果（2）
4. 如果不存在则尝试获取Token
5. 获取成功后将标识加入Set

### 分布式安全
1. 使用Redis分布式锁确保只有一个实例执行初始化
2. 锁的过期时间防止死锁
3. 重试机制处理网络抖动等异常情况

## 监控与日志

限流器提供详细的日志输出，包括：
- 初始化过程日志
- Token获取结果日志
- 定时任务执行日志
- 分布式锁获取日志

可以通过配置日志级别来控制输出：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.limiter.redis: DEBUG
```

## 性能建议

1. **合理设置Token数量**：根据业务QPS和重置周期计算合适的Token数量
2. **优化Cron表达式**：避免过于频繁的重置操作
3. **使用哈希存储**：对于长字符串标识，建议使用哈希存储节省内存
4. **监控Redis性能**：关注Redis的CPU和内存使用情况

## 故障排查

### 常见问题

**Q: 限流器不生效？**
A: 检查配置是否正确启用：`io.github.surezzzzzz.sdk.limiter.redis.enable=true`

**Q: 所有请求都被限流？**
A: 检查Token桶大小配置，确认是否Token数量设置过小

**Q: 分布式环境下数据不一致？**
A: 检查Redis连接是否正常，分布式锁是否正确工作

**Q: @SimpleRedisRateLimiter 注解不生效？**
A: 检查以下几点：
- 确保方法所在的类被 Spring 管理（有 `@Service`、`@Component` 等注解）
- 检查 Token 桶是否启用：`io.github.surezzzzzz.sdk.limiter.redis.token.enable=true`
- 如果使用 key 参数，检查 Set 桶是否启用：`io.github.surezzzzzz.sdk.limiter.redis.set.enable=true`
- 确认切面类已正确加载：`SimpleRedisRateLimiterAspect`

**Q: SpEL 表达式解析失败？**
A: 检查表达式语法是否正确，参数名是否与方法参数匹配：
```java
// 正确
@SimpleRedisRateLimiter(key = "#userId")
public void method(String userId) { }

// 错误（参数名不匹配）
@SimpleRedisRateLimiter(key = "#id")
public void method(String userId) { }
```

**Q: 自定义降级方法调用失败？**
A: 检查降级方法的签名是否与原方法完全一致：
```java
// 原方法
public Result process(String userId, int amount)

// 正确的降级方法（参数和返回值必须一致）
public Result processFallback(String userId, int amount)

// 错误（参数不一致）
public Result processFallback(String userId)
```

### 调试模式

开启调试日志获取更多信息：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.limiter.redis: DEBUG
    org.springframework.data.redis: DEBUG
```
