# prometheus-client-starter

基于 Apache HttpClient 的轻量级 Prometheus Remote-Write & Query SDK，Spring Boot 一键接入。

## 功能
- **Remote-Write**：向 Prometheus 推送指标（protobuf 编码，gzip 压缩，重试机制）
- **Instant Query**：瞬时查询 `/api/v1/query`
- **Range Query**：范围查询 `/api/v1/query_range`
- **自动重试**：可配置重试次数与退避策略
- **Basic Auth**：用户名/密码认证
- **Spring Boot Starter**：零配置注入 `PrometheusClient`

## 快速开始

### 1. 引入依赖

```xml
<!-- 本 SDK -->
<dependency>
    <groupId>io.github.surezzzzzz</groupId>
    <artifactId>prometheus-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 必须手动引入的 compileOnly 依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>javax.annotation</groupId>
    <artifactId>javax.annotation-api</artifactId>
    <version>1.3.2</version>
</dependency>
```

> ⚠️ 注意：上面这 5 个坐标在 SDK 中被声明为 `compileOnly`，请**务必**自行引入，否则运行时会报 `ClassNotFoundException`。

### 2. 配置

```yaml
prometheus:
  write:
    host: http://localhost:9090
    write-uri: /api/v1/write
    content-type: application/x-protobuf
    content-encoding: gzip
    x-prometheus-remote-write-version: 0.1.0
    username: ""      # Basic Auth 用户名（可选）
    password: ""      # Basic Auth 密码（可选）
  read:
    host: http://localhost:9090
    query-uri: /api/v1/query
    query-range-uri: /api/v1/query_range
    username: ""
    password: ""
```

### 3. 注入使用

```java
@Autowired
private PrometheusClient prometheusClient;

// 推送指标
Remote.WriteRequest request = Remote.WriteRequest.newBuilder()
        .addTimeseries(timeSeries)
        .build();
prometheusClient.write(request);

// 瞬时查询
QueryInstantResponse resp = prometheusClient.query("up");

// 范围查询
QueryRangeResponse rangeResp = prometheusClient.queryRange(
        "up",
        1729767247.153,
        1729853647.153,
        60);
```

## API 列表

| 方法 | 说明 |
| ---- | ---- |
| `write(WriteRequest)` | 远程写入 |
| `query(String promql)` | 瞬时查询 |
| `query(String promql, String host)` | 指定 host 瞬时查询 |
| `query(QueryInstantRequest)` | 对象式瞬时查询 |
| `queryRange(String promql, double start, double end, int step)` | 范围查询 |
| `queryRange(String promql, double start, double end, int step, String host)` | 指定 host 范围查询 |
| `queryRange(QueryRangeRequest)` | 对象式范围查询 |

## 请求/响应对象

- **QueryInstantRequest** → **QueryInstantResponse**
- **QueryRangeRequest** → **QueryRangeResponse**

字段与官方 HTTP API 一一对应，可直接 Jackson 序列化。

## 重试策略

默认：最大重试 3 次，固定间隔 2 秒；可通过 `RetryExecutor` Bean 自定义。

## 日志

仅打印框架级 DEBUG/ERROR/WARN 日志，**不会输出业务指标名、标签值等敏感信息**。

## 示例项目

见 `src/test/java/.../PrometheusClientTest.java` 完整示例。

