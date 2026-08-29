package io.github.surezzzzzz.sdk.s3.client.configuration;

import io.github.surezzzzzz.sdk.s3.client.constant.SimpleS3ClientConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 Client 配置。target 连接与凭据复用 simple-s3-route-starter 配置。
 * 默认值与老 s3-client-starter 的 S3ClientConstant 对齐迁移。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleS3ClientConstant.CONFIG_PREFIX)
public class SimpleS3ClientProperties {

    /**
     * 是否启用 Client。启用前必须先启用 simple-s3-route-starter。
     */
    private boolean enable;

    /**
     * STS 临时凭证配置。
     */
    private Sts sts = new Sts();

    /**
     * 预签名 URL 配置。
     */
    private PresignedUrl presignedUrl = new PresignedUrl();

    /**
     * 上传/下载重试配置。
     */
    private Retry retry = new Retry();

    /**
     * 自动分片配置。
     */
    private Multipart multipart = new Multipart();

    /**
     * 存储桶生命周期配置。
     */
    private BucketLifecycle bucketLifecycle = new BucketLifecycle();

    /**
     * 断点续传下载默认目录。
     */
    private String downloadDirectory = SimpleS3ClientConstant.DEFAULT_DOWNLOAD_DIRECTORY;

    /**
     * 事件回调接收器配置。
     */
    private EventCallback eventCallback = new EventCallback();

    /**
     * STS 临时凭证配置。
     */
    @Data
    public static class Sts {

        /**
         * assumeRole 角色 ARN（路径级降权凭证必填，普通凭证不需要）。
         */
        private String roleArn;

        /**
         * 凭证有效时长（秒）。
         */
        private int durationSeconds = SimpleS3ClientConstant.DEFAULT_STS_DURATION_SECONDS;
    }

    /**
     * 预签名 URL 配置。
     */
    @Data
    public static class PresignedUrl {

        /**
         * 默认有效时长（秒）；方法入参显式传入时覆盖。
         */
        private long expirationSeconds = SimpleS3ClientConstant.DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS;

        /**
         * 网关域名前缀（可选）。配置后返回「前缀 + 签名路径」，未配置返回完整 URL。
         * 仅限签名一致性经生产验证的网关组合使用（V4 签名的 host 是签名要素）。
         */
        private String urlPrefix = "";
    }

    /**
     * 上传/下载重试配置。
     */
    @Data
    public static class Retry {

        /**
         * 上传重试次数。
         */
        private int uploadTimes = SimpleS3ClientConstant.DEFAULT_UPLOAD_RETRY_TIMES;

        /**
         * 上传重试间隔（毫秒）。
         */
        private long uploadIntervalMs = SimpleS3ClientConstant.DEFAULT_RETRY_INTERVAL_MS;

        /**
         * 下载重试次数。
         */
        private int downloadTimes = SimpleS3ClientConstant.DEFAULT_DOWNLOAD_RETRY_TIMES;

        /**
         * 下载重试间隔（毫秒）。
         */
        private long downloadIntervalMs = SimpleS3ClientConstant.DEFAULT_RETRY_INTERVAL_MS;
    }

    /**
     * 自动分片配置。
     */
    @Data
    public static class Multipart {

        /**
         * 自动分片触发阈值（MB）；文件超过该阈值走分段上传。
         */
        private int thresholdMb = SimpleS3ClientConstant.DEFAULT_MULTIPART_THRESHOLD_MB;

        /**
         * 分段大小（MB，S3 协议最小 5）。
         */
        private int partSizeMb = SimpleS3ClientConstant.DEFAULT_PART_SIZE_MB;

        /**
         * 分段上传并发度。
         */
        private int concurrency = SimpleS3ClientConstant.DEFAULT_MULTIPART_CONCURRENCY;
    }

    /**
     * 存储桶生命周期配置。
     */
    @Data
    public static class BucketLifecycle {

        /**
         * 生命周期规则过期前缀。
         */
        private String expirationPrefix = SimpleS3ClientConstant.DEFAULT_BUCKET_EXPIRATION_PREFIX;

        /**
         * 生命周期规则过期天数。
         */
        private int expirationDays = SimpleS3ClientConstant.DEFAULT_BUCKET_EXPIRATION_DAYS;
    }

    /**
     * 事件回调接收器配置。
     */
    @Data
    public static class EventCallback {

        /**
         * 是否启用事件回调接收端点（默认关闭）。
         */
        private boolean enable;

        /**
         * 接收端点路径。
         */
        private String path = SimpleS3ClientConstant.DEFAULT_CALLBACK_PATH;

        /**
         * 认证 token。空表示不校验（仅限网络层已隔离的部署）；
         * 配置后兼容 Bearer 头与 URL query 双通道（不支持自定义请求头的存储用 query 通道）。
         */
        private String token;
    }
}
