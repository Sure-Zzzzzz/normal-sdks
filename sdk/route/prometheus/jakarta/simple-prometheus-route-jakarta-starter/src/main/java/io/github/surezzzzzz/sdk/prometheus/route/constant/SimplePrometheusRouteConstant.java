package io.github.surezzzzzz.sdk.prometheus.route.constant;

/**
 * Prometheus Route 常量。
 *
 * @author surezzzzzz
 */
public final class SimplePrometheusRouteConstant {

    // ==================== 配置常量 ====================

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.prometheus.route";

    /**
     * 启用配置名称。
     */
    public static final String CONFIG_PROPERTY_ENABLE = "enable";

    /**
     * 布尔真值。
     */
    public static final String BOOLEAN_TRUE = "true";

    // ==================== 默认 HTTP 配置 ====================

    /**
     * 默认关闭等待时间（毫秒）。
     */
    public static final int DEFAULT_SHUTDOWN_TIMEOUT_MS = 10000;

    /**
     * 默认连接超时（毫秒）。
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    /**
     * 默认读超时（毫秒）。
     */
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 10000;

    /**
     * 默认连接池获取超时（毫秒）。
     */
    public static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_MS = 2000;

    /**
     * 默认空闲连接校验阈值（毫秒）。
     */
    public static final int DEFAULT_VALIDATE_AFTER_INACTIVITY_MS = 1000;

    /**
     * 默认 target 连接池总连接数。
     */
    public static final int DEFAULT_MAX_TOTAL = 20;

    /**
     * 默认单路由最大连接数。
     */
    public static final int DEFAULT_MAX_PER_ROUTE = 20;

    /**
     * 默认最大响应正文长度（字节）。
     */
    public static final int DEFAULT_MAX_RESPONSE_BODY_BYTES = 10485760;

    // ==================== 传输常量 ====================

    /**
     * HTTP 协议。
     */
    public static final String HTTP_SCHEME = "http";

    /**
     * HTTPS 协议。
     */
    public static final String HTTPS_SCHEME = "https";

    /**
     * 根路径。
     */
    public static final String ROOT_PATH = "/";

    /**
     * 协议相对路径前缀。
     */
    public static final String AUTHORITY_PATH_PREFIX = "//";

    /**
     * query 分隔符。
     */
    public static final char QUERY_SEPARATOR = '?';

    /**
     * fragment 分隔符。
     */
    public static final char FRAGMENT_SEPARATOR = '#';

    /**
     * 百分号编码的点。
     */
    public static final String ENCODED_DOT = "%2e";

    /**
     * 当前路径段。
     */
    public static final String CURRENT_PATH_SEGMENT = ".";

    /**
     * 父级路径段。
     */
    public static final String PARENT_PATH_SEGMENT = "..";

    /**
     * 根路径最小长度。
     */
    public static final int MIN_PATH_LENGTH = 1;

    /**
     * 保留末尾空路径段的 split 限制。
     */
    public static final int KEEP_TRAILING_EMPTY_SEGMENTS = -1;

    /**
     * 响应读取缓冲区长度（字节）。
     */
    public static final int RESPONSE_BUFFER_BYTES = 4096;

    private SimplePrometheusRouteConstant() {
    }
}
