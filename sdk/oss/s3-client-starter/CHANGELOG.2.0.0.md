# CHANGELOG

## 2.0.0

### 破坏性变更

- **包名迁移**：`io.github.surezzzzzz.sdk.s3` → `io.github.surezzzzzz.sdk.oss.s3`
- 所有类按规范重命名：
  - `OssClient` → `S3Client`
  - `OssConfiguration` → `S3ClientConfiguration`
  - `OssProperties` → `S3ClientProperties`
  - `OssFileDisposition` → `FileDisposition`
  - `OssPackage` → `S3ClientPackage`
  - `OssComponent` → `S3ClientComponent`
  - `ContentTypeUtil` → `ContentTypeHelper`（包名 `util` → `support`）

### 新增能力

- **判断对象是否存在**：`doesObjectExist(bucket, key)`
- **获取对象元信息**：`getObjectMetadata(bucket, key)` / `getObjectMetadata(bucket, key, versionId)`
- **删除对象**：`deleteObject(bucket, key)` / `deleteObject(bucket, key, bypassGovernanceRetention)`（幂等，NoSuchKey 视为成功）
- **列举对象**：`listObjects(bucket)` / `listObjects(bucket, prefix)` / `listObjects(bucket, prefix, maxKeys)`
- **复制对象**：`copyObject(sourceBucket, sourceKey, destBucket, destKey)`
- **生成上传预签名 URL**：`generateUploadPresignedUrl(bucket, key, expiration)` / `generateUploadPresignedUrl(bucket, key, expiration, contentType)`
- **列举版本历史**：`listVersions(bucket)` / `listVersions(bucket, prefix)`
- **UUID 桶名生成**：`generateBucketName(symbol)` / `generateBucketName(prefix, symbol)`（MD5 → UUID，保证全局唯一）
- **InputStream 上传**：`uploadObject(bucket, key, InputStream, contentLength)`

### 重构

- 新建 `S3ClientConstant`：所有默认值和魔法数提取为常量（`RETRY_INTERVAL_MILLIS` / `BUFFER_SIZE` / `SIGNER_TYPE_STS` / `S3_ERROR_NO_SUCH_KEY` 等）
- 新建 `ErrorCode`（OSS_101~208）和 `ErrorMessage`（一一对应）
- 重构异常基类：`S3ClientException` 和 `S3ServerException` 添加 `errorCode` 字段和 `serialVersionUID`
- 所有子异常统一补充 `cause` 构造函数
- 新增 `ObjectAlreadyExistsException`、`DownloadObjectFailedException`、`DeleteObjectFailedException`、`CopyObjectFailedException`、`ListObjectsFailedException`、`GetObjectMetadataFailedException`
- `S3ClientProperties`：`@Data` + `@ConditionalOnProperty`，默认值全部引用常量，移除 `@S3ClientComponent`（避免与 `@EnableConfigurationProperties` 双注册）
- `S3ClientConfiguration`：`@EnableConfigurationProperties` + `@ConditionalOnProperty` + `@ComponentScan(PackageMarker + Component)`，`capitalizeObjectMapper` 移入 S3Client 构造函数（避免 ObjectMapper Bean 冲突）
- 提取 `CapitalizeNamingStrategy`、`DateTimeHelper`、`ContentTypeHelper`、`PolicyDocumentHelper` 为 `support` 层独立类

### 代码优化

- 所有重试逻辑统一使用 `TaskRetryExecutor`（`task-retry-starter:1.0.1`），消除手写重试循环
- `uploadObjectWithRetry` 中 `inputStream` 未赋值的 dead code 已消除（TaskRetryExecutor 替代）
- `createHalfYearBucket` 改为委托 `createVersioningAndDefaultLifecycleBucket`（复用逻辑）
- STS 方法返回前增加 null 检查，credentials 为 null 时抛出 `S3ObjectNotExistException`
- 下载重试逻辑中魔法数 30000 / 8192 改为引用 `S3ClientConstant`
- 上传/删除/文件夹创建重试逻辑中魔法数 30000 改为引用 `S3ClientConstant`
- 删除废弃方法 `getBucketOnlyPutStsCredentials`

### 测试

- 新增 `S3ClientSupportTest`（22 项纯逻辑测试，覆盖 ContentTypeHelper / FileDisposition / DateTimeHelper / generateBucketName）
- 新增 `S3ClientIntegrationTest`（36 项有序 E2E 测试，全程操作 `normal-sdks-dev` 桶）
- 测试配置：`application.yml`（提交 git）+ `application-local.yml`（本地凭证，不提交）
- STS 测试用例使用 `assumeTrue` 保护（未配置 roleArn 时跳过）

---

## 1.0.x

- 初始版本