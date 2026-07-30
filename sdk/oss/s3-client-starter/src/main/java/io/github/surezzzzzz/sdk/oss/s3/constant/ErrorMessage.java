package io.github.surezzzzzz.sdk.oss.s3.constant;

/**
 * S3Client 错误消息
 */
public final class ErrorMessage {

    public static final String OBJECT_NOT_EXIST = "对象不存在";
    public static final String FILE_NOT_FOUND = "文件不存在：%s";
    public static final String OBJECT_ALREADY_EXISTS = "对象已存在";
    public static final String CREATE_BUCKET_FAILED = "创建存储桶失败：%s";
    public static final String CREATE_FOLDER_FAILED = "创建文件夹失败：%s";
    public static final String UPLOAD_OBJECT_FAILED = "上传对象失败：%s";
    public static final String DOWNLOAD_OBJECT_FAILED = "下载对象失败：%s";
    public static final String DELETE_OBJECT_FAILED = "删除对象失败：%s";
    public static final String COPY_OBJECT_FAILED = "复制对象失败：%s";
    public static final String LIST_OBJECTS_FAILED = "列举对象失败：%s";
    public static final String GET_OBJECT_METADATA_FAILED = "获取对象元信息失败：%s";
    public static final String SET_OBJECT_TAGGING_FAILED = "设置对象标签失败：%s";
    public static final String GET_OBJECT_TAGGING_FAILED = "获取对象标签失败：%s";
    public static final String DELETE_OBJECT_TAGGING_FAILED = "删除对象标签失败：%s";
    public static final String UPLOAD_PART_FAILED = "上传分段失败：%s";
    public static final String COMPLETE_MULTIPART_UPLOAD_FAILED = "完成分段上传失败：%s";
    public static final String BUCKET_EXISTS_BUT_NOT_FOUND = "存储桶已存在，但无法获取存储桶信息";
    // ==================== S3 标签校验 ====================
    public static final String TAGGING_NULL = "S3 标签不能为空";
    public static final String TAGGING_TOO_MANY = "S3 标签最多 %d 个键值对，当前传入 %d 个";
    public static final String TAGGING_KEY_EMPTY = "S3 标签 Key 不能为空";
    public static final String TAGGING_KEY_TOO_LONG = "S3 标签 Key 字节数不能超过 %d，当前 Key：\"%s\"";
    public static final String TAGGING_VALUE_NULL = "S3 标签 Value 不能为 null，当前 Key：\"%s\"";
    public static final String TAGGING_VALUE_TOO_LONG = "S3 标签 Value 字节数不能超过 %d，当前 Key：\"%s\"";
    // ==================== 分段上传校验 ====================
    public static final String PART_SIZE_TOO_SMALL = "partSizeMB 不能小于 %d，当前传入 %d";
    public static final String MULTIPART_PART_COUNT_EXCEEDED = "文件分段数不能超过 %d，当前分段数 %d，请调大 partSizeMB";
    public static final String PART_ETAGS_NULL = "partETags 不能为 null";
    public static final String PART_ETAGS_EMPTY = "partETags 不能为空";
    public static final String PART_ETAG_NULL = "partETags 不能包含 null 元素";
    public static final String PART_ETAG_PART_NUMBER_INVALID = "partNumber 必须在 1 到 %d 之间，当前值：%d";
    public static final String PART_ETAG_ETAG_EMPTY = "part ETag 不能为空，partNumber：%d";
    public static final String PART_ETAG_PART_NUMBER_DUPLICATE = "partNumber 不能重复，当前值：%d";
    // ==================== S3Client 配置校验 ====================
    public static final String PROPERTIES_MULTIPART_PART_SIZE_INVALID = "multipartPartSizeMB 不能小于 %d，当前传入 %d";
    public static final String PROPERTIES_MULTIPART_CONCURRENCY_INVALID = "multipartConcurrency 不能小于 1，当前传入 %d";
    public static final String PROPERTIES_MULTIPART_THRESHOLD_INVALID = "multipartThresholdMB 必须大于 0，当前传入 %d";
    public static final String PROPERTIES_TRUSTED_CA_ENDPOINT_NOT_HTTPS =
            "配置 trusted-ca-file 时，S3 endpoint 必须使用 HTTPS";
    public static final String PROPERTIES_TRUSTED_CA_FILE_NOT_READABLE =
            "S3 自定义 CA 文件不存在、不是普通文件或不可读：%s";
    public static final String PROPERTIES_TRUSTED_CA_FILE_INVALID =
            "S3 自定义 CA 文件无法解析为 X.509 证书：%s";
    public static final String PROPERTIES_TRUSTED_CA_FILE_EMPTY =
            "S3 自定义 CA 文件不包含有效 X.509 证书：%s";
    public static final String PROPERTIES_TRUSTED_CA_NOT_CA =
            "S3 自定义 CA 文件包含非 CA 证书：%s";
    public static final String PROPERTIES_TRUSTED_CA_KEY_USAGE_INVALID =
            "S3 自定义 CA 证书未声明 keyCertSign 用途：%s";
    public static final String PROPERTIES_TRUSTED_CA_NOT_VALID =
            "S3 自定义 CA 证书不在有效期内：%s";
    public static final String PROPERTIES_ENDPOINT_INVALID =
            "S3 endpoint 必须是合法的 HTTP 或 HTTPS 地址：%s";
    public static final String PROPERTIES_AWS_CERT_CHECKING_DISABLED =
            "检测到 AWS SDK 全局证书校验已关闭，不允许与 S3 客户端安全配置同时使用";
    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}