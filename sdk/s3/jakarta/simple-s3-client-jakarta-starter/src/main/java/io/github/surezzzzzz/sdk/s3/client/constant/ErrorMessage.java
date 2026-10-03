package io.github.surezzzzzz.sdk.s3.client.constant;

/**
 * S3 Client Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 请求参数非法
     */
    public static final String REQUEST_ILLEGAL = "请求参数非法";

    // ==================== 请求错误 ====================
    /**
     * 对象不存在
     */
    public static final String OBJECT_NOT_EXIST = "对象不存在";

    // ==================== 对象与桶语义错误 ====================
    /**
     * 桶不存在
     */
    public static final String BUCKET_NOT_EXIST = "桶不存在";
    /**
     * 桶已存在
     */
    public static final String BUCKET_ALREADY_EXIST = "桶已存在";
    /**
     * 访问被拒绝
     */
    public static final String ACCESS_DENIED = "访问被拒绝";
    /**
     * 上传失败（含分片上传全链路），格式化参数为存储侧错误码
     */
    public static final String UPLOAD_FAILED = "上传失败，存储侧错误码：%s";

    // ==================== 操作失败错误 ====================
    /**
     * 下载失败，格式化参数为存储侧错误码
     */
    public static final String DOWNLOAD_FAILED = "下载失败，存储侧错误码：%s";
    /**
     * 删除失败，格式化参数为存储侧错误码
     */
    public static final String DELETE_FAILED = "删除失败，存储侧错误码：%s";
    /**
     * 列举失败，格式化参数为存储侧错误码
     */
    public static final String LIST_FAILED = "列举失败，存储侧错误码：%s";
    /**
     * 元数据获取失败，格式化参数为存储侧错误码
     */
    public static final String GET_METADATA_FAILED = "元数据获取失败，存储侧错误码：%s";
    /**
     * 复制失败，格式化参数为存储侧错误码
     */
    public static final String COPY_FAILED = "复制失败，存储侧错误码：%s";
    /**
     * 事件 JSON 解析失败，格式化参数为失败原因描述
     */
    public static final String EVENT_PARSE_FAILED = "事件 JSON 解析失败：%s";

    // ==================== 事件解析错误 ====================
    /**
     * 桶操作失败，格式化参数为存储侧错误码
     */
    public static final String BUCKET_OPERATION_FAILED = "桶操作失败，存储侧错误码：%s";

    // ==================== 桶操作错误 ====================
    /**
     * 对象标签操作失败，格式化参数为存储侧错误码
     */
    public static final String TAGGING_FAILED = "对象标签操作失败，存储侧错误码：%s";

    // ==================== 对象标签错误 ====================
    /**
     * 标签集合不能为 null
     */
    public static final String TAGGING_NULL = "标签集合不能为 null";
    /**
     * 标签数量超限，格式化参数：上限，实际数量
     */
    public static final String TAGGING_TOO_MANY = "标签数量超过上限 %d，实际 %d";
    /**
     * 标签 Key 不能为空
     */
    public static final String TAGGING_KEY_EMPTY = "标签 Key 不能为空";
    /**
     * 标签 Value 不能为 null，格式化参数：Key
     */
    public static final String TAGGING_VALUE_NULL = "标签 %s 的 Value 不能为 null";
    /**
     * 标签 Key 超长，格式化参数：上限字节数，Key
     */
    public static final String TAGGING_KEY_TOO_LONG = "标签 %s 的 Key 超过 %d 字节上限";
    /**
     * 标签 Value 超长，格式化参数：上限字节数，Key
     */
    public static final String TAGGING_VALUE_TOO_LONG = "标签 %s 的 Value 超过 %d 字节上限";
    /**
     * STS 临时凭证获取失败，格式化参数为失败原因描述
     */
    public static final String STS_CREDENTIALS_FAILED = "STS 临时凭证获取失败：%s";

    // ==================== STS 凭证错误 ====================
    /**
     * 分段大小过小，格式化参数：最小值（MB），实际值（MB）
     */
    public static final String PART_SIZE_TOO_SMALL = "分段大小不能小于 %d MB，实际 %d MB";

    // ==================== 分段上传校验 ====================
    /**
     * 分段数量超限，格式化参数：上限，实际数量
     */
    public static final String MULTIPART_PART_COUNT_EXCEEDED = "分段数量超过上限 %d，实际 %d";
    /**
     * 分段 ETag 列表不能为 null
     */
    public static final String PART_ETAGS_NULL = "分段 ETag 列表不能为 null";
    /**
     * 分段 ETag 列表不能为空
     */
    public static final String PART_ETAGS_EMPTY = "分段 ETag 列表不能为空";
    /**
     * 分段 ETag 元素不能为 null
     */
    public static final String PART_ETAG_NULL = "分段 ETag 元素不能为 null";
    /**
     * 分段编号非法，格式化参数：编号上限，实际编号
     */
    public static final String PART_ETAG_PART_NUMBER_INVALID = "分段编号须在 1~%d 内，实际 %d";
    /**
     * 分段 ETag 为空，格式化参数：分段编号
     */
    public static final String PART_ETAG_ETAG_EMPTY = "分段 %d 的 ETag 不能为空";
    /**
     * 分段编号重复，格式化参数：分段编号
     */
    public static final String PART_ETAG_PART_NUMBER_DUPLICATE = "分段编号 %d 重复";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
