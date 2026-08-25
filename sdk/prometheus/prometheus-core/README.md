# prometheus-core

Prometheus Remote Storage 协议的 Protobuf Java 绑定。它只提供稳定的协议对象，不持有 endpoint、认证、HTTP 客户端、连接池或重试策略。

> **1.x 已封版**：`1.0.0` 是 1.x 最终版本，冻结使用说明见 [README.1.x.md](README.1.x.md)。`2.0.0` 与 Route 原生化的 Client 依赖链同步发布，协议生成代码保持不变。`2.0.1` 修复 Protobuf CVE 漏洞。

## 快速接入

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:prometheus-core:2.0.1'
}
```

`protobuf-java` 与 `protobuf-java-util` 以传递依赖提供，业务无需重复声明。`2.0.1` 起两者版本升至 `3.25.5`（修复已知 CVE），由仓库根 `build.gradle` 统一约束，不在本模块单独声明版本号。

## 使用

```java
Remote.WriteRequest request = Remote.WriteRequest.newBuilder()
        .addTimeseries(timeSeries)
        .build();

byte[] payload = request.toByteArray();
```

`prometheus-client-starter:2.0.0` 已传递依赖本模块。业务只需自行直接引用 Core 的协议对象时，才需要单独引入本模块。

## 边界

- 仅包含 `Remote`、`Types` 等 Protobuf 协议生成对象。
- 不提供 Prometheus Server 的 HTTP 调用、认证、路由、连接池或重试。
- 不提供 Micrometer、exporter、采集或应用 metrics 能力。

## 生成说明

协议变更时更新源 `.proto` 并重新生成 Java 文件；仅在协议本身变化时调整生成代码。
