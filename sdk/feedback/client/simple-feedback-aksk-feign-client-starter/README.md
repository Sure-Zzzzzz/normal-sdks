# simple-feedback-aksk-feign-client-starter

为 Spring Boot 2 / Java 8 应用提供 Feedback `FeedbackClient` 的 AKSK Feign 传输实现。Feign wire 接口只承载公开 JSON Map 与下载响应，适配器再调用 `simple-feedback-client-core:1.0.1` 的响应校验器构造客户端快照，避免把服务端 DTO 引入调用方。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-feedback-aksk-feign-client-starter:1.0.1'
    implementation 'io.github.sure-zzzzzz:simple-feedback-client-core:1.0.1'
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:3.0.2'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

启用 Feign 扫描并扫描本模块的 wire 接口：

```java
@EnableFeignClients(basePackageClasses = FeedbackFeignClient.class)
```

自动配置会把 Feign 代理适配为 `FeedbackClient`。宿主必须提供 `base-url`，并按 AKSK 底座文档配置凭据与 Redis；机密值只通过运行环境注入。

## 配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `io.github.surezzzzzz.sdk.feedback.client.enable` | `true` | 是否装配 `FeedbackClient` 适配器 |
| `io.github.surezzzzzz.sdk.feedback.client.base-url` | 无（必填） | Feedback Server origin；`/api/feedback` 由接口固定 |

示例：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        feedback:
          client:
            enable: true
            base-url: http://feedback.example.test
```

## 行为约定

- `createSubmission` 使用 multipart：`submission` 为 JSON 对象，附件保留文件名和媒体类型；幂等键必须非空，失败重试复用原键。
- 列表方法接受 `Map<String, ?>` 查询参数，`null` 查询值由调用方省略；路径参数由 Feign 作为单个路径变量编码。
- 下载结果保留字节、`Content-Type` 与 `Content-Disposition`。
- 4xx 和响应结构错误抛 `FeedbackClientProtocolException`；429、5xx、网络或 Feign 无状态异常抛 `FeedbackClientRetryableException`；参数非法抛 `FeedbackClientValidationException`。
- 不记录 Authorization、请求体、附件内容或其他敏感值。

## 兼容性

本模块属于 javax / Spring Boot 2.7.9 / Spring Cloud 2021.0.8 / OpenFeign 3.1.8 线，Java 8+。Spring Boot 3 / Jakarta 应用使用对应的 `simple-feedback-aksk-feign-client-jakarta-starter`，不可在同一应用同时引入两条线。服务端和 core 契约基线为 1.0.1。
