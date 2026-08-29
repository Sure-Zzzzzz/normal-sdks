package io.github.surezzzzzz.sdk.s3.client.constant;

/**
 * S3 Client Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 请求参数非法
     */
    public static final String REQUEST_ILLEGAL = "S3_CLIENT_001";

    // ==================== 请求错误 ====================
    /**
     * 对象不存在
     */
    public static final String OBJECT_NOT_EXIST = "S3_CLIENT_002";

    // ==================== 对象与桶语义错误 ====================
    /**
     * 桶不存在
     */
    public static final String BUCKET_NOT_EXIST = "S3_CLIENT_003";
    /**
     * 桶已存在
     */
    public static final String BUCKET_ALREADY_EXIST = "S3_CLIENT_004";
    /**
     * 访问被拒绝
     */
    public static final String ACCESS_DENIED = "S3_CLIENT_005";
    /**
     * 上传失败（含分片上传全链路）
     */
    public static final String UPLOAD_FAILED = "S3_CLIENT_006";

    // ==================== 操作失败错误 ====================
    /**
     * 下载失败
     */
    public static final String DOWNLOAD_FAILED = "S3_CLIENT_007";
    /**
     * 删除失败
     */
    public static final String DELETE_FAILED = "S3_CLIENT_008";
    /**
     * 列举失败
     */
    public static final String LIST_FAILED = "S3_CLIENT_009";
    /**
     * 元数据获取失败
     */
    public static final String GET_METADATA_FAILED = "S3_CLIENT_010";
    /**
     * 复制失败
     */
    public static final String COPY_FAILED = "S3_CLIENT_011";
    /**
     * 事件 JSON 解析失败
     */
    public static final String EVENT_PARSE_FAILED = "S3_CLIENT_012";

    // ==================== 事件解析错误 ====================
    /**
     * 桶操作失败
     */
    public static final String BUCKET_OPERATION_FAILED = "S3_CLIENT_013";

    // ==================== 桶操作错误 ====================
    /**
     * 对象标签操作失败（含参数校验）
     */
    public static final String TAGGING_FAILED = "S3_CLIENT_014";

    // ==================== 对象标签错误 ====================
    /**
     * STS 临时凭证获取失败（含 target 配置不符）
     */
    public static final String STS_CREDENTIALS_FAILED = "S3_CLIENT_015";

    // ==================== STS 凭证错误 ====================

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }
}
