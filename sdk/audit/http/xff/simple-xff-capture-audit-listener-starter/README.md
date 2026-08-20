# simple-xff-capture-audit-listener-starter

XFF Capture Audit 的事件监听与 Provider 广播层。模块监听 `simple-xff-capture-starter` 发布的 `XffCaptureEvent`，在同步事件线程读取可选上下文并构造一份不可变审计文档，再通过唯一有界执行器广播给全部 `XffCaptureAuditPersistenceProvider`。

本模块不依赖 Elasticsearch、Persistence 或 Route，可单独启用并只输出默认安全日志。

## 接入

```gradle
implementation 'io.github.sure-zzzzzz:simple-xff-capture-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-listener-starter:1.0.0'
```

```yaml
spring:
  application:
    name: example-service

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
                  application-name: example-service
                  executor:
                    core-size: 2
                    max-size: 4
                    queue-capacity: 1000
                    keep-alive-seconds: 60
                    await-termination-seconds: 10
```

`application-name` 未配置时读取 `spring.application.name`。两者都为空、执行器参数非法或配置校验失败时启动失败。Listener 关闭时不会注册 Factory、校验器、Listener、执行器或默认日志 Provider。

## 默认日志 Provider

Listener 启用后始终注册 Bean：

```text
loggingXffCaptureAuditPersistenceProvider
```

默认日志 Provider 与其他 Provider 广播共存，不是 Elasticsearch 失败后的 fallback，也不会因自定义 Provider 存在而消失。它只输出受控摘要：`eventId`、`applicationName`、`requestId`、XFF 是否存在及 IP 数量；不输出 URI、Host、Cookie、Token、认证头、完整 XFF 原文、业务扩展字段、异常消息、堆栈或文档 `toString()`。

队列成功入队后，默认日志 Provider 会在异步任务中执行；队列拒绝、进程退出或执行器停机造成的 best-effort 丢失不承诺必然留痕。

### 自动装配与注册

无需为默认日志 Provider 手工声明 `@Bean`。应用启用 Listener 后，Spring Boot 会通过本模块的 `spring.factories` 导入自动配置；自动配置以 Package Marker 为范围，仅扫描标记了 `SimpleXffCaptureAuditListenerComponent` 的内部组件。`LoggingXffCaptureAuditPersistenceProvider` 与 Listener、文档 Factory、配置校验器使用同一受限扫描链注册，其固定 Bean 名称为：

```text
loggingXffCaptureAuditPersistenceProvider
```

该名称保留给默认日志 Provider。若应用以同名 Bean 覆盖为其他类型，启动期校验会失败；业务扩展应使用不同名称的 `XffCaptureAuditPersistenceProvider` Bean。

## 自定义 Provider

```java
@Bean
public XffCaptureAuditPersistenceProvider customAuditProvider() {
    return document -> {
        // 将不可变文档投影到业务所需介质
    };
}
```

所有 Provider 都会收到同一份不可变文档，并按容器顺序同步调用。某个 Provider 抛出运行时异常时，Listener 只记录事件标识、Provider 类型和异常类型，然后继续调用后续 Provider。Provider 不应依赖其他 Provider 的成功或创建独立线程池。

## 可选上下文

```java
import java.util.Collections;

@Bean
public XffCaptureAuditContextProvider xffCaptureAuditContextProvider() {
    return () -> new XffCaptureAuditContext(
            currentRequestId(),
            currentTraceId(),
            Collections.singletonMap("clientId", currentClientId()));
}
```

`extensions` 固定为 `Map<String, String>`，键和值会 trim 且不得为空。Context 在同步事件线程中完成防御性复制；Listener 在异步交接前再构造一份不可变文档快照。业务方必须在传入前完成扩展值脱敏。

上下文 Provider 只能读取当前同步事件线程中已经建立的上下文。允许零个或一个 Provider；多个 Provider 会因来源歧义在启动时失败，需要组合来源时由应用提供一个组合实现。Provider 读取异常只丢失可选 `requestId`、`traceId` 和 `extensions`，不会丢弃网络事实。

## Elasticsearch Provider

需要 Elasticsearch 写入时，另外引入并启用 `simple-xff-capture-audit-es-persistence-provider-starter`。Persistence、Route、物理日期索引、模板和真实 Elasticsearch E2E 不属于本模块。

## 行为边界

- Listener 只负责事件监听、同步上下文快照、不可变文档构造和异步 Provider 广播。
- Listener 不定义业务扩展字段的存储或查询语义；需要 Elasticsearch 查询时由 Provider 与部署模板显式声明 mapping。
- Listener 不创建 Elasticsearch Client、Persistence Engine、Route、物理索引、模板或 Search。
- 事件转换失败、队列拒绝和 Provider 异常不会传播到 Capture 事件发布线程。
- 1.0.0 是 best effort，不承诺进程崩溃时队列内任务不丢失。
