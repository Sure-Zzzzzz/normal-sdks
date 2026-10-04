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
    public static final String CONFIG_ASYNC_WRITE_THREAD_POOL_INVALID = "asyncWriteThreadPoolSize 必须 > 0";
    public static final String CONFIG_SERVER_VERSION_UNSUPPORTED =
            "serverVersion [%s] 不受 Jakarta 线支持：仅支持 Elasticsearch 7.17+ 与 8.x，ES 6.x 不支持";
    public static final String CONFIG_ROUTE_DATASOURCE_EMPTY = "datasource 不能为空";
    public static final String CONFIG_SERVER_VERSION_REQUIRED =
            "关闭 versionDetect 时必须显式配置 serverVersion，避免在未知 Elasticsearch 版本上启动";
    public static final String CONFIG_PROXY_PORT_INVALID = "proxyPort 必须 > 0";
    public static final String CONFIG_SSL_SKIP_VALIDATION_INVALID =
            "skipSslValidation 仅能用于 https 数据源";
    public static final String CONFIG_CREDENTIAL_INCOMPLETE =
            "username 与 password 必须同时配置或同时不配置";
    public static final String CONFIG_VALIDATION_FAILED = "Simple Elasticsearch Route 配置验证失败，请检查配置文件";

    // ========== 版本相关 ==========

    public static final String VERSION_EMPTY = "server-version 不能为空";
    public static final String VERSION_PARSE_FAILED = "无法解析 server-version: %s";
    public static final String VERSION_DETECT_FAILED = "[ds=%s] 自动探测 Elasticsearch 服务端版本失败";
    public static final String VERSION_NUMBER_NOT_FOUND = "Elasticsearch 根节点响应中未找到 version.number";
    public static final String VERSION_UNSUPPORTED =
            "[ds=%s] 自动探测到不受 Jakarta 线支持的 Elasticsearch 版本 [%s]，仅支持 7.17+ 与 8.x";
    public static final String VERSION_MISMATCH =
            "[ds=%s] server-version [%s] 与自动探测版本 [%s] 不一致，拒绝以错误版本启动";

    // ========== 路由相关 ==========

    public static final String ROUTE_DATASOURCE_NOT_FOUND = "数据源 [%s] 不存在，已配置的数据源: %s";
    public static final String ROUTE_CROSS_DATASOURCE = "不支持跨数据源操作，datasources=%s, indices=%s";
    public static final String ROUTE_NO_DATASOURCE = "未初始化任何 Elasticsearch 数据源";
    public static final String ROUTE_REFLECTION_INVOKE_FAILED = "调用 ElasticsearchOperations 方法失败";
    public static final String ROUTE_READ_INDEX_AMBIGUOUS =
            "read-index.pattern [%s] 中存在多个文档 ID [%s]，无法执行按 ID 的确定性读取";
    public static final String ROUTE_INDEX_REWRITE_UNSUPPORTED =
            "%s操作的方法 [%s] 不支持安全的 IndexCoordinates 重载";
    public static final String ROUTE_WRITE_MULTI_INDEX_UNSUPPORTED =
            "写操作 [%s] 一次涉及多个索引 %s，无法保证写入目标，已拒绝执行";
    public static final String ROUTE_OPERATION_UNSUPPORTED =
            "路由代理不支持复合操作 [%s]；请按 datasource 拆分后使用对应的 low-level client";
    public static final String ROUTE_POINT_IN_TIME_UNRESOLVED =
            "未找到 PIT [%s] 对应的数据源，拒绝在默认数据源关闭 PIT";
    public static final String ROUTE_INDEX_RESOLVE_FAILED = "解析索引 SpEL 表达式失败: %s";
    public static final String ROUTE_PATTERN_MATCH_FAILED =
            "路由规则匹配失败，拒绝回退到默认数据源";

    // ========== 其他 ==========

    public static final String OTHER_CLIENT_EXTRACT_FAILED = "调用底层 Elasticsearch client 失败: %s";
    public static final String OTHER_ELC_TEMPLATE_INIT_FAILED =
            "创建 Spring Data Elasticsearch ELC 模板失败，请确认 Spring Boot 3 的 Elasticsearch 依赖完整";
    public static final String OTHER_SSL_CONFIG_FAILED = "配置数据源 SSL 失败: %s";
    public static final String OTHER_URL_INVALID = "无效的 URL 格式: %s";
    public static final String OTHER_URL_EMPTY = "hosts 和 urls 都为空";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
