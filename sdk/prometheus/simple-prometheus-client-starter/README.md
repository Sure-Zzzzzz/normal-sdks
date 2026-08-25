# simple-prometheus-client-starter

面向业务方的 Prometheus 客户端 Starter。业务方使用它完成 Remote Write 指标写入和 PromQL 即时/范围查询；target 管理、连接池、认证和 HTTP 生命周期全部由 `simple-prometheus-route-starter` 负责，Client 只做协议翻译。

## 依赖

Gradle：

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-prometheus-client-starter:1.0.0'

    // Client 基于 Route 执行请求，需要一并引入。
    implementation 'io.github.surezzzzz:simple-prometheus-route-starter:1.0.0'

    // Route 内部基于 Apache HttpClient 4.x；由业务按自身依赖治理提供运行时版本。
    implementation 'org.apache.httpcomponents:httpclient'
}
```

Maven：

```xml
<dependency>
    <groupId>io.github.surezzzzz</groupId>
    <artifactId>simple-prometheus-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Client 的 JSON 响应解析使用 Jackson Databind，Snappy 压缩使用 snappy-java，均由 Starter 传递；业务无需额外配置。写入示例中的 `Remote` 与 `Types` 是 protobuf 生成类，来自传递依赖 `prometheus-core`，直接 import 即可使用。

## 接入

Client 不配置 Prometheus URL。在 Route 侧登记 target，之后每次调用显式传入 `targetKey`：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        prometheus:
          route:
            enable: true
            targets:
              monitoring-primary:
                url: http://prometheus-a.internal:9090
                authentication:
                  type: BASIC
                  username: ${PROM_USERNAME}
                  password: ${PROM_PASSWORD}
              monitoring-secondary:
                url: http://prometheus-b.internal:9090
                authentication:
                  type: NONE
```

账号、密码和 token 应通过部署环境管理，不能写入版本库。Route 支持 `NONE`、`BASIC`、`BEARER` 三种认证，`Authorization` 由 Route 按 target 自动注入。

引入依赖后即可注入 `PrometheusClientTemplate` 使用，容器中实例唯一。连接池、超时、认证等可定制能力全部在 Route 层，业务声明自己的 `PrometheusRouteTemplate` Bean 即可覆盖默认实现。业务未登记任何 Route target 时应用启动即失败，并提示缺少 `PrometheusRouteTemplate`。

### 写入指标

```java
@Autowired
private PrometheusClientTemplate prometheusClient;

public void recordOrderAmount(double amount) {
    Remote.WriteRequest request = Remote.WriteRequest.newBuilder()
            .addTimeseries(Types.TimeSeries.newBuilder()
                    .addLabels(Types.Label.newBuilder()
                            .setName("__name__").setValue("business_order_amount"))
                    .addLabels(Types.Label.newBuilder()
                            .setName("env").setValue("production"))
                    .addSamples(Types.Sample.newBuilder()
                            .setTimestamp(Instant.now().toEpochMilli())
                            .setValue(amount)))
            .build();
    prometheusClient.write("monitoring-primary", request);
}
```

写入固定使用 Remote Write 协议：`POST /api/v1/write`、`application/x-protobuf`、`Content-Encoding: snappy`、`X-Prometheus-Remote-Write-Version: 0.1.0`，由 Client 自动完成压缩和协议头。写入目标需要在 Prometheus 侧启用 `--web.enable-remote-write-receiver`。

### 查询指标

```java
public void query() {
    // 即时查询；time 传 null 表示使用 Prometheus 当前时间。
    QueryInstantResponse instant = prometheusClient.query("monitoring-primary", "up", null);

    // 范围查询；start/end 为 Instant，step 单位为秒。
    QueryRangeResponse range = prometheusClient.queryRange(
            "monitoring-primary", "up", start, end, 15);
}
```

方法契约：

- `write(String targetKey, Remote.WriteRequest writeRequest)`：Snappy 压缩 protobuf 后发送 Remote Write。
- `query(String targetKey, String promql, Instant time)`：即时查询，返回 `vector` 结果。
- `queryRange(String targetKey, String promql, Instant start, Instant end, int stepSeconds)`：范围查询，返回 `matrix` 结果。
- 查询时间戳使用 Unix 秒并保留三位小数，格式与 Locale 无关。
- 1.0.0 公开查询模型承诺 Prometheus `vector` 和 `matrix`；`scalar` 与 `string` 不在本版本范围内。

## 调用模型与边界

- `write`/`query`/`queryRange` 是同步调用，由调用方线程执行网络 I/O；Client 不创建隐藏线程池、不内置重试。需要重试时在业务调用边界显式实现。
- 没有默认 target：每次调用必须显式传入 `targetKey`，未登记的 `targetKey` 由 Route 抛出异常，不会回退到任何默认地址。
- Client 不拼接 URL、不添加 `Authorization`、不接受 host/URL 覆盖，也不创建 HttpClient 或连接池。
- 连接池、超时和响应体上限在 Route 侧按 target 配置，每个 target 使用独立连接池、资源彼此隔离：

```yaml
targets:
  monitoring-primary:
    url: http://prometheus-a.internal:9090
    http:
      connect-timeout-ms: 3000
      socket-timeout-ms: 10000
      connection-request-timeout-ms: 2000
      validate-after-inactivity-ms: 2000
      max-total: 20
      max-per-route: 20
      max-response-body-bytes: 4194304
```

完整 target 配置项见 `simple-prometheus-route-starter` 的 README。

## 异常

- `PrometheusClientException`：参数非法、Prometheus 返回非成功业务状态、HTTP 失败、非预期重定向或查询响应结构解析失败。异常只包含 targetKey、固定路径、状态码和响应体字节数等受控元数据，不输出 Query、正文或 Header。
- `PrometheusRouteException`：target 未登记、Route 配置错误、连接/传输失败、Route 已关闭或响应体超过 Route 限制。Route 异常由 Client 原样透传，不重新包装。

两类异常即故障分层：连接不通、target 未登记属于基础设施问题；写入被拒、查询失败属于业务或服务端问题。

Client 错误码：

- `PROMETHEUS_CLIENT_001`：写入被拒绝。
- `PROMETHEUS_CLIENT_002`：查询失败。
- `PROMETHEUS_CLIENT_003`：响应解析失败。
- `PROMETHEUS_CLIENT_004`：非预期重定向。
- `PROMETHEUS_CLIENT_005`：请求参数非法。

## 日志

Client 在 DEBUG 级别输出调用埋点：写入在请求开始与完成时各输出一条，查询在完成时输出一条，内容包含 targetKey、固定路径、序列数、压缩字节数、状态码和耗时毫秒，失败路径附带异常类型。日志只包含上述受控元数据，不输出 Query、标签、样本值、请求体或响应正文。排查调用行为时按包名开启：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.prometheus.client: DEBUG
```

## Spring Boot 兼容性

1.0.0 已完成以下完整测试矩阵。每一档均使用真实 Prometheus 2.37.0 和 2.45.2 执行 Remote Write 写入后的即时查询与范围查询回读 E2E，并执行全部模块测试（15 项，零跳过、零失败、零错误）。

| Spring Boot | JDK | Gradle | 状态 |
| --- | --- | --- | --- |
| 2.2.13.RELEASE | 8 | 7.6 | 已验证 |
| 2.3.12.RELEASE | 8 | 7.6 | 已验证 |
| 2.4.5 | 8 | 7.6 | 已验证 |
| 2.7.9 | 11 | 8.5 | 已验证 |
