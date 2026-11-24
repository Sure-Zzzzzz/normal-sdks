# simple-feedback-aksk-resttemplate-client-jakarta-starter

为 Spring Boot 3.4 / Java 17+ 应用提供 Feedback AKSK RestTemplate 客户端。客户端契约来自 `simple-feedback-client-core:1.0.1`，认证、连接池和超时复用 Jakarta AKSK 底座提供的 `akskClientRestTemplate`。

```groovy
implementation 'io.github.sure-zzzzzz:simple-feedback-aksk-resttemplate-client-jakarta-starter:1.0.1'
implementation 'io.github.sure-zzzzzz:simple-feedback-client-core:1.0.1'
implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-jakarta-starter:1.1.0'
```

配置服务地址：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        feedback:
          client:
            enable: true
            base-url: http://feedback.example.internal
```

自动配置只在 `AutoConfiguration.imports` 注册，并在宿主已有 `FeedbackClient` 时让出 Bean。附件使用内存字节，不读取本地文件；资源标识按单个 URI 路径段编码，响应结构经 core validator 校验，4xx、5xx/429 和网络错误分别映射为不可重试协议异常或可重试异常。

兼容性基线为 Spring Boot 3.4.2 / Java 17；Spring Boot 2.7.9 / Java 8 宿主使用 `simple-feedback-aksk-resttemplate-client-starter:1.0.1`。两条运行时线共享 `simple-feedback-client-core:1.0.1`，同一应用只选择一条。

## AKP Profile

本模块支持 AKSK Client 多 AKP Profile。调用方在 AKSK Client 底座的 `auth.aksk.client.profiles.<name>`
中声明 Profile，再通过本模块配置键 `io.github.surezzzzzz.sdk.feedback.client.aksk-profile` 选择 Profile。

- 配置留空或 `default` 时使用顶层默认 AKP，原有接入不变。
- 配置命名 Profile 时，RestTemplate 走底座 Profile 模板；Feign 只挂一个 Profile 认证拦截器。
- Profile 不存在或凭据不完整时启动失败，不静默改用默认 AKP。
- Secret 由 AKSK 底座管理；本模块不输出 Secret、clientId、Token 或 Authorization。
