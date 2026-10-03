# Simple AKSK Feign Redis Client Jakarta Starter

`simple-aksk-feign-redis-client-jakarta-starter` 为 Spring Boot 3.x 应用中的显式 Feign Client 添加 AKSK 认证头。它从 Jakarta Redis Token Manager 获取 Token，但不会影响普通 Feign Client。

它是调用侧组件，不校验入站请求、不依赖特定身份系统，也不推断接口可访问范围。目标资源服务的 OpenAPI、API 权限和数据权限共同决定请求是否可以执行。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-aksk-feign-redis-client-starter` | `3.0.2` |
| Spring Boot 3.x（Jakarta） | `simple-aksk-feign-redis-client-jakarta-starter` | `1.0.0` |

两条线不可同时引入。Jakarta 线要求 Java 17 或更高版本、Spring Boot 3.x、Spring Framework 6.x 与调用方自行选择的 Spring Cloud OpenFeign。

## 最小接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-jakarta-starter:1.0.0'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

该 starter 在运行时传递 `simple-aksk-redis-token-manager-jakarta-starter:1.0.0`。直接注入 `TokenManager` 或使用其公共类型时，请额外声明 `simple-aksk-client-core-jakarta`。

调用方启用 Feign 后，使用 `@AkskClientFeignClient` 标记需要 AKSK 认证的接口：

```java
@AkskClientFeignClient(name = "resource-service", url = "https://resource.example.test")
public interface ResourceClient {

    @GetMapping("/api/orders")
    List<OrderView> listOrders();
}
```

也可在普通 `@FeignClient` 上显式声明 `configuration = AkskFeignConfiguration.class`。该配置不是全局 `@Configuration`，未标记的 Feign Client 不会获得 AKSK 认证头。

## 行为边界

- 仅启用 `io.github.surezzzzzz.sdk.auth.aksk.client.enable=true`、Feign 在类路径且容器有 `TokenManager` 时装配。
- OpenFeign 是 `compileOnly` 依赖，未使用 Feign 的应用不会被强制带入 Spring Cloud。
- 每次请求覆盖已有 `Authorization` 头，避免调用方误带过期 AKSK Token。
- Token 为空、空白或获取失败时抛出 `TokenFetchException`，不生成缺少认证头的请求，也不自动重试业务请求。
- 主代码 DEBUG 仅记录 HTTP 方法与 Authorization 头覆盖标记；不记录 Token、Client Secret、安全上下文、Authorization、Cookie、完整 URL、URL Query、请求体或响应体。

## 日志与审计边界

- 调用端 DEBUG 用于诊断认证头注入，Token 不可用时输出不含敏感数据的 WARN。
- 调用端不为每次出站 HTTP 请求发布审计事件。该路径高频，且客户端无法确认目标资源是否实际执行成功。
- 认证服务负责凭证签发、撤销和生命周期审计；目标资源启用 AKSK 安全上下文后，在认证成功时发布 `AkskAccessEvent`，这是资源访问审计的权威事件。

## 验证

- 模块完整测试：18 项通过，0 项失败。
- 集成验证：完成 Token 获取与受保护接口调用验证，认证头注入与资源访问均符合预期。
- 联调边界：该验证覆盖 AKSK Token 获取和受保护接口调用；具体 OpenAPI、API 权限及数据权限由目标资源服务单独定义和校验。

`1.0.0` 为首发版本，首发版本不单独维护 CHANGELOG。
