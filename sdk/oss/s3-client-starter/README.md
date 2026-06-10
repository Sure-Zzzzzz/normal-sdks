# S3 Client Starter

[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
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
- `deleteObject(bucketName, objectKey, bypassGovernanceRetention)` - 删除对象（可指定是否绕过治理保留模式）

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

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:s3-client-starter:2.0.0'
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

            # 连接池配置
            max-connections: 500
            connection-timeout: 10000
            client-execution-timeout: 0
            connection-max-idle-millis: 60000
            connection-ttl: -1
```

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

---

## 版本历史

### 2.0.0 (2026-06-10)

破坏性升级：包名迁移 `sdk.s3` → `sdk.oss.s3`，所有类按规范重命名。新增 14 项能力，重试统一使用 `TaskRetryExecutor`。详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)

---

## 许可证

Apache License 2.0