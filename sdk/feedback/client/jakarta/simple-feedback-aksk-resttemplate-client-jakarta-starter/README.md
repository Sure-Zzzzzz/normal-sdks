# simple-feedback-aksk-resttemplate-client-jakarta-starter

为 Spring Boot 3.4 / Java 17+ 应用提供 Feedback AKSK RestTemplate 客户端。客户端契约来自 `simple-feedback-client-core:1.0.1`，认证、连接池和超时复用 Jakarta AKSK 底座提供的 `akskClientRestTemplate`。

```groovy
implementation 'io.github.sure-zzzzzz:simple-feedback-aksk-resttemplate-client-jakarta-starter:1.0.1'
implementation 'io.github.sure-zzzzzz:simple-feedback-client-core:1.0.1'
implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-jakarta-starter:1.0.0'
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
