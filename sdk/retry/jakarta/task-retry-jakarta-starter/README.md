# Task Retry Jakarta Starter

面向 Spring Boot 3 应用的进程内同步任务重试组件。它在同一业务线程内，对短暂故障的调用按指定策略再次执行，并将最终结果或异常返回给调用方。

适合重试可安全重复执行的查询，或已具备幂等保护（重复提交不会产生额外业务效果）的写入。它不保存任务状态、不跨实例协调，不替代消息队列、延迟任务或分布式任务系统。

## 快速接入

### 1. 添加依赖

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:task-retry-jakarta-starter:1.0.0"
    implementation "org.springframework.boot:spring-boot-autoconfigure"
}
```

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>task-retry-jakarta-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
```

`spring-boot-autoconfigure` 由本组件以编译期依赖方式使用；应用已经通过其他 Spring Boot starter 引入它时，无需重复声明。

本组件与面向 Spring Boot 2 的 `task-retry-starter` 使用相同公开包名。一个应用只能选择其中一个，不可同时引入。

### 2. 注入并执行任务

无需配置即可注入 `TaskRetryExecutor`。下面的示例重试只读查询；任务最终失败时，原始异常会返回给调用方。

```java
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    private final TaskRetryExecutor taskRetryExecutor;
    private final UserProfileClient userProfileClient;

    public UserProfileService(TaskRetryExecutor taskRetryExecutor,
                              UserProfileClient userProfileClient) {
        this.taskRetryExecutor = taskRetryExecutor;
        this.userProfileClient = userProfileClient;
    }

    public UserProfile findBySubjectId(String subjectId) throws Exception {
        return taskRetryExecutor.execute(
                () -> userProfileClient.findBySubjectId(subjectId)
        );
    }
}
```

不要把没有幂等保护的创建、扣款或外部状态变更直接放入重试任务。需要重试写操作时，应先由业务侧提供稳定的幂等键或等价保护。

## 选择重试策略

`retryTimes` 表示首次失败后的额外重试次数：`retryTimes = 3` 最多执行 `4` 次，`retryTimes = 0` 只执行一次。所有延迟参数的单位均为毫秒。

| 调用方法 | 策略来源 | 适用场景 |
| --- | --- | --- |
| `execute(task)` | 默认预置策略 | 使用应用统一默认值 |
| `executeWithFastRetry(task)` | 快速预置策略 | 需要更短等待时间的短暂故障 |
| `executeWithSlowRetry(task)` | 慢速预置策略 | 可接受更长等待时间的短暂故障 |
| `executeWithRetry(task, retryTimes, initialDelayMillis, backoffMultiplier, maxDelayMillis)` | 显式指数退避策略 | 单次调用需要独立参数 |
| `executeWithFixedDelay(task, retryTimes, delayMillis)` | 显式固定延迟策略 | 每次等待时间相同 |
| `execute(task, retryRequest)` | `RetryRequest` | 需要指定指数、固定或无延迟策略 |

默认预置策略如下；可通过配置覆盖。

| 预置策略 | 额外重试次数 | 初始等待 | 退避倍数 | 最大等待 |
| --- | --- | --- | --- | --- |
| 默认 | `5` | `5000` | `1.5` | `30000` |
| 快速 | `5` | `2000` | `1.2` | `10000` |
| 慢速 | `5` | `10000` | `2.0` | `60000` |

指数退避的第 `n` 次等待时间为 `initialDelayMillis * backoffMultiplier^(n - 1)`，超过 `maxDelayMillis` 时取最大值。固定延迟始终使用 `initialDelayMillis`；无延迟策略每次等待为 `0`。

```java
String profile = taskRetryExecutor.executeWithRetry(
        () -> userProfileClient.findRawProfile(subjectId),
        3,
        1000L,
        2.0D,
        10000L
);
```

上例最多执行 `4` 次；三次等待依次为 `1000`、`2000`、`4000` 毫秒。

`executeWithRetry(task, retryTimes, initialDelayMillis)` 的简写形式仅覆盖前两个参数，退避倍数和最大等待时间继承当前默认预置策略。需要让一次调用完全独立于应用配置时，使用上面的五参数形式。

需要无延迟策略或完整模型时，使用 `RetryRequest`：

```java
import io.github.surezzzzzz.sdk.retry.task.constant.RetryStrategyType;
import io.github.surezzzzzz.sdk.retry.task.model.RetryRequest;

RetryRequest request = RetryRequest.builder()
        .retryTimes(2)
        .strategyType(RetryStrategyType.NONE)
        .build();

String profile = taskRetryExecutor.execute(
        () -> userProfileClient.findRawProfile(subjectId),
        request
);
```

`retryTimes`、延迟值不能小于 `0`；`backoffMultiplier` 不能小于 `1`。指数或固定延迟策略还要求 `maxDelayMillis` 不小于 `initialDelayMillis`。参数不满足约束时会抛出 `TaskRetryValidationException`。

## 配置预置策略

仅在需要统一调整预置策略或关闭自动装配时配置。自动装配是应用启动时由 Spring Boot 自动注册默认组件的机制。三个策略组均支持 `retry-times`、`initial-delay-millis`、`backoff-multiplier` 和 `max-delay-millis`。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          task:
            default-policy:
              retry-times: 3
              initial-delay-millis: 1000
              backoff-multiplier: 2.0
              max-delay-millis: 10000
            fast-policy:
              retry-times: 2
              initial-delay-millis: 500
              backoff-multiplier: 1.5
              max-delay-millis: 2000
            slow-policy:
              retry-times: 5
              initial-delay-millis: 5000
              backoff-multiplier: 2.0
              max-delay-millis: 30000
```

设置以下配置后，组件不会注册默认执行器、判断器、等待器或监听器：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          task:
            enable: false
```

## 扩展行为

应用可以分别声明一个同类型 Spring Bean（由 Spring 容器管理的对象）覆盖默认实现；替换一个扩展点不会影响其他三个默认组件。

| 扩展点 | 职责 | 异常语义 |
| --- | --- | --- |
| `TaskRetryExecutor` | 完全替换默认执行流程 | 由应用实现决定 |
| `RetryPredicate` | 在任务失败且还有剩余次数时决定是否继续重试，默认重试所有 `Exception` | 返回 `false` 时抛出原任务异常；自身抛出的异常直接返回调用方 |
| `RetrySleeper` | 执行两次任务之间的等待 | 抛出 `InterruptedException` 时恢复线程中断标记，并将该异常返回调用方 |
| `RetryListener` | 接收执行前、失败和成功回调 | 监听器异常不会阻断任务执行 |

例如，只重试输入输出异常：

```java
import io.github.surezzzzzz.sdk.retry.task.predicate.RetryPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class RetryConfiguration {

    @Bean
    public RetryPredicate retryPredicate() {
        return (exception, attempt, request) -> exception instanceof IOException;
    }
}
```

默认执行器会以 `WARN` 记录失败位点和下一次等待时间，以 `DEBUG` 记录异常类型；不会记录异常消息、异常栈、认证信息、请求体或响应体。

## 兼容性

模块以 Java `17` 编译，已在以下 Spring Boot 与 JDK 组合完成完整测试：

| Spring Boot | 验证 JDK |
| --- | --- |
| `3.4.2` | `17`、`21` |
| `3.3.13` | `17`、`21` |
| `3.2.12` | `17`、`21` |
