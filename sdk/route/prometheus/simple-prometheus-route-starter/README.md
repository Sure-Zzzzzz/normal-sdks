# simple-prometheus-route-starter

面向 Prometheus Server 的固定 target 集群路由组件。调用方使用显式 `targetKey` 选择已登记的 Prometheus Server，并通过统一的同步 HTTP 门面执行 Prometheus HTTP API 或 Remote Write 请求。

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-prometheus-route-starter:1.0.0'

    // Route 内部基于 Apache HttpClient 4.x；由业务按自身依赖治理提供运行时版本。
    implementation 'org.apache.httpcomponents:httpclient'
}
```

`simple-prometheus-route-starter` 对 Apache HttpClient 使用 `compileOnly`，不会传递或锁定业务工程的 HTTP 客户端版本。业务可同时使用 OkHttp 等其他 HTTP 客户端；只有启用 Route 时，运行时 classpath 需要存在兼容的 Apache HttpClient 4.x。

## 接入

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        prometheus:
          route:
            enable: true
            targets:
              test-main:
                url: http://localhost:19090
              test-secondary:
                url: http://localhost:19091
```

Route 只接受显式登记的 `targetKey`，不提供默认 target、模糊匹配、PromQL/指标自动路由或 host/URL 覆盖。调用方通过 `PrometheusRouteTemplate.exchange(targetKey, request)` 发送结构化的相对 path、query 参数、header 和可选二进制 body。

```java
PrometheusRouteResponse response = prometheusRouteTemplate.exchange("monitoring-primary",
        new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query",
                Collections.singletonList(new PrometheusRouteParameter("query", "up")),
                Collections.emptyList(), null));
```

每个 target 使用独立的 Apache HttpClient 4.x 连接池。Route 注入固定认证，禁止自动重试、自动重定向、cookie 管理和透明响应解压，并将响应转换为有大小上限的不可变快照；底层 response 不会暴露给调用方。

连接池和调用模型：

- `exchange(...)` 是同步调用，由调用方线程执行网络 I/O；Route 不创建隐藏业务线程池、不提供异步旁路，也不自动重试。
- `max-total` 和 `max-per-route` 限制单个 target 的并发连接容量；每个 target 的连接资源彼此隔离。
- 连接池容量耗尽时，请求最多等待 `connection-request-timeout-ms`；等待超时映射为 `PROMETHEUS_ROUTE_005`，不会转发到 Prometheus。
- 业务需要更高并发时，应显式配置足够的 target 连接池，并由业务自己的 executor 管理调用方并发；Route 不提供无限排队。
- `validate-after-inactivity-ms` 控制连接池复用长期空闲连接前的可用性校验间隔。

示例 HTTP 配置：

```yaml
targets:
  test-main:
    url: http://localhost:19090
    http:
      connect-timeout-ms: 3000
      socket-timeout-ms: 10000
      connection-request-timeout-ms: 2000
      validate-after-inactivity-ms: 1000
      max-total: 20
      max-per-route: 20
      max-response-body-bytes: 10485760
```

Remote Write 所需的 `Content-Type`、`Content-Encoding`、`User-Agent` 和 `X-Prometheus-Remote-Write-Version` 可以作为普通请求 header 传入。Route 不解析 Prometheus 协议、不判断业务状态码、不自动重试。

## 边界

- 一个 `targetKey` 固定映射一个 Prometheus Server 或逻辑集群端点；联邦入口可作为一个独立 target 配置。
- Route 不提供指标级路由、PromQL 解析、目标组/fan-out、默认/fallback target、任意 URL 或 host 覆盖。
- 调用方不能覆盖 `Authorization`、`Host`、`Content-Length`、`Cookie`、`Accept-Encoding` 等 Route 管理的 header。
- `NONE`、`BASIC`、`BEARER` 三种认证都由 target 配置固定注入；账号、密码和 token 应通过部署环境管理，不能写入版本库。

## Spring Boot 兼容性

1.0.0 已完成以下完整测试矩阵。每一档均使用真实 Prometheus 2.37.0 和 2.45.2 执行 buildinfo 与 query E2E，并执行全部模块测试（23 项，零跳过、零失败、零错误）。

| Spring Boot | JDK | Gradle | 状态 |
| --- | --- | --- | --- |
| 2.2.13.RELEASE | 8 | 7.6 | 已验证 |
| 2.3.12.RELEASE | 8 | 7.6 | 已验证 |
| 2.4.5 | 8 | 7.6 | 已验证 |
| 2.7.9 | 11 | 8.5 | 已验证 |
