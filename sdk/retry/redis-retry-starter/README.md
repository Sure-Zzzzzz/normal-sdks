# Redis Retry Starter

基于Redis的分布式重试机制Starter，提供可靠的重试计数和间隔控制功能。

## 版本信息

当前版本：`1.0.0`

## 依赖引用

由于本组件使用`compileOnly`依赖，使用时需要显式引入相关依赖：

### Gradle

```gradle
dependencies {
    implementation "io.github.surezzzzzz:redis-retry-starter:1.0.0"
    
    // 必须显式引入以下依赖
    implementation "org.springframework.boot:spring-boot-starter-data-redis"
    implementation "org.springframework.boot:spring-boot-autoconfigure"
    implementation "org.springframework.boot:spring-boot-configuration-processor"
    implementation "org.springframework.boot:spring-boot-starter-validation"
    implementation "com.fasterxml.jackson.core:jackson-databind"
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.surezzzzzz</groupId>
    <artifactId>redis-retry-starter</artifactId>
    <version>1.0.0</version>
</dependency>
+

<!-- 必须显式引入以下依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## 核心功能

- **分布式重试计数**：基于Redis实现跨实例的重试计数
- **重试间隔控制**：支持可配置的重试间隔时间
- **最大重试次数限制**：防止无限重试
- **重试信息查询**：获取当前重试状态和详细信息
- **重试记录清除**：支持手动清除重试记录

## 主要API

### RedisRetryService

```java
@Autowired
private RedisRetryService redisRetryService;
```

#### 判断是否可以重试
```java
boolean canRetry = redisRetryService.canRetry(String retryContext, String retryKey);
```

#### 记录重试失败
```java
redisRetryService.recordRetry(String retryContext, String retryKey, 
                              int maxRetryTimes, int retryInterval, TimeUnit timeUnit);
```

#### 获取当前重试信息
```java
RetryInfo retryInfo = redisRetryService.getCurrentRetryInfo(String retryContext, String retryKey);
```

#### 清除重试记录
```java
redisRetryService.clearRetry(String retryContext, String retryKey);
```

## 使用示例

```java
@Service
public class BusinessService {
    
    @Autowired
    private RedisRetryService redisRetryService;
    
    public void processWithRetry(String businessId) {
        String retryContext = "business-process";
        String retryKey = businessId;
        
        // 检查是否可以重试
        if (!redisRetryService.canRetry(retryContext, retryKey)) {
            throw new RuntimeException("已达到最大重试次数");
        }
        
        try {
            // 执行业务逻辑
            doBusinessLogic(businessId);
            
            // 成功后清除重试记录
            redisRetryService.clearRetry(retryContext, retryKey);
            
        } catch (Exception e) {
            // 记录重试失败
            redisRetryService.recordRetry(retryContext, retryKey, 5, 60, TimeUnit.SECONDS);
            throw e;
        }
    }
}
```

## 配置说明

本组件会自动配置，无需额外配置。确保Redis连接配置正确即可：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: your-password
    database: 0
```

## 测试参考

详细的API使用示例请参考单元测试：
- `RedisRetryServiceTest.java` - 完整的API使用示例

测试用例覆盖了以下场景：
- 首次执行重试判断
- 重试次数累加
- 最大重试次数限制
- 重试间隔控制
- 重试记录清除
- 不同重试键的独立性
- 不同重试上下文的独立性

## 注意事项

1. 由于使用`compileOnly`依赖，必须显式引入所有相关依赖
2. 确保Redis服务可用且配置正确
3. 重试键(`retryKey`)和重试上下文(`retryContext`)的组合应该是唯一的
4. 重试间隔时间单位可以灵活配置（秒、分钟、小时等）