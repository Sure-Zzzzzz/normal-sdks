package io.github.surezzzzzz.sdk.oss.s3.configuration;

import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3Client 配置属性类
 */
@Data
@ConfigurationProperties(S3ClientConstant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = S3ClientConstant.CONFIG_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class S3ClientProperties {

    /** 是否启用 S3Client */
    private boolean enabled = true;
    /** S3 服务端点 */
    private String endpoint;
    /** Access Key */
    private String accessKey;
    /** Secret Key */
    private String secretKey;
    /** STS 角色 ARN */
    private String roleArn;
    /** URL 前缀 */
    private String urlPrefix;

    /** STS 临时凭证有效期（秒） */
    private int stsDurationSeconds = S3ClientConstant.DEFAULT_STS_DURATION_SECONDS;
    /** 预签名 URL 有效期（秒） */
    private long presignedUrlExpirationSeconds = S3ClientConstant.DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS;

    /** 存储桶过期前缀 */
    private String bucketExpirationPrefix = S3ClientConstant.DEFAULT_BUCKET_EXPIRATION_PREFIX;
    /** 存储桶对象过期天数 */
    private int bucketExpirationDays = S3ClientConstant.DEFAULT_BUCKET_EXPIRATION_DAYS;

    /** 下载保存目录 */
    private String downloadDirectory = S3ClientConstant.DEFAULT_DOWNLOAD_DIRECTORY;
    /** 下载最大重试次数 */
    private int maxDownloadRetryTimes = S3ClientConstant.DEFAULT_MAX_DOWNLOAD_RETRY_TIMES;
    /** 下载最大重试秒数 */
    private int maxDownloadRetrySeconds = S3ClientConstant.DEFAULT_MAX_DOWNLOAD_RETRY_SECONDS;

    /** 上传最大重试次数 */
    private int maxUploadRetryTimes = S3ClientConstant.DEFAULT_MAX_UPLOAD_RETRY_TIMES;
    /** 上传最大重试秒数 */
    private int maxUploadRetrySeconds = S3ClientConstant.DEFAULT_MAX_UPLOAD_RETRY_SECONDS;

    /** 分段上传阈值（MB） */
    private int multipartThresholdMB = S3ClientConstant.DEFAULT_MULTIPART_THRESHOLD_MB;
    /** 分段上传每段大小（MB） */
    private int multipartPartSizeMB = S3ClientConstant.DEFAULT_PART_SIZE_MB;
    /** 分段上传并发数 */
    private int multipartConcurrency = S3ClientConstant.DEFAULT_MULTIPART_CONCURRENCY;

    /** 最大连接数 */
    private int maxConnections = S3ClientConstant.DEFAULT_MAX_CONNECTIONS;
    /** 连接超时（毫秒） */
    private int connectionTimeout = S3ClientConstant.DEFAULT_CONNECTION_TIMEOUT;
    /** 客户端执行超时（毫秒） */
    private int clientExecutionTimeout = S3ClientConstant.DEFAULT_CLIENT_EXECUTION_TIMEOUT;
    /** 连接最大空闲时间（毫秒） */
    private int connectionMaxIdleMillis = S3ClientConstant.DEFAULT_CONNECTION_MAX_IDLE_MILLIS;
    /** 连接 TTL（毫秒） */
    private long connectionTTL = S3ClientConstant.DEFAULT_CONNECTION_TTL;
}