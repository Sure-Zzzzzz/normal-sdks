# simple-prometheus-route-jakarta-starter

面向 Spring Boot 3.4.2、Java 17+ 的 Prometheus 固定 target 路由组件。调用方使用显式 `targetKey` 选择已登记的 Prometheus Server，并通过统一的同步 HTTP 门面执行 Prometheus HTTP API 或 Remote Write 请求。

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-prometheus-route-jakarta-starter:1.0.0'

    // Route 内部基于 Apache HttpClient 4.x；由业务按自身依赖治理提供运行时版本。
    implementation 'org.apache.httpcomponents:httpclient'
}
```

`simple-prometheus-route-jakarta-starter` 对 Apache HttpClient 使用 `compileOnly`，不会传递或锁定业务工程的 HTTP 客户端版本。业务可同时使用其他 HTTP 客户端；只有启用 Route 时，运行时 classpath 需要存在兼容的 Apache HttpClient 4.x。

## 接入

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        prometheus:
          route:
            enable: true
            shutdown-timeout-ms: 10000
            targets:
              monitoring-primary:
                url: https://prometheus.example.invalid
                authentication:
                  type: BEARER
                  token: ${PROMETHEUS_TOKEN}
                http:
                  connect-timeout-ms: 3000
                  socket-timeout-ms: 10000
                  connection-request-timeout-ms: 2000
                  validate-after-inactivity-ms: 1000
                  max-total: 20
                  max-per-route: 20
                  max-response-body-bytes: 10485760
```

认证类型为 `NONE`、`BASIC`、`BEARER`。`BASIC` 必须同时提供 `username` 与 `password`；`BEARER` 必须只提供 `token`。凭据应由部署环境注入，不能写入版本库。

Route 只接受显式登记的 `targetKey`，不提供默认 target、模糊匹配、PromQL/指标自动路由或 host/URL 覆盖。调用方通过 `PrometheusRouteTemplate.exchange(targetKey, request)` 发送结构化的相对 path、query 参数、header 和可选二进制 body。

```java
PrometheusRouteResponse response = prometheusRouteTemplate.exchange("monitoring-primary",
        new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query",
                Collections.singletonList(new PrometheusRouteParameter("query", "up")),
                Collections.emptyList(), null));
```

每个 target 使用独立的 Apache HttpClient 4.x 连接池。Route 注入固定认证，禁止自动重试、自动重定向、cookie 管理和透明响应解压，并将响应转换为有大小上限的不可变快照；底层 response 不会暴露给调用方。

## 调用与关闭

- `exchange(...)` 是同步调用，由调用方线程执行网络 I/O；Route 不创建业务线程池、不提供异步旁路，也不自动重试。
- `max-total` 和 `max-per-route` 限制单个 target 的并发连接容量；容量耗尽时最多等待 `connection-request-timeout-ms`，失败映射为 `PROMETHEUS_ROUTE_005`，不会转发到 Prometheus。
- `validate-after-inactivity-ms` 控制连接池复用长期空闲连接前的可用性校验间隔。
- 容器关闭时，Route 立即拒绝新请求，并在 `shutdown-timeout-ms` 内等待已准入请求完成；到期后会关闭该 target 的 HTTP 连接。因此长请求应将自身超时控制在该窗口内，窗口到期仍未完成的请求可能以 `PROMETHEUS_ROUTE_005` 失败。

Remote Write 所需的 `Content-Type`、`Content-Encoding`、`User-Agent` 和 `X-Prometheus-Remote-Write-Version` 可以作为普通请求 header 传入。Route 不解析 Prometheus 协议、不判断业务状态码、不自动重试。

## 边界

- 一个 `targetKey` 固定映射一个 Prometheus Server 或逻辑集群端点；联邦入口可作为独立 target 配置。
- Route 不提供指标级路由、PromQL 解析、目标组/fan-out、默认/fallback target、任意 URL 或 host 覆盖。
- 调用方不能覆盖 `Authorization`、`Host`、`Content-Length`、`Cookie`、`Accept-Encoding` 等 Route 管理的 header。
- target URL 只允许 `http` 或 `https`，且不能带 user-info、query、fragment 或路径穿越；请求 path 必须是 target 内以 `/` 开始的相对路径。

## 兼容性与验收资产

该模块面向 Spring Boot `3.4.2` 与 Java `17+`。同一应用不能同时引入 javax 线 `simple-prometheus-route-starter` 和本 Jakarta 线，两条线包名与调用契约相同，只替换依赖坐标。

`1.0.0` 已在 Spring Boot `3.4.2`、Java `17`、Gradle `8.5` 下完成模块完整测试；验收覆盖固定 target、认证、请求隔离、连接池、响应上限、关闭生命周期，以及 Prometheus `2.37.0`、`2.45.2` 的 buildinfo 与 query 端到端请求。

`docker-compose.prometheus-e2e-matrix.yml` 提供上述双版本 Prometheus 本地验收 target。
