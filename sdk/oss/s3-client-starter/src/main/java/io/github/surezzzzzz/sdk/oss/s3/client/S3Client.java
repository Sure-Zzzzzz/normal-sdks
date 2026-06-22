package io.github.surezzzzzz.sdk.oss.s3.client;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter;
import com.amazonaws.services.s3.model.lifecycle.LifecyclePrefixPredicate;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.oss.s3.annotation.S3ClientComponent;
import io.github.surezzzzzz.sdk.oss.s3.configuration.S3ClientProperties;
import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.constant.FileDisposition;
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.FileNotFoundException;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.S3ClientPropertiesInvalidException;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.S3ObjectNotExistException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.*;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUpload;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUploadList;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUploadPart;
import io.github.surezzzzzz.sdk.oss.s3.model.MultipartUploadPartList;
import io.github.surezzzzzz.sdk.oss.s3.strategy.CapitalizeNamingStrategy;
import io.github.surezzzzzz.sdk.oss.s3.support.ContentTypeHelper;
import io.github.surezzzzzz.sdk.oss.s3.support.DateTimeHelper;
import io.github.surezzzzzz.sdk.oss.s3.support.PolicyDocumentHelper;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * S3 客户端
 */
@S3ClientComponent
@Slf4j
public class S3Client {

    @Autowired
    private AWSSecurityTokenService awsSecurityTokenService;

    @Autowired
    private AmazonS3 amazonS3;

    @Autowired
    private S3ClientProperties properties;

    @Autowired
    private TaskRetryExecutor taskRetryExecutor;

    private final ObjectMapper capitalizeObjectMapper;

    private ExecutorService multipartExecutor;

    public S3Client() {
        this.capitalizeObjectMapper = new ObjectMapper();
        this.capitalizeObjectMapper.setPropertyNamingStrategy(new CapitalizeNamingStrategy());
    }

    /**
     * 校验配置并初始化分段上传线程池（依赖注入完成后执行）
     */
    @PostConstruct
    public void init() {
        validateProperties();
        this.multipartExecutor = Executors.newFixedThreadPool(properties.getMultipartConcurrency());
    }

    /**
     * 校验分段上传配置参数
     */
    private void validateProperties() {
        if (properties.getMultipartPartSizeMB() < S3ClientConstant.MIN_PART_SIZE_MB) {
            throw new S3ClientPropertiesInvalidException(String.format(
                    ErrorMessage.PROPERTIES_MULTIPART_PART_SIZE_INVALID,
                    S3ClientConstant.MIN_PART_SIZE_MB, properties.getMultipartPartSizeMB()));
        }
        if (properties.getMultipartConcurrency() < S3ClientConstant.MIN_MULTIPART_CONCURRENCY) {
            throw new S3ClientPropertiesInvalidException(String.format(
                    ErrorMessage.PROPERTIES_MULTIPART_CONCURRENCY_INVALID,
                    properties.getMultipartConcurrency()));
        }
        if (properties.getMultipartThresholdMB() < S3ClientConstant.MIN_MULTIPART_THRESHOLD_MB) {
            throw new S3ClientPropertiesInvalidException(String.format(
                    ErrorMessage.PROPERTIES_MULTIPART_THRESHOLD_INVALID,
                    properties.getMultipartThresholdMB()));
        }
    }

    // ==================== STS 凭证 ====================

    /**
     * 生成普通临时凭证
     */
    public Credentials getNormalStsCredentials() {
        Credentials credentials = awsSecurityTokenService.getSessionToken(
                new GetSessionTokenRequest().withDurationSeconds(properties.getStsDurationSeconds())
        ).getCredentials();
        if (credentials == null) {
            throw new S3ObjectNotExistException(ErrorMessage.OBJECT_NOT_EXIST);
        }
        return credentials;
    }

    /**
     * 生成桶级临时凭证
     */
    public Credentials getBucketStsCredentials(String bucketName) {
        return getPathStsCredentials(bucketName);
    }

    /**
     * 生成目录级临时凭证
     */
    public Credentials getDirStsCredentials(String bucketName, String dir) {
        return getPathStsCredentials(bucketName + S3ClientConstant.PATH_SEPARATOR + dir);
    }

    /**
     * 生成路径级临时凭证
     */
    public Credentials getPathStsCredentials(String path) {
        try {
            String sessionName = path.contains(S3ClientConstant.PATH_SEPARATOR)
                    ? path.split(S3ClientConstant.PATH_SEPARATOR)[0] : path;
            AssumeRoleRequest req = new AssumeRoleRequest()
                    .withDurationSeconds(properties.getStsDurationSeconds())
                    .withRoleArn(properties.getRoleArn())
                    .withRoleSessionName(String.format(S3ClientConstant.STS_SESSION_NAME_TEMPLATE, sessionName))
                    .withPolicy(capitalizeObjectMapper.writeValueAsString(
                            PolicyDocumentHelper.builder().statement(
                                    Arrays.asList(PolicyDocumentHelper.Statement.builder()
                                            .notResource(Arrays.asList(
                                                    String.format(S3ClientConstant.RESOURCE_POLICY_ARN_TEMPLATE, path),
                                                    String.format(S3ClientConstant.BUCKET_POLICY_ARN_TEMPLATE, path)))
                                            .build())
                            ).build()
                    ));
            log.debug("获取路径STS凭证请求已发送");
            Credentials credentials = awsSecurityTokenService.assumeRole(req).getCredentials();
            if (credentials == null) {
                throw new S3ObjectNotExistException(ErrorMessage.OBJECT_NOT_EXIST);
            }
            return credentials;
        } catch (Exception e) {
            throw new S3ObjectNotExistException(e.getMessage(), e);
        }
    }

    // ==================== 下载 ====================

    /**
     * 将对象下载到本地路径
     */
    public S3Object downloadObject(String bucketName, String objectKey, String saveTo) throws IOException {
        try {
            return taskRetryExecutor.executeWithRetry(
                    () -> doDownload(bucketName, objectKey, saveTo, null),
                    properties.getMaxDownloadRetryTimes(),
                    properties.getMaxDownloadRetrySeconds()
            );
        } catch (FileNotFoundException | S3ObjectNotExistException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloadObjectFailedException(e.getMessage(), e);
        }
    }

    /**
     * 如果桶是有前缀匹配删除规则的，需要用这个
     */
    public S3Object downloadObjectWithExpirationPrefix(String bucketName, String objectKey, String saveTo) throws IOException {
        return downloadObject(bucketName, properties.getBucketExpirationPrefix() + objectKey, saveTo);
    }

    /**
     * 带版本号的下载
     */
    public S3Object downloadObject(String bucketName, String objectKey, String saveTo, String versionId) throws IOException {
        try {
            return taskRetryExecutor.executeWithRetry(
                    () -> doDownload(bucketName, objectKey, saveTo, versionId),
                    properties.getMaxDownloadRetryTimes(),
                    properties.getMaxDownloadRetrySeconds()
            );
        } catch (FileNotFoundException | S3ObjectNotExistException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloadObjectFailedException(e.getMessage(), e);
        }
    }

    private S3Object doDownload(String bucketName, String objectKey, String saveTo, String versionId) throws IOException {
        S3Object s3Object = null;
        InputStream inputStream = null;
        RandomAccessFile outputStream = null;
        try {
            String filePath = StringUtils.isNotEmpty(saveTo) ? saveTo :
                    properties.getDownloadDirectory() + File.separator + bucketName + File.separator + objectKey;
            File localFile = new File(filePath);
            if (localFile.exists()) {
                localFile.delete();
            }
            localFile.getParentFile().mkdirs();

            s3Object = (versionId == null)
                    ? getObject(bucketName, objectKey, 0)
                    : getObject(bucketName, objectKey, 0, versionId);

            if (s3Object == null) {
                throw new S3ObjectNotExistException();
            }

            inputStream = s3Object.getObjectContent();
            outputStream = new RandomAccessFile(localFile, S3ClientConstant.FILE_MODE_READ_WRITE);
            byte[] buffer = new byte[S3ClientConstant.BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            log.info("对象下载成功");
            return s3Object;
        } catch (AmazonS3Exception e) {
            if (S3ClientConstant.S3_ERROR_INVALID_RANGE.equals(e.getErrorCode())) {
                log.info("文件已存在且已下载完成，跳过下载");
                return s3Object;
            }
            if (S3ClientConstant.S3_ERROR_NO_SUCH_KEY.equals(e.getErrorCode())) {
                throw new S3ObjectNotExistException();
            }
            throw e;
        } finally {
            closeQuietly(inputStream);
            closeQuietly(outputStream);
        }
    }

    // ==================== 上传 ====================

    /**
     * 上传文件对象
     */
    public void uploadObject(String bucketName, String objectKey, File file) {
        if (!file.exists()) {
            throw new FileNotFoundException(file.getPath());
        }
        try {
            taskRetryExecutor.executeWithFixedDelay(
                    () -> {
                        amazonS3.putObject(new PutObjectRequest(bucketName, objectKey, file));
                        log.info("对象上传成功");
                        return null;
                    },
                    properties.getMaxUploadRetryTimes(),
                    properties.getMaxUploadRetrySeconds()
            );
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UploadObjectFailedException(e.getMessage(), e);
        }
    }

    /**
     * 上传文件对象（带过期前缀）
     */
    public void uploadObjectWithExpirationPrefix(String bucketName, String objectKey, File file) {
        uploadObject(bucketName, properties.getBucketExpirationPrefix() + objectKey, file);
    }

    /**
     * 上传 InputStream 对象
     */
    public void uploadObject(String bucketName, String objectKey, InputStream inputStream, long contentLength) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            amazonS3.putObject(new PutObjectRequest(bucketName, objectKey, inputStream, metadata));
            log.info("对象上传成功");
        } catch (Exception e) {
            log.debug("uploadObject e:", e);
            throw new UploadObjectFailedException(e.getMessage(), e);
        }
    }

    // ==================== 删除 ====================

    /**
     * 删除对象（幂等，NoSuchKey 视为成功）
     */
    public void deleteObject(String bucketName, String objectKey) {
        deleteObject(bucketName, objectKey, false);
    }

    /**
     * 删除对象（可指定是否绕过治理保留模式）
     *
     * @param bypassGovernanceRetention 是否绕过治理保留模式（AWS SDK 1.x 暂不支持此参数，传入 true 不会实际绕过治理保留）
     */
    public void deleteObject(String bucketName, String objectKey, boolean bypassGovernanceRetention) {
        try {
            taskRetryExecutor.executeWithFixedDelay(
                    () -> {
                        try {
                            // AWS SDK 1.x DeleteObjectRequest 不支持 withBypassGovernanceRetention，
                            // bypassGovernanceRetention 参数在此版本中暂不生效
                            amazonS3.deleteObject(new DeleteObjectRequest(bucketName, objectKey));
                            log.info("对象删除成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
                        } catch (AmazonS3Exception e) {
                            if (S3ClientConstant.S3_ERROR_NO_SUCH_KEY.equals(e.getErrorCode())) {
                                log.info("对象不存在，视为删除成功");
                                return null;
                            }
                            throw e;
                        }
                        return null;
                    },
                    properties.getMaxUploadRetryTimes(),
                    properties.getMaxUploadRetrySeconds()
            );
        } catch (Exception e) {
            throw new DeleteObjectFailedException(e.getMessage(), e);
        }
    }

    // ==================== 对象标签 ====================

    /**
     * 设置对象标签（覆盖已有标签）
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @param tags       标签键值对，最多 10 个，Key/Value UTF-8 字节长度不能超过限制
     * @throws SetObjectTaggingFailedException 设置对象标签失败时抛出
     */
    public void setObjectTagging(String bucketName, String objectKey, Map<String, String> tags) {
        try {
            validateObjectTagging(tags);
            ObjectTagging tagging = new ObjectTagging(tags.entrySet().stream()
                    .map(e -> new Tag(e.getKey(), e.getValue()))
                    .collect(Collectors.toList()));
            amazonS3.setObjectTagging(new SetObjectTaggingRequest(bucketName, objectKey, tagging));
            log.info("对象标签设置成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
        } catch (SetObjectTaggingFailedException e) {
            throw e;
        } catch (Exception e) {
            log.debug("setObjectTagging e:", e);
            throw new SetObjectTaggingFailedException(e.getMessage(), e);
        }
    }

    /**
     * 校验 S3 标签参数
     */
    private void validateObjectTagging(Map<String, String> tags) {
        if (tags.size() > S3ClientConstant.MAX_OBJECT_TAGS) {
            throw new SetObjectTaggingFailedException(String.format(
                    ErrorMessage.TAGGING_TOO_MANY, S3ClientConstant.MAX_OBJECT_TAGS, tags.size()));
        }
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (entry.getKey().getBytes(StandardCharsets.UTF_8).length > S3ClientConstant.MAX_TAG_KEY_BYTES) {
                throw new SetObjectTaggingFailedException(String.format(
                        ErrorMessage.TAGGING_KEY_TOO_LONG, S3ClientConstant.MAX_TAG_KEY_BYTES, entry.getKey()));
            }
            if (entry.getValue().getBytes(StandardCharsets.UTF_8).length > S3ClientConstant.MAX_TAG_VALUE_BYTES) {
                throw new SetObjectTaggingFailedException(String.format(
                        ErrorMessage.TAGGING_VALUE_TOO_LONG, S3ClientConstant.MAX_TAG_VALUE_BYTES, entry.getKey()));
            }
        }
    }

    /**
     * 获取对象标签
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @return 对象标签键值对
     * @throws GetObjectTaggingFailedException 获取对象标签失败时抛出
     */
    public Map<String, String> getObjectTagging(String bucketName, String objectKey) {
        try {
            List<Tag> tagList = amazonS3.getObjectTagging(
                    new GetObjectTaggingRequest(bucketName, objectKey)).getTagSet();
            Map<String, String> result = new HashMap<>();
            for (Tag tag : tagList) {
                result.put(tag.getKey(), tag.getValue());
            }
            return result;
        } catch (Exception e) {
            log.debug("getObjectTagging e:", e);
            throw new GetObjectTaggingFailedException(e.getMessage(), e);
        }
    }

    /**
     * 删除对象全部标签
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @throws DeleteObjectTaggingFailedException 删除对象标签失败时抛出
     */
    public void deleteObjectTagging(String bucketName, String objectKey) {
        try {
            amazonS3.deleteObjectTagging(new DeleteObjectTaggingRequest(bucketName, objectKey));
            log.info("对象标签删除成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
        } catch (Exception e) {
            log.debug("deleteObjectTagging e:", e);
            throw new DeleteObjectTaggingFailedException(e.getMessage(), e);
        }
    }

    // ==================== 分段上传 ====================

    /**
     * 大文件分段上传（自动切分 parts，文件超过阈值时触发）
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @param file       待上传文件
     * @throws FileNotFoundException                  文件不存在时抛出
     * @throws UploadPartFailedException              上传分段失败时抛出
     * @throws CompleteMultipartUploadFailedException 完成分段上传失败时抛出
     */
    public void uploadObjectMultipart(String bucketName, String objectKey, File file) {
        uploadObjectMultipart(bucketName, objectKey, file, properties.getMultipartPartSizeMB());
    }

    /**
     * 大文件分段上传（指定每段大小）
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @param file       待上传文件
     * @param partSizeMB 分段大小，单位 MB，不能小于 5
     * @throws FileNotFoundException                  文件不存在时抛出
     * @throws UploadPartFailedException              上传分段失败或分段参数非法时抛出
     * @throws CompleteMultipartUploadFailedException 完成分段上传失败时抛出
     */
    public void uploadObjectMultipart(String bucketName, String objectKey, File file, int partSizeMB) {
        if (!file.exists()) {
            throw new FileNotFoundException(file.getPath());
        }
        if (partSizeMB < S3ClientConstant.MIN_PART_SIZE_MB) {
            throw new UploadPartFailedException(String.format(
                    ErrorMessage.PART_SIZE_TOO_SMALL, S3ClientConstant.MIN_PART_SIZE_MB, partSizeMB));
        }
        long fileSize = file.length();
        long thresholdBytes = (long) properties.getMultipartThresholdMB() * S3ClientConstant.MB_IN_BYTES;
        if (fileSize <= thresholdBytes && fileSize <= S3ClientConstant.MAX_SINGLE_UPLOAD_BYTES) {
            uploadObject(bucketName, objectKey, file);
            return;
        }

        long partSizeBytes = (long) partSizeMB * S3ClientConstant.MB_IN_BYTES;
        long partCount = (fileSize + partSizeBytes - 1) / partSizeBytes;
        if (partCount > S3ClientConstant.MAX_MULTIPART_PARTS) {
            throw new UploadPartFailedException(String.format(
                    ErrorMessage.MULTIPART_PART_COUNT_EXCEEDED, S3ClientConstant.MAX_MULTIPART_PARTS, partCount));
        }
        final String uploadId = generateUploadId(bucketName, objectKey);
        try {
            List<PartETag> allPartETags = Collections.synchronizedList(new ArrayList<>());
            int maxConcurrent = properties.getMultipartConcurrency();

            for (int i = 0; i < partCount; i += maxConcurrent) {
                List<Future<PartETag>> futures = new ArrayList<>();
                for (int j = 0; j < maxConcurrent && (i + j) < partCount; j++) {
                    int partNumber = i + j + 1;
                    long offset = (long) (i + j) * partSizeBytes;
                    long length = Math.min(partSizeBytes, fileSize - offset);
                    futures.add(multipartExecutor.submit(() ->
                            uploadPartWithRetry(bucketName, objectKey, uploadId, partNumber, file, offset, length)));
                }
                for (Future<PartETag> future : futures) {
                    try {
                        allPartETags.add(future.get());
                    } catch (Exception e) {
                        throw new UploadPartFailedException(e.getMessage(), e);
                    }
                }
            }

            allPartETags.sort((a, b) -> Integer.compare(a.getPartNumber(), b.getPartNumber()));
            completeMultipartUpload(bucketName, objectKey, uploadId, allPartETags);
            log.info("分段上传成功，bucketName：{}，objectKey：{}，partCount：{}", bucketName, objectKey, partCount);
        } catch (UploadPartFailedException | CompleteMultipartUploadFailedException e) {
            try {
                abortMultipartUpload(bucketName, objectKey, uploadId);
                log.info("分段上传失败，已清理 uploadId：{}", uploadId);
            } catch (Exception cleanupEx) {
                log.warn("清理 uploadId 失败：{}", cleanupEx.getMessage());
            }
            throw e;
        } catch (Exception e) {
            try {
                abortMultipartUpload(bucketName, objectKey, uploadId);
            } catch (Exception cleanupEx) {
                log.warn("清理 uploadId 失败：{}", cleanupEx.getMessage());
            }
            throw new UploadPartFailedException(e.getMessage(), e);
        }
    }

    /**
     * 初始化分段上传，返回 uploadId
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @return S3 返回的 uploadId
     * @throws UploadPartFailedException 初始化分段上传失败时抛出
     */
    public String generateUploadId(String bucketName, String objectKey) {
        try {
            return amazonS3.initiateMultipartUpload(
                    new InitiateMultipartUploadRequest(bucketName, objectKey)).getUploadId();
        } catch (Exception e) {
            log.debug("generateUploadId e:", e);
            throw new UploadPartFailedException(e.getMessage(), e);
        }
    }

    /**
     * 上传单个分段
     *
     * @param bucketName    存储桶名称
     * @param objectKey     对象 Key
     * @param uploadId      分段上传 ID
     * @param partNumber    分段编号，从 1 开始
     * @param inputStream   分段内容输入流，调用方负责提供可读流
     * @param contentLength 分段内容长度
     * @return 已上传分段的 ETag 信息
     * @throws UploadPartFailedException 上传分段失败时抛出
     */
    public PartETag uploadPart(String bucketName, String objectKey, String uploadId,
                               int partNumber, InputStream inputStream, long contentLength) {
        try {
            UploadPartRequest request = new UploadPartRequest()
                    .withBucketName(bucketName)
                    .withKey(objectKey)
                    .withUploadId(uploadId)
                    .withPartNumber(partNumber)
                    .withInputStream(inputStream)
                    .withPartSize(contentLength);
            return amazonS3.uploadPart(request).getPartETag();
        } catch (Exception e) {
            log.debug("uploadPart e:", e);
            throw new UploadPartFailedException(e.getMessage(), e);
        }
    }

    /**
     * 从文件指定偏移读取一段分段内容并上传
     */
    private PartETag uploadPartWithRetry(String bucketName, String objectKey, String uploadId,
                                         int partNumber, File file, long offset, long length) {
        try {
            return taskRetryExecutor.executeWithRetry(
                    () -> uploadPartFromFile(bucketName, objectKey, uploadId, partNumber, file, offset, length),
                    properties.getMaxUploadRetryTimes(),
                    properties.getMaxUploadRetrySeconds()
            );
        } catch (Exception e) {
            log.debug("uploadPartWithRetry e:", e);
            throw new UploadPartFailedException(e.getMessage(), e);
        }
    }

    /**
     * 从文件指定偏移上传分段，保证每次重试都由 AWS SDK 重新打开文件片段
     */
    private PartETag uploadPartFromFile(String bucketName, String objectKey, String uploadId,
                                        int partNumber, File file, long offset, long length) {
        UploadPartRequest request = new UploadPartRequest()
                .withBucketName(bucketName)
                .withKey(objectKey)
                .withUploadId(uploadId)
                .withPartNumber(partNumber)
                .withFile(file)
                .withFileOffset(offset)
                .withPartSize(length);
        return amazonS3.uploadPart(request).getPartETag();
    }

    /**
     * 完成分段上传
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @param uploadId   分段上传 ID
     * @param partETags  已上传分段 ETag 列表，应按 partNumber 升序传入
     * @throws CompleteMultipartUploadFailedException 完成分段上传失败时抛出
     */
    public void completeMultipartUpload(String bucketName, String objectKey, String uploadId, List<PartETag> partETags) {
        try {
            amazonS3.completeMultipartUpload(new CompleteMultipartUploadRequest(
                    bucketName, objectKey, uploadId, partETags));
            log.info("完成分段上传成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
        } catch (Exception e) {
            log.debug("completeMultipartUpload e:", e);
            throw new CompleteMultipartUploadFailedException(e.getMessage(), e);
        }
    }

    /**
     * 中止分段上传，清理已上传 parts（幂等：已完成或已中止的分段上传再次调用不抛异常）
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @param uploadId   分段上传 ID
     * @throws UploadPartFailedException 中止分段上传失败时抛出
     */
    public void abortMultipartUpload(String bucketName, String objectKey, String uploadId) {
        try {
            amazonS3.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, objectKey, uploadId));
            log.info("中止分段上传成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
        } catch (AmazonS3Exception e) {
            if (S3ClientConstant.S3_ERROR_NO_SUCH_UPLOAD.equals(e.getErrorCode())) {
                log.info("分段上传不存在或已结束，视为中止成功，bucketName：{}，objectKey：{}", bucketName, objectKey);
                return;
            }
            throw new UploadPartFailedException(e.getMessage(), e);
        } catch (Exception e) {
            throw new UploadPartFailedException(e.getMessage(), e);
        }
    }

    /**
     * 列举已上传的分段
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象 Key
     * @param uploadId   分段上传 ID
     * @return 已上传分段集合，方法内部会聚合全部分页，完成时 nextPartNumberMarker 为 0
     * @throws ListObjectsFailedException 列举分段失败时抛出
     */
    public MultipartUploadPartList listParts(String bucketName, String objectKey, String uploadId) {
        try {
            List<MultipartUploadPart> allParts = new ArrayList<>();
            int nextMarker = 0;
            while (true) {
                ListPartsRequest request = new ListPartsRequest(bucketName, objectKey, uploadId)
                        .withPartNumberMarker(nextMarker)
                        .withMaxParts(S3ClientConstant.LIST_PARTS_PAGE_SIZE);
                PartListing result = amazonS3.listParts(request);
                for (PartSummary part : result.getParts()) {
                    allParts.add(new MultipartUploadPart(
                            part.getPartNumber(),
                            part.getETag(),
                            part.getSize(),
                            part.getLastModified()));
                }
                if (!result.isTruncated()) {
                    nextMarker = 0;
                    break;
                }
                nextMarker = result.getNextPartNumberMarker();
            }
            return new MultipartUploadPartList(allParts, nextMarker);
        } catch (Exception e) {
            log.debug("listParts e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }

    /**
     * 列举进行中的分段上传
     *
     * @param bucketName 存储桶名称
     * @return 进行中的分段上传集合，方法内部会聚合全部分页
     * @throws ListObjectsFailedException 列举进行中的分段上传失败时抛出
     */
    public MultipartUploadList listMultipartUploads(String bucketName) {
        try {
            List<MultipartUpload> allUploads = new ArrayList<>();
            String nextKeyMarker = null;
            String nextUploadIdMarker = null;
            while (true) {
                ListMultipartUploadsRequest request = new ListMultipartUploadsRequest(bucketName)
                        .withKeyMarker(nextKeyMarker)
                        .withUploadIdMarker(nextUploadIdMarker)
                        .withMaxUploads(S3ClientConstant.LIST_UPLOADS_PAGE_SIZE);
                MultipartUploadListing result = amazonS3.listMultipartUploads(request);
                for (com.amazonaws.services.s3.model.MultipartUpload upload : result.getMultipartUploads()) {
                    allUploads.add(new MultipartUpload(
                            upload.getUploadId(),
                            upload.getKey(),
                            upload.getInitiated()));
                }
                if (!result.isTruncated()) {
                    break;
                }
                nextKeyMarker = result.getNextKeyMarker();
                nextUploadIdMarker = result.getNextUploadIdMarker();
            }
            return new MultipartUploadList(allUploads, false, null, null);
        } catch (Exception e) {
            log.debug("listMultipartUploads e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }


    // ==================== 查询 ====================

    /**
     * 判断对象是否存在
     */
    public boolean doesObjectExist(String bucketName, String objectKey) {
        return amazonS3.doesObjectExist(bucketName, objectKey);
    }

    /**
     * 获取对象元信息
     */
    public ObjectMetadata getObjectMetadata(String bucketName, String objectKey) {
        try {
            return amazonS3.getObjectMetadata(bucketName, objectKey);
        } catch (Exception e) {
            log.debug("getObjectMetadata e:", e);
            throw new GetObjectMetadataFailedException(e.getMessage(), e);
        }
    }

    /**
     * 获取对象元信息（指定版本）
     */
    public ObjectMetadata getObjectMetadata(String bucketName, String objectKey, String versionId) {
        try {
            return amazonS3.getObjectMetadata(new GetObjectMetadataRequest(bucketName, objectKey, versionId));
        } catch (Exception e) {
            log.debug("getObjectMetadata e:", e);
            throw new GetObjectMetadataFailedException(e.getMessage(), e);
        }
    }

    /**
     * 获取对象（设置下载范围）
     */
    public S3Object getObject(String bucketName, String objectKey, long localFileSize) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey);
        getObjectRequest.setRange(localFileSize);
        return amazonS3.getObject(getObjectRequest);
    }

    /**
     * 获取完整对象
     */
    public S3Object getFullObject(String bucketName, String objectKey) {
        return getObject(bucketName, objectKey, 0);
    }

    /**
     * 获取对象（设置下载范围和版本号）
     */
    public S3Object getObject(String bucketName, String objectKey, long localFileSize, String versionId) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectKey, versionId);
        getObjectRequest.setRange(localFileSize);
        return amazonS3.getObject(getObjectRequest);
    }

    /**
     * 列举对象列表
     */
    public ObjectListing listObjects(String bucketName) {
        try {
            return amazonS3.listObjects(bucketName);
        } catch (Exception e) {
            log.debug("listObjects e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }

    /**
     * 列举对象列表（带前缀过滤）
     */
    public ObjectListing listObjects(String bucketName, String prefix) {
        try {
            return amazonS3.listObjects(bucketName, prefix);
        } catch (Exception e) {
            log.debug("listObjects e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }

    /**
     * 列举对象列表（带前缀过滤和最大条数限制）
     */
    public ObjectListing listObjects(String bucketName, String prefix, int maxKeys) {
        try {
            return amazonS3.listObjects(new ListObjectsRequest()
                    .withBucketName(bucketName)
                    .withPrefix(prefix)
                    .withMaxKeys(maxKeys));
        } catch (Exception e) {
            log.debug("listObjects e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }

    // ==================== 复制 ====================

    /**
     * 复制对象
     *
     * @return 新对象的 ETag
     */
    public String copyObject(String sourceBucketName, String sourceKey, String destBucketName, String destKey) {
        try {
            CopyObjectResult result = amazonS3.copyObject(sourceBucketName, sourceKey, destBucketName, destKey);
            log.info("对象复制成功，source：{}/{}，dest：{}/{}，ETag：{}",
                    sourceBucketName, sourceKey, destBucketName, destKey, result.getETag());
            return result.getETag();
        } catch (Exception e) {
            log.debug("copyObject e:", e);
            throw new CopyObjectFailedException(e.getMessage(), e);
        }
    }

    // ==================== 预签名 URL ====================

    /**
     * 生成下载预签名 URL（默认下载模式）
     */
    public String generatePresignedUrl(String bucketName, String objectKey, Long expirationSeconds) throws Exception {
        return generatePresignedUrl(bucketName, objectKey, expirationSeconds, FileDisposition.DOWNLOAD);
    }

    /**
     * 生成下载预签名 URL
     */
    public String generatePresignedUrl(String bucketName, String objectKey, Long expirationSeconds, FileDisposition disposition) throws Exception {
        long exp = (expirationSeconds == null) ? properties.getPresignedUrlExpirationSeconds() : expirationSeconds;
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.GET)
                .withResponseHeaders(
                        new ResponseHeaderOverrides()
                                .withContentDisposition(disposition.getContentDisposition(objectKey))
                                .withContentType(ContentTypeHelper.getContentType(objectKey))
                )
                .withExpiration(new Date(System.currentTimeMillis() + exp * S3ClientConstant.MILLIS_PER_SECOND));
        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
        log.debug("生成预签名URL成功");
        return properties.getUrlPrefix() + url.getFile();
    }

    /**
     * 生成上传预签名 URL
     */
    public String generateUploadPresignedUrl(String bucketName, String objectKey, Long expirationSeconds) throws Exception {
        return generateUploadPresignedUrl(bucketName, objectKey, expirationSeconds, null);
    }

    /**
     * 生成上传预签名 URL（带 Content-Type）
     */
    public String generateUploadPresignedUrl(String bucketName, String objectKey, Long expirationSeconds, String contentType) throws Exception {
        long exp = (expirationSeconds == null) ? properties.getPresignedUrlExpirationSeconds() : expirationSeconds;
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.PUT)
                .withExpiration(new Date(System.currentTimeMillis() + exp * S3ClientConstant.MILLIS_PER_SECOND));
        if (contentType != null) {
            generatePresignedUrlRequest.setContentType(contentType);
        }
        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
        log.debug("生成上传预签名URL成功");
        return properties.getUrlPrefix() + url.getFile();
    }

    /**
     * 自定义 HmacSHA1 签名预签名 URL
     */
    public String customPresignedUrl(String bucketName, String objectKey, Long expirationSeconds) throws Exception {
        long exp = (expirationSeconds == null) ? properties.getPresignedUrlExpirationSeconds() : expirationSeconds;
        String canonicalizedResource = String.format(S3ClientConstant.CANONICALIZED_RESOURCE_TEMPLATE, bucketName, objectKey);
        Date expirationDate = new Date(System.currentTimeMillis() + exp * S3ClientConstant.MILLIS_PER_SECOND);
        String signStr = String.format(S3ClientConstant.CUSTOM_SIGN_STRING_TEMPLATE, expirationDate.getTime(), canonicalizedResource);
        SecretKeySpec signinKey = new SecretKeySpec(properties.getSecretKey().getBytes(), S3ClientConstant.HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(S3ClientConstant.HMAC_SHA1_ALGORITHM);
        mac.init(signinKey);
        byte[] rawHmac = mac.doFinal(signStr.getBytes());
        String url = String.format(S3ClientConstant.CUSTOM_PRESIGNED_URL_TEMPLATE,
                properties.getUrlPrefix(), canonicalizedResource,
                properties.getAccessKey(), expirationDate.getTime(),
                new String(Base64.getEncoder().encode(rawHmac)));
        log.debug("生成自定义预签名URL成功");
        return url;
    }

    // ==================== 存储桶管理 ====================

    /**
     * 创建 S3 存储桶
     */
    public Bucket createS3Bucket(String bucketName) {
        if (amazonS3.doesBucketExistV2(bucketName)) {
            log.info("存储桶已存在");
            return amazonS3.listBuckets().stream()
                    .filter(bucket -> bucket.getName().equals(bucketName))
                    .findFirst()
                    .orElseThrow(() -> new CreateBucketFailedException(ErrorMessage.BUCKET_EXISTS_BUT_NOT_FOUND));
        }
        log.info("开始创建存储桶");
        return amazonS3.createBucket(new CreateBucketRequest(bucketName));
    }

    /**
     * 创建带多版本和对象过期策略的存储桶
     */
    public Bucket createVersioningAndDefaultLifecycleBucket(String bucketName) {
        try {
            Bucket bucket = createS3Bucket(bucketName);
            enableBucketVersioning(bucketName);
            setDefaultBucketLifecycle(bucketName);
            return bucket;
        } catch (CreateBucketFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建存储桶失败：{}", e.getMessage());
            throw new CreateBucketFailedException(e.getMessage(), e);
        }
    }

    /**
     * 创建半年过期桶
     */
    public void createHalfYearBucket(String bucketName) {
        createVersioningAndDefaultLifecycleBucket(bucketName);
    }

    /**
     * 启用多版本
     */
    public void enableBucketVersioning(String bucketName) {
        BucketVersioningConfiguration versioningConfiguration =
                new BucketVersioningConfiguration().withStatus(BucketVersioningConfiguration.ENABLED);
        SetBucketVersioningConfigurationRequest versioningRequest =
                new SetBucketVersioningConfigurationRequest(bucketName, versioningConfiguration);
        amazonS3.setBucketVersioningConfiguration(versioningRequest);
    }

    /**
     * 设置桶的对象过期策略
     */
    public void setDefaultBucketLifecycle(String bucketName) {
        BucketLifecycleConfiguration lifecycleConfiguration = new BucketLifecycleConfiguration().withRules(
                new BucketLifecycleConfiguration.Rule()
                        .withId(properties.getBucketExpirationPrefix() + S3ClientConstant.LIFECYCLE_RULE_SUFFIX)
                        .withFilter(new LifecycleFilter(new LifecyclePrefixPredicate(properties.getBucketExpirationPrefix())))
                        .withExpirationInDays(properties.getBucketExpirationDays())
                        .withStatus(BucketLifecycleConfiguration.ENABLED)
        );
        SetBucketLifecycleConfigurationRequest setBucketLifecycleConfiguration =
                new SetBucketLifecycleConfigurationRequest(bucketName, lifecycleConfiguration);
        amazonS3.setBucketLifecycleConfiguration(setBucketLifecycleConfiguration);
    }

    // ==================== 文件夹管理 ====================

    /**
     * 创建文件夹（本质是创建以 / 结尾的空对象）
     */
    public void createFolder(String bucketName, String folderName) {
        try {
            taskRetryExecutor.executeWithFixedDelay(
                    () -> {
                        String key = folderName.endsWith(S3ClientConstant.PATH_SEPARATOR)
                                ? folderName : folderName + S3ClientConstant.PATH_SEPARATOR;
                        if (amazonS3.doesObjectExist(bucketName, key)) {
                            log.info("文件夹已存在");
                            return null;
                        }
                        ObjectMetadata metadata = new ObjectMetadata();
                        metadata.setContentLength(0);
                        try (InputStream emptyContent = new ByteArrayInputStream(new byte[0])) {
                            amazonS3.putObject(new PutObjectRequest(bucketName, key, emptyContent, metadata));
                            log.info("文件夹创建成功");
                        }
                        return null;
                    },
                    properties.getMaxUploadRetryTimes(),
                    properties.getMaxUploadRetrySeconds()
            );
        } catch (Exception e) {
            throw new CreateFolderFailedException(e.getMessage(), e);
        }
    }

    // ==================== 版本管理 ====================

    /**
     * 列举版本历史
     */
    public VersionListing listVersions(String bucketName) {
        try {
            return amazonS3.listVersions(new ListVersionsRequest().withBucketName(bucketName));
        } catch (Exception e) {
            log.debug("listVersions e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }

    /**
     * 列举版本历史（带前缀过滤）
     */
    public VersionListing listVersions(String bucketName, String prefix) {
        try {
            return amazonS3.listVersions(new ListVersionsRequest()
                    .withBucketName(bucketName)
                    .withPrefix(prefix));
        } catch (Exception e) {
            log.debug("listVersions e:", e);
            throw new ListObjectsFailedException(e.getMessage(), e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 生成 UUID 桶名
     *
     * @param symbol 业务标识，当前实现保留参数但不参与桶名生成（历史 API 兼容）
     */
    @SuppressWarnings("unused")
    public String generateBucketName(String symbol) {
        return UUID.randomUUID().toString().replace(S3ClientConstant.UUID_HYPHEN, StringUtils.EMPTY);
    }

    /**
     * 生成 UUID 桶名（带前缀）
     *
     * @param symbol 业务标识，当前实现保留参数但不参与桶名生成（历史 API 兼容）
     */
    @SuppressWarnings("unused")
    public String generateBucketName(String prefix, String symbol) {
        return prefix + UUID.randomUUID().toString().replace(S3ClientConstant.UUID_HYPHEN, StringUtils.EMPTY);
    }

    /**
     * 将 Date 转换为 OffsetDateTime
     */
    public OffsetDateTime toOffsetDateTime(Date date) {
        return DateTimeHelper.toOffsetDateTime(date);
    }

    // ==================== 内部工具 ====================

    /**
     * 静默关闭 InputStream，忽略关闭异常
     */
    private void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * 静默关闭 RandomAccessFile，忽略关闭异常
     */
    private void closeQuietly(RandomAccessFile raf) {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * 静默关闭 S3Object，忽略关闭异常
     */
    private void closeQuietly(S3Object s3Object) {
        if (s3Object != null) {
            try {
                s3Object.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * 关闭分段上传线程池
     */
    @PreDestroy
    public void shutdownMultipartExecutor() {
        if (multipartExecutor != null && !multipartExecutor.isShutdown()) {
            multipartExecutor.shutdown();
        }
    }
}
