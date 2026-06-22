# CHANGELOG

## 2.0.1

### 新增能力

- **对象标签（Object Tagging）**
  - `setObjectTagging(bucketName, objectKey, tags)` — 设置对象标签（覆盖已有标签）
  - `getObjectTagging(bucketName, objectKey)` — 获取对象标签
  - `deleteObjectTagging(bucketName, objectKey)` — 删除对象全部标签

- **分段上传（Multipart Upload）**
  - `uploadObjectMultipart(bucketName, objectKey, file)` — 大文件自动分段上传（阈值 100MB，默认 5MB/段，3 并发）
  - `uploadObjectMultipart(bucketName, objectKey, file, partSizeMB)` — 指定分段大小
  - `generateUploadId(bucketName, objectKey)` — 初始化分段上传，返回 uploadId（单次调用，不做 SDK 层重试，避免重复创建孤儿 uploadId）
  - `uploadPart(bucketName, objectKey, uploadId, partNumber, inputStream, contentLength)` — 上传单个分段，返回 `PartETag`
  - `completeMultipartUpload(bucketName, objectKey, uploadId, partETags)` — 完成分段上传（单次调用，避免成功响应丢失后重复 complete 误报失败）
  - `abortMultipartUpload(bucketName, objectKey, uploadId)` — 中止分段上传，清理已上传 parts（`NoSuchUpload` 视为成功）
  - `listParts(bucketName, objectKey, uploadId)` — 列举已上传的分段，返回 `MultipartUploadPartList`，内部聚合全部分页
  - `listMultipartUploads(bucketName)` — 列举进行中的分段上传，内部聚合全部分页

### 异常新增

| 异常类 | errorCode | 说明 |
|--------|-----------|------|
| `SetObjectTaggingFailedException` | OSS_209 | 设置对象标签失败 |
| `GetObjectTaggingFailedException` | OSS_212 | 获取对象标签失败 |
| `DeleteObjectTaggingFailedException` | OSS_213 | 删除对象标签失败 |
| `UploadPartFailedException` | OSS_210 | 上传分段失败 |
| `CompleteMultipartUploadFailedException` | OSS_211 | 完成分段上传失败 |
| `S3ClientPropertiesInvalidException` | OSS_301 | 配置参数非法 |

### 规范合规修复

- **常量集中化**：`FileDisposition` 中 `"attachment"` / `"inline"` / 模板 `"%s; filename=\"%s\""` 移至 `S3ClientConstant`
- **常量集中化**：`PolicyDocumentHelper` 中 6 个策略常量（`EFFECT_ALLOW` / `EFFECT_DENY` / `SESSION_NAME_TEMPLATE` / `RESOURCE_POLICY_ARN_TEMPLATE` / `BUCKET_POLICY_ARN_TEMPLATE` / `POLICY_VERSION`）移至 `S3ClientConstant`；`S3Client.getPathStsCredentials` 中相关模板引用同步更新
- **FQN 修复**：`S3Client.java` 中 `java.util.Arrays.asList` 硬编码 FQN 替换为 import 语句
- **常量新增**：`S3ClientConstant` 新增 `CONTENT_DISPOSITION_ATTACHMENT`、`CONTENT_DISPOSITION_INLINE`、`CONTENT_DISPOSITION_TEMPLATE`、`CANONICALIZED_RESOURCE_TEMPLATE`、`MAX_OBJECT_TAGS`、`MAX_TAG_KEY_BYTES`、`MAX_TAG_VALUE_BYTES`、`LIST_PARTS_PAGE_SIZE`、`LIST_UPLOADS_PAGE_SIZE`、`MAX_MULTIPART_PARTS`、`MAX_SINGLE_UPLOAD_BYTES` 及 STS 策略文档相关常量
- **Javadoc 全量补充**：所有异常类公开构造器、新增对象标签/分段上传公开 API、`S3ClientConfiguration` 的 2 个 `@Bean` 方法、`CapitalizeNamingStrategy` 的 4 个 `nameForXxx` 覆盖方法、`PolicyDocumentHelper` 全部常量及字段、`FileDisposition` 枚举常量及字段、`S3ClientProperties` 全部配置属性字段

### 代码质量

- `S3Client.validateObjectTagging` — 使用 `S3ClientConstant` 常量校验标签数量/字节数
- `S3Client.uploadObjectMultipart` — 移除仅用于日志的全文件 MD5 预读，避免大文件上传前额外完整读盘；新增 5GB 单次上传上限和 10000 分段上限保护
- `S3Client.generateUploadId` / `completeMultipartUpload` — 对非幂等 S3 multipart 边界操作使用单次调用，避免响应丢失时重复 initiate/complete 造成孤儿 uploadId 或误报失败
- `S3Client.uploadPartWithRetry` — 自动分段上传改用 `UploadPartRequest.withFile/withFileOffset`，每次重试由 AWS SDK 重新读取文件片段，避免复用已消费的输入流
- `S3Client.listParts` / `listMultipartUploads` — 分页大小改用 `LIST_PARTS_PAGE_SIZE` / `LIST_UPLOADS_PAGE_SIZE`；`listParts` 聚合完全部分页后返回终止 marker `0`
- `MultipartUpload` / `MultipartUploadPart` / `MultipartUploadPartList` / `MultipartUploadList` — 分段上传返回模型移入 `model` 包；分段列表返回对象按单数类名规范命名，替代复数命名 `MultipartUploadParts`
- `CapitalizeNamingStrategy` — 从工具类 `support` 包移入专用 `strategy` 包，`support` 仅保留 `Helper` 类
- `version.properties` — 模块版本更新为 `2.0.1`
- `S3Client.customPresignedUrl` — 规范化资源路径模板改用 `CANONICALIZED_RESOURCE_TEMPLATE`
- `S3Client.createVersioningAndDefaultLifecycleBucket` — 修复异常双重包装（`CreateBucketFailedException` 直接上抛）；修复 `log.error` 中文提示与占位符不一致（`"创建存储桶失败："` → `"创建存储桶失败：{}"`，`e.getMessage()` 填入）
- `S3Client.generateBucketName` — 方法参数 `symbol` 未使用已做 `@SuppressWarnings("unused")` + Javadoc 说明（历史 API 兼容，保留参数签名）；空字符串替换改用 `StringUtils.EMPTY`

### 测试

- 新增 `testSetObjectTagging`（Order 37）— 上传文件并设置标签，**保留文件和标签**便于 S3 Browser 等工具观察
- 新增 `testSetObjectTaggingTooMany`（Order 38）— 超过 10 个标签抛 `SetObjectTaggingFailedException(OSS_209)`，断言 `assertEquals(ErrorCode.SET_OBJECT_TAGGING_FAILED, ex.getErrorCode())`
- 新增 `testDeleteObjectTagging`（Order 39）— 独立链路：upload → setObjectTagging 铺数据 → deleteObjectTagging 清空 → deleteObject 自清理文件
- 新增 `testSetObjectTaggingKeyTooLong`（Order 40）— Key 超 128 字节抛 `SetObjectTaggingFailedException(OSS_209)`
- 新增 `testSetObjectTaggingValueTooLong`（Order 41）— Value 超 128 字节抛 `SetObjectTaggingFailedException(OSS_209)`
- 新增 `testGetObjectTaggingNotFound`（Order 42）— 获取不存在对象的标签抛 `GetObjectTaggingFailedException(OSS_212)`
- 新增 `testDeleteObjectTaggingNotFound`（Order 43）— 删除不存在对象的标签抛 `DeleteObjectTaggingFailedException(OSS_213)`
- 新增 `testUploadObjectMultipartSmallFile`（Order 44）— 小文件走 fallback
- 新增 `testUploadObjectMultipartLargeFile`（Order 45）— 大文件触发分段
- 新增 `testUploadObjectMultipartSpecifyPartSize`（Order 46）— 指定 partSizeMB=6 上传
- 新增 `testUploadObjectMultipartPartSizeTooSmall`（Order 47）— partSizeMB 过小抛异常，断言 `OSS_210`
- 新增 `testManualMultipartUpload`（Order 48）— 手动分段完整流程（init → uploadPart × N → listParts → complete）
- 新增 `testAbortMultipartUpload`（Order 49）— 中止后列举不再包含该 uploadId
- 完整执行 `:sdk:oss:s3-client-starter:test --rerun-tasks`，模块全量测试通过

---

## 2.0.0

- 包名迁移：`sdk.s3` → `sdk.oss.s3`
- 新增 14 项能力，统一 TaskRetryExecutor 重试
- 详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)