# S3 Client Starter

[![Version](https://img.shields.io/badge/version-2.0.2-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> **1.x 封版文档**：如果你使用的是 1.x 版本（包名 `io.github.surezzzzzz.sdk.s3`），请查看 [README.1.x.md](README.1.x.md)。

基于 AWS SDK 的 S3 对象存储 Spring Boot Starter，提供简洁易用的对象存储操作接口。

**包名**：`io.github.surezzzzzz.sdk.oss.s3`

---

## 核心能力

### 1. 存储桶管理

- `createS3Bucket(bucketName)` - 创建存储桶（幂等）
- `createVersioningAndDefaultLifecycleBucket(bucketName)` - 创建带多版本和过期策略的存储桶
- `createHalfYearBucket(bucketName)` - 创建半年过期桶
- `enableBucketVersioning(bucketName)` - 启用多版本
- `setDefaultBucketLifecycle(bucketName)` - 设置默认生命周期

### 2. 文件夹管理

- `createFolder(bucketName, folderName)` - 创建文件夹（本质是创建以 / 结尾的空对象，幂等）

### 3. 上传

- `uploadObject(bucketName, objectKey, file)` - 上传文件（含重试）
- `uploadObject(bucketName, objectKey, inputStream, contentLength)` - 上传 InputStream（满足非文件来源场景）
- `uploadObjectWithExpirationPrefix(bucketName, objectKey, file)` - 上传并添加过期前缀

### 4. 下载

- `downloadObject(bucketName, objectKey, saveTo)` - 下载对象（含重试）
- `downloadObject(bucketName, objectKey, saveTo, versionId)` - 下载指定版本
- `downloadObjectWithExpirationPrefix(bucketName, objectKey, saveTo)` - 下载带过期前缀的对象
- `getObject(bucketName, objectKey, localFileSize)` - 获取对象（支持断点续传）
- `getFullObject(bucketName, objectKey)` - 获取完整对象

### 5. 删除

- `deleteObject(bucketName, objectKey)` - 删除对象（幂等，NoSuchKey 视为成功）
- `deleteObject(bucketName, objectKey, bypassGovernanceRetention)` - 删除对象（兼容保留参数；当前单对象删除不实际绕过治理保留）

### 6. 查询

- `doesObjectExist(bucketName, objectKey)` - 判断对象是否存在
- `getObjectMetadata(bucketName, objectKey)` - 获取对象元信息（大小/类型/ETag/最后修改时间）
- `getObjectMetadata(bucketName, objectKey, versionId)` - 获取指定版本的元信息
- `listObjects(bucketName)` - 列举对象
- `listObjects(bucketName, prefix)` - 列举对象（带前缀过滤）
- `listObjects(bucketName, prefix, maxKeys)` - 列举对象（带前缀和条数限制）

### 7. 复制

- `copyObject(sourceBucket, sourceKey, destBucket, destKey)` - 复制对象，返回新对象 ETag

### 8. 版本历史

- `listVersions(bucketName)` - 列举版本历史
- `listVersions(bucketName, prefix)` - 列举版本历史（带前缀过滤）

### 9. STS 凭证

- `getNormalStsCredentials()` - 普通临时凭证
- `getBucketStsCredentials(bucketName)` - 桶级临时凭证
- `getDirStsCredentials(bucketName, dir)` - 目录级临时凭证
- `getPathStsCredentials(path)` - 路径级临时凭证

### 10. 预签名 URL

- `generatePresignedUrl(bucketName, objectKey, expirationSeconds)` - 下载预签名 URL（默认下载模式）
- `generatePresignedUrl(bucketName, objectKey, expirationSeconds, disposition)` - 下载预签名 URL（指定 FileDisposition）
- `generateUploadPresignedUrl(bucketName, objectKey, expirationSeconds)` - 上传预签名 URL
- `generateUploadPresignedUrl(bucketName, objectKey, expirationSeconds, contentType)` - 上传预签名 URL（指定 Content-Type）
- `customPresignedUrl(bucketName, objectKey, expirationSeconds)` - 自定义 HmacSHA1 签名预签名 URL

### 11. 工具方法

- `generateBucketName(symbol)` - 生成 32 位 UUID 桶名
- `generateBucketName(prefix, symbol)` - 生成带前缀的 UUID 桶名
- `toOffsetDateTime(date)` - Date 转 OffsetDateTime

### 12. 对象标签

- `setObjectTagging(bucketName, objectKey, tags)` - 设置对象标签（覆盖已有标签；当前 SDK 限制最多 10 个键值对，Key/Value 最大 128 UTF-8 字节）
- `getObjectTagging(bucketName, objectKey)` - 获取对象标签
- `deleteObjectTagging(bucketName, objectKey)` - 删除对象全部标签（对象不存在时抛 `DeleteObjectTaggingFailedException`）

### 13. 分段上传

- `uploadObjectMultipart(bucketName, objectKey, file)` - 大文件自动分段上传（阈值 100MB，默认 5MB/段，3 并发）
- `uploadObjectMultipart(bucketName, objectKey, file, partSizeMB)` - 指定分段大小
- `generateUploadId(bucketName, objectKey)` - 初始化分段上传，返回 uploadId
- `uploadPart(bucketName, objectKey, uploadId, partNumber, inputStream, contentLength)` - 上传单个分段，返回 `PartETag`
- `completeMultipartUpload(bucketName, objectKey, uploadId, partETags)` - 完成分段上传（partETags 非空，partNumber 1~10000 且不能重复；SDK 会复制并升序提交）
- `abortMultipartUpload(bucketName, objectKey, uploadId)` - 中止分段上传（`NoSuchUpload` 视为成功）
- `listParts(bucketName, objectKey, uploadId)` - 列举已上传的分段，返回 `MultipartUploadPartList`，内部聚合全部分页
- `listMultipartUploads(bucketName)` - 列举进行中的分段上传，内部聚合全部分页

---

## 对象标签示例

```java
Map<String, String> tags = new HashMap<>();
tags.put("env", "dev");
tags.put("owner", "surezzzzzz");

s3Client.setObjectTagging("normal-sdks-dev", "demo/object.txt", tags);
Map<String, String> currentTags = s3Client.getObjectTagging("normal-sdks-dev", "demo/object.txt");
s3Client.deleteObjectTagging("normal-sdks-dev", "demo/object.txt");
```

说明：

- `setObjectTagging` 会覆盖对象已有标签
- `tags` 不能为 null；空 Map 允许，SDK 目标语义为清空标签。为最大兼容性，生产代码清空标签优先使用 `deleteObjectTagging`
- 单对象最多 10 个标签
- Key 不能为 null / empty / blank；Value 不能为 null，但允许空字符串
- Key / Value 按 UTF-8 字节长度校验，当前 SDK 限制均为 128 字节
- 空 Map 会通过 PUT Object tagging 发送空 TagSet；如目标 S3 兼容服务不接受空 TagSet，请使用 `deleteObjectTagging`
- `deleteObjectTagging` 只删除标签，不删除对象；对象不存在时抛 `DeleteObjectTaggingFailedException`

---

## 分段上传示例

### 自动分段上传

```java
File file = new File("/data/big-object.bin");
s3Client.uploadObjectMultipart("normal-sdks-dev", "demo/big-object.bin", file);
```

自动分段上传会根据 `multipart-threshold-mb` 判断是否触发分段；文件未超过阈值且不超过 5GB 时，会自动退回单次 `uploadObject`。

### 指定分段大小

```java
s3Client.uploadObjectMultipart("normal-sdks-dev", "demo/big-object.bin", file, 16);
```

`partSizeMB` 不能小于 5。单对象最多 10000 个 parts；当文件较大时，需要调大 `partSizeMB`，确保总分段数不超过 10000。

### 手动分段上传

```java
String uploadId = s3Client.generateUploadId("normal-sdks-dev", "demo/manual.bin");
List<PartETag> partETags = new ArrayList<>();

try {
    PartETag part1 = s3Client.uploadPart("normal-sdks-dev", "demo/manual.bin",
            uploadId, 1, inputStream1, contentLength1);
    PartETag part2 = s3Client.uploadPart("normal-sdks-dev", "demo/manual.bin",
            uploadId, 2, inputStream2, contentLength2);
    partETags.add(part1);
    partETags.add(part2);

    s3Client.completeMultipartUpload("normal-sdks-dev", "demo/manual.bin", uploadId, partETags);
} catch (Exception e) {
    s3Client.abortMultipartUpload("normal-sdks-dev", "demo/manual.bin", uploadId);
    throw e;
}
```

注意：

- `generateUploadId` 和 `completeMultipartUpload` 是 S3 multipart 边界操作，SDK 层不做重复调用重试，避免响应丢失时创建孤儿 uploadId 或重复 complete 误报失败
- `completeMultipartUpload` 要求 `partETags` 非空，元素不能为 null，partNumber 范围为 1~10000，ETag 不能为空，partNumber 不能重复；调用方传入列表可以无序，SDK 会复制并按 partNumber 升序提交，不修改原始 List
- public `uploadPart(InputStream...)` 不复用输入流做 SDK 层重试；如果调用方需要手动重试，应重新提供可读的 `InputStream`
- 自动分段上传内部会按文件偏移读取 part，并对单个 part 上传做重试；批次内发现 part 失败后会取消当前批次未完成 future，并由 `abortMultipartUpload` 兜底清理 S3 侧 uploadId
- `abortMultipartUpload` 对 `NoSuchUpload` 视为成功，可用于清理兜底
- `listParts` 和 `listMultipartUploads` 会在方法内部聚合全部分页结果；`listParts` 聚合完成后 `nextPartNumberMarker=0`，`listMultipartUploads` 聚合完成后 `truncated=false` 且 marker 为 null
- multipart 返回模型位于 `io.github.surezzzzzz.sdk.oss.s3.model` 包：`MultipartUpload`、`MultipartUploadPart`、`MultipartUploadPartList`、`MultipartUploadList`

---

## 删除对象说明

`deleteObject(bucketName, objectKey, bypassGovernanceRetention)` 中的 `bypassGovernanceRetention` 为历史兼容参数。当前单对象删除实现使用 AWS SDK S3 `DeleteObjectRequest`，不会实际设置 bypass governance retention；如对象受治理保留保护，删除仍可能失败。

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:s3-client-starter:2.0.2'
```

**⚠️ 必须添加 Spring Boot 依赖**（本 starter 使用 compileOnly 配置）：

```gradle
implementation 'org.springframework.boot:spring-boot-starter'
implementation 'org.springframework.boot:spring-boot-starter-web'
```

---

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        oss:
          s3:
            # 基础连接配置
            endpoint: "https://s3.example.com"
            access-key: "${OSS_ACCESS_KEY}"
            secret-key: "${OSS_SECRET_KEY}"

            # STS配置
            role-arn: "arn:aws:iam::123456789012:role/OSSRole"
            sts-duration-seconds: 86400

            # URL配置
            presigned-url-expiration-seconds: 86400
            url-prefix: "https://cdn.example.com"

            # 存储桶生命周期
            bucket-expiration-prefix: "expiration-"
            bucket-expiration-days: 180

            # 下载配置
            download-directory: "/tmp/oss-downloads"
            max-download-retry-times: 5
            max-download-retry-seconds: 600

            # 上传配置
            max-upload-retry-times: 5
            max-upload-retry-seconds: 600

            # 分段上传配置
            multipart-threshold-mb: 100
            multipart-part-size-mb: 5
            multipart-concurrency: 3

            # 连接池配置
            max-connections: 500
            connection-timeout: 10000
            client-execution-timeout: 0
            connection-max-idle-millis: 60000
            connection-ttl: -1
```

### 分段上传配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `multipart-threshold-mb` | `100` | 自动分段上传阈值；文件大于该值时触发 multipart |
| `multipart-part-size-mb` | `5` | 默认分段大小，不能小于 5MB |
| `multipart-concurrency` | `3` | 自动分段上传并发数，不能小于 1 |

边界说明：

- 单次 `PutObject` 上限为 5GB；即使阈值配置大于 5GB，超过 5GB 的文件也会走 multipart
- S3 单对象最多 10000 个 parts；超出时会抛 `UploadPartFailedException`
- `multipart-part-size-mb` 越大，part 数越少，但单 part 失败后的重传成本越高

---

## 异常体系

| 异常类 | errorCode | 说明 |
|--------|-----------|------|
| `S3ObjectNotExistException` | OSS_101 | S3 对象不存在 |
| `FileNotFoundException` | OSS_102 | 本地文件未找到 |
| `ObjectAlreadyExistsException` | OSS_103 | 对象已存在 |
| `CreateBucketFailedException` | OSS_201 | 创建存储桶失败 |
| `CreateFolderFailedException` | OSS_202 | 创建文件夹失败 |
| `UploadObjectFailedException` | OSS_203 | 上传对象失败 |
| `DownloadObjectFailedException` | OSS_204 | 下载对象失败 |
| `DeleteObjectFailedException` | OSS_205 | 删除对象失败 |
| `CopyObjectFailedException` | OSS_206 | 复制对象失败 |
| `ListObjectsFailedException` | OSS_207 | 列举对象失败 |
| `GetObjectMetadataFailedException` | OSS_208 | 获取对象元信息失败 |
| `SetObjectTaggingFailedException` | OSS_209 | 设置对象标签失败 |
| `UploadPartFailedException` | OSS_210 | 上传分段失败 |
| `CompleteMultipartUploadFailedException` | OSS_211 | 完成分段上传失败 |
| `GetObjectTaggingFailedException` | OSS_212 | 获取对象标签失败 |
| `DeleteObjectTaggingFailedException` | OSS_213 | 删除对象标签失败 |
| `S3ClientPropertiesInvalidException` | OSS_301 | 配置参数非法 |

---

## 版本历史

### 2.0.2 (2026-07-01)

Patch 稳定化版本：补充对象标签 null / blank / null value 前置校验；补充 `completeMultipartUpload` 的 partETags 非空、partNumber 范围、ETag 非空、重复 partNumber 校验，并在 starter 层显式复制排序后提交；自动分段上传批次失败时取消未完成 future；明确 `bypassGovernanceRetention` 兼容参数、分页聚合返回语义和标签空 Map 兼容性。详见 [CHANGELOG.2.0.2.md](CHANGELOG.2.0.2.md)

### 2.0.1 (2026-06-12)

新增对象标签（set/get/deleteObjectTagging）和分段上传（自动分段 + 手动分步 API）能力。新增 6 个异常类（OSS_209~213、OSS_301）及对应 ErrorCode/ErrorMessage。自动分段上传补充 5GB 单次上传上限、10000 parts 上限、非幂等 multipart 边界操作保护；全量 Javadoc 补充，常量集中化修复。详见 [CHANGELOG.2.0.1.md](CHANGELOG.2.0.1.md)

### 2.0.0 (2026-06-10)

破坏性升级：包名迁移 `sdk.s3` → `sdk.oss.s3`，所有类按规范重命名。新增 14 项能力，重试统一使用 `TaskRetryExecutor`。详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)

---

## 许可证

Apache License 2.0