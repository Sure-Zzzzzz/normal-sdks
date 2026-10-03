package io.github.surezzzzzz.sdk.prometheus.route.constant;

/**
 * Prometheus Route 受控错误消息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    // ==================== Target 错误 ====================

    /**
     * target 未登记。
     */
    public static final String TARGET_NOT_REGISTERED = "target 未登记";

    /**
     * targetKey 非法。
     */
    public static final String TARGET_KEY_ILLEGAL = "targetKey 非法";

    // ==================== 配置错误 ====================

    /**
     * target 配置非法。
     */
    public static final String TARGET_CONFIGURATION_ILLEGAL = "target 配置非法";

    // ==================== 请求错误 ====================

    /**
     * 请求非法。
     */
    public static final String REQUEST_ILLEGAL = "请求非法";

    /**
     * 请求执行失败。
     */
    public static final String REQUEST_EXECUTION_FAILED = "请求执行失败";

    /**
     * Route 已关闭。
     */
    public static final String ROUTE_CLOSED = "Route 已关闭";

    /**
     * 响应正文超过限制。
     */
    public static final String RESPONSE_BODY_EXCEEDS_LIMIT = "响应正文超过限制";

    private ErrorMessage() {
    }
}
