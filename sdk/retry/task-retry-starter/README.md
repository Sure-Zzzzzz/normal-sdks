# Task Retry Starter

进程内同步任务重试 SDK，提供默认策略、显式策略、固定延迟、指数退避、快速重试和慢速重试能力。

## 版本信息

当前版本：`2.0.0`

`2.0.0` 是破坏性规范化重构版本：包结构、自动配置、配置前缀、异常体系和公共延迟单位均已调整。

## 依赖引用

由于本组件使用 `compileOnly` 依赖，调用方需要显式引入运行时依赖。

### Gradle

```gradle
dependencies {
    implementation "io.github.surezzzzzz:task-retry-starter:2.0.0"
    implementation "org.springframework.boot:spring-boot-autoconfigure"
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.surezzzzzz</groupId>
    <artifactId>task-retry-starter</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
```

## 快速开始

默认引包即用，不需要编写 `application.yml`。

### 默认策略

```java
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import org.springframework.stereotype.Service;

@Service
public class RetryDemoService {

    private final TaskRetryExecutor taskRetryExecutor;

    public RetryDemoService(TaskRetryExecutor taskRetryExecutor) {
        this.taskRetryExecutor = taskRetryExecutor;
    }

    public String execute() throws Exception {
        return taskRetryExecutor.execute(() -> callExternalService());
    }
}
```

### 显式指数退避

```java
String result = taskRetryExecutor.executeWithRetry(
        () -> callExternalService(),
        3,
        1000L,
        1.5D,
        10000L
);
```

`retryTimes` 表示失败后最多重试次数，总执行次数为 `retryTimes + 1`。所有延迟参数单位均为毫秒。

### 固定延迟

```java
String result = taskRetryExecutor.executeWithFixedDelay(
        () -> callExternalService(),
        3,
        1000L
);
```

### 预置策略

```java
String fastResult = taskRetryExecutor.executeWithFastRetry(() -> callExternalService());
String slowResult = taskRetryExecutor.executeWithSlowRetry(() -> callExternalService());
```

### 显式请求模型

```java
import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;

RetryRequest request = RetryRequest.builder()
        .retryTimes(3)
        .initialDelayMillis(1000L)
        .backoffMultiplier(2.0D)
        .maxDelayMillis(10000L)
        .strategyType(RetryStrategyType.EXPONENTIAL)
        .build();

String result = taskRetryExecutor.execute(() -> callExternalService(), request);
```

## 可选配置

一般情况下不需要配置文件；只有需要统一覆盖默认策略、快速策略、慢速策略，或显式关闭自动装配时才需要配置。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          task:
            default-policy:
              retry-times: 5
              initial-delay-millis: 5000
              backoff-multiplier: 1.5
              max-delay-millis: 30000
            fast-policy:
              retry-times: 5
              initial-delay-millis: 2000
              backoff-multiplier: 1.2
              max-delay-millis: 10000
            slow-policy:
              retry-times: 5
              initial-delay-millis: 10000
              backoff-multiplier: 2.0
              max-delay-millis: 60000
```

如果需要关闭默认自动装配：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          task:
            enable: false
```

## 扩展点

- `RetryPredicate`：判断异常是否继续重试，默认所有 `Exception` 都允许重试。
- `RetrySleeper`：执行等待，默认使用 `TimeUnit.MILLISECONDS.sleep`，测试或调用方可替换为自定义 Bean。
- `RetryListener`：重试过程回调，默认空实现。

### 自定义 RetryPredicate

```java
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetryDemoConfiguration {

    @Bean
    public RetryPredicate retryPredicate() {
        return (exception, attempt, request) -> !(exception instanceof IllegalArgumentException);
    }
}
```

### 自定义 RetrySleeper

```java
import io.github.surezzzzzz.sdk.retry.task.sleeper.RetrySleeper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetryDemoConfiguration {

    @Bean
    public RetrySleeper retrySleeper() {
        return delayMillis -> {
        };
    }
}
```

## 1.x 升级到 2.0.0

- `RetryPackage` 改为 `TaskRetryPackage`。
- `configuration.RetryComponent` 改为 `annotation.TaskRetryComponent`。
- `RetryConfiguration` 改为 `TaskRetryAutoConfiguration`。
- 新增配置前缀 `io.github.surezzzzzz.sdk.retry.task`，默认零配置启用；显式配置 `enable=false` 时关闭默认 Bean。
- `retryInterval` 语义改为 `initialDelayMillis`，`maxDelaySeconds` 语义改为 `maxDelayMillis`。
- 2.0.0 公共 API 延迟单位统一为毫秒；如果 1.x 调用方按秒传参，升级时需要换算为毫秒。
- 参数校验失败抛 `TaskRetryValidationException`，最终执行失败仍抛出任务最后一次原始异常。

## 注意事项

- 本模块只做进程内同步重试，不保存任务状态，不做跨实例协调，不替代分布式重试能力。
- `RetryListener` 异常只记录 debug 日志，不覆盖任务异常。
- 线程等待被中断时会恢复中断标记，并继续抛出 `InterruptedException`。
