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

    private boolean enabled = true;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String roleArn;
    private String urlPrefix;

    // STS 配置
    private int stsDurationSeconds = S3ClientConstant.DEFAULT_STS_DURATION_SECONDS;
    private long presignedUrlExpirationSeconds = S3ClientConstant.DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS;

    // 存储桶生命周期配置
    private String bucketExpirationPrefix = S3ClientConstant.DEFAULT_BUCKET_EXPIRATION_PREFIX;
    private int bucketExpirationDays = S3ClientConstant.DEFAULT_BUCKET_EXPIRATION_DAYS;

    // 下载配置
    private String downloadDirectory = S3ClientConstant.DEFAULT_DOWNLOAD_DIRECTORY;
    private int maxDownloadRetryTimes = S3ClientConstant.DEFAULT_MAX_DOWNLOAD_RETRY_TIMES;
    private int maxDownloadRetrySeconds = S3ClientConstant.DEFAULT_MAX_DOWNLOAD_RETRY_SECONDS;

    // 上传配置
    private int maxUploadRetryTimes = S3ClientConstant.DEFAULT_MAX_UPLOAD_RETRY_TIMES;
    private int maxUploadRetrySeconds = S3ClientConstant.DEFAULT_MAX_UPLOAD_RETRY_SECONDS;

    // 连接池配置
    private int maxConnections = S3ClientConstant.DEFAULT_MAX_CONNECTIONS;
    private int connectionTimeout = S3ClientConstant.DEFAULT_CONNECTION_TIMEOUT;
    private int clientExecutionTimeout = S3ClientConstant.DEFAULT_CLIENT_EXECUTION_TIMEOUT;
    private int connectionMaxIdleMillis = S3ClientConstant.DEFAULT_CONNECTION_MAX_IDLE_MILLIS;
    private long connectionTTL = S3ClientConstant.DEFAULT_CONNECTION_TTL;
}