# Simple AKSK Client Core Jakarta

`simple-aksk-client-core-jakarta` 为 Spring Boot 3.x 应用提供 AKSK 调用侧的公共类型：Token 管理抽象、Token 刷新执行器、安全上下文与缓存策略接口。它适合实现 Token Manager 的 starter 或需要自行管理 Token 缓存的调用方。

该模块不是自动配置 starter，不注册 Bean，也不校验入站请求。业务应用通常应接入后续的 AKSK Redis Token Manager Jakarta starter，而不是直接组合本模块。

## 版本选择

| 应用运行时 | 使用模块 | 版本 | 是否可同时引入 |
| --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-aksk-client-core` | `3.0.0` | 否 |
| Spring Boot 3.x（Jakarta） | `simple-aksk-client-core-jakarta` | `1.0.0` | 否 |

两条线保留相同包名和公共类型，业务升级到 Spring Boot 3.x 时只替换依赖坐标。它们的 `TaskRetryExecutor` 来自不同代际的依赖，不能在同一个应用中并存。

## 最小依赖

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-client-core-jakarta:1.0.0'
}
```

模块通过 API 传递 Jakarta Task Retry，因此直接使用 `TokenRefreshExecutor` 或继承 `AbstractTokenManager` 时可获得 `TaskRetryExecutor` 类型。调用方负责注册 `SimpleAkskClientCoreProperties` 并提供具体的 Token 缓存和并发控制实现。

## 提供的能力

| 类型 | 用途 |
| --- | --- |
| `TokenManager` | 获取和清理当前调用上下文的 Access Token。 |
| `AbstractTokenManager` | 封装缓存读取、缓存未命中和 Token 刷新的公共流程；下游实现加锁策略。 |
| `TokenCacheStrategy` | 定义 Token 缓存的读、写、删和缓存键生成边界。 |
| `SecurityContextProvider` | 提供当前调用的安全上下文，用于用户级 AKU 的 Token 隔离。 |
| `TokenRefreshExecutor` | 以 Client Credentials 向 AKSK Token 端点换取 Token，并校验 Token 与有效期。 |
| `SimpleAkskClientCoreProperties` | 定义 Token 端点、AK/SK 与 HTTP 超时配置。 |

`TokenRefreshExecutor` 只获取 Token，不推断任意接口是否允许访问。目标资源服务的 OpenAPI、API 权限与数据权限共同决定请求最终是否成功。

## 配置

下游 starter 或手动集成时使用以下配置前缀。密钥应由受控配置源提供，示例不包含真实凭据。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              server-url: https://auth.example.test
              token-endpoint: /oauth2/token
              client-id: example-client-id
              client-secret: ${AKSK_CLIENT_SECRET}
              http:
                connect-timeout-ms: 5000
                read-timeout-ms: 15000
```

| 配置项 | 说明 |
| --- | --- |
| `enable` | 供下游 starter 判断是否装配。core 本身不注册 Bean，手动集成时由调用方决定是否创建实现。 |
| `server-url` | AKSK Server 的基础地址，不包含 Token 端点路径。 |
| `token-endpoint` | Token 端点，默认 `/oauth2/token`。 |
| `client-id` / `client-secret` | AKSK 的 Client Credentials。`client-secret` 不应写入代码、日志或版本库。 |
| `http.connect-timeout-ms` | 建立 Token 请求连接的最长等待时间，默认 5000 毫秒。 |
| `http.read-timeout-ms` | 等待 Token 响应的最长时间，默认 15000 毫秒。 |

## 使用边界

- AKP 与 AKU 都通过 Client Credentials 换取 Token；AKU 的所属人继承和授权投影由 AKSK Server 决定。
- 空白 Token、缺失或非正的 `expires_in`、Token 端点失败及缓存回调失败都会抛出 `TokenFetchException`，不会带着缺失 Authorization 继续请求。
- Token、Client Secret、安全上下文、Authorization、Cookie、请求体和响应体不会出现在本模块的主代码日志或对外异常消息中。
- 本模块不引入 IAM、资源校验、Redis、Feign、HttpSession 或业务资源调用逻辑；这些由对应的可选下游模块承担。

## 运行时要求

- Java 17 或更高版本。
- Spring Boot 3.x 与 Spring Framework 6.x。
- 真实 AKSK Token、缓存并发与资源接口端到端验证由下游 Token Manager 和调用客户端模块负责。
