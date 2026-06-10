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
import io.github.surezzzzzz.sdk.oss.s3.exception.client.S3ObjectNotExistException;
import io.github.surezzzzzz.sdk.oss.s3.exception.server.*;
import io.github.surezzzzzz.sdk.oss.s3.support.CapitalizeNamingStrategy;
import io.github.surezzzzzz.sdk.oss.s3.support.ContentTypeHelper;
import io.github.surezzzzzz.sdk.oss.s3.support.DateTimeHelper;
import io.github.surezzzzzz.sdk.oss.s3.support.PolicyDocumentHelper;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

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

    public S3Client() {
        this.capitalizeObjectMapper = new ObjectMapper();
        this.capitalizeObjectMapper.setPropertyNamingStrategy(new CapitalizeNamingStrategy());
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
                    .withRoleSessionName(String.format(PolicyDocumentHelper.SESSION_NAME_TEMPLATE, sessionName))
                    .withPolicy(capitalizeObjectMapper.writeValueAsString(
                            PolicyDocumentHelper.builder().statement(
                                    java.util.Arrays.asList(PolicyDocumentHelper.Statement.builder()
                                            .notResource(java.util.Arrays.asList(
                                                    String.format(PolicyDocumentHelper.RESOURCE_POLICY_ARN_TEMPLATE, path),
                                                    String.format(PolicyDocumentHelper.BUCKET_POLICY_ARN_TEMPLATE, path)))
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
        String canonicalizedResource = String.format("/%s/%s", bucketName, objectKey);
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
        } catch (Exception e) {
            log.error("创建存储桶失败：", e);
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
     */
    public String generateBucketName(String symbol) {
        return UUID.randomUUID().toString().replace(S3ClientConstant.UUID_HYPHEN, "");
    }

    /**
     * 生成 UUID 桶名（带前缀）
     */
    public String generateBucketName(String prefix, String symbol) {
        return prefix + UUID.randomUUID().toString().replace(S3ClientConstant.UUID_HYPHEN, "");
    }

    /**
     * 将 Date 转换为 OffsetDateTime
     */
    public OffsetDateTime toOffsetDateTime(Date date) {
        return DateTimeHelper.toOffsetDateTime(date);
    }

    // ==================== 内部工具 ====================

    private void closeQuietly(InputStream inputStream) {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void closeQuietly(RandomAccessFile raf) {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void closeQuietly(S3Object s3Object) {
        if (s3Object != null) {
            try {
                s3Object.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
