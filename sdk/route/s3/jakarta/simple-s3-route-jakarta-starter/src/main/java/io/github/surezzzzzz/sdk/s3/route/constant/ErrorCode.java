package io.github.surezzzzzz.sdk.s3.route.constant;

/**
 * S3 Route 错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    // ==================== Target 错误 ====================

    /**
     * target 未登记。
     */
    public static final String TARGET_NOT_REGISTERED = "S3_ROUTE_001";

    /**
     * targetKey 非法。
     */
    public static final String TARGET_KEY_ILLEGAL = "S3_ROUTE_002";

    // ==================== 配置错误 ====================

    /**
     * target 配置非法。
     */
    public static final String TARGET_CONFIGURATION_ILLEGAL = "S3_ROUTE_003";

    // ==================== 请求错误 ====================

    /**
     * 请求非法。
     */
    public static final String REQUEST_ILLEGAL = "S3_ROUTE_004";

    // ==================== 生命周期错误 ====================

    /**
     * Route 已关闭。
     */
    public static final String ROUTE_CLOSED = "S3_ROUTE_005";

    private ErrorCode() {
    }
}
