# simple-aksk-openapi-resttemplate-client-starter

为不使用 OpenFeign 的 Spring Boot 2.7.x 宿主提供 `AkskOpenApiClient` 编程式客户端的默认实现
`DefaultAkskOpenApiClient`（20 端点）。出站请求经 AKSK 底座（javax 线）的 `akskClientRestTemplate`
自动挂 Bearer 头；本模块不管理令牌、不接触凭据。

与 jakarta 线（`simple-aksk-openapi-resttemplate-client-jakarta-starter:1.0.0`）同契约、同包名、
同配置键——SB2 升 SB3 只换坐标（唯一实现差异：自动配置经 `spring.factories` 注册，jakarta 线用
`AutoConfiguration.imports`）。当前版本 `1.0.0`，对接 `simple-aksk-server-starter:3.2.2`。

## 版本选择

| 应用运行时 | 使用模块 | 版本 |
| --- | --- | --- |
| Spring Boot 2.x（javax，本模块） | `simple-aksk-openapi-resttemplate-client-starter` | `1.0.0` |
| Spring Boot 3.x（jakarta） | `simple-aksk-openapi-resttemplate-client-jakarta-starter` | `1.0.0` |

两条线**不可同时引入**。与 Feign 形态按宿主技术栈二选一。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-openapi-resttemplate-client-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

本家族**零新增配置键**：装配复用底座总开关 `auth.aksk.client.enable=true`，连接配置全部来自底座。
宿主 YAML、装配行为、调用示例与错误语义（异常族映射）与 jakarta 线完全一致，见
[simple-aksk-openapi-resttemplate-client-jakarta-starter README](../jakarta/simple-aksk-openapi-resttemplate-client-jakarta-starter/README.md)。

## 验证

模块完整测试与真实 E2E（六步闭环同家族口径，含批量查询断言）结果见 `LOCAL_TEST_COMMANDS.md`；
实证基线 Spring Boot 2.7.9 × Java 8。
