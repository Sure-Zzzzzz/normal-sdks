package io.github.surezzzzzz.sdk.prometheus.route.constant;

/**
 * Prometheus Route 错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    // ==================== Target 错误 ====================

    /**
     * target 未登记。
     */
    public static final String TARGET_NOT_REGISTERED = "PROMETHEUS_ROUTE_001";

    /**
     * targetKey 非法。
     */
    public static final String TARGET_KEY_ILLEGAL = "PROMETHEUS_ROUTE_002";

    // ==================== 配置错误 ====================

    /**
     * target 配置非法。
     */
    public static final String TARGET_CONFIGURATION_ILLEGAL = "PROMETHEUS_ROUTE_003";

    // ==================== 请求错误 ====================

    /**
     * 请求非法。
     */
    public static final String REQUEST_ILLEGAL = "PROMETHEUS_ROUTE_004";

    /**
     * 请求执行失败。
     */
    public static final String REQUEST_EXECUTION_FAILED = "PROMETHEUS_ROUTE_005";

    /**
     * Route 已关闭。
     */
    public static final String ROUTE_CLOSED = "PROMETHEUS_ROUTE_006";

    /**
     * 响应正文超过限制。
     */
    public static final String RESPONSE_BODY_EXCEEDS_LIMIT = "PROMETHEUS_ROUTE_007";

    private ErrorCode() {
    }
}
