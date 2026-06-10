# S3 Client Starter 1.x

> **⚠️ 1.x 已封版**：此文档对应 1.x 最终状态，不再接受新功能开发，仅做安全修复。
>
> 新项目请使用 [2.0.0](README.md)。

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 AWS SDK 的 S3 对象存储 Spring Boot Starter，提供简洁易用的对象存储操作接口。

**包名**：`io.github.surezzzzzz.sdk.s3`（2.0.0 迁移至 `io.github.surezzzzzz.sdk.oss.s3`）

**核心类**：`OssClient`、`OssProperties`、`OssConfiguration`

---

## 核心能力

### 1. 存储桶管理

- `createS3Bucket(bucketName)` - 创建存储桶（幂等）
- `createVersioningAndDefaultLifecycleBucket(bucketName)` - 创建带多版本和过期策略的存储桶
- `createHalfYearBucket(bucketName)` - 创建半年过期桶

### 2. 文件夹管理

- `createFolder(bucketName, folderName)` - 创建文件夹（本质是创建以 / 结尾的空对象）

### 3. 上传

- `uploadObject(bucketName, objectKey, file)` - 上传文件（含重试）
- `uploadObjectWithExpirationPrefix(bucketName, objectKey, file)` - 上传并添加过期前缀

### 4. 下载

- `downloadObject(bucketName, objectKey, saveTo)` - 下载对象
- `downloadObjectWithExpirationPrefix(bucketName, objectKey, saveTo)` - 下载带过期前缀的对象
- `getObject(bucketName, objectKey, localFileSize)` - 获取对象（支持断点续传）
- `getFullObject(bucketName, objectKey)` - 获取完整对象

### 5. STS 凭证

- `getNormalStsCredentials()` - 普通临时凭证
- `getBucketOnlyPutStsCredentials(bucketName)` - 桶级只写凭证（**已废弃**，2.0 删除）
- `getBucketStsCredentials(bucketName)` - 桶级临时凭证
- `getDirStsCredentials(bucketName, dir)` - 目录级临时凭证
- `getPathStsCredentials(path)` - 路径级临时凭证

### 6. 预签名 URL

- `generatePresignedUrl(bucketName, objectKey, expirationSeconds)` - 下载预签名 URL
- `generatePresignedUrl(bucketName, objectKey, expirationSeconds, disposition)` - 下载预签名 URL（指定 FileDisposition）
- `customPresignedUrl(bucketName, objectKey, expirationSeconds)` - 自定义 HmacSHA1 签名预签名 URL

### 7. 工具方法

- `generator32CharactersBucketName(symbol)` - 生成 32 位桶名（MD5，2.0 改为 UUID）
- `convertToOffsetDateTime(date)` - Date 转 OffsetDateTime

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:s3-client-starter:1.0.0'
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
        s3:
          endpoint: "https://s3.example.com"
          access-key: "your-access-key"
          secret-key: "your-secret-key"
          role-arn: "arn:aws:iam::123456789012:role/OSSRole"
          sts-duration-seconds: 3600
          presigned-url-expiration-seconds: 3600
          url-prefix: "https://cdn.example.com"
          download-directory: "/tmp/oss-downloads"
          max-download-retry-times: 3
          max-download-retry-seconds: 300
          max-upload-retry-times: 3
          max-upload-retry-seconds: 300
```

---

## 异常体系

| 异常类 | errorCode | 说明 |
|--------|-----------|------|
| `S3ObjectNotExistException` | OSS_101 | S3 对象不存在 |
| `FileNotFoundException` | OSS_102 | 本地文件未找到 |
| `CreateBucketFailedException` | OSS_201 | 创建存储桶失败 |
| `CreateFolderFailedException` | OSS_202 | 创建文件夹失败 |
| `UploadObjectFailedException` | OSS_203 | 上传对象失败 |

---

## 封版说明（迁移至 2.0.0）

2.0.0 为破坏性升级，主要变更：

- **包名**：`sdk.s3` → `sdk.oss.s3`
- **类重命名**：OssClient → S3Client，OssProperties → S3ClientProperties 等
- **新增能力**：判断对象存在、获取元信息、删除对象、列举对象、复制对象、上传预签名 URL、列举版本历史、UUID 桶名、InputStream 上传
- **重试重构**：所有重试统一使用 `TaskRetryExecutor`，消除手写重试循环

详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)

---

## 许可证

Apache License 2.0