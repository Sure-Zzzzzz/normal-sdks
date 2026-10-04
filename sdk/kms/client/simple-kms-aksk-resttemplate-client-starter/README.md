# simple-kms-aksk-resttemplate-client-starter

为 Spring Boot 2 应用提供 `KmsClient`：用 Java 方法调用密钥管理服务（KMS）的密钥管理、精确授权策略、签名验签、信封加解密和公钥查询接口。请求通过访问密钥凭据（AKSK）底座创建的 `akskClientRestTemplate` 发送，由底座获取访问令牌并添加认证头。本模块不创建第二套 HTTP 连接池或管理 AKSK 凭据。

适合已使用 RestTemplate、需要在服务端调用 KMS 的应用。当前版本为 `2.0.0`，对接 `smart-kms-server-starter:2.0.0` 的 `/api/kms` 接口，公开 Java 契约来自 `simple-kms-client-core:2.0.0`。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kms-aksk-resttemplate-client-starter:2.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.apache.httpcomponents:httpclient'
    implementation 'com.github.ben-manes.caffeine:caffeine'
}
```

AKSK 底座与 Redis 令牌缓存由本模块运行时传递；宿主提供上面的 Spring、HTTP 连接池和本地缓存依赖。以下配置使用 Redis Route 管理 Redis 连接，`cache.me` 是同一应用多实例共享的标识。AKSK 凭据通过部署环境注入，不要写入仓库。

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
          key-prefix: example-kms-client
          me: example-app
          l1:
            enabled: true
          l2:
            enabled: true
        auth:
          aksk:
            client:
              enable: true
              server-url: https://aksk.example.test
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
              resttemplate:
                enable: true
        kms:
          client:
            enable: true
            base-url: https://kms.example.test
```

`base-url` 只填协议、主机和端口，不附加 `/api/kms`、查询参数或用户信息。`kms.client.enable=true` 注册 `KmsClient`；`aksk.client.resttemplate.enable=true` 创建带认证拦截器的 `akskClientRestTemplate`。连接池与超时由 `io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.*` 管理。缺少底座 RestTemplate 时应用启动失败，不会静默发送无认证请求。

## 调用

```java
import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKey;

public class KeyReader {
    private final KmsClient kmsClient;

    public KeyReader(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    public KmsKey read(String keyRef) {
        return kmsClient.getKey(keyRef);
    }
}
```

`KmsClient` 提供 16 个方法。创建密钥、变更状态、轮换、安排或取消销毁、创建或撤销策略等写操作由调用方传入并保存幂等键（相同操作重试时复用的唯一标识）；本模块不自动重试。持有 AKSK 凭据不等于拥有所有 KMS 权限，实际可访问的接口和密钥由 KMS 服务端策略决定。

## 错误与数据

- HTTP 4xx/5xx 响应由 RestTemplate 默认错误处理器抛出 `HttpStatusCodeException`，保留服务端状态码；成功响应的结构不符合契约时，抛出 core 的 `KmsProtocolException`。
- 二进制请求和响应使用无填充 Base64url（适合 URL 传输的 Base64 编码）；`KmsClient` 负责字节与协议字段转换，不接受带填充的协议响应。
- 诊断日志仅记录 HTTP 方法、路径和状态码，不记录令牌、密钥材料、明文、密文、签名或附加认证数据。调用端不为每次 HTTP 请求发布审计事件；认证和资源操作由服务端审计。

## 兼容性

使用 Java 8、Gradle 7.6，在 Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5`、`2.7.9` 上完成模块测试；使用应用级凭据（AKP）经 AKSK、Redis 和 KMS 完成创建、查询、自授 SIGN/VERIFY、签名、验签、安排销毁的真实六步调用。六步联调不代表全部 16 个方法都经过真实服务验证。Spring Boot 3 / Java 17 应用选用 `simple-kms-aksk-resttemplate-client-jakarta-starter:1.0.0`。
