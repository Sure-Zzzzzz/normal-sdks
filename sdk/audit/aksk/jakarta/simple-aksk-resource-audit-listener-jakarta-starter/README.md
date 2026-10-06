# Simple AKSK Resource Audit Listener Jakarta Starter

面向 Spring Boot 3 / Jakarta 应用的 AKSK 服务身份访问审计组件。监听公共资源安全链发布的 `ResourceAccessEvent`（已认证访问事件），转换为 `AkskAuditRecord`，再异步交给应用声明的 `AkskAuditHandler`（记录处理接口）。应用只需实现记录的存储或转发，无需自行编写公共事件监听器。

本模块不替代认证、授权或业务操作审计，也不自带存储实现。

## 版本与依赖

| 模块 | 版本 | 运行环境 |
|---|---|---|
| `simple-aksk-resource-audit-listener-jakarta-starter` | `1.0.0` | Java 17+，Spring Boot `3.4.2` 基线 |
| `simple-aksk-resource-audit-listener-starter` | `3.0.0` | 原 javax 运行线；不与 Jakarta 包同时引入 |

```groovy
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-audit-listener-jakarta-starter:1.0.0'
```

使用前提：应用已经接通公共资源安全链与 AKSK 认证来源。新建资源应用还需引入以下认证组件，并配置受保护路径、应用授权和 AKSK 内省（向认证服务验证令牌并获取身份）：

```groovy
implementation 'io.github.sure-zzzzzz:simple-resource-server-jakarta-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-jakarta-starter:1.0.0'
```

审计包只依赖公共事件模型 `simple-resource-server-core:1.1.1` 和 AKSK 来源常量 `simple-aksk-core:3.0.2`，不会自动安装上述认证组件，不调用 AKSK Server，不自带数据库或 Elasticsearch 客户端。人员与服务身份并存时，按需同时接入 IAM Resource 和 IAM Resource Audit；两个审计监听器各自按认证来源过滤。

## 最小配置

### 只输出访问日志

在已有 AKSK 资源认证的应用中，加入审计依赖并开启日志 Handler 即可，不需要自定义 Bean：

```yaml
io.github.surezzzzzz.sdk.audit.aksk.resource.listener:
  handler.log.enabled: true
```

每条日志只包含 `requestId`、`subjectType`、请求方法和事件时间戳，不输出整个记录。日志输出不代表已完成审计持久化。

### 交给应用存储

生产环境通常由应用声明 `AkskAuditHandler`，按自身存储方案处理记录。保留默认关闭的日志 Handler，避免重复输出：

```yaml
io.github.surezzzzzz.sdk.audit.aksk.resource.listener:
  enable: true
```

总开关默认开启，因此这一配置也可以省略。必须同时声明业务 Handler，示例见下方“使用记录”。仅引入依赖或设置 `enable: true`，既没有 Handler 又未开启日志时，不会注册官方监听器，也不会输出审计记录。

## 完整配置

```yaml
io.github.surezzzzzz.sdk.audit.aksk.resource.listener:
  enable: true
  handler:
    log:
      enabled: false
```

以上包含本模块全部配置项。线程池、持久化、重试均不是本模块的配置项；诊断日志使用 Spring Boot 标准的 `logging.level` 配置。

## 配置项说明

| 配置项 | 默认值 | 含义 |
|---|---|---|
| `enable` | `true` | 注册模块自带组件。设为 `false` 时不注册官方监听器、日志 Handler 或模块配置 Bean；宿主自有 Bean 不受影响 |
| `handler.log.enabled` | `false` | 输出最小访问日志摘要；开启后与业务 Handler 并存，不代表记录已持久化 |

配置前缀为 `io.github.surezzzzzz.sdk.audit.aksk.resource.listener`。

未提供自定义监听器时，装配结果如下：

| 总开关 | 日志 Handler | 业务 Handler | 官方监听器与消费结果 |
|---|---|---|---|
| 开启 | 关闭 | 无 | 不注册监听器，不消费 |
| 开启 | 开启 | 无 | 注册监听器，仅输出日志摘要 |
| 开启 | 关闭 | 有 | 注册监听器，仅交给业务 Handler |
| 开启 | 开启 | 有 | 注册监听器，同时分发给日志和业务 Handler |
| 关闭 | 任意 | 任意 | 不注册官方组件；宿主自有 Handler Bean 保留，但官方监听器不调用它 |

## 使用记录

在应用自己的配置类中声明 Handler，无需手工导入自动配置、编写 `@EventListener` 或添加 SDK 内部组件注解：

```java
import io.github.surezzzzzz.sdk.audit.aksk.resource.handler.AkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ResourceAuditConfiguration {
    /** 将记录交给应用的存储服务。 */
    @Bean
    public AkskAuditHandler businessAuditHandler(AuditRecordWriter writer) {
        return writer::write;
    }

    /** 示例中的应用接口，由应用提供实现并注册为 Bean。 */
    public interface AuditRecordWriter {
        void write(AkskAuditRecord record);
    }
}
```

`AuditRecordWriter` 不是 SDK 内置接口，而是示例中的应用存储边界，应用需要提供实现。也可以让已有的 `@Component` 类直接实现 `AkskAuditHandler`。官方监听器由自动配置根据 Handler Bean 注册，不在监听器的组件扫描阶段提前判断 Handler 是否存在。

## 记录字段

| 字段 | 含义 |
|---|---|
| `authenticationSourceId` | 认证来源，官方监听器仅转换 `aksk` |
| `subjectType` | 公共事件主体类型的枚举名称；AKSK 服务身份通常为 `SERVICE` |
| `subjectId` | 已验证的主体标识；服务身份通常为客户端 ID，不是网络地址 |
| `applicationCode` | 目标应用编码 |
| `requestId` | 公共安全链中的请求关联标识 |
| `requestUri` | 公共事件的请求路径；正常资源链不包含查询参数 |
| `httpMethod` | 请求方法 |
| `remoteAddr` | 公共事件中的网络来源地址，不据此推断已验证身份或真实客户端 |
| `userAgent` | 公共事件中的客户端标识原值，存储前按需脱敏 |
| `timestamp` | 公共事件生成时的毫秒时间戳，不替换为异步消费时间 |
| `traceId` | 可选 `AkskAuditTraceIdProvider` 返回的追踪标识；未提供或提供者失败时为 `null` |

字段不包含令牌、密钥、认证头、请求正文或权限集合。路径、客户端标识与地址仍可能包含业务或个人信息，应用应在写入前执行自身脱敏与保留规则。

## 分发与扩展

- 只消费 `ResourceAccessEvent` 中的 AKSK 来源，忽略 IAM 及其他来源。
- 使用 Spring `@Async` 异步执行，模块自动启用 Spring 异步处理。对同一事件，多个 Handler 按 Spring 注入列表的顺序依次调用，可用 `@Order` 指定优先级，数值较小者优先；每个 Handler 获取独立记录副本，修改不会污染后续 Handler。
- 不同事件可能并发消费，不保证跨请求顺序。Handler 应支持并发调用，不在共享可变字段中保存当前请求的记录。
- 单个 Handler 抛出普通异常时记录错误并继续分发，不回传给请求线程；本模块不重试、不保证持久化成功。
- 宿主提供任意名称的 `AkskAuditEventListener` Bean 时，官方监听器按类型让位，避免重复注册。总开关不会停止宿主自己声明的监听器。
- `AkskAuditTraceIdProvider` 为可选扩展接口。存在多个实现时需由宿主指定唯一候选，例如使用 `@Primary`。

## 最佳实践

### 用 Handler 承接存储

让应用实现 `AkskAuditHandler`，复用官方事件转换和来源过滤，不再另写 `ResourceAccessEvent` 监听器。写入失败、重试、幂等（重复处理不产生额外结果）、记录保留和查询授权由应用自己的存储方案负责；不要将 Handler 被调用等同于审计持久化完成。

写入前将记录转换为应用自己的存储模型，选择必要字段并脱敏。用 `requestId` 关联请求，但不要假定它是全局唯一的持久化主键或仅凭它实现所有场景的去重。多个存储 Handler 不构成一个事务，一个成功、另一个失败时不会自动回滚已完成的写入。

### 区分访问记录与业务操作审计

本模块消费的是公共资源层的已认证访问事件，不是业务事务成功事件，也没有 HTTP 状态码、业务结果、耗时或异常结果字段。不能仅凭该记录判定请求授权成功或业务提交成功。未认证请求不会生成此类已认证访问记录；需要记录认证失败、权限拒绝或业务终态时，应接入相应事件或在业务自己的终态边界记录，不伪造本记录的结果字段。

### 明确异步上下文边界

`AkskAuditTraceIdProvider#getTraceId()` 在异步消费线程调用，每条记录转换时读取一次。本模块不自动传播发布线程的 `ThreadLocal`（线程局部变量）、日志 MDC（线程内日志上下文）或请求上下文；不要直接读取这些对象并假定能获得请求线程数据。未配置可靠传播方案时允许 `traceId` 为空，使用记录已有的 `requestId` 关联请求。

只有宿主已经建立可在消费线程访问的追踪上下文时，才注册该 Provider。没有 Provider 或 Provider 抛出普通异常时，不中断记录分发，`traceId` 保留为 `null`。

### 由宿主管理异步执行

宿主可通过 Spring 的 `taskExecutor` Bean 或 `AsyncConfigurer`（异步执行器配置接口）配置有界线程池、监控和关闭等待。模块不自建专用线程池；调整宿主默认异步执行器可能影响应用其他 `@Async` 方法，不要重复声明已有的同名 Bean。

下面是宿主尚无异步执行器时的示例，容量值需要按实际请求量和写入耗时调整，不是 SDK 默认值：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class ApplicationAsyncConfiguration {
    /** 管理宿主默认异步执行器的容量与关闭等待。 */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("application-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
```

示例采用 `AbortPolicy`，容量耗尽时拒绝提交并抛出异常。提交发生在发布线程，拒绝异常不属于监听器内部的 Handler 异常隔离范围，可能影响发布方；宿主应明确选择并监控拒绝策略。改用 `CallerRunsPolicy` 时可能在提交线程直接执行 Handler，增加请求耗时，不能再假定所有消费都在独立线程完成。静默丢弃策略可能丢失记录，不应用于需要完整审计的场景。

线程池排队、进程异常退出或关闭等待超时都可能使记录无法完成消费。关闭等待只能改善正常退出，不提供崩溃恢复；本模块不是持久化消息队列，不保证可靠投递。需要可靠审计时，应由应用建立持久化交接和失败恢复机制，不以请求成功反推审计成功。

### 控制日志中的数据

默认日志 Handler 关闭，生产存储模式通常保持关闭。排障时可开启模块 `DEBUG`，查看装配、来源过滤、分发及追踪提供者失败分支：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.audit.aksk.resource: DEBUG
```

排障结束后恢复正常日志级别。应用自定义 Handler 也不应直接打印整个记录或异常中的敏感报文；模块错误日志只记录处理器类型、异常类型等摘要，实际存储失败原因由应用自己的存储边界进行受控诊断。

## 常见问题

| 现象 | 检查项 |
|---|---|
| 加入依赖后没有日志或记录 | 默认日志关闭，需声明 `AkskAuditHandler` 或开启日志；同时检查总开关 |
| Handler 存在，但官方监听器不存在 | 检查总开关、自动配置是否被排除，以及是否已声明同类型自定义监听器 |
| 监听器存在，但没有收到记录 | 确认请求走公共资源认证链且认证来源为 `aksk`；IAM 来源和未认证请求不会转换为本记录 |
| 一次访问被记录两次 | 排查应用是否还保留自行编写的公共事件监听器，以及业务 Handler 是否重复注册 |
| `traceId` 为空 | 检查 Provider 是否注册、是否异常、消费线程是否能访问追踪上下文；没有上下文时为空是正常结果 |
| 请求正常但存储缺记录 | 分别检查事件消费、执行器容量及 Handler 写入结果；请求结果不能证明审计写入成功 |

## 运行线切换

从旧 javax 包切换时，替换依赖为 Jakarta 包，并配套使用 Jakarta 资源组件。Handler、TraceId Provider、记录类的包名和公开字段与旧线 `3.0.0` 保持一致，默认日志仍关闭；同一应用不可同时引入两条运行线，否则相同类名会产生冲突。

## 验证范围

验证基线为 Spring Boot `3.4.2`、Gradle `8.5`。其他 Spring Boot 补丁版本不由下表推定为已验证。

| 验证项 | Java | 用例 | 结果 |
|---|---|---|---|
| 模块完整测试 | 17 | 16 | 无失败、错误或跳过 |
| 模块完整测试 | 21 | 16 | 无失败、错误或跳过 |
| 独立 JAR/POM 消费 | 17 | 1 | 自动发现监听器，宿主 `@Bean` Handler 单次消费 |

测试覆盖自动配置发现、宿主 `@Bean` 和组件扫描 Handler、无 Handler、开关、日志并存、来源过滤、字段转换、可选追踪提供者、Handler 异常与修改隔离、监听器按类型让位以及异步队列消费。

HTTP 集成测试使用 MockMvc（Spring 的模拟 HTTP 请求测试工具），走正式公共资源安全链和 AKSK Provider，仅替代内省网络边界。独立制品消费验证使用 JAR 与 POM（Maven 依赖描述），不引用模块源码。以上证明组件装配和事件分发，不等同于真实 AKSK Server 网络联调、业务存储验收、线程池饱和或可靠投递测试。
