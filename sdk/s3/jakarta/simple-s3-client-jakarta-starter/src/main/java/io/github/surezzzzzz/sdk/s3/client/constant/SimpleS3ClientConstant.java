package io.github.surezzzzzz.sdk.s3.client.constant;

/**
 * S3 Client 常量。默认值与老 s3-client-starter 的 S3ClientConstant 对齐迁移。
 *
 * @author surezzzzzz
 */
public final class SimpleS3ClientConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.s3.client";

    // ==================== 配置相关常量 ====================
    /**
     * enable 配置项名
     */
    public static final String CONFIG_PROPERTY_ENABLE = "enable";
    /**
     * 布尔真值
     */
    public static final String BOOLEAN_TRUE = "true";
    /**
     * 毫秒/秒换算系数
     */
    public static final long MILLIS_PER_SECOND = 1000L;
    /**
     * STS 临时凭证默认有效时长（秒）
     */
    public static final int DEFAULT_STS_DURATION_SECONDS = 86400;

    // ==================== STS 临时凭证 ====================
    /**
     * IAM 策略文档版本
     */
    public static final String POLICY_VERSION = "2012-10-17";
    /**
     * IAM 策略效果：允许
     */
    public static final String POLICY_EFFECT_ALLOW = "Allow";
    /**
     * IAM 策略效果：拒绝
     */
    public static final String POLICY_EFFECT_DENY = "Deny";
    /**
     * STS 会话名称模板，格式化参数：会话标识
     */
    public static final String STS_SESSION_NAME_TEMPLATE = "%s-session";
    /**
     * 资源策略 ARN 模板，格式化参数：资源路径
     */
    public static final String RESOURCE_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s/*";
    /**
     * 存储桶策略 ARN 模板，格式化参数：桶名
     */
    public static final String BUCKET_POLICY_ARN_TEMPLATE = "arn:aws:s3:::%s";
    /**
     * 预签名 URL 默认有效时长（秒）
     */
    public static final long DEFAULT_PRESIGNED_URL_EXPIRATION_SECONDS = 86400L;

    // ==================== 预签名 URL ====================
    /**
     * 生命周期规则默认过期前缀
     */
    public static final String DEFAULT_BUCKET_EXPIRATION_PREFIX = "expiration-";

    // ==================== 存储桶生命周期 ====================
    /**
     * 生命周期规则默认过期天数
     */
    public static final int DEFAULT_BUCKET_EXPIRATION_DAYS = 180;
    /**
     * 生命周期规则 ID 后缀
     */
    public static final String LIFECYCLE_RULE_SUFFIX = "rule";
    /**
     * 断点续传下载默认目录
     */
    public static final String DEFAULT_DOWNLOAD_DIRECTORY = "./";

    // ==================== 下载 ====================
    /**
     * 文件读写模式（RandomAccessFile 追加写续传）
     */
    public static final String FILE_MODE_READ_WRITE = "rw";
    /**
     * 上传默认重试次数
     */
    public static final int DEFAULT_UPLOAD_RETRY_TIMES = 5;

    // ==================== 重试 ====================
    /**
     * 下载默认重试次数
     */
    public static final int DEFAULT_DOWNLOAD_RETRY_TIMES = 5;
    /**
     * 重试默认间隔（毫秒，对齐老模块实际传参语义）
     */
    public static final long DEFAULT_RETRY_INTERVAL_MS = 600L;
    /**
     * 自动分片触发阈值默认值（MB）
     */
    public static final int DEFAULT_MULTIPART_THRESHOLD_MB = 100;

    // ==================== 分段上传 ====================
    /**
     * 分段大小默认值（MB）
     */
    public static final int DEFAULT_PART_SIZE_MB = 5;
    /**
     * 分段大小最小值（MB，S3 协议限制）
     */
    public static final int MIN_PART_SIZE_MB = 5;
    /**
     * 分段上传并发度默认值
     */
    public static final int DEFAULT_MULTIPART_CONCURRENCY = 3;
    /**
     * 分段上传并发度最小值
     */
    public static final int MIN_MULTIPART_CONCURRENCY = 1;
    /**
     * 自动分片触发阈值最小值（MB）
     */
    public static final int MIN_MULTIPART_THRESHOLD_MB = 1;
    /**
     * MB 换算字节数
     */
    public static final long MB_IN_BYTES = 1024L * 1024L;
    /**
     * S3 单对象最多分段数
     */
    public static final int MAX_MULTIPART_PARTS = 10000;
    /**
     * S3 单次 PutObject 最大对象大小（5GB）
     */
    public static final long MAX_SINGLE_UPLOAD_BYTES = 5L * 1024 * 1024 * 1024;
    /**
     * listParts 单次请求最大返回条数
     */
    public static final int LIST_PARTS_PAGE_SIZE = 1000;
    /**
     * listMultipartUploads 单次请求最大返回条数
     */
    public static final int LIST_UPLOADS_PAGE_SIZE = 1000;
    /**
     * 对象不存在
     */
    public static final String S3_ERROR_NO_SUCH_KEY = "NoSuchKey";

    // ==================== S3 协议错误码 ====================
    /**
     * 下载范围超出对象大小（对象已全部下载）
     */
    public static final String S3_ERROR_INVALID_RANGE = "InvalidRange";
    /**
     * 分段上传不存在或已完成/已中止
     */
    public static final String S3_ERROR_NO_SUCH_UPLOAD = "NoSuchUpload";
    /**
     * 单个对象最大标签个数
     */
    public static final int MAX_OBJECT_TAGS = 10;

    // ==================== 对象标签限制 ====================
    /**
     * 单个标签 Key 最大 UTF-8 字节数
     */
    public static final int MAX_TAG_KEY_BYTES = 128;
    /**
     * 单个标签 Value 最大 UTF-8 字节数
     */
    public static final int MAX_TAG_VALUE_BYTES = 128;
    /**
     * 默认 Content-Type（未知或无扩展名）
     */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    // ==================== Content-Type / Content-Disposition ====================
    /**
     * Content-Disposition: 附件下载
     */
    public static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";
    /**
     * Content-Disposition: 内联预览
     */
    public static final String CONTENT_DISPOSITION_INLINE = "inline";
    /**
     * Content-Disposition 头部值模板，格式化参数：disposition，fileName
     */
    public static final String CONTENT_DISPOSITION_TEMPLATE = "%s; filename=\"%s\"";
    /**
     * 路径分隔符
     */
    public static final String PATH_SEPARATOR = "/";

    // ==================== 路径 ====================
    /**
     * 事件回调接收端点默认路径
     */
    public static final String DEFAULT_CALLBACK_PATH = "/api/s3-events";

    // ==================== 事件回调 ====================
    /**
     * Authorization 头部 Bearer 方案前缀
     */
    public static final String BEARER_PREFIX = "Bearer ";
    /**
     * URL query 认证参数名（不支持自定义请求头的存储使用）
     */
    public static final String CALLBACK_TOKEN_PARAM = "token";
    /**
     * 事件回调 event-callback 配置项名
     */
    public static final String CONFIG_PROPERTY_EVENT_CALLBACK = "event-callback";

    private SimpleS3ClientConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
