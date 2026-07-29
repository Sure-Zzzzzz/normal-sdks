# simple-kms-client-starter

面向 Java 8、Spring Boot 2.2.13.RELEASE / 2.3.12 / 2.4.5 / 2.7.9 业务服务的 KMS HTTP Client。它只调用已发布 KMS Server 的 `/api/v1/kms` 接口，不连接 KMS MySQL，也不会获取、缓存或导出私钥、对称密钥或其他密钥材料。

## 接入

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-kms-client-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.apache.httpcomponents:httpclient:4.5.13'
}
```

默认 Client 关闭。配置 KMS 服务 origin 并显式启用：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        kms:
          client:
            enabled: true
            base-url: https://kms.example.internal
```

`base-url` 只能是 `http` 或 `https` origin：必须包含主机，可包含端口；不能包含路径、query、fragment 或 user-info。Client 固定追加 `/api/v1/kms`，并禁用 HTTP 重定向。

## 调用身份

Client 不保存认证凭据、不构造 tenant，也不继承宿主全局 `RestTemplate` 拦截器。业务服务必须提供唯一的 `KmsClientAuthenticationInterceptor`，由它写入 Server 认可的调用身份；未提供时由 Server 返回 `401`。

```java
@Bean
public KmsClientAuthenticationInterceptor kmsClientAuthenticationInterceptor() {
    return (request, body, execution) -> {
        request.getHeaders().setBearerAuth(loadServiceToken());
        return execution.execute(request, body);
    };
}
```

认证步骤较多时组合为一个 interceptor；定义多个 `KmsClientAuthenticationInterceptor` 会在启动时以 `KmsClientConfigurationException` 失败。认证头、token、tenant、明文、密文、签名、AAD、envelope 和请求响应原文均不应写入日志。

## 推荐：注入最小端口

业务代码优先依赖最小端口，而不是完整的 KMS 管理 API。默认端口委托 `KmsClient`，调用方也可以直接提供同类型 Bean 替换任一端口。

```java
private final TenantSignerPort tenantSignerPort;
private final TenantPublicKeyPort tenantPublicKeyPort;
private final KeyEncryptionPort keyEncryptionPort;
```

签名时传入逻辑密钥引用和期望版本；`version` 为 `null` 时由 KMS 选择当前活动版本。调用方应保存自己的业务对象到 `(kmsKeyRef, kmsKeyVersion)` 的映射，并校验响应实际版本。

```java
KmsSigningResult result = tenantSignerPort.sign(keyRef, expectedVersion, signingInput);
if (expectedVersion != null && !expectedVersion.equals(result.getVersion())) {
    rejectIssuance();
}
```

公钥读取可用于验证方按版本获取可发布公钥：

```java
KmsPublicKey publicKey = tenantPublicKeyPort.read(keyRef, version);
List<KmsPublicKey> publicKeys = tenantPublicKeyPort.list(keyRef);
```

加密端口返回 KMS 的完整版本化 envelope。解密时必须原样传回该 envelope；调用方不得拆分或重组逻辑密钥、版本或 envelope。`aad` 可以为 `null`，但传入时解密必须提供完全相同的字节。

```java
byte[] envelope = keyEncryptionPort.encrypt(keyRef, plaintext, aad);
byte[] restored = keyEncryptionPort.decrypt(envelope, aad);
```

## 完整 Client

`KmsClient` 适用于需要管理逻辑密钥、版本、策略和密码操作的管理服务。管理写操作由调用方生成并持久化 `Idempotency-Key`；状态修改、轮换、销毁安排/取消和策略撤销还必须传入当前 `expectedRowVersion`。Client 不生成幂等键，也不做自动重试、退避、重放、缓存或后台队列。

`sign`、`encrypt` 和 `decrypt` 在网络中断后具有未知结果语义，尤其不能由调用方盲目重试。`verify` 返回 `false` 是正常验签不通过，不是异常。

## 配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enabled` | `false` | 是否创建默认 HTTP Client |
| `base-url` | 无 | KMS 服务 origin；启用默认 Client 时必填 |
| `max-total` | `50` | HTTP 连接池最大连接数 |
| `max-per-route` | `20` | 单个路由最大连接数 |
| `connect-timeout-millis` | `3000` | 建连超时，毫秒 |
| `connection-request-timeout-millis` | `3000` | 从连接池取得连接超时，毫秒 |
| `read-timeout-millis` | `10000` | 读取超时，毫秒 |
| `max-request-bytes` | `2097152` | 请求 JSON 和 Base64url 二进制字段的最大总字节数 |
| `max-response-bytes` | `2097152` | 响应体最大字节数，同时约束 `Content-Length` 与流式读取累计量 |

所有数值上限和超时必须大于零。SDK 使用独立 `ObjectMapper`、Apache HttpClient 连接池和专属 `RestTemplate`，不会复用宿主的同类 Bean。业务服务可以自行提供 `KmsClient`、`KmsHttpExecutor`、`KmsJsonCodec`、`KmsHttpErrorMapper` 或任一最小端口替换默认实现。

## 错误处理与安全语义

Client 将 HTTP 状态转换为稳定异常类型：

| HTTP 状态 | 异常类型 |
|---|---|
| `400` / `405` / `415` | `KmsBadRequestException` |
| `401` | `KmsUnauthenticatedException` |
| `403` | `KmsUnauthorizedException` |
| `404` | `KmsNotFoundException` |
| `409` | `KmsConflictException` |
| `413` | `KmsPayloadTooLargeException` |
| `422` | `KmsUnprocessableException` |
| `5xx` | `KmsServiceUnavailableException` |
| 协议格式或编码不合法 | `KmsProtocolException` |
| 响应超过上限 | `KmsResponseTooLargeException` |
| 连接、超时或 I/O 失败 | `KmsTransportException` |
| 本地配置不合法 | `KmsClientConfigurationException` |

所有异常均继承 `SimpleKmsClientException`，只保留安全诊断元数据：HTTP 状态、方法、非敏感 endpoint 路径、请求标识和时间。异常不保留原始请求或响应 body、认证头、密码学材料或业务身份数据。

二进制字段使用无 padding 的 Base64url。Client 会拒绝带 padding 的响应二进制字段以及不符合固定 UTC 毫秒精度的时间字段；所有成功、错误、协议异常和超限分支都会关闭 HTTP response stream。

## 兼容范围与验证

| Client Spring Boot | 验证范围 |
|---|---|
| `2.2.13.RELEASE` | `src/test` 的自动装配、HTTP 契约、边界、安全行为和远程真实 Server E2E |
| `2.3.12` | `src/test` 的自动装配、HTTP 契约、边界、安全行为和远程真实 Server E2E |
| `2.4.5` | `src/test` 的自动装配、HTTP 契约、边界、安全行为和远程真实 Server E2E |
| `2.7.9` | `src/test` 的自动装配、HTTP 契约、边界、安全行为和远程真实 Server E2E |

真实 Server 固定以 Spring Boot 2.7.9 的本地 `e2eServer` fixture 启动已发布 `smart-kms-server-starter:1.0.0` 与 MySQL。每档 Client 的 `src/test` 都通过 HTTP 调用同一 Server；`e2eServer` 独占 Server、Core、JDBC、MySQL 与 schema 依赖，Client 的 `main` 和 `test` 均不解析这些类型。因此这只证明 Client 的跨版本调用能力，不表示 Server 支持其他 Spring Boot 版本。

`spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 是同一个自动配置的 Spring Boot 2.x 双入口，不是两套 Client 实现：前者覆盖 2.2.x、2.3.12、2.4.5，后者供 2.7.9 读取；二者只注册 `SimpleKmsClientAutoConfiguration`，启动时只会创建一套默认 Client Bean。
