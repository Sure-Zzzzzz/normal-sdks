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

    // ==================== 分段上传配置 ====================
    public static final int DEFAULT_MULTIPART_THRESHOLD_MB = 100;
    public static final int DEFAULT_PART_SIZE_MB = 5;
    public static final int MIN_PART_SIZE_MB = 5;
    public static final int DEFAULT_MULTIPART_CONCURRENCY = 3;
    public static final int MIN_MULTIPART_CONCURRENCY = 1;
    public static final int MIN_MULTIPART_THRESHOLD_MB = 1;
    public static final int MB_IN_BYTES = 1024 * 1024;

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

    /**
     * 分段上传不存在或已完成/已中止
     */
    public static final String S3_ERROR_NO_SUCH_UPLOAD = "NoSuchUpload";

    /**
     * S3 单对象最多分段数
     */
    public static final int MAX_MULTIPART_PARTS = 10000;

    /**
     * S3 单次 PutObject 最大对象大小（5GB）
     */
    public static final long MAX_SINGLE_UPLOAD_BYTES = 5L * 1024 * 1024 * 1024;

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
     * 规范化资源路径模板（用于自定义签名）
     * 参数：bucketName，objectKey
     */
    public static final String CANONICALIZED_RESOURCE_TEMPLATE = "/%s/%s";

    /**
     * 默认 Content-Type（未知或无扩展名）
     */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /**
     * 文件读写模式
     */
    public static final String FILE_MODE_READ_WRITE = "rw";

    /**
     * 文件只读模式
     */
    public static final String FILE_MODE_READ = "r";

    // ==================== 对象标签限制 ====================
    /**
     * 单个对象最大标签个数
     */
    public static final int MAX_OBJECT_TAGS = 10;

    /**
     * 单个标签 Key 最大字节数
     */
    public static final int MAX_TAG_KEY_BYTES = 128;

    /**
     * 单个标签 Value 最大字节数
     */
    public static final int MAX_TAG_VALUE_BYTES = 128;

    // ==================== 分段上传列举分页 ====================
    /**
     * listParts 单次最大返回条数
     */
    public static final int LIST_PARTS_PAGE_SIZE = 1000;

    /**
     * listMultipartUploads 单次最大返回条数
     */
    public static final int LIST_UPLOADS_PAGE_SIZE = 1000;

    // ==================== Content-Disposition ====================
    /**
     * Content-Disposition: 附件下载
     */
    public static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";

    /**
     * Content-Disposition: 内联预览
     */
    public static final String CONTENT_DISPOSITION_INLINE = "inline";

    /**
     * Content-Disposition 头部值模板
     * 参数：disposition，fileName
     */
    public static final String CONTENT_DISPOSITION_TEMPLATE = "%s; filename=\"%s\"";

    // ==================== STS 策略文档 ====================
    /**
     * IAM 策略效果：允许
     */
    public static final String POLICY_EFFECT_ALLOW = "Allow";

    /**
     * IAM 策略效果：拒绝
     */
    public static final String POLICY_EFFECT_DENY = "Deny";

    /**
     * STS 会话名称模板
     * 参数：sessionName
     */
    public static final String STS_SESSION_NAME_TEMPLATE = "%s-session";

    /**
     * 资源策略 ARN 模板
     * 参数：path
     */
    public static final String RESOURCE_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s/*";

    /**
     * 存储桶策略 ARN 模板
     * 参数：path
     */
    public static final String BUCKET_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s";

    /**
     * IAM 策略版本
     */
    public static final String POLICY_VERSION = "2012-10-17";
}