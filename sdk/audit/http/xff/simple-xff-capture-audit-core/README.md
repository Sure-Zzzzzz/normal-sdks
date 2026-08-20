# Simple XFF Capture Audit Core

`simple-xff-capture-audit-core` 提供 XFF Capture 审计的纯 Java 领域契约：不可变审计文档和同步投影 SPI。生产代码仅依赖 JDK 8 与 `simple-xff-capture-core`，不依赖 Spring、Elasticsearch、Persistence 或 Route。

## 引入依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-core:1.0.0'
}
```

通常由 `simple-xff-capture-audit-listener-starter` 构造文档并调用 Provider；仅需要领域文档或自行消费 Capture 事件时，也可以直接引入本模块。

## 实现审计投影

实现 `XffCaptureAuditPersistenceProvider`，在 `persist` 中将已完成快照的文档投影到业务介质：

```java
public class CustomXffCaptureAuditPersistenceProvider
        implements XffCaptureAuditPersistenceProvider {

    @Override
    public void persist(XffCaptureAuditDocument document) {
        // 将 document 投影到业务所需介质
    }
}
```

`persist` 是同步 SPI。Core 不定义线程模型、重试、失败隔离或具体存储方式；由调用方决定这些行为。Provider 应将 `eventId` 作为幂等依据，并且不能修改收到的文档。

## 审计文档契约

`XffCaptureAuditDocument` 保存一次 Capture Event 的不可变审计快照，包含以下 15 个核心字段：

| 字段 | 说明 |
| --- | --- |
| `eventId` | 事件唯一标识 |
| `capturedTime` | UTC ISO-8601 捕获时间 |
| `applicationName` | 产生审计记录的应用名称 |
| `requestId` | 可选请求标识 |
| `traceId` | 可选链路标识 |
| `requestMethod` | HTTP 请求方法 |
| `requestUri` | 不含查询参数的请求 URI |
| `hostList` | 原始 Host Header 快照 |
| `xffPresent` | XFF Header 是否存在 |
| `xffRawList` | 原始 XFF 值链 |
| `xffIpList` | 规范化后的合法 XFF IP 列表 |
| `publicIpList` | `xffIpList` 中的公网 IP 子集 |
| `applicationRawRemoteAddress` | 应用看到的原始远端地址 |
| `applicationRemoteIp` | 规范化后的合法应用远端 IP，可为空 |
| `classificationVersion` | IP 分类规则版本 |

所有 List 和 Map 都会防御性复制，对外只暴露不可修改视图。`xffPresent=false` 时，`xffRawList`、`xffIpList` 与 `publicIpList` 必须均为空；`publicIpList` 只能包含 `xffIpList` 中的 `PUBLIC` 地址。

原始请求 URI、Host、XFF、远端地址及业务扩展不会出现在 `toString()` 中。调用方仍不应将完整审计文档输出到不受控日志。

## 业务扩展字段

业务可在固定顶层 `extensions` envelope 中记录 `clientId` 等自有字段：

- 类型固定为 `Map<String, String>`，不支持 `Map<String, Object>`；
- 键和值都会 trim，且均不得为空；
- 文档会防御性复制扩展字段，对外只暴露不可修改 Map；
- 旧构造器默认使用空扩展字段，兼容已有调用方；
- `toString()` 不输出扩展字段，业务方必须在传入前完成敏感值脱敏。

扩展字段是领域文档的一部分，但 Core 不定义其外部存储或查询语义。使用 Elasticsearch Provider 时，查询字段必须由部署模板显式声明 mapping。

## 模块边界

- `simple-xff-capture-audit-core`：领域文档与同步 Provider SPI；
- `simple-xff-capture-audit-listener-starter`：监听 Capture 事件、读取可选上下文、建立异步边界并广播 Provider；
- `simple-xff-capture-audit-es-persistence-provider-starter`：将文档投影到 Elasticsearch。

Core 不采集 HTTP 请求、不发布或监听 Spring Event、不创建线程池、不创建 Elasticsearch Client，也不管理 Persistence、Route、索引、模板或查询能力。

## 兼容性

`1.0.x` 中，15 个核心字段、扩展字段 envelope、`XffCaptureAuditPersistenceProvider` 方法签名，以及文档的不可变与校验语义均为稳定契约。新增字段应通过固定扩展 envelope 承载；破坏性字段或语义调整应使用新 minor 版本。