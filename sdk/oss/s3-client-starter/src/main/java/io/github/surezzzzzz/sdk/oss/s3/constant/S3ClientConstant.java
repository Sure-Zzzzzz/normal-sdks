package io.github.surezzzzzz.sdk.oss.s3.constant;

/**
 * S3Client 常量类
 */
public final class S3ClientConstant {

    private S3ClientConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置前缀 ====================
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.oss.s3";

    // ==================== STS 配置 ====================
    public static final int DEFAULT_STS_DURATION_SECONDS = 86400;
    public static final long DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS = 86400L;

    // ==================== 存储桶生命周期配置 ====================
    public static final String DEFAULT_BUCKET_EXPIRATION_PREFIX = "expiration-";
    public static final int DEFAULT_BUCKET_EXPIRATION_DAYS = 180;

    // ==================== 下载配置 ====================
    public static final String DEFAULT_DOWNLOAD_DIRECTORY = "./";
    public static final int DEFAULT_MAX_DOWNLOAD_RETRY_TIMES = 5;
    public static final int DEFAULT_MAX_DOWNLOAD_RETRY_SECONDS = 600;

    // ==================== 上传配置 ====================
    public static final int DEFAULT_MAX_UPLOAD_RETRY_TIMES = 5;
    public static final int DEFAULT_MAX_UPLOAD_RETRY_SECONDS = 600;
    public static final int RETRY_INTERVAL_MILLIS = 30 * 1000;
    public static final int BUFFER_SIZE = 8192;

    // ==================== 连接池配置 ====================
    public static final int DEFAULT_MAX_CONNECTIONS = 500;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 10 * 1000;
    public static final int DEFAULT_CLIENT_EXECUTION_TIMEOUT = 0;
    public static final int DEFAULT_CONNECTION_MAX_IDLE_MILLIS = 60 * 1000;
    public static final long DEFAULT_CONNECTION_TTL = -1L;

    // ==================== 签名类型 ====================
    /**
     * STS 客户端签名类型
     */
    public static final String SIGNER_TYPE_STS = "AWSS3V4SignerType";

    /**
     * S3 客户端签名类型
     */
    public static final String SIGNER_TYPE_S3 = "S3SignerType";

    // ==================== S3 协议错误码 ====================
    /**
     * 对象不存在
     */
    public static final String S3_ERROR_NO_SUCH_KEY = "NoSuchKey";

    /**
     * 下载范围超出文件大小（文件已全部下载）
     */
    public static final String S3_ERROR_INVALID_RANGE = "InvalidRange";

    // ==================== 自定义签名 ====================
    /**
     * HmacSHA1 算法名称
     */
    public static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * 自定义签名字符串前缀模板
     * 参数：过期时间戳，规范化资源路径
     */
    public static final String CUSTOM_SIGN_STRING_TEMPLATE = "GET\n\n\n%s\n%s";

    /**
     * 自定义预签名 URL 模板
     * 参数：urlPrefix，规范化资源路径，accessKey，过期时间戳，Signature
     */
    public static final String CUSTOM_PRESIGNED_URL_TEMPLATE = "%s%s?AWSAccessKeyId=%s&Expires=%s&Signature=%s";

    /**
     * 毫秒/秒换算系数
     */
    public static final long MILLIS_PER_SECOND = 1000L;

    // ==================== 存储桶生命周期 ====================
    /**
     * 生命周期规则 ID 后缀
     */
    public static final String LIFECYCLE_RULE_SUFFIX = "rule";

    // ==================== 路径 ====================
    /**
     * UUID 连字符（用于去除 UUID 中的 -）
     */
    public static final String UUID_HYPHEN = "-";

    /**
     * 路径分隔符
     */
    public static final String PATH_SEPARATOR = "/";

    /**
     * 默认 Content-Type（未知或无扩展名）
     */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /**
     * 文件读写模式
     */
    public static final String FILE_MODE_READ_WRITE = "rw";
}