package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

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
    public static final String VERSION_DETECT_FAILED = "[ds=%s] 未配置 server-version，且自动探测服务端版本失败";
    public static final String VERSION_NUMBER_NOT_FOUND = "Elasticsearch 根节点响应中未找到 version.number";

    // ========== 路由相关 ==========

    public static final String ROUTE_DATASOURCE_NOT_FOUND = "数据源 [%s] 不存在，已配置的数据源: %s";
    public static final String ROUTE_CROSS_DATASOURCE = "不支持跨数据源查询，datasources=%s, indices=%s";
    public static final String ROUTE_NO_DATASOURCE = "未初始化任何 Elasticsearch 数据源";
    public static final String ROUTE_TEMPLATE_UNAVAILABLE = "数据源 [%s] 未创建 ElasticsearchRestTemplate，请改用 SimpleElasticsearchRouteRegistry.getHighLevelClient() 获取原生客户端";
    public static final String ROUTE_ES3X_STRING_INDEX_FAILED = "Spring Data Elasticsearch 3.x 索引级方法兼容调用失败: %s";
    public static final String ROUTE_ES3X_INDEX_FAILED = "Spring Data Elasticsearch 3.x 文档写入兼容调用失败";
    public static final String ROUTE_ES3X_GET_FAILED = "Spring Data Elasticsearch 3.x 按 ID 查询兼容调用失败";
    public static final String ROUTE_UNEXPECTED_INDEX_STATUS = "Elasticsearch 写入响应状态异常: %d";
    public static final String ROUTE_UNEXPECTED_GET_STATUS = "Elasticsearch GET 响应状态异常: %d";
    public static final String ROUTE_UNEXPECTED_SEARCH_STATUS = "Elasticsearch SEARCH 响应状态异常: %d";
    public static final String ROUTE_INDEX_QUERY_OBJECT_FAILED = "读取 IndexQuery 文档对象失败";
    public static final String ROUTE_INDEX_QUERY_ID_FAILED = "读取 IndexQuery 文档 ID 失败";
    public static final String ROUTE_DOCUMENT_INDEX_NAME_NOT_FOUND = "文档类未配置 @Document.indexName: %s";
    public static final String ROUTE_INDEX_COORDINATES_CREATE_FAILED = "创建 IndexCoordinates 失败，index: %s";
    public static final String ROUTE_REFLECTION_INVOKE_FAILED = "反射调用 ElasticsearchRestTemplate 方法失败";

    // ========== 代理相关 ==========

    public static final String PROXY_JDK_INTERFACE_NOT_FOUND = "ElasticsearchRestTemplate 实现类 [%s] 及其父类均未实现任何接口，无法创建 JDK 代理";
    public static final String PROXY_CREATION_FAILED = "CGLIB 和 JDK 代理均创建失败，请检查 spring-data-elasticsearch 版本兼容性";

    // ========== 其他 ==========

    public static final String OTHER_CLIENT_EXTRACT_FAILED = "从 ElasticsearchRestTemplate 提取 RestHighLevelClient 失败";
    public static final String OTHER_DATASOURCE_INIT_FAILED = "初始化数据源失败: %s";
    public static final String OTHER_SSL_CONFIG_FAILED = "配置数据源 SSL 失败: %s";
    public static final String OTHER_URL_INVALID = "无效的 URL 格式: %s";
    public static final String OTHER_URL_EMPTY = "hosts 和 urls 都为空";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
