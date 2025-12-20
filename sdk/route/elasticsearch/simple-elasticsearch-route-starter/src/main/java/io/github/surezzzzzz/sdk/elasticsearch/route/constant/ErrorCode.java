package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public class ErrorCode {

    // ========== 配置相关 (CONFIG_xxx) ==========

    public static final String CONFIG_SOURCES_EMPTY = "CONFIG_001";
    public static final String CONFIG_DEFAULT_SOURCE_NOT_FOUND = "CONFIG_002";
    public static final String CONFIG_HOSTS_AND_URLS_EMPTY = "CONFIG_003";
    public static final String CONFIG_CONNECT_TIMEOUT_INVALID = "CONFIG_004";
    public static final String CONFIG_SOCKET_TIMEOUT_INVALID = "CONFIG_005";
    public static final String CONFIG_SERVER_VERSION_INVALID = "CONFIG_006";
    public static final String CONFIG_MAX_CONN_TOTAL_INVALID = "CONFIG_007";
    public static final String CONFIG_MAX_CONN_PER_ROUTE_INVALID = "CONFIG_008";
    public static final String CONFIG_MAX_CONN_MISMATCH = "CONFIG_009";
    public static final String CONFIG_PROXY_HOST_MISSING = "CONFIG_010";
    public static final String CONFIG_KEEP_ALIVE_INVALID = "CONFIG_011";
    public static final String CONFIG_URL_FORMAT_INVALID = "CONFIG_012";
    public static final String CONFIG_ROUTE_PATTERN_EMPTY = "CONFIG_013";
    public static final String CONFIG_ROUTE_DATASOURCE_NOT_FOUND = "CONFIG_014";
    public static final String CONFIG_ROUTE_TYPE_INVALID = "CONFIG_015";
    public static final String CONFIG_ROUTE_PRIORITY_INVALID = "CONFIG_016";
    public static final String CONFIG_ROUTE_REGEX_INVALID = "CONFIG_017";
    public static final String CONFIG_ROUTE_EXACT_DUPLICATE = "CONFIG_018";
    public static final String CONFIG_VERSION_DETECT_TIMEOUT_INVALID = "CONFIG_019";
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_999";

    // ========== 版本相关 (VERSION_xxx) ==========

    public static final String VERSION_EMPTY = "VERSION_001";
    public static final String VERSION_PARSE_FAILED = "VERSION_002";
    public static final String VERSION_DETECT_FAILED = "VERSION_003";
    public static final String VERSION_NUMBER_NOT_FOUND = "VERSION_004";

    // ========== 路由相关 (ROUTE_xxx) ==========

    public static final String ROUTE_DATASOURCE_NOT_FOUND = "ROUTE_001";
    public static final String ROUTE_CROSS_DATASOURCE = "ROUTE_002";
    public static final String ROUTE_NO_DATASOURCE = "ROUTE_003";

    // ========== 其他 (OTHER_xxx) ==========

    public static final String OTHER_CLIENT_EXTRACT_FAILED = "OTHER_001";
    public static final String OTHER_DATASOURCE_INIT_FAILED = "OTHER_002";
    public static final String OTHER_SSL_CONFIG_FAILED = "OTHER_003";
    public static final String OTHER_URL_INVALID = "OTHER_004";
    public static final String OTHER_URL_EMPTY = "OTHER_005";

    private ErrorCode() {
        // 私有构造函数，防止实例化
    }
}
