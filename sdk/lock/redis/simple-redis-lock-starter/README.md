# Simple Redis Lock Starter

基于Redis的分布式锁Spring Boot Starter，提供简单易用的分布式锁功能。

## 功能特性

- 🚀 **简单易用** - 基于Spring Boot自动配置，开箱即用
- 🔒 **分布式锁** - 基于Redis实现可靠的分布式锁
- ⏰ **自动过期** - 支持锁自动过期，防止死锁
- 🔒 **互斥锁** - 确保同一时刻只有一个客户端持有锁
- 🧪 **完整测试** - 包含embedded-redis单元测试，无需外部Redis环境
- 📊 **监控日志** - 详细的操作日志，便于问题排查

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:simple-redis-lock-starter:1.0.0'
}
```

### 2. 配置Redis连接

在`application.yml`中配置Redis连接信息：

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    
    @Autowired
    private SimpleRedisLock simpleRedisLock;
    
    public void processOrder(String orderId) {
        String lockKey = "order:" + orderId;
        String requestId = UUID.randomUUID().toString();
        int expireTime = 30; // 30秒过期时间
        
        try {
            // 尝试获取锁
            if (simpleRedisLock.tryLock(lockKey, requestId, expireTime)) {
                try {
                    // 执行业务逻辑
                    doBusiness(orderId);
                } finally {
                    // 释放锁
                    simpleRedisLock.unlock(lockKey, requestId);
                }
            } else {
                // 获取锁失败的处理
                throw new RuntimeException("获取锁失败，订单正在处理中");
            }
        } catch (Exception e) {
            log.error("处理订单失败", e);
            throw e;
        }
    }
}
```

## API文档

### tryLock方法

```java
/**
 * 尝试获取分布式锁
 * @param lockKey 锁的键名
 * @param requestId 请求标识（用于解锁时验证）
 * @param expireTime 过期时间（秒）
 * @return 是否成功获取锁
 */
boolean tryLock(String lockKey, String requestId, int expireTime)

/**
 * 尝试获取分布式锁（支持时间单位）
 * @param lockKey 锁的键名
 * @param requestId 请求标识
 * @param expireTime 过期时间
 * @param timeUnit 时间单位
 * @return 是否成功获取锁
 */
boolean tryLock(String lockKey, String requestId, long expireTime, TimeUnit timeUnit)
```

### unlock方法

```java
/**
 * 释放分布式锁
 * @param lockKey 锁的键名
 * @param requestId 请求标识（必须和加锁时的requestId一致）
 * @return 是否成功释放锁
 */
boolean unlock(String lockKey, String requestId)
```

## 使用示例

### 基本使用

```java
// 获取锁
boolean locked = simpleRedisLock.tryLock("user:123", "request-001", 30);
if (locked) {
    try {
        // 执行业务逻辑
        processUser(123);
    } finally {
        // 释放锁
        simpleRedisLock.unlock("user:123", "request-001");
    }
}
```

### 带时间单位的使用

```java
// 设置5分钟过期时间
boolean locked = simpleRedisLock.tryLock("task:execute", "worker-1", 5, TimeUnit.MINUTES);
```

### 错误处理

```java
public void safeProcess(String key) {
    String lockKey = "lock:" + key;
    String requestId = Thread.currentThread().getId() + "-" + System.currentTimeMillis();
    
    try {
        if (simpleRedisLock.tryLock(lockKey, requestId, 10)) {
            // 业务逻辑
            doSomething(key);
        } else {
            log.warn("获取锁失败，key: {}", key);
            throw new BusinessException("系统繁忙，请稍后重试");
        }
    } finally {
        // 确保锁被释放
        simpleRedisLock.unlock(lockKey, requestId);
    }
}
```

## 配置说明

### Redis配置

```yaml
spring:
  redis:
    host: localhost          # Redis服务器地址
    port: 6379              # Redis服务器端口
    database: 0             # 数据库索引
    timeout: 2000ms         # 连接超时时间
    password:               # 密码（如果有）
    lettuce:
      pool:
        max-active: 8       # 最大连接数
        max-idle: 8         # 最大空闲连接数
        min-idle: 0         # 最小空闲连接数
        max-wait: -1ms      # 最大等待时间
```

### 日志配置

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.lock.redis: DEBUG  # 开启调试日志
```

## 最佳实践

### 1. 锁键命名规范

- 使用业务前缀，如：`order:`, `user:`, `payment:`
- 包含唯一标识，如订单ID、用户ID
- 示例：`order:12345`, `user:67890:profile`

### 2. 过期时间设置

- 根据业务处理时间设置合理的过期时间
- 一般建议30-300秒
- 避免设置过短导致业务未完成锁就过期
- 避免设置过长导致死锁风险

### 3. 请求ID生成

```java
// 推荐方式：UUID
String requestId = UUID.randomUUID().toString();

// 或者：线程ID + 时间戳
String requestId = Thread.currentThread().getId() + "-" + System.currentTimeMillis();

// 或者：业务相关ID
String requestId = "order-" + orderId + "-" + System.currentTimeMillis();
```

### 4. 异常处理

```java
public void processWithLock(String key) {
    String lockKey = "lock:" + key;
    String requestId = UUID.randomUUID().toString();
    
    try {
        if (simpleRedisLock.tryLock(lockKey, requestId, 30)) {
            try {
                // 业务逻辑
                doBusiness(key);
            } catch (Exception e) {
                log.error("业务处理失败", e);
                throw e;
            } finally {
                // 确保释放锁
                simpleRedisLock.unlock(lockKey, requestId);
            }
        } else {
            log.warn("获取锁失败，key: {}", key);
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
    } catch (Exception e) {
        log.error("处理失败", e);
        throw e;
    }
}
```

## 测试

项目包含完整的单元测试，使用embedded-redis，无需外部Redis环境：

```bash
./gradlew test
```

测试覆盖：
- ✅ 基础加锁解锁功能
- ✅ 锁的互斥性验证
- ✅ 锁过期机制测试
- ✅ 并发竞争测试
- ✅ 异常场景测试

## 注意事项

1. **非可重入锁** - 当前实现不支持同一线程重复获取锁，同一线程第二次获取会失败
2. **锁的粒度**：尽量使用细粒度锁，避免大范围锁定
3. **过期时间**：必须设置合理的过期时间，防止死锁
4. **解锁验证**：解锁时必须使用相同的requestId，防止误解锁
5. **异常处理**：确保在finally块中释放锁，避免死锁
6. **性能考虑**：高并发场景下注意Redis连接池配置
