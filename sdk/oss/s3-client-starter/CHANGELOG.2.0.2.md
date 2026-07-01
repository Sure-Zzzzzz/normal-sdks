# CHANGELOG

## 2.0.2

### 参数校验增强

- `S3Client.validateObjectTagging` 新增显式前置校验：
  - `tags == null` 抛 `SetObjectTaggingFailedException(OSS_209)`
  - tag key 为 `null` / empty / blank 抛 `SetObjectTaggingFailedException(OSS_209)`
  - tag value 为 `null` 抛 `SetObjectTaggingFailedException(OSS_209)`
- 空 Map 继续允许，SDK 目标语义为清空对象标签；如目标 S3 兼容服务不接受空 TagSet，建议使用 `deleteObjectTagging`
- 标签 Key / Value 长度限制继续沿用当前 SDK 的 128 UTF-8 字节限制，本版本不调整 2.0.1 已发布行为
- `completeMultipartUpload` 新增 `partETags` 前置校验：
  - `partETags == null` / empty 拒绝
  - 列表元素为 `null` 拒绝
  - `partNumber` 必须在 `1 ~ 10000`
  - ETag 不能为空白
  - `partNumber` 不能重复
- `completeMultipartUpload` 在 starter 层显式复制 `partETags` 并按 `partNumber` 升序提交给 AWS SDK，不修改调用方原始 List
- 本地核验当前 AWS SDK S3 `1.12.573` 在 XML 序列化阶段已复制并排序；本版本显式排序用于形成 starter 层 public API 语义承诺，不写作“修复无序 partETags 导致 InvalidPartOrder”

### 失败清理优化

- 自动分段上传收集当前批次 `Future<PartETag>` 时，发现失败后会取消当前批次未完成 future
- `InterruptedException` 分支恢复中断标记：`Thread.currentThread().interrupt()`
- `ExecutionException` 分支提取 root cause，避免只暴露 Future 包装层异常
- S3 侧 multipart 残留仍由外层 `abortMultipartUpload` 兜底清理

### 文档补充

- 明确 `deleteObject(bucketName, objectKey, bypassGovernanceRetention)` 中 `bypassGovernanceRetention` 为历史兼容参数
- 当前单对象删除实现使用 AWS SDK S3 `DeleteObjectRequest`，不会实际绕过治理保留；如对象受治理保留保护，删除仍可能失败
- 明确 `listParts` / `listMultipartUploads` 返回 SDK 内部聚合后的全量分页结果：
  - `listParts` 聚合完成后 `nextPartNumberMarker=0`
  - `listMultipartUploads` 聚合完成后 `truncated=false`，marker 为 `null`
- README badge、依赖示例和版本历史更新到 `2.0.2`
- `version.properties` 更新为 `2.0.2`

### 测试

- 新增无 Spring 的 `S3ClientValidationTest`，不依赖真实 S3 端点和 `application-local.yml`
- 新增对象标签非法入参前置校验测试：
  - `testSetObjectTaggingNullTags`
  - `testSetObjectTaggingEmptyKey`
  - `testSetObjectTaggingBlankKey`
  - `testSetObjectTaggingNullValue`
- 新增 `completeMultipartUpload` 非法 `partETags` 前置校验测试：
  - `testCompleteMultipartUploadNullPartETags`
  - `testCompleteMultipartUploadEmptyPartETags`
  - `testCompleteMultipartUploadNullPartETag`
  - `testCompleteMultipartUploadInvalidPartNumber`
  - `testCompleteMultipartUploadPartNumberTooLarge`
  - `testCompleteMultipartUploadEmptyETag`
  - `testCompleteMultipartUploadBlankETag`
  - `testCompleteMultipartUploadDuplicatePartNumber`
- 新增 `testCompleteMultipartUploadUnorderedPartETagsSortedCopy`，使用 Mockito 捕获 `CompleteMultipartUploadRequest`，验证提交给 AWS SDK 的列表为升序副本，且调用方原始 List 不被修改
- 端到端 `testManualMultipartUpload` 改为传入无序 `partETags` 完成分段上传，回归真实链路中的 starter 层排序行为
