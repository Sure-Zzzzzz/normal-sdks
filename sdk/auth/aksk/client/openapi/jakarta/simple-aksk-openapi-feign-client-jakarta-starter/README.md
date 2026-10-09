# simple-aksk-openapi-feign-client-jakarta-starter

为 Spring Boot 3 应用提供 `AkskOpenApiFeignClient`：通过 OpenFeign 调用 AKSK Server 管理面
OpenAPI（Client 管理 / 应用授权 / Token 管理三组共 20 端点）。接口标注 AKSK 底座的
`@AkskClientFeignClient`，底座获取访问令牌并添加认证头——AKSK Server 管理面本身就是公共资源层
保护的 OpenAPI，用自己的 AKP 调自己的管理 API。本模块不创建独立令牌链，也不接触凭据。

适合已有 OpenFeign 的服务端应用做接入自动化（建 Client → 配应用授权 → 准入 → 换 Token 验证一条龙）
与运维脚本化。当前版本 `1.0.0`，对接 `simple-aksk-server-starter:3.2.2`；契约常量与 wire DTO 引
`simple-aksk-openapi-client-core:1.0.1`。

## 解决的问题

平台接入自动化（建 Client → 配应用授权 → 准入 → 换 Token 验证）此前只能走 Admin 管理台或手拼 curl；本家族把它变成可编程调用，适用于运维脚本化、多环境批量初始化与接入流水线。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 3.x（jakarta，本模块） | `simple-aksk-openapi-feign-client-jakarta-starter` | `1.0.0` |
| Spring Boot 2.x（javax） | `simple-aksk-openapi-feign-client-starter` | `1.0.0` |

两条线同包名同类型，**不可同时引入**（同功能 javax/jakarta starter 互斥）。与本家族 RestTemplate
形态按宿主技术栈二选一。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-openapi-feign-client-jakarta-starter:1.0.0'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'io.github.openfeign:feign-hc5'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

由宿主的 Spring Cloud 版本管理 OpenFeign 与 `feign-hc5` 版本；`feign-hc5` 提供本接口 PATCH
方法支持。AKSK 底座与 Redis 令牌缓存由本模块运行时传递。本家族**零新增配置键**——底座配置原样
生效（管理 API 与 Token 端点同在 AKSK Server 一个进程，`server-url` 同时是两者目标）：

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
              enable: true
              server-url: https://aksk.example.test
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
```

将以下配置类放在应用的组件扫描范围内，或由应用显式导入，以启用 Feign 扫描：

```java
import io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client.AkskOpenApiFeignClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackageClasses = AkskOpenApiFeignClient.class)
public class AkskOpenApiFeignConfiguration {
}
```

## 调用

```java
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientResponse;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client.AkskOpenApiFeignClient;

public class ClientProvisioner {
    private final AkskOpenApiFeignClient openApi;

    public ClientProvisioner(AkskOpenApiFeignClient openApi) {
        this.openApi = openApi;
    }

    public CreateClientResponse provision() {
        CreateClientRequest request = new CreateClientRequest();
        request.setType("platform");
        request.setName("order-service");
        return openApi.createClient(request);
    }
}
```

21 方法（HTTP 端点 20 个，`listClients` 分页/批量双形态）与 core 的 `AkskOpenApiClient`
一一对应（完整契约映射表见
[simple-aksk-openapi-client-core README](../../simple-aksk-openapi-client-core/README.md)）。
listClients 的可选参数缺省时传 null 即不发送；按标识批量查询走 `listClientsByClientIds`。

## 错误与数据

- 非 2xx HTTP 响应由 Feign 抛出 `FeignException`，保留服务端状态码；本模块不包装异常，也不自动重试
  （与 RestTemplate 形态的异常族映射口径不同，按形态各自README 为准）。
- `createClient` / `rotateSecret` 响应中的 `clientSecret` 仅出现一次，调用方必须立即落受保护配置；
  本模块不在 DEBUG 输出 Secret、Authorization、Token 或完整 URL query。
- 持有管理凭据不等于拥有全部权限：实际可访问的接口与数据由 AKSK Server 应用授权的精确 API
  permission 与 DATA 决定（401 认证层 / 403 授权层）。

## 验证

- 模块完整测试与真实 E2E（对 AKSK Server 测试形态六步闭环：建 Client（含批量查询断言）→ 授权 →
  换 Token → statistics → rotateSecret → 撤销，清理后 404 裸 FeignException 口径）结果见
  `LOCAL_TEST_COMMANDS.md`。
- Boot 矩阵 × JDK17 全绿：3.4.2（默认）/ 3.3.13 / 3.2.12（后两者配 Cloud 2023.0.4）。
