# simple-feedback-aksk-feign-client-jakarta-starter

为 Spring Boot 3.4 / Java 17+ 应用提供 Feedback AKSK Feign 传输实现。内部 Feign 接口只承载公开 wire Map 和下载响应，边界适配器再调用 `simple-feedback-client-core:1.0.1` 的响应校验器构造快照，避免把 core 模型直接交给 Jackson。

```groovy
implementation 'io.github.sure-zzzzzz:simple-feedback-aksk-feign-client-jakarta-starter:1.0.1'
implementation 'io.github.sure-zzzzzz:simple-feedback-client-core:1.0.1'
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-jakarta-starter:1.1.0'
implementation 'io.github.openfeign:feign-hc5'
```

宿主配置 `io.github.surezzzzzz.sdk.feedback.client.base-url` 和 `enable`，并提供 AKSK 底座的 `akskFeignRequestInterceptor`。自动配置基线为 Spring Boot 3.4.2 / Spring Cloud 2024simple-feedback-aksk-feign-client-jakarta-starter:1.1.0 / OpenFeign 4.2.0，提交附件使用内存 multipart，下载结果保留 `Content-Type` 与 `Content-Disposition`。同一应用不要同时引入 javax 和 Jakarta 两条传输线。

## AKP Profile

本模块支持 AKSK Client 多 AKP Profile。调用方在 AKSK Client 底座的 `auth.aksk.client.profiles.<name>`
中声明 Profile，再通过本模块配置键 `io.github.surezzzzzz.sdk.feedback.client.aksk-profile` 选择 Profile。

- 配置留空或 `default` 时使用顶层默认 AKP，原有接入不变。
- 配置命名 Profile 时，RestTemplate 走底座 Profile 模板；Feign 只挂一个 Profile 认证拦截器。
- Profile 不存在或凭据不完整时启动失败，不静默改用默认 AKP。
- Secret 由 AKSK 底座管理；本模块不输出 Secret、clientId、Token 或 Authorization。
