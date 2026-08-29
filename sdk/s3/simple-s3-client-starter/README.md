# simple-s3-client-starter

面向 S3 兼容对象存储（AWS S3、MinIO 及私有化兼容存储）的对象操作语义客户端。在 `simple-s3-route-starter` 的多 target 连接治理之上封装高频对象操作——上传（直传/流式/自动分片一把梭）、下载（回读/落盘/Range 断点续传）、删除、存在性、列举（对象/版本/分片）、元数据、复制、对象标签、预签名（GET/PUT）、分片（显式四步）、STS 临时凭证、桶管理与事件通知接收——SDK 异常统一翻译为带稳定错误码的语义异常；连接治理（target 路由、连接池、凭据、TLS、生命周期）全部复用 Route，本组件不重复定义。

## ✨ 特性

- **语义门面直给**：51 个高频对象操作一行调用，参数即业务语义，无 SDK 回调样板代码；长尾操作经 `execute` 回调拿到标准 `AmazonS3` 客户端兜底。
- **异常统一翻译**：SDK 操作异常按 S3 错误码翻译为语义异常（`NoSuchKey` → `ObjectNotExistException`、`AccessDenied` → `AccessDeniedException` 等），cause 保留原异常；无精确映射的按操作归类包装；Route 连接层异常（`S3RouteException`）原样透传，层次分明。
- **自动 contentLength**：内存流（`ByteArrayInputStream`）上传未提供元数据时自动补 contentLength，规避 `aws-chunked` 编码在部分私有化存储的兼容问题；其余流不缓冲不探测，行为完全可预期。
- **断点续传下载**：`downloadObject` 对本地已存在部分内容的文件从已下载字节处 Range 续传追加；服务端 `InvalidRange` 视为已下载完成（幂等）；版本化桶可传 `versionId` 按指定版本下载。
- **自动分片一把梭**：`uploadObjectMultipart` 阈值内直传、超阈值批间并发分段上传，任一分段失败自动取消并 abort 清理已传分段。
- **分片显式四步**：initiate → uploadPart → complete / abort 显式表达，不隐藏状态机；complete 前置校验分段编号/ETag 并按编号升序提交；`listParts` / `listMultipartUploads` 内部分页聚合。
- **配置化重试**：File 上传、删除、落盘下载、断点续传与分段上传按配置重试（次数与间隔可配）；流式上传因流不可重放不参与重试。
- **幂等语义**：删除对象（`NoSuchKey` 视为成功）、中止分段上传（`NoSuchUpload` 视为成功）、建桶（已存在找回既有桶）、建文件夹（已存在跳过）。
- **STS 临时凭证**：普通会话凭证与路径级降权凭证（assumeRole + NotResource 策略限定桶/目录外不可操作），按 target 自动构建与缓存 STS 客户端。
- **事件通知接收端点**：内置可选 webhook 接收 Controller（默认关闭），认证三通道（Bearer 头 / URL query / 不校验）兼容不支持自定义请求头的存储，分发给业务监听器 Bean。
- **事件通知解析**：S3 事件通知 JSON 宽松解析（未知字段容错），适配 AWS 与各类兼容存储的事件字段差异。
- **行为可预期**：DEBUG 埋点只含操作名、targetKey、耗时与异常类型，不含 bucket、key、内容或凭据。

## 📦 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-s3-client-starter:1.0.0'

    // 基于 AWS SDK v1（S3）；由业务按自身依赖治理提供运行时版本。
    implementation 'com.amazonaws:aws-java-sdk-s3:1.12.797'

    // 仅使用 STS 临时凭证功能时需要；与 S3 客户端保持同版本。
    implementation 'com.amazonaws:aws-java-sdk-sts:1.12.797'
}
```

本组件以 `api` 传递 `simple-s3-route-starter`（连接治理）与 `task-retry-starter`（重试执行器），引入本组件即具备上述能力，无需重复声明；AWS SDK v1（S3 与 STS）与 jackson 由业务方自带（Spring Boot Web/JSON 场景天然具备 jackson），不使用 STS 临时凭证功能时无需引入 STS 客户端。

**AWS SDK v1 支持范围**：`1.12.x ≥ 1.12.787`（本组件按 `1.12.797` 验证，S3 与 STS 客户端保持同版本）；`1.12.786` 及以下存在路径遍历漏洞 CVE-2025-25394，`1.11.x` 及更早版本均不在支持范围内。jackson 家族版本治理口径与 `simple-s3-route-starter` 一致（提升至 `2.18.6` 及以上并保持同版本对齐）。

事件回调接收端点需要 Spring Web 环境（`spring-web` + DispatcherServlet）；非 web 应用该端点自动不装配，其余功能不受影响。

## 🔧 最小配置

连接配置（target、凭据、超时、TLS）全部在 `simple-s3-route-starter`，本组件只声明启用：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          client:
            enable: true
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

注入 `S3ClientTemplate` 即可使用（容器中实例唯一）：

```java
@Autowired
private S3ClientTemplate s3ClientTemplate;

public void upload(String content) {
    s3ClientTemplate.putObject("storage-primary", "order-attachments",
            "2026/08/report.txt", new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
}
```

启用 Client 前必须先启用 Route：Client 门面构造注入 Route 门面与配置，Route 未启用时应用启动即失败（fail-fast 暴露配置矛盾）。重试执行器由 `task-retry-starter` 自动装配提供（默认启用）。

## ⚙️ 最大配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          client:
            enable: true
            sts:
              role-arn: arn:aws:iam::123456789012:role/s3-limited    # 路径级降权凭证必填
              duration-seconds: 86400
            presigned-url:
              expiration-seconds: 86400
              url-prefix: https://download.example.com              # 网关前缀（见风险条款）
            retry:
              upload-times: 5
              upload-interval-ms: 600
              download-times: 5
              download-interval-ms: 600
            multipart:
              threshold-mb: 100
              part-size-mb: 5
              concurrency: 3
            bucket-lifecycle:
              expiration-prefix: "expiration-"
              expiration-days: 180
            download-directory: ./
            event-callback:
              enable: false
              path: /api/s3-events
              token: ${S3_EVENT_CALLBACK_TOKEN:}
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

| 配置项 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `io.github.surezzzzzz.sdk.s3.client.enable` | boolean | `false` | 是否启用 Client；`false` 时不注册任何 Bean |
| `client.sts.role-arn` | String | — | assumeRole 角色 ARN；路径级降权凭证（`getPathStsCredentials` 族）必填，普通凭证不需要 |
| `client.sts.duration-seconds` | int | `86400` | STS 临时凭证有效时长（秒） |
| `client.presigned-url.expiration-seconds` | long | `86400` | 预签名 URL 默认有效时长；方法入参显式传入时覆盖 |
| `client.presigned-url.url-prefix` | String | 空 | 网关域名前缀；配置后返回「前缀 + 签名路径与查询串」，未配置返回完整 URL（见风险条款） |
| `client.retry.upload-times` | int | `5` | 上传类操作重试次数 |
| `client.retry.upload-interval-ms` | long | `600` | 上传类操作重试间隔（毫秒） |
| `client.retry.download-times` | int | `5` | 下载类操作重试次数 |
| `client.retry.download-interval-ms` | long | `600` | 下载类操作重试间隔（毫秒） |
| `client.multipart.threshold-mb` | int | `100` | 自动分片触发阈值；文件超过走分段上传 |
| `client.multipart.part-size-mb` | int | `5` | 分段大小（MB，S3 协议最小 5） |
| `client.multipart.concurrency` | int | `3` | 分段上传并发度 |
| `client.bucket-lifecycle.expiration-prefix` | String | `expiration-` | 生命周期规则过期前缀 |
| `client.bucket-lifecycle.expiration-days` | int | `180` | 生命周期规则过期天数 |
| `client.download-directory` | String | `./` | 断点续传下载默认目录（saveTo 未指定时按 `目录/bucket/key` 落盘） |
| `client.event-callback.enable` | boolean | `false` | 是否启用事件回调接收端点（需 web 环境） |
| `client.event-callback.path` | String | `/api/s3-events` | 接收端点路径 |
| `client.event-callback.token` | String | 空 | 认证 token；长期静态、无有效期，变更需重启；空表示不校验（见风险条款） |

target 连接配置（endpoint、region、签名版本、TLS、凭据、连接池参数）见 `simple-s3-route-starter` 的配置说明。

## 💡 最佳实践场景

### 1. 上传：File 直传，内存流自动补长度，大文件一把梭

File 上传由 SDK 按文件长度自动携带 contentLength；`ByteArrayInputStream` 上传未提供元数据时自动补 contentLength，规避 `aws-chunked` 流式签名编码在个别私有化存储的兼容问题。其余流类型（HTTP 响应流、管道流等）不做隐藏缓冲与探测，这类流建议显式传元数据：

```java
// File 直传（按配置重试）
s3ClientTemplate.putObject("storage-primary", "order-attachments",
        "2026/08/report.pdf", reportFile);

// 内存流自动补长度（流式路径不重试）
s3ClientTemplate.putObject("storage-primary", "order-attachments",
        "2026/08/report.txt", new ByteArrayInputStream(jsonBytes));

// 其他流显式传元数据
ObjectMetadata metadata = new ObjectMetadata();
metadata.setContentLength(length);
s3ClientTemplate.putObject("storage-primary", "order-attachments", key, inputStream, metadata);

// 大文件一把梭：阈值内直传，超阈值并发分段、失败自动 abort 清理
s3ClientTemplate.uploadObjectMultipart("storage-primary", "order-attachments",
        "2026/08/video.mp4", videoFile);
```

### 2. 下载：回读、落盘与断点续传

`getObject` 返回标准 `S3Object`（内容流由调用方负责关闭，可带 Range 起点与版本号）；`downloadToFile` 直接落盘覆盖写；`downloadObject` 断点续传——本地已有部分内容时从已下载字节处 Range 续传追加，网络中断后重调同方法即续传：

```java
// 小对象回读（用完关流）
try (S3Object object = s3ClientTemplate.getObject("storage-primary",
        "order-attachments", "2026/08/report.txt")) {
    String content = IOUtils.toString(object.getObjectContent(), StandardCharsets.UTF_8);
}

// 落盘（覆盖写）
s3ClientTemplate.downloadToFile("storage-primary", "order-attachments",
        "2026/08/report.pdf", new File("/tmp/report.pdf"));

// 断点续传（saveTo 为空按 download-directory/bucket/key 落盘）
s3ClientTemplate.downloadObject("storage-primary", "order-attachments",
        "2026/08/video.mp4", "/data/download/video.mp4");

// 版本化桶内按版本下载（versionId 来自 listVersions 结果）
s3ClientTemplate.downloadObject("storage-primary", "audit-logs",
        "2026/08/report.txt", "/data/download/report-v2.txt", versionId);
```

### 3. 删除与幂等

删除对象幂等（对象已不存在视为成功）；重删、删后建同 key 均无副作用：

```java
s3ClientTemplate.deleteObject("storage-primary", "order-attachments", "2026/08/report.txt");
boolean exists = s3ClientTemplate.doesObjectExist("storage-primary",
        "order-attachments", "2026/08/report.txt");   // false
```

### 4. 预签名 URL：下载带响应头，上传限 Content-Type

GET 预签名自动携带附件下载 `Content-Disposition` 与按扩展名推断的 `Content-Type`（可切换 INLINE 内联预览）；PUT 预签名可限定上传请求必须携带的 `Content-Type`：

```java
// 下载预签名（300 秒有效，附件下载语义）
String downloadUrl = s3ClientTemplate.generatePresignedUrl("storage-primary",
        "order-attachments", "2026/08/report.pdf", 300L);

// 内联预览
String previewUrl = s3ClientTemplate.generatePresignedUrl("storage-primary",
        "order-attachments", "2026/08/report.pdf", 300L, FileDisposition.INLINE);

// 上传预签名（限定 text/plain）
String uploadUrl = s3ClientTemplate.generateUploadPresignedUrl("storage-primary",
        "order-attachments", "2026/08/note.txt", 300L, "text/plain");
```

### 5. 对象标签

数量 ≤10、Key/Value UTF-8 ≤128 字节，参数校验前置（非法直接 `TaggingFailedException` 不打到存储侧）：

```java
Map<String, String> tags = new HashMap<>();
tags.put("env", "production");
s3ClientTemplate.setObjectTagging("storage-primary", "order-attachments",
        "2026/08/report.pdf", tags);
Map<String, String> readBack = s3ClientTemplate.getObjectTagging("storage-primary",
        "order-attachments", "2026/08/report.pdf");
s3ClientTemplate.deleteObjectTagging("storage-primary", "order-attachments",
        "2026/08/report.pdf");
```

### 6. 分片上传：显式四步与列举

大文件分片显式表达，不隐藏状态机；complete 前置校验分段编号（1~10000、不重复）与 ETag 非空，并按编号升序提交；失败路径 `abortMultipartUpload` 清理已上传分片（重复中止幂等）；`listParts` / `listMultipartUploads` 内部分页聚合：

```java
String uploadId = s3ClientTemplate.initiateMultipartUpload("storage-primary",
        "order-attachments", "2026/08/large.bin");
List<PartETag> etags = new ArrayList<>();
etags.add(s3ClientTemplate.uploadPart("storage-primary", "order-attachments",
        "2026/08/large.bin", uploadId, 1, partFileOne).getPartETag());
etags.add(s3ClientTemplate.uploadPart("storage-primary", "order-attachments",
        "2026/08/large.bin", uploadId, 2, partFileTwo).getPartETag());
s3ClientTemplate.completeMultipartUpload("storage-primary", "order-attachments",
        "2026/08/large.bin", uploadId, etags);

// 进行中的分段上传排查
MultipartUploadList uploads = s3ClientTemplate.listMultipartUploads("storage-primary",
        "order-attachments");
MultipartUploadPartList parts = s3ClientTemplate.listParts("storage-primary",
        "order-attachments", "2026/08/large.bin", uploadId);
```

### 7. STS 临时凭证

普通凭证直接降时效；路径级降权凭证经 assumeRole + `NotResource` 策略限定（桶/目录外资源不可操作），需要 target 为 ACCESS_KEY 凭据形态且配置 `sts.role-arn`：

```java
// 普通临时凭证
Credentials normal = s3ClientTemplate.getNormalStsCredentials("storage-primary");

// 桶级降权（桶外不可操作）
Credentials bucketScoped = s3ClientTemplate.getBucketStsCredentials("storage-primary",
        "order-attachments");

// 目录级降权（桶内其他目录不可操作）
Credentials dirScoped = s3ClientTemplate.getDirStsCredentials("storage-primary",
        "order-attachments", "2026/08");
```

### 8. 桶治理：幂等建桶、版本化、生命周期

`createBucket` 幂等（已存在找回既有桶不抛异常）；`createVersioningBucket` 一步到位（建桶 + 启用多版本 + 配置 `expiration-` 前缀生命周期过期规则）；版本化桶内 `listVersions` 列举版本历史；`createFolder` 创建以 `/` 结尾的文件夹对象（已存在幂等）：

```java
s3ClientTemplate.createBucket("storage-primary", "order-attachments");
s3ClientTemplate.createVersioningBucket("storage-primary", "audit-logs");
VersionListing versions = s3ClientTemplate.listVersions("storage-primary", "audit-logs", "2026/");
s3ClientTemplate.createFolder("storage-primary", "order-attachments", "2026/08");
```

`putObjectWithExpirationPrefix` / `downloadObjectWithExpirationPrefix` / `deleteObjectWithExpirationPrefix` 自动在 key 前拼接 `expiration-` 前缀——与桶生命周期规则配合实现「到期自动清理」的临时对象管理：

```java
s3ClientTemplate.putObjectWithExpirationPrefix("storage-primary", "order-attachments",
        "tmp/preview.pdf", tempFile);   // 实际写入 expiration-tmp/preview.pdf
```

### 9. 事件通知：接收端点与解析

**接收端点**（可选，`event-callback.enable` 开启）：存储侧事件通知推送到本服务，认证 → 解析 → 分发给业务的 `S3EventListener` Bean（按 Order 顺序）。认证三通道，配置一个 token 同时兼容两类存储推送源：

| 通道 | 形态 | 适用 |
| --- | --- | --- |
| Header Bearer | `Authorization: Bearer <token>` | 支持自定义请求头的存储（推荐，token 不落访问日志） |
| URL query | `<path>?token=<token>` | 只能配回调 URL、无法配置自定义请求头的存储 |
| 不校验 | token 未配置 | 纯内网 / 网络层隔离场景 |

```java
@Component
public class OrderAttachmentListener implements S3EventListener {

    @Override
    public void onEvent(S3Event event) {
        for (S3Event.Record record : event.getRecords()) {
            // 按 record.getS3().getObject().getSequencer() 做去重（见风险条款）
        }
    }
}
```

**解析**：收到的 S3 事件通知 JSON（webhook、队列消息体等）经 `parseEvent` 宽松解析，未知字段容错：

```java
S3Event event = s3ClientTemplate.parseEvent(messageBody);
```

### 10. 长尾操作：execute 兜底

门面未覆盖的操作（桶策略、通知配置等）经 `execute` 回调拿到标准 `AmazonS3` 客户端表达；回调内的 SDK 异常不经翻译原样抛出：

```java
TagSet tags = s3ClientTemplate.execute("storage-primary", client ->
        client.getBucketTagging("order-attachments"));
```

### 11. 异常语义分拣

对象不存在、无权限等语义错误按异常类型捕获处理；连接与配置问题（target 未登记、Route 已关闭等）保持 `S3RouteException` 原样抛出：

```java
try {
    s3ClientTemplate.getObject("storage-primary", "order-attachments", "2026/08/report.txt");
} catch (ObjectNotExistException ignored) {
    // 对象不存在
} catch (AccessDeniedException exception) {
    // 凭据或权限问题
}
```

## ⚠️ 风险条款

- **`presigned-url.url-prefix` 仅限签名一致性已验证的网关组合**：预签名 URL 的 host 是签名要素，网关转发必须保持 Host 头与签名一致，否则存储侧返回 403。配置前先在该网关组合上实测预签名 URL 可用性。
- **`event-callback.token` 未配置 = 不校验**：任何能访问该端点的请求都会被受理。公网可达的部署必须配置 token，并配合网络层（防火墙/安全组）限制来源。
- **URL query 认证通道弱于 Header**：query token 会出现在反向代理与访问日志中。能配置自定义请求头的存储一律优先 Bearer 头通道。
- **事件通知至少一次投递**：重复推送的可能由存储侧重投引起，去重是监听器责任——用 `S3Event.Record.S3.Object.sequencer` 字段做幂等判重。
- **断点续传的本地文件假定**：续传以本地文件字节数为已下载进度，若本地文件与远端对象被并发修改，续传结果不可预期——同一文件不要并发调用 `downloadObject`。

## 🔍 调用日志

Client 在 DEBUG 级别输出调用埋点：每个语义操作完成时输出一条（操作名、targetKey、耗时毫秒），失败路径附带异常类型。日志只包含上述受控元数据，不输出 bucket、对象 key、对象内容或凭据。排查调用行为时按包名开启：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.s3.client: DEBUG
```

## 🛡️ 异常与错误码

对象操作语义问题抛具体异常（均继承 `S3ClientException`，只含稳定错误码与受控消息，cause 保留原 SDK 异常）；连接与配置问题保持 Route 异常原样抛出。

| 异常 | 错误码 | 触发 |
| --- | --- | --- |
| `S3ClientException` | `S3_CLIENT_001` | 请求参数非法（空白 targetKey/bucket/key 等） |
| `ObjectNotExistException` | `S3_CLIENT_002` | 对象不存在（`NoSuchKey` / 404） |
| `BucketNotExistException` | `S3_CLIENT_003` | 桶不存在（`NoSuchBucket`） |
| `BucketAlreadyExistException` | `S3_CLIENT_004` | 桶已存在（`BucketAlreadyExists` 等） |
| `AccessDeniedException` | `S3_CLIENT_005` | 访问被拒绝（`AccessDenied` / 403 / 签名不匹配） |
| `UploadFailedException` | `S3_CLIENT_006` | 上传失败（含分片上传全链路与分段校验拒绝） |
| `DownloadFailedException` | `S3_CLIENT_007` | 下载失败（含写本地文件失败） |
| `DeleteFailedException` | `S3_CLIENT_008` | 删除失败 |
| `ListFailedException` | `S3_CLIENT_009` | 列举失败 |
| `GetMetadataFailedException` | `S3_CLIENT_010` | 元数据获取失败 |
| `CopyFailedException` | `S3_CLIENT_011` | 复制失败 |
| `EventParseFailedException` | `S3_CLIENT_012` | 事件 JSON 解析失败 |
| `S3ClientException` | `S3_CLIENT_013` | 桶操作失败（建桶 / 删桶 / 桶存在性 / 版本化 / 生命周期） |
| `TaggingFailedException` | `S3_CLIENT_014` | 对象标签参数非法或设置失败 |
| `StsCredentialsFailedException` | `S3_CLIENT_015` | STS 临时凭证获取失败（配置缺失 / target 未登记 / 凭据形态不支持） |

## 📄 Spring Boot 兼容性

以下矩阵每档使用真实 MinIO 双版本执行上传、下载、断点续传、分片、标签、版本化桶与预签名的闭环 E2E，并执行全部模块测试（89 项，无跳过、无失败），全部通过：

| Spring Boot | JDK | Gradle |
| --- | --- | --- |
| 2.2.13.RELEASE | 8 | 7.6 |
| 2.3.12.RELEASE | 8 | 7.6 |
| 2.4.5 | 8 | 7.6 |
| 2.7.9 | 11 | 8.5 |
