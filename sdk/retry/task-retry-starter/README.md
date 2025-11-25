# Task Retry Starter

通用任务重试执行器Starter，提供灵活的重试机制和退避策略。

## 版本信息

当前版本：`1.0.0`

## 依赖引用

由于本组件使用`compileOnly`依赖，使用时需要显式引入相关依赖：

### Gradle

```gradle
dependencies {
    implementation "io.github.surezzzzzz:sdk-task-retry-starter:1.0.0"
    
    // 必须显式引入以下依赖
    implementation "org.springframework.boot:spring-boot-autoconfigure"
    implementation "org.springframework.boot:spring-boot-configuration-processor"
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.surezzzzzz</groupId>
    <artifactId>sdk-task-retry-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 必须显式引入以下依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
</dependency>
```

## 核心功能

- **灵活的重试机制**：支持自定义重试次数、初始延迟、退避系数和最大延迟
- **多种重试策略**：提供指数退避、固定延迟、快速重试、慢速重试等多种策略
- **异常处理**：支持所有类型的异常，任务执行失败时自动重试
- **返回值支持**：支持各种返回值类型的任务重试
- **线程安全**：所有重试操作都是线程安全的

## 主要API

### TaskRetryExecutor

```java
@Autowired
private TaskRetryExecutor taskRetryExecutor;
```

#### 基础重试方法
```java
// 完整参数的重试方法
T result = taskRetryExecutor.executeWithRetry(Callable<T> task, int maxRetries, 
                                             long initialDelay, double backoffMultiplier, 
                                             long maxDelay);

// 简化版重试方法（默认退避策略）
T result = taskRetryExecutor.executeWithRetry(Callable<T> task, int maxRetries, long initialDelay);

// 默认重试配置（3次重试，1秒初始延迟）
T result = taskRetryExecutor.executeWithRetry(Callable<T> task);
```

#### 固定延迟重试
```java
T result = taskRetryExecutor.executeWithFixedDelay(Callable<T> task, int maxRetries, long delay);
```

#### 预定义重试策略
```java
// 快速重试（2次重试，500ms延迟）
T result = taskRetryExecutor.executeWithFastRetry(Callable<T> task);

// 慢速重试（5次重试，5秒初始延迟，2倍退避，60秒最大延迟）
T result = taskRetryExecutor.executeWithSlowRetry(Callable<T> task);
```

## 使用示例

### 基础使用

```java
@Service
public class BusinessService {
    
    @Autowired
    private TaskRetryExecutor taskRetryExecutor;
    
    public String processWithRetry(String param) {
        try {
            return taskRetryExecutor.executeWithRetry(() -> {
                // 执行业务逻辑
                return callExternalService(param);
            }, 3, 1000); // 最多重试3次，初始延迟1秒
            
        } catch (Exception e) {
            // 所有重试都失败后的处理
            log.error("处理失败，参数: {}", param, e);
            throw new RuntimeException("处理失败", e);
        }
    }
}
```

### 高级配置

```java
public void processWithAdvancedRetry() {
    String result = taskRetryExecutor.executeWithRetry(() -> {
        // 执行业务逻辑
        return performComplexOperation();
    }, 
    5,           // 最多重试5次
    2000,        // 初始延迟2秒
    1.5,         // 退避系数1.5倍
    30000        // 最大延迟30秒
    );
}
```

### 固定延迟重试

```java
public void processWithFixedDelay() {
    taskRetryExecutor.executeWithFixedDelay(() -> {
        // 每3秒重试一次，最多重试4次
        return executeTask();
    }, 4, 3000);
}
```

### 使用预定义策略

```java
public void processWithPredefinedStrategy() {
    // 使用快速重试策略
    String fastResult = taskRetryExecutor.executeWithFastRetry(() -> {
        return quickOperation();
    });
    
    // 使用慢速重试策略
    String slowResult = taskRetryExecutor.executeWithSlowRetry(() -> {
        return slowOperation();
    });
}
```

### 处理不同类型的返回值

```java
public void processDifferentTypes() {
    // Integer返回值
    Integer count = taskRetryExecutor.executeWithRetry(() -> {
        return getDataCount();
    }, 3, 1000);
    
    // Boolean返回值
    Boolean success = taskRetryExecutor.executeWithRetry(() -> {
        return validateData();
    }, 3, 1000);
    
    // 自定义对象返回值
    User user = taskRetryExecutor.executeWithRetry(() -> {
        return fetchUserInfo(userId);
    }, 3, 1000);
}
```

## 重试策略说明

### 指数退避策略
延迟时间 = min(初始延迟 × 退避系数^(重试次数-1), 最大延迟)

例如：初始延迟1秒，退避系数2，最大延迟30秒
- 第1次重试：延迟1秒
- 第2次重试：延迟2秒
- 第3次重试：延迟4秒
- 第4次重试：延迟8秒
- 第5次重试：延迟16秒

### 固定延迟策略
每次重试都使用相同的延迟时间。

### 预定义策略参数
- **快速重试**：2次重试，500ms固定延迟
- **慢速重试**：5次重试，5秒初始延迟，2倍退避，60秒最大延迟
- **默认重试**：3次重试，1秒初始延迟，1.5倍退避，30秒最大延迟

## 配置说明

本组件会自动配置，无需额外配置。如果需要自定义配置，可以创建对应的配置类。

## 测试参考

详细的API使用示例请参考单元测试：
- `TaskRetryExecutorTest.java` - 完整的API使用示例

测试用例覆盖了以下场景：
- 首次执行成功
- 重试后成功
- 所有重试都失败
- 指数退避计算
- 固定延迟重试
- 默认重试策略
- 快速重试策略
- 慢速重试策略
- 零重试次数
- 不同类型异常处理
- 最大延迟限制
- 不同返回值类型

## 注意事项

1. 由于使用`compileOnly`依赖，必须显式引入所有相关依赖
2. 重试次数包含初始执行，所以`maxRetries=3`表示最多执行4次（初始1次 + 重试3次）
3. 延迟时间单位为毫秒
4. 退避系数必须大于等于1
5. 最大延迟必须大于等于初始延迟
6. 所有重试操作都是线程安全的