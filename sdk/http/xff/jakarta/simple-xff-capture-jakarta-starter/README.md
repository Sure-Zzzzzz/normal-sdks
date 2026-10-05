# Simple XFF Capture Jakarta Starter

面向 Spring Boot 3.x Servlet 应用的 X-Forwarded-For（XFF，请求经过代理时携带的地址链）事实采集 Starter。它通过 Servlet Filter（请求过滤器）生成不可变快照，并发布进程内 `XffCaptureEvent` 事件，供业务读取或监听。模块不判断哪个地址是真实客户端 IP。

## 版本选择

| 应用运行时 | 使用模块 |
| --- | --- |
| Spring Boot 2.x / javax Servlet | `simple-xff-capture-starter:1.1.2` |
| Spring Boot 3.x / Jakarta Servlet | `simple-xff-capture-jakarta-starter:1.0.0` |

两条线保持相同包名、配置键和业务 API，但 Servlet 类型二进制不兼容；一个应用只能选择其中一条。WebFlux 不属于本模块范围。

本模块依赖 `simple-xff-capture-core:1.1.1`，使用下述 Starter 坐标即可传递获得 Core，无需再单独声明。Spring Boot `3.4.2`、`3.3.13`、`3.2.12` 分别在 Java `17` 和 `21` 下完成完整测试；默认构建基线为 Spring Boot `3.4.2` / Java `17`。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
```

启用后 Filter 注册在 `/*`，仅处理 `REQUEST` 分派。默认顺序为 `Ordered.LOWEST_PRECEDENCE - 100`，可通过 `order` 调整；需要跳过的应用内路径配置为 `excluded-path-patterns`。

## 使用采集结果

业务可按需注入 `XffCaptureService`：

```java
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import jakarta.servlet.http.HttpServletRequest;

public class RequestViewService {

    private final XffCaptureService xffCaptureService;

    public RequestViewService(XffCaptureService xffCaptureService) {
        this.xffCaptureService = xffCaptureService;
    }

    public boolean hasXff(HttpServletRequest request) {
        return xffCaptureService.capture(request).isPresent();
    }
}
```

同一请求会复用同一份快照，最多尝试发布一次事件。模块记录 XFF 原始事实，不以连接地址、`X-Real-IP` 或其他请求头回填缺失的 XFF。

固定采集的转发上下文仅包括 `Host`、`X-Real-IP`、`X-Forwarded-Host`、`X-Forwarded-Port`、`X-Forwarded-Proto`。每项保留是否存在及原始值列表；不会采集 Authorization、Cookie 或全量 Header。

## 可选请求数据快照

Query（URL 查询参数）、Form（表单参数）和 Body（请求体）默认关闭。启用其中任一项后，只有命中 `whitelist` 且未命中 `blacklist` 的请求才采集该项；规则按 HTTP 方法和应用内 URI（不含 context path）匹配，黑名单优先。Body 只有在 Content-Type（请求体媒体类型）命中 `allowed-content-types` 时才采集；列表默认为空，即不采集任何类型。`max-bytes` 是快照字节上限，默认 `65536`（64 KiB），可按实际数据范围收紧。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              request-data:
                query-parameters:
                  enabled: true
                body:
                  enabled: true
                  max-bytes: 4096
                  allowed-content-types:
                    - application/json
                whitelist:
                  - method: POST
                    path-pattern: /api/orders/**
```

读取受限 Body 时，模块会为下游 Controller 回放完整输入流。采集失败不会阻断原请求；下游 Servlet 或 IO 异常仍原样传播。

`max-bytes` 只限制采集快照大小，不限制实际请求体大小。Form 与 Body 同时启用，且 `application/x-www-form-urlencoded` 请求命中 Body 允许类型时，模块会将完整请求体暂存到磁盘以供下游回放，并解析完整表单；宿主必须在采集 Filter 之前限制请求体总大小，并为临时文件目录设置容量边界，网关可额外限制。格式错误的表单参数会标记为 `READ_FAILED`，但不会截断下游读取的请求体。Query、Form、Body 可能包含敏感数据，只应对确有需要的接口启用，并限制后续事件消费者的访问与留存。

## 审计接入与边界

本模块只采集并发布 Core 中定义的事件，不存储审计文档，也不创建 Elasticsearch 客户端、路由、模板或索引。需要异步审计落库时，可另行接入 `simple-xff-capture-audit-listener-jakarta-starter`，并由宿主提供 `XffCaptureAuditPersistenceProvider` 实现。

默认关闭的 DEBUG 诊断只用于定位 XFF 采集阶段，不记录 Authorization、Cookie、Token、密码、完整 Query、完整 Body 或全量请求头。
