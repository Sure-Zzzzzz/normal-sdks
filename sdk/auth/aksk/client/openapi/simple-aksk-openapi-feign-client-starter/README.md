# simple-aksk-openapi-feign-client-starter

为 Spring Boot 2.7.x 应用提供 `AkskOpenApiFeignClient`：通过 OpenFeign 调用 AKSK Server 管理面
OpenAPI（20 端点）。接口标注 AKSK 底座（javax 线）的 `@AkskClientFeignClient`，底座获取访问令牌
并添加认证头；本模块不创建独立令牌链，也不接触凭据。

与 jakarta 线（`simple-aksk-openapi-feign-client-jakarta-starter:1.1.0`）同契约、同包名、同配置键——
业务从 SB2 升 SB3 只换坐标。当前版本 `1.1.0`，对接 `simple-aksk-server-starter:3.2.2`。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 2.x（javax，本模块） | `simple-aksk-openapi-feign-client-starter` | `1.1.0` |
| Spring Boot 3.x（jakarta） | `simple-aksk-openapi-feign-client-jakarta-starter` | `1.1.0` |

两条线**不可同时引入**。与 RestTemplate 形态按宿主技术栈二选一。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-openapi-feign-client-starter:1.1.0'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'io.github.openfeign:feign-hc5'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

由宿主的 Spring Cloud 版本管理 OpenFeign（SB 2.7.x 档对应 3.1.x）；`feign-hc5` 提供 PATCH 方法
支持。本家族新增唯一配置键 `io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile`——底座配置
（redis route + cache + `auth.aksk.client.*`）原样生效，`server-url` 同时是换 Token 与管理 API 的目标。

启用扫描的配置类与调用示例与 jakarta 线完全一致，见
[simple-aksk-openapi-feign-client-jakarta-starter README](../jakarta/simple-aksk-openapi-feign-client-jakarta-starter/README.md)
（版本选择表指向本线坐标）。

## 错误与数据

非 2xx 由 Feign 抛 `FeignException` 保留状态码；不包装、不自动重试。`clientSecret` 仅出现一次，
立即落受保护配置；日志不输出 Secret/Authorization/Token/完整 URL query。持有管理凭据不等于拥有
全部权限（401 认证层 / 403 授权层）。

## AKP Profile

管理 OpenAPI 使用 `io.github.surezzzzzz.sdk.auth.aksk.openapi.client.aksk-profile` 选择 AKP。
留空或 `default` 继续使用底座默认拦截器；命名 Profile 只挂一个 Profile 认证拦截器。

## 版本历史

### 1.1.0

- 对接 AKSK Feign 底座 3.1.0，支持 AKP Profile。
- 20 端点契约和默认 Feign 行为不变；契约 core 保持 1.0.2。

## 验证

模块完整测试与真实 E2E（六步闭环同家族口径，清理后 404 裸 FeignException 口径）结果见
`LOCAL_TEST_COMMANDS.md`；实证基线 Spring Boot 2.7.9 × Java 8。
