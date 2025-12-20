package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public class ErrorMessage {

    // ========== 配置相关 ==========

    public static final String CONFIG_SOURCES_EMPTY = "配置项 'sources' 不能为空，至少需要配置一个数据源";
    public static final String CONFIG_DEFAULT_SOURCE_NOT_FOUND = "默认数据源 [%s] 不存在，已配置的数据源: %s";
    public static final String CONFIG_HOSTS_AND_URLS_EMPTY = "必须配置 hosts 或 urls";
    public static final String CONFIG_CONNECT_TIMEOUT_INVALID = "connectTimeout 必须 > 0";
    public static final String CONFIG_SOCKET_TIMEOUT_INVALID = "socketTimeout 必须 > 0";
    public static final String CONFIG_SERVER_VERSION_INVALID = "serverVersion 格式不正确: %s";
    public static final String CONFIG_MAX_CONN_TOTAL_INVALID = "maxConnTotal 必须 > 0";
    public static final String CONFIG_MAX_CONN_PER_ROUTE_INVALID = "maxConnPerRoute 必须 > 0";
    public static final String CONFIG_MAX_CONN_MISMATCH = "maxConnPerRoute (%d) 不能大于 maxConnTotal (%d)";
    public static final String CONFIG_PROXY_HOST_MISSING = "设置了 proxyPort 必须同时设置 proxyHost";
    public static final String CONFIG_KEEP_ALIVE_INVALID = "keepAliveStrategy 必须 > 0";
    public static final String CONFIG_URL_FORMAT_INVALID = "URL 格式验证失败: %s";
    public static final String CONFIG_ROUTE_PATTERN_EMPTY = "pattern 不能为空";
    public static final String CONFIG_ROUTE_DATASOURCE_NOT_FOUND = "[%s] 引用的数据源 [%s] 不存在，已配置的数据源: %s";
    public static final String CONFIG_ROUTE_TYPE_INVALID = "[%s] 匹配类型 [%s] 无效，有效值: exact, prefix, suffix, wildcard, regex";
    public static final String CONFIG_ROUTE_PRIORITY_INVALID = "[%s] priority 必须在 [1, 10000] 范围内，当前值: %d";
    public static final String CONFIG_ROUTE_REGEX_INVALID = "[%s] 正则表达式语法错误: %s";
    public static final String CONFIG_ROUTE_EXACT_DUPLICATE = "存在 %d 个 exact 类型的重复规则，pattern: %s";
    public static final String CONFIG_VERSION_DETECT_TIMEOUT_INVALID = "versionDetect.timeoutMs 必须 > 0";
    public static final String CONFIG_VALIDATION_FAILED = "Simple Elasticsearch Route 配置验证失败，请检查配置文件";

    // ========== 版本相关 ==========

    public static final String VERSION_EMPTY = "server-version 不能为空";
    public static final String VERSION_PARSE_FAILED = "无法解析 server-version: %s";
    public static final String VERSION_DETECT_FAILED = "[ds=%s] detect server-version failed and server-version is not configured";
    public static final String VERSION_NUMBER_NOT_FOUND = "version.number not found in response";

    // ========== 路由相关 ==========

    public static final String ROUTE_DATASOURCE_NOT_FOUND = "Datasource [%s] not found, available: %s";
    public static final String ROUTE_CROSS_DATASOURCE = "Cross datasource query is not supported, datasources=%s, indices=%s";
    public static final String ROUTE_NO_DATASOURCE = "No Elasticsearch datasource initialized!";

    // ========== 其他 ==========

    public static final String OTHER_CLIENT_EXTRACT_FAILED = "Failed to extract RestHighLevelClient from ElasticsearchRestTemplate";
    public static final String OTHER_DATASOURCE_INIT_FAILED = "Failed to initialize datasource: %s";
    public static final String OTHER_SSL_CONFIG_FAILED = "Failed to configure SSL for datasource: %s";
    public static final String OTHER_URL_INVALID = "无效的 URL 格式: %s";
    public static final String OTHER_URL_EMPTY = "hosts 和 urls 都为空";

    private ErrorMessage() {
        // 私有构造函数，防止实例化
    }
}
