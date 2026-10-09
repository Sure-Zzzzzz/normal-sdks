# simple-aksk-openapi-resttemplate-client-jakarta-starter

为不使用 OpenFeign 的 Spring Boot 3 宿主（运维工具、批处理、测试夹具）提供 `AkskOpenApiClient`
编程式客户端的默认实现 `DefaultAkskOpenApiClient`：与 Feign 形态同契约同方法面，覆盖 20 个管理
端点。出站请求经 AKSK 底座的 `akskClientRestTemplate`（拦截器自动挂 Bearer 头 + HttpClient5
连接池）；本模块不管理令牌、不接触凭据。

适合低频管理操作与无 Spring Cloud 依赖栈的宿主。当前版本 `1.0.0`，对接
`simple-aksk-server-starter:3.2.2`；契约与异常族来自 `simple-aksk-openapi-client-core:1.0.1`。

## 解决的问题

平台接入自动化（建 Client → 配应用授权 → 准入 → 换 Token 验证）此前只能走 Admin 管理台或手拼 curl；本家族把它变成可编程调用，适用于运维脚本化、多环境批量初始化与接入流水线。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 3.x（jakarta，本模块） | `simple-aksk-openapi-resttemplate-client-jakarta-starter` | `1.0.0` |
| Spring Boot 2.x（javax） | `simple-aksk-openapi-resttemplate-client-starter` | `1.0.0` |

两条线同包名同类型，**不可同时引入**。与 Feign 形态按宿主技术栈二选一；RestTemplate 形态无需
`@EnableFeignClients`，装配见下。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-openapi-resttemplate-client-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.apache.httpcomponents.client5:httpclient5'
    implementation 'com.github.ben-manes.caffeine:caffeine'
    runtimeOnly 'org.apache.commons:commons-pool2'
}
```

本家族**零新增配置键**：装配条件复用底座总开关，连接配置全部来自 AKSK 底座
（管理 API 与 Token 端点同进程，`server-url` 同时是两者目标）：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: redis.example.test
                port: 6379
                database: 0
        cache:
          enabled: true
          key-prefix: my-app-openapi
          me: my-app
        auth:
          aksk:
            client:
              enable: true                          # 底座总开关 = 本客户端装配开关
              server-url: https://aksk.example.test
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
```

`enable=true` 且类路径存在 RestTemplate 时自动装配 `DefaultAkskOpenApiClient` Bean
（jakarta 线经 `AutoConfiguration.imports` 注册）；底座 `akskClientRestTemplate` 缺失时启动即报
缺 Bean（响亮失败，不静默）。

## 调用

```java
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.AkskOpenApiClient;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientResponse;

public class ClientProvisioner {
    private final AkskOpenApiClient openApi;

    public ClientProvisioner(AkskOpenApiClient openApi) {
        this.openApi = openApi;
    }

    public CreateClientResponse provision() {
        CreateClientRequest request = new CreateClientRequest();
        request.setType("platform");
        request.setName("ops-tool");
        return openApi.createClient(request);
    }
}
```

21 方法（20 端点，`listClients` 分页/批量双形态）契约映射见
[simple-aksk-openapi-client-core README](../../simple-aksk-openapi-client-core/README.md)。

## 错误与数据

- 非 2xx 响应经 core 的 `AkskOpenApiHttpErrorMapper` 映射为异常族（400→`AkskOpenApiBadRequestException`、
  401→`AkskOpenApiUnauthenticatedException`、403→`AkskOpenApiUnauthorizedException`……），
  保留状态码等非敏感元数据；本模块不包装、不自动重试。与 Feign 形态的裸 FeignException 口径不同。
- `createClient` / `rotateSecret` 响应中的 `clientSecret` 仅出现一次，调用方必须立即落受保护配置；
  DEBUG 只记录方法/路径/耗时，不输出 Secret、Authorization、Token 或完整 URL query。
- 持有管理凭据不等于拥有全部权限：实际可访问的接口与数据由 AKSK Server 应用授权决定。

## 验证

- 模块完整测试与真实 E2E（六步闭环同家族口径，含批量查询断言）结果见 `LOCAL_TEST_COMMANDS.md`。
- Boot 矩阵 × JDK17 全绿：3.4.2（默认）/ 3.3.13 / 3.2.12。
