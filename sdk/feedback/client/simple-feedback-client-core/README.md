# simple-feedback-client-core

Feedback 公开 HTTP 契约的 Java 客户端契约包。它只定义请求参数、响应快照、附件下载结果和错误分类，不创建网络连接、不获取令牌，也不依赖 Spring。需要调用 Feedback Server 的服务引入本模块，再按宿主运行时选择一个认证传输 starter。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-client-core:1.0.1'
}
```

`FeedbackClient` 的方法对应 Feedback OpenAPI 1.0.1 的公开操作：反馈提交、管理列表与详情、处理、附件下载、本人的列表与详情、提交提示、可选应用和注册表读写。请求体使用 `Map` 表示网络字段，避免把服务端内部 DTO 带入消费方；返回的提交、分页、提示和注册表结果会先经过客户端的公开结构校验并以快照返回。

## 错误分类

- `FeedbackClientValidationException`：调用参数不满足客户端契约，修正参数后再发起请求。
- `FeedbackClientProtocolException`：服务端返回 4xx，或响应缺少契约要求的结构，不应按网络故障盲目重试。
- `FeedbackClientRetryableException`：网络异常、429 或 5xx；是否重试由调用方策略决定，提交重试时复用同一个 `Idempotency-Key`。

客户端不会转发人员令牌。认证传输 starter 负责使用宿主提供的 AKSK `RestTemplate` 获取机器凭据并发送请求；服务端以 SERVICE 主体进入同一 API/DATA 三权链，owner 归一为 `aksk:{clientId}`。

## 版本边界

本版本的传输实现只有 `simple-feedback-aksk-resttemplate-client-starter:1.0.1`（Spring Boot 2.7.9 / Java 8）。Feign 和 Spring Boot 3/Jakarta 传输件不属于本版本支持范围，也没有从本模块间接提供。

## 兼容性

Java 8 字节码；无 Spring 依赖，可被 Spring Boot 2.x 或其他 Java 8 宿主引用。公开 HTTP 字段和状态码以仓内 `sdk/feedback/contract/openapi/simple-feedback-server.openapi.yaml` 的 1.0.1 契约为准。

## 版本映射

| 客户端契约 | 对接服务端契约 | 说明 |
|---|---|---|
| 1.0.1 | simple-feedback-server-starter 1.0.1 | 13 个 REST 操作、幂等提交和分页查询 |
