package io.github.surezzzzzz.sdk.prometheus.client.constant;

/**
 * Prometheus Client 错误码（区别于 Route 层错误码 PROMETHEUS_ROUTE_00x，本模块 CLIENT_00x）。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 写入被拒绝（HTTP 状态码 4xx/5xx）。
     */
    public static final String WRITE_REJECTED = "PROMETHEUS_CLIENT_001";

    /**
     * 查询失败（HTTP 状态码 4xx/5xx）。
     */
    public static final String QUERY_FAILED = "PROMETHEUS_CLIENT_002";

    /**
     * 响应解析失败（JSON 或响应结构错误）。
     */
    public static final String RESPONSE_PARSE_FAILED = "PROMETHEUS_CLIENT_003";

    /**
     * 收到非预期重定向响应（3xx）。
     */
    public static final String UNEXPECTED_REDIRECT = "PROMETHEUS_CLIENT_004";

    /**
     * 请求参数非法。
     */
    public static final String REQUEST_ILLEGAL = "PROMETHEUS_CLIENT_005";

    private ErrorCode() {
    }
}
