# simple-s3-route-starter

面向 S3 兼容对象存储（AWS S3、MinIO 及私有化兼容存储）的固定 target 集群路由组件。调用方使用显式 `targetKey` 选择已登记的对象存储，通过 `execute` 回调或客户端获取器拿到标准 `AmazonS3` 客户端执行任意对象操作；连接池、凭据、超时与生命周期按 target 独立治理，Route 不二次封装对象操作语义。

## ✨ 特性

- **固定 target 集群路由**：显式 `targetKey` 选择存储，没有默认 target、不回退、不猜测，未登记即失败。
- **多存储独立治理**：每个 target 拥有独立连接池、凭据、超时与签名版本，资源彼此隔离、关停互不影响。
- **标准客户端直给**：`execute` 回调内拿到标准 `AmazonS3` 客户端，任意 S3 操作（上传、下载、列举、预签名等）以 SDK 原生 API 表达，无二次封装、无 API 学习成本。
- **私有 CA 信任链**：企业自签 HTTPS 场景按 target 配置可信 CA 文件（PEM/DER、多 CA），保留严格主机名校验，不修改 JRE 全局信任。
- **双签名版本**：默认 AWS Signature V4，可按 target 显式切换 S3 V2 签名，兼容老私有化存储。
- **优雅关停**：应用关闭时等待进行中的请求排空后再释放连接，超时可配置。
- **异常分层**：路由与配置问题抛带稳定错误码的 `S3RouteException`，回调内的对象操作失败原样透传 SDK 异常，故障定位一目了然。
- **行为可预期**：不创建隐藏线程池、不内置重试，同步调用行为完全可预期。

## 📦 依赖

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-s3-route-starter:1.0.0'

    // Route 基于 AWS SDK v1（S3）；由业务按自身依赖治理提供运行时版本。
    implementation 'com.amazonaws:aws-java-sdk-s3:1.12.797'
}
```

`simple-s3-route-starter` 对 AWS SDK 使用 `compileOnly`，不会传递或锁定业务工程的 SDK 版本；启用 Route 时运行时 classpath 需要 AWS SDK v1 的 S3 客户端。建议业务提供 `1.12.787` 及以上版本（本组件按 `1.12.797` 验证）：`1.12.786` 及以下存在路径遍历漏洞 CVE-2025-25394。另外，AWS SDK v1 全系传递的老版本 jackson-core 涉及 WS-2026-0003（修复于 jackson-core `2.18.6`），该传递版本不随 SDK 补丁版本更新，业务应在自身依赖治理中将 jackson 家族提升至 `2.18.6` 及以上并保持同版本对齐。

## 🔧 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          route:
            enable: true
            targets:
              storage-primary:
                endpoint: http://minio-a.internal:9000
                authentication:
                  type: ACCESS_KEY
                  access-key: ${S3_ACCESS_KEY}
                  secret-key: ${S3_SECRET_KEY}
```

注入 `S3RouteTemplate` 即可使用（容器中实例唯一）：

```java
@Autowired
private S3RouteTemplate s3RouteTemplate;

public void upload(String content) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    s3RouteTemplate.execute("storage-primary", client -> {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        client.putObject("order-attachments", "2026/08/report.txt",
                new ByteArrayInputStream(bytes), metadata);
        return null;
    });
}
```

## ⚙️ 最大配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          route:
            enable: true
            shutdown-timeout-ms: 10000
            targets:
              storage-primary:
                endpoint: http://minio-a.internal:9000
                region: us-east-1
                signer-type: AWS_V4
                path-style-enabled: true
                authentication:
                  type: ACCESS_KEY
                  access-key: ${S3_ACCESS_KEY}
                  secret-key: ${S3_SECRET_KEY}
                client:
                  connect-timeout-ms: 10000
                  socket-timeout-ms: 50000
                  max-connections: 500
                  request-timeout-ms: 0
                  client-execution-timeout-ms: 0
                  connection-max-idle-ms: 60000
                  connection-ttl-ms: -1
              storage-legacy:
                endpoint: https://storage-b.internal:9000
                region: us-east-1
                signer-type: S3_V2
                trusted-ca-file: /etc/internal-ca/storage-ca.crt
                authentication:
                  type: ACCESS_KEY
                  access-key: ${S3_B_ACCESS_KEY}
                  secret-key: ${S3_B_SECRET_KEY}
                  session-token: ${S3_B_SESSION_TOKEN:}
                client:
                  socket-timeout-ms: 120000
                  max-connections: 100
```

## 📖 配置说明

顶层：

| 配置项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `enable` | boolean | `false` | 是否启用 Route；`false` 时不注册任何 Bean |
| `shutdown-timeout-ms` | int | `10000` | 关停时等待进行中请求完成的时长（毫秒），超时后强制关停 |
| `targets` | Map | 必填 | targetKey 到存储配置的映射，启用时不能为空 |

target：

| 配置项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `endpoint` | String | 必填 | 存储 endpoint；仅允许 http/https，无 query/fragment，path 为空或 `/` |
| `region` | String | `us-east-1` | 签名与寻址 Region；小写字母数字与中划线 |
| `signer-type` | 枚举 | `AWS_V4` | `AWS_V4` / `S3_V2`；V2 不使用 region 参与签名计算，仍建议如实配置 |
| `path-style-enabled` | boolean | `true` | Path Style 寻址；直连 AWS S3 官方 endpoint 时应设为 `false` |
| `trusted-ca-file` | String | — | 可信私有 CA 文件路径；配置时 endpoint 必须为 HTTPS |
| `authentication` | 对象 | — | 认证配置，见下表 |
| `client` | 对象 | — | 客户端参数，见下表 |

authentication：

| 配置项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `type` | 枚举 | `NONE` | `NONE`（匿名）/ `ACCESS_KEY`（静态凭据） |
| `access-key` | String | — | ACCESS_KEY 时必填；NONE 时必须为空 |
| `secret-key` | String | — | ACCESS_KEY 时必填；NONE 时必须为空 |
| `session-token` | String | — | 可选，用于临时凭据 |

client：

| 配置项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `connect-timeout-ms` | int | `10000` | 建连超时（毫秒） |
| `socket-timeout-ms` | int | `50000` | 读超时（毫秒）；大对象传输保持足够大 |
| `max-connections` | int | `500` | 单 target 连接池容量；耗尽时请求排队等待 |
| `request-timeout-ms` | int | `0` | 请求级超时（毫秒），0 不启用 |
| `client-execution-timeout-ms` | int | `0` | 执行级超时（毫秒），0 不启用 |
| `connection-max-idle-ms` | int | `60000` | 空闲连接回收时长（毫秒） |
| `connection-ttl-ms` | long | `-1` | 连接 TTL（毫秒），-1 不限制 |

AccessKey、SecretKey 应通过部署环境管理，不能写入版本库。

## 💡 最佳实践场景

### 1. 上传：流必须带 contentLength

InputStream 上传未设置 `contentLength` 时 SDK 使用 `aws-chunked` 流式签名传输，个别 S3 兼容存储（尤其较老或网关形态的私有化部署）不支持该编码会报错。统一在回调内组装 `ObjectMetadata` 并设置长度：

```java
s3RouteTemplate.execute("storage-primary", client -> {
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(file.length());
    return client.putObject("order-attachments", "2026/08/report.txt", file, metadata);
});
```

### 2. 下载：大对象流式读取，用完关流

`getObject` 返回的 `S3Object` 持有网络连接，内容流由调用方负责关闭；小对象用 SDK 便捷方法一行完成，大对象流式搬运在回调内 try-with-resources：

```java
// 小对象
String content = s3RouteTemplate.execute("storage-primary", client ->
        client.getObjectAsString("order-attachments", "2026/08/report.txt"));

// 大对象流式搬运
s3RouteTemplate.execute("storage-primary", client -> {
    try (S3Object object = client.getObject("order-attachments", "2026/08/report.txt")) {
        IOUtils.copy(object.getObjectContent(), targetOutputStream);
    }
    return null;
});
```

在回调内完成整段流式读取可让对象读取纳入 Route 的关停排空窗口：应用关停时回调内仍在读取的流会被等待，超时后强制关停。

### 3. 预签名 URL 共享

为浏览器、移动端生成临时访问地址；AWS S3 对 SigV4 预签名 URL 的最长期限为 7 天（604800 秒），私有化存储以各自实现为准：

```java
URL url = s3RouteTemplate.execute("storage-primary", client -> client.generatePresignedUrl(
        new GeneratePresignedUrlRequest("order-attachments", "2026/08/report.txt")
                .withMethod(HttpMethod.GET)
                .withExpiration(new Date(System.currentTimeMillis() + 600_000L))));
```

### 4. 老私有化存储：V2 签名 + 私有 CA

部署为 V2 签名的自签 HTTPS 存储按 target 组合配置 `signer-type: S3_V2` 与 `trusted-ca-file`（见最大配置的 `storage-legacy`）。CA 文件支持 PEM / DER、单文件多 CA；Route 建立"JRE 默认可信根 + 私有 CA"的专用信任链，保留严格主机名校验——域名不匹配时应修复证书或 endpoint，不存在绕过手段，也不支持 AWS SDK 的 `disableCertChecking` 全局开关（启动期拒绝）。

### 5. 客户端引用：跳出回调的长持有

个别场景（框架适配、与既有代码集成）需要长持有客户端引用时，通过 `amazonS3(targetKey)` 获取标准 `AmazonS3` 自行调用——引用生命周期归 Route 管理，调用方不得调用 `shutdown()`；该引用不参与请求生命周期记账，常规操作一律优先 `execute` 回调。

### 6. 多存储隔离

不同 target 的连接池、凭据、超时完全独立；同一 bucket 名在不同存储各自独立。MinIO 服务端配置了 `MINIO_REGION` 时，target 的 `region` 必须与其一致，否则签名校验失败（403 SignatureDoesNotMatch）。

## 🔍 调用日志

Route 在 DEBUG 级别输出调用埋点：`execute` 回调在完成时输出一条（操作名、targetKey、耗时毫秒），失败路径附带异常类型；target 初始化、初始化失败回滚与关停排空输出生命周期埋点。日志只包含上述受控元数据，不输出 bucket、对象 key、对象内容、凭据或 endpoint。排查调用行为时按包名开启：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.s3.route: DEBUG
```

## 🛡️ 异常与错误码

路由与配置问题抛 `S3RouteException`（只含稳定错误码与受控消息，不输出凭据或 endpoint）；对象操作失败（如 `AmazonS3Exception`）原样透传不包装——连接与配置问题看 Route 异常，对象操作失败看 SDK 异常的状态码与错误码。

| 错误码 | 含义 |
| --- | --- |
| `S3_ROUTE_001` | target 未登记 |
| `S3_ROUTE_002` | targetKey 非法 |
| `S3_ROUTE_003` | target 配置非法 |
| `S3_ROUTE_004` | 请求参数非法 |
| `S3_ROUTE_005` | Route 已关闭 |

## 📄 Spring Boot 兼容性

以下矩阵每档使用真实 MinIO 双版本执行上传、下载、列举、预签名与删除的闭环 E2E，并执行全部模块测试（49 项，无跳过、无失败），全部通过：

| Spring Boot | JDK | Gradle |
| --- | --- | --- |
| 2.2.13.RELEASE | 8 | 7.6 |
| 2.3.12.RELEASE | 8 | 7.6 |
| 2.4.5 | 8 | 7.6 |
| 2.7.9 | 11 | 8.5 |
