# simple-kms-aksk-feign-client-jakarta-starter

为 Spring Boot 3 应用提供 `KmsFeignClient`：通过 Spring Cloud OpenFeign 接口调用密钥管理服务（KMS）的密钥管理、精确授权策略、签名验签、信封加解密和公钥查询接口。接口标注访问密钥凭据（AKSK）底座的 `@AkskClientFeignClient`，底座获取访问令牌并添加认证头。本模块不创建独立令牌链，也不接触凭据。

适合已有 OpenFeign 的服务端应用。当前版本为 `1.0.0`，对接 `smart-kms-server-starter:2.0.0` 的 `/api/kms` 接口；接口路径和字段名引用 `simple-kms-client-core:2.0.0` 契约。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kms-aksk-feign-client-jakarta-starter:1.0.0'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'io.github.openfeign:feign-hc5'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'com.github.ben-manes.caffeine:caffeine'
    runtimeOnly 'org.apache.commons:commons-pool2'
}
```

由宿主的 Spring Cloud 版本管理 OpenFeign 与 `feign-hc5` 的具体版本。`feign-hc5` 支持本接口的 PATCH 方法。AKSK 底座与 Redis 令牌缓存由本模块运行时传递。以下配置使用 Redis Route 管理 Redis 连接，`cache.me` 是同一应用多实例共享的标识；AKSK 凭据通过部署环境注入。

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
        kms:
          client:
            base-url: https://kms.example.test
```

`base-url` 只填协议、主机和端口，不附加 `/api/kms`。将以下配置类放在应用的组件扫描范围内，或由应用显式导入，以启用 Feign 扫描：

```java
import io.github.surezzzzzz.sdk.kms.feign.client.KmsFeignClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackageClasses = KmsFeignClient.class)
public class KmsFeignConfiguration {
}
```

## 调用

```java
import io.github.surezzzzzz.sdk.kms.feign.client.KmsFeignClient;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsKeyResponse;

public class KeyReader {
    private final KmsFeignClient kmsClient;

    public KeyReader(KmsFeignClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    public KmsKeyResponse read(String keyRef) {
        return kmsClient.getKey(keyRef);
    }
}
```

接口提供 16 个方法。写操作的幂等键（相同操作重试时复用的唯一标识）通过 `Idempotency-Key` 请求头传入，由调用方生成并保存；请求体为 `Map<String, Object>`，可选字段不传时应从 Map 中省略。持有 AKSK 凭据不等于拥有所有 KMS 权限，实际可访问的接口和密钥由 KMS 服务端策略决定。

## 错误与数据

- 非 2xx HTTP 响应由 Feign 抛出 `FeignException`，保留服务端状态码；本模块不包装异常，也不自动重试。
- 响应模型是 `model` 包中的 HTTP 原始字段投影；时间字段保留 UTC 毫秒字符串，二进制字段保留无填充 Base64url（适合 URL 传输的 Base64 编码）字符串，由调用方按需解析。
- 认证诊断日志仅记录 HTTP 方法与是否覆盖已有认证头，不记录令牌或请求体。调用端不为每次 HTTP 请求发布审计事件；认证和资源操作由服务端审计。

## 兼容性

以 Spring Boot `3.4.2`、Spring Cloud `2024.0.0`、OpenFeign `4.2.0` 和 JDK 17 完成编译，JDK 21 完成模块测试及应用级凭据（AKP）经 AKSK、Redis、KMS 的真实六步调用（创建、查询、自授 SIGN/VERIFY、签名、验签、安排销毁）。六步联调不代表全部 16 个方法都经过真实服务验证。Spring Boot 2 / Java 8 应用选用 `simple-kms-aksk-feign-client-starter:2.0.0`。
