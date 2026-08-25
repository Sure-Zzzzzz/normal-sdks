package io.github.surezzzzzz.sdk.prometheus.client.constant;

/**
 * Prometheus Client 常量。
 *
 * @author surezzzzzz
 */
public final class SimplePrometheusClientConstant {

    /**
     * Prometheus HTTP API 远程写入路径。
     */
    public static final String WRITE_PATH = "/api/v1/write";

    /**
     * Content-Type header 名称。
     */
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * Content-Encoding header 名称。
     */
    public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";

    /**
     * Prometheus HTTP API 即时查询路径。
     */
    public static final String QUERY_PATH = "/api/v1/query";

    /**
     * Prometheus HTTP API 范围查询路径。
     */
    public static final String QUERY_RANGE_PATH = "/api/v1/query_range";

    /**
     * Remote Write 协议要求的 Content-Type。
     */
    public static final String CONTENT_TYPE_PROTOBUF = "application/x-protobuf";

    /**
     * Remote Write 协议要求的 Content-Encoding。
     */
    public static final String CONTENT_ENCODING_SNAPPY = "snappy";

    /**
     * Remote Write 协议版本 header。
     */
    public static final String HEADER_REMOTE_WRITE_VERSION = "X-Prometheus-Remote-Write-Version";

    /**
     * Remote Write 协议版本号。
     */
    public static final String REMOTE_WRITE_VERSION = "0.1.0";

    /**
     * HTTP 成功状态码下界（含）。
     */
    public static final int HTTP_SUCCESS_MIN = 200;

    /**
     * HTTP 成功状态码上界（不含）。
     */
    public static final int HTTP_SUCCESS_MAX = 300;

    /**
     * HTTP 重定向状态码下界（含）。
     */
    public static final int HTTP_REDIRECT_MIN = 300;

    /**
     * HTTP 重定向状态码上界（不含）。
     */
    public static final int HTTP_REDIRECT_MAX = 400;

    /**
     * Instant 的纳秒精度位数。
     */
    public static final int TIME_NANOS_SCALE = 9;

    /**
     * 即时查询参数名。
     */
    public static final String PARAMETER_QUERY = "query";

    /**
     * 即时查询时间参数名。
     */
    public static final String PARAMETER_TIME = "time";

    /**
     * 范围查询起始时间参数名。
     */
    public static final String PARAMETER_START = "start";

    /**
     * 范围查询结束时间参数名。
     */
    public static final String PARAMETER_END = "end";

    /**
     * 范围查询步长参数名。
     */
    public static final String PARAMETER_STEP = "step";

    /**
     * Prometheus 成功状态值。
     */
    public static final String RESPONSE_STATUS_SUCCESS = "success";

    /**
     * Prometheus 即时向量结果类型。
     */
    public static final String RESULT_TYPE_VECTOR = "vector";

    /**
     * Prometheus 范围矩阵结果类型。
     */
    public static final String RESULT_TYPE_MATRIX = "matrix";

    /**
     * Prometheus 时间戳保留的小数位数。
     */
    public static final int TIME_FRACTION_SCALE = 3;

    /**
     * 范围查询允许的最小步长（秒）。
     */
    public static final int MIN_STEP_SECONDS = 1;


    private SimplePrometheusClientConstant() {
    }
}
