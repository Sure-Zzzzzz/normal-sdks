# Simple AKSK RestTemplate Redis Client Jakarta Starter

`simple-aksk-resttemplate-redis-client-jakarta-starter` 为 Spring Boot 3.x 应用提供带 AKSK 认证的 `RestTemplate`。它从 Jakarta Redis Token Manager 获取 Token，并仅向使用该拦截器的请求添加 `Authorization` 头。

适合服务调用受 AKSK 保护的接口。它不校验入站请求、不依赖特定身份系统，也不推断目标接口是否有权访问；最终权限由目标资源服务的 OpenAPI、API 权限和数据权限决定。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-aksk-resttemplate-redis-client-starter` | `3.0.2` |
| Spring Boot 3.x（Jakarta） | `simple-aksk-resttemplate-redis-client-jakarta-starter` | `1.0.0` |

两条线不可同时引入。Jakarta 线要求 Java 17 或更高版本、Spring Boot 3.x 与 Spring Framework 6.x。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.apache.httpcomponents.client5:httpclient5'
}
```

该 starter 运行时依赖 Jakarta Redis Token Manager。直接注入 `TokenManager` 或使用其公共类型时，请额外声明 `simple-aksk-client-core-jakarta`。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              server-url: https://aksk.example.test
              token-endpoint: /oauth2/token
              client-id: example-client-id
              client-secret: ${AKSK_CLIENT_SECRET}
              resttemplate:
                enable: true
```

注入名称固定为 `akskClientRestTemplate` 的 Bean，或注入 `AkskRestTemplateInterceptor` 并加入业务自建的 `RestTemplate`。

## 行为边界

- 仅启用 `io.github.surezzzzzz.sdk.auth.aksk.client.enable=true`，且容器有 `TokenManager` 时装配。
- `resttemplate.enable=true` 时创建带 HttpClient 5 连接池的 `akskClientRestTemplate`；连接池实现不进入通用 AKSK core。
- 每次请求覆盖已有 `Authorization` 头，防止调用方误带过期 AKSK Token。
- Token 为空、空白或获取失败时抛出 `TokenFetchException`，不发送缺少认证头的请求，也不自动重试业务请求。
- 主代码 DEBUG 记录 HTTP 方法、目标主机、Authorization 头覆盖标记和响应状态码；DEBUG/WARN 不记录 Token、Client Secret、安全上下文、Authorization、Cookie、完整 URL、URL Query、请求体或响应体。

## 日志与审计边界

- 调用端 DEBUG 用于诊断认证头注入和响应状态，Token 不可用时输出不含敏感数据的 WARN。
- 调用端不为每次出站 HTTP 请求发布审计事件。该路径高频，且客户端无法确认目标资源是否实际执行成功。
- 认证服务负责凭证签发、撤销和生命周期审计；目标资源启用 AKSK 安全上下文后，在认证成功时发布 `AkskAccessEvent`，这是资源访问审计的权威事件。

## 验证

- 模块完整测试：21 项通过，0 项失败。
- 集成验证：完成 Token 获取与受保护接口调用验证，认证头注入与资源访问均符合预期。
- 联调边界：该验证覆盖 AKSK Token 获取和受保护接口调用；具体 OpenAPI、API 权限及数据权限由目标资源服务单独定义和校验。

`1.0.0` 为首发版本，首发版本不单独维护 CHANGELOG。
