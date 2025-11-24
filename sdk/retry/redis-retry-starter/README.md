# Redis Retry Starter

基于Redis的分布式重试控制组件，支持指数退避、智能重试和Redis Cluster环境

## 特性

- 基于Redis的分布式重试控制
- 指数退避算法，避免雪崩效应
- 自动适配Redis单机和Cluster环境
- 三种灵活的使用方式
- SHA-1摘要确保key一致性
- 完善的配置和监控
- 零配置即插即用

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.surezzzzzz.sdk:redis-retry-starter:1.0.0'
}
```

### 2. 配置Redis连接

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### 3. 基本使用

```java
@Service
public class MessageProcessor {
    
    @Autowired
    private RedisRetryService redisRetryService;
    
    public void processMessage(String messageId) {
        String prefix = "my-app";
        
        if (!redisRetryService.canRetry(prefix, messageId)) {
            log.warn("消息重试次数已达上限: {}", messageId);
            return;
        }
        
        try {
            // 业务处理逻辑
            doProcessMessage(messageId);
            
            // 处理成功，清除重试记录
            redisRetryService.clearRetryRecord(prefix, messageId);
            
        } catch (Exception e) {
            // 记录失败并计算重试延迟
            redisRetryService.recordFailure(prefix, messageId, e);
            
            RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(prefix, messageId);
            long delay = redisRetryService.calculateRetryDelay(retryInfo.getCount());
            
            log.warn("消息处理失败，{}ms后重试，当前重试次数: {}", delay, retryInfo.getCount());
            
            // 延迟后重新投递消�?
            scheduleRetry(messageId, delay);
        }
    }
}
```

## 使用方式

### 方式1：前缀+标识符（推荐）

```java
// 检查是否可以重�?
boolean canRetry = redisRetryService.canRetry("my-app", "order-123");

// 记录失败
redisRetryService.recordFailure("my-app", "order-123", exception);

// 获取重试信息
RetryInfo info = redisRetryService.getCurrentRetryInfo("my-app", "order-123");

// 清除重试记录
redisRetryService.clearRetryRecord("my-app", "order-123");
```

### 方式2：上下文模式

```java
// 创建带前缀的上下文
RetryContext context = redisRetryService.withPrefix("my-app");

// 使用上下文操作（无需重复传前缀）
boolean canRetry = context.canRetry("order-123");
context.recordFailure("order-123", exception);
RetryInfo info = context.getCurrentRetryInfo("order-123");
long delay = context.calculateRetryDelay("order-123");
context.clearRetryRecord("order-123");
```

### 方式3：完整key

```java
// 完全控制key格式
String customKey = "special-logic:retry:" + complexKey;
boolean canRetry = redisRetryService.canRetry(customKey);
redisRetryService.recordFailure(customKey, exception);
```

## 配置选项

```yaml
com:
  fri:
    schumann:
      retry:
        redis:
          # 最大重试次数（默认：5）
          max-retry-count: 5
          
          # 基础延迟时间（毫秒，默认：2000）
          base-delay-ms: 2000
          
          # 最大延迟时间（毫秒，默认：30000）
          max-delay-ms: 60000
          
          # 重试记录TTL（秒，默认：86400）
          retry-record-ttl-seconds: 172800
          
          # Hash Tag策略（默认：null自动检测）
          # null: 自动检测Redis环境
          # true: 强制使用hash tag (Cluster模式)
          # false: 强制不使用hash tag (单机模式)
          force-hash-tag: null
```

## Redis Cluster支持

组件会自动检测Redis环境类型

- **单机Redis**: 生成key格式 `my-app:retry:ABC123`
- **Redis Cluster**: 生成key格式 `{my-app}:retry:ABC123`

Hash Tag确保同一应用的重试记录位于同一slot，避免跨slot操作限制

### 手动配置

如果自动检测不准确，可以手动指定：

```yaml
io.github.surezzzzzz.sdk.retry.redis.force-hash-tag: true  # 强制Cluster模式
```

## 重试策略

### 指数退避算法

重试延迟时间按指数增长：

- 第1次重试：1秒后
- 第2次重试：2秒后
- 第3次重试：4秒后
- 第4次重试：8秒后
- 第5次重试：16秒后（不超过max-delay-ms）

### 重试信息结构

```java
public class RetryInfo {
    private int count;          // 重试次数
    private long firstFailTime; // 第一次失败时�?
    private long lastFailTime;  // 最后一次失败时�?
    private String lastError;   // 最后一次错误信�?
}
```

## 高级用法

### 获取重试延迟时间

```java
// 方法1：通过重试次数计算
long delay1 = redisRetryService.calculateRetryDelay(3); // �第1次重试的延迟

// 方法2：通过上下文计�?
RetryContext context = redisRetryService.withPrefix("my-app");
long delay2 = context.calculateRetryDelay("order-123"); // 基于当前重试次数
```

### 构建key预览

```java
// 查看会生成什么key
String key = redisRetryService.buildRetryKey("my-app", "order-123");
System.out.println("Redis key: " + key);
// 输出: Redis key: {my-app}:retry:AF1B2C3D... (Cluster模式)
```

### 批量操作

```java
RetryContext context = redisRetryService.withPrefix("batch-job");

List<String> taskIds = Arrays.asList("task1", "task2", "task3");
for (String taskId : taskIds) {
    if (context.canRetry(taskId)) {
        // 处理任务
        try {
            processTask(taskId);
            context.clearRetryRecord(taskId);
        } catch (Exception e) {
            context.recordFailure(taskId, e);
        }
    }
}
```

## 监控和日�?

### 日志级别

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.retry.redis: DEBUG  # 详细的调试日�?
    io.github.surezzzzzz.sdk.retry.redis: INFO   # 重要操作日志
```

### 关键监控指标

- 重试记录创建/更新
- 重试次数分布
- Redis环境检测结�?
- Key构建过程

## 异常处理

组件内置完善的异常处理：

```java
// 所有Redis操作失败都会抛出RedisRetryException
try {
    redisRetryService.recordFailure("app", "id", exception);
} catch (RedisRetryException e) {
    log.error("重试服务异常", e);
    // 可以选择降级处理
}
```

## 性能考虑

### Redis Key设计

- 使用SHA-1摘要确保key长度固定
- 支持hash tag，Cluster环境下同应用记录聚集
- TTL自动清理过期记录

### 内存使用

```java
// 重试记录会自动过�?
// 默认24小时TTL，可配置
retry-record-ttl-seconds: 86400
```

## 最佳实�?

### 1. 前缀设计

```java
// 推荐：使用应用名+业务模块
redisRetryService.canRetry("order-service:payment", orderId);
redisRetryService.canRetry("user-service:register", userId);

// 避免：过于简单的前缀
redisRetryService.canRetry("app", id); // 不够具体
```

### 2. 重试次数设置

```java
// 根据业务重要性调�?
max-retry-count: 3  // 一般业�?
max-retry-count: 5  // 重要业务
max-retry-count: 1  // 实时性要求高的业�?
```

### 3. 延迟时间配置

```java
// 根据下游服务能力调整
base-delay-ms: 1000   // 内部服务
base-delay-ms: 5000   // 外部API
max-delay-ms: 30000   // 一般上�?
max-delay-ms: 300000  // 可容忍较长延迟的场景
```

## 故障排查

### 常见问题

1. **Redis连接失败**
   ```
   检查spring.redis配置
   确认Redis服务状�?
   验证网络连通�?
   ```

2. **重试次数不正�?*
   ```
   检查是否正确调用clearRetryRecord()
   确认TTL设置是否合理
   查看日志中的重试记录操作
   ```

3. **Cluster环境key分散**
   ```
   检查force-hash-tag配置
   确认是否正确检测到Cluster环境
   查看生成的key格式是否包含{}
   ```

### 调试日志

启用DEBUG日志查看详细信息�?

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.retry.redis: DEBUG
```

关键日志信息�?
- Redis环境检测结�?
- Key构建过程和格�?
- 重试记录的创�?更新/删除
- 延迟时间计算过程

## 更新日志

### v1.0.0
- 初始版本发布
- 支持基本重试控制功能
- 自动适配Redis Cluster
- 三种使用方式
- 指数退避算�?

## 支持

如有问题请提交Issue或联系开发团队