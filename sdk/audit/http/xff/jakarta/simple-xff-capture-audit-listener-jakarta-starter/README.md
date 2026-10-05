# Simple XFF Capture Audit Listener Jakarta Starter

面向需要记录 X-Forwarded-For（XFF，代理转发的地址链）事实的 Spring Boot 3 Servlet 应用。本模块接收 Capture Starter 发布的 `XffCaptureEvent`，生成不可变的 `XffCaptureAuditDocument`，再交给日志或业务提供的存储 Provider 处理。它不判断哪个地址是真实客户端 IP，也不自行连接数据库或 Elasticsearch。

## 最小接入

在已有 Servlet Web 应用中同时引入采集与监听模块：

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-jakarta-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-listener-jakarta-starter:1.0.0'
}
```

```yaml
spring:
  application:
    name: sample-service

io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
        audit:
          http:
            xff:
              capture:
                listener:
                  enable: true
```

两个开关默认都关闭。启用后，每个 Capture 事件先在请求线程中完成上下文读取和文档构造，再交给专用有界线程池异步分发。默认日志 Provider（文档处理器）会输出 `eventId`、应用名、可选 `requestId`、XFF 是否存在及合法 IP 数量；它不保存文档。只有引入业务自己的存储 Provider 后，审计文档才会写入相应介质。

`application-name` 可覆盖审计文档中的应用名；未配置时使用 `spring.application.name`。两者都为空时启动失败。

## 使用审计文档

业务持久化时，把实现了 `XffCaptureAuditPersistenceProvider` 的 Bean 注册到 Spring，并在 `persist(XffCaptureAuditDocument document)` 中调用业务自己的存储接口。不要注册空实现：Listener 不代替业务保存审计数据。

每个 Provider 收到同一份不可变文档，按 Spring 容器确定的顺序调用。文档包含事件时间、应用与请求标识、请求方法和不含查询参数的 URI、原始 XFF 与固定转发请求头列表、规范化 IP 列表，以及 Capture 已生成的请求数据快照。原始地址是事实记录，不表示该地址可信；可选 Query、Form、Body 快照是否存在仍由 Capture 的白名单和采集配置决定。

默认日志 Provider 与自定义 Provider 并存，不是自定义存储失败后的回退。单个 Provider 抛出运行时异常时，Listener 记录事件 ID、Provider 类型和异常类型，然后继续调用后续 Provider；不会把异常传回原 HTTP 请求。

## 可选请求上下文

宿主可以提供零个或一个 `XffCaptureAuditContextProvider`，在 Capture 事件所在的请求线程补充 `requestId`、`traceId` 和业务扩展字段。下面的例子仅适用于宿主已将这两个标识写入 MDC（线程内日志上下文）的情况：

```java
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContextProvider;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditContextConfiguration {

    @Bean
    public XffCaptureAuditContextProvider auditContextProvider() {
        return () -> new XffCaptureAuditContext(
                MDC.get("requestId"), MDC.get("traceId"));
    }
}
```

Context Provider 只读取当前线程已建立的上下文，不应在事件回调中查询数据库或反向调用业务服务。多个 Provider 会因来源不明确而导致启动失败；多个来源应由宿主合并成一个 Provider。读取上下文失败只会丢失可选上下文字段，不会丢弃 XFF 网络事实。扩展字段在交给 SDK 前应完成脱敏，并由实际存储方控制访问与留存。

## 配置与失败边界

以下配置位于 `io.github.surezzzzzz.sdk.audit.http.xff.capture.listener` 下：

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `enable` | `false` | 启用事件监听和默认日志 Provider |
| `application-name` | 使用 `spring.application.name` | 审计文档中的应用名 |
| `executor.core-size` | `2` | 常驻工作线程数 |
| `executor.max-size` | `4` | 最大工作线程数 |
| `executor.queue-capacity` | `1000` | 等待处理的任务数上限 |
| `executor.keep-alive-seconds` | `60` | 非常驻线程的空闲存活时间 |
| `executor.await-termination-seconds` | `10` | 停机时等待任务完成的时间 |

执行器配置非法时启动失败。审计分发采用尽力而为语义：文档转换失败、队列满、任务提交失败或进程异常退出都没有自动重试和持久化补偿；默认日志也不能保证这些事件一定留痕。需要不丢失审计记录的业务，应在宿主侧设计可靠队列或事务性持久化，不能把本模块当作可靠消息系统。

Listener 不读取 Servlet 请求、不重新采集请求头或请求体，也不创建 Capture Filter、Elasticsearch Client、路由、索引或模板。默认 INFO 日志只记录受控摘要；按模块包名开启 DEBUG 可定位事件接收、文档构建、任务提交和 Provider 执行阶段，不输出完整 XFF、请求数据、Cookie、Token、密码或异常消息。

## 版本与验证

| 应用运行时 | Capture Starter | Audit Listener |
| --- | --- | --- |
| Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5`、`2.7.9` / javax Servlet | `simple-xff-capture-starter:1.1.2` | `simple-xff-capture-audit-listener-starter:1.1.1` |
| Spring Boot `3.2.12`、`3.3.13`、`3.4.2` / Jakarta Servlet | `simple-xff-capture-jakarta-starter:1.0.0` | `simple-xff-capture-audit-listener-jakarta-starter:1.0.0` |

两条线的 Servlet 类型不兼容，一个应用只能选择对应运行时的一条。Jakarta Listener 传递依赖 `simple-xff-capture-audit-core:1.1.1`，无需重复声明；WebFlux 不在支持范围内。

Jakarta Listener 的完整测试在上述三个 Spring Boot 版本分别搭配 Java `17` 和 `21` 执行，六种组合各 30 个测试、零失败、零跳过。测试包含真实 HTTP 请求到 Capture 事件、异步 Provider 文档投影的链路；未连接真实 Elasticsearch 或其他外部存储。
