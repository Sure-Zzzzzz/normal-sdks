package io.github.surezzzzzz.sdk.prometheus.client.constant;

/**
 * Prometheus Client 错误消息模板。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 请求参数非法消息模板。
     */
    public static final String REQUEST_PARAMETER_ILLEGAL = "请求参数非法: operation=%s, reason=%s";

    /**
     * Remote Write 请求为空的原因。
     */
    public static final String REASON_WRITE_REQUEST_NULL = "writeRequest 不能为 null";

    /**
     * PromQL 为空的原因。
     */
    public static final String REASON_PROMQL_EMPTY = "promql 不能为 null 或空白";

    /**
     * 范围查询时间为空的原因。
     */
    public static final String REASON_RANGE_TIME_NULL = "start 和 end 不能为 null";

    /**
     * 范围查询步长非法的原因。
     */
    public static final String REASON_RANGE_STEP_INVALID = "stepSeconds 必须大于 0";

    /**
     * 范围查询时间顺序非法的原因。
     */
    public static final String REASON_RANGE_ORDER_INVALID = "start 不能晚于 end";

    /**
     * Snappy 压缩失败消息模板。
     */
    public static final String WRITE_COMPRESSION_FAILED = "Snappy 压缩 WriteRequest 失败: targetKey=%s";

    /**
     * 非预期重定向消息模板。
     */
    public static final String UNEXPECTED_REDIRECT =
            "收到非预期重定向响应（请检查 target URL 配置）: targetKey=%s, path=%s, statusCode=%d, responseBodyBytes=%d";

    /**
     * HTTP 请求失败消息模板。
     */
    public static final String HTTP_REQUEST_FAILED =
            "HTTP 请求失败: targetKey=%s, path=%s, statusCode=%d, responseBodyBytes=%d";

    /**
     * 响应解析失败消息模板。
     */
    public static final String RESPONSE_PARSE_FAILED = "响应解析失败: targetKey=%s, path=%s";

    /**
     * Prometheus 业务响应失败消息模板。
     */
    public static final String RESPONSE_STATUS_FAILED =
            "Prometheus 响应状态失败: targetKey=%s, path=%s";

    /**
     * Prometheus 响应结构非法消息模板。
     */
    public static final String RESPONSE_STRUCTURE_ILLEGAL =
            "Prometheus 响应结构非法: targetKey=%s, path=%s, reason=%s";


    private ErrorMessage() {
    }
}
