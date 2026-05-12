package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * Simple Elasticsearch Route Constants
 *
 * @author surezzzzzz
 */
public final class SimpleElasticsearchRouteConstant {

    private SimpleElasticsearchRouteConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.elasticsearch.route";

    /**
     * 默认数据源 key
     */
    public static final String DEFAULT_DATASOURCE_KEY = "primary";

    /**
     * 默认连接超时时间（毫秒）
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    /**
     * 默认 Socket 超时时间（毫秒）
     */
    public static final int DEFAULT_SOCKET_TIMEOUT = 60000;

    /**
     * 默认 Keep-Alive 保持时间（秒）
     */
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 300;

    /**
     * 默认最大连接数
     */
    public static final int DEFAULT_MAX_CONN_TOTAL = 100;

    /**
     * 默认每个路由的最大连接数
     */
    public static final int DEFAULT_MAX_CONN_PER_ROUTE = 10;

    /**
     * 默认是否启用连接重用
     */
    public static final boolean DEFAULT_ENABLE_CONNECTION_REUSE = true;

    /**
     * 默认版本探测超时时间（毫秒）
     */
    public static final int DEFAULT_VERSION_DETECT_TIMEOUT_MS = 1500;

    // ==================== 路由优先级范围 ====================

    /**
     * 最低优先级
     */
    public static final int PRIORITY_MIN = 1;

    /**
     * 最高优先级
     */
    public static final int PRIORITY_MAX = 10000;

    /**
     * 默认优先级
     */
    public static final int PRIORITY_DEFAULT = 100;

    // ==================== 端口 & 协议 ====================

    /**
     * 默认 HTTP 端口
     */
    public static final int DEFAULT_HTTP_PORT = 9200;

    /**
     * 默认 HTTPS 端口
     */
    public static final int DEFAULT_HTTPS_PORT = 443;

    /**
     * HTTP 协议
     */
    public static final String PROTOCOL_HTTP = "http";

    /**
     * HTTPS 协议
     */
    public static final String PROTOCOL_HTTPS = "https";

    // ==================== 版本探测线程池 ====================

    /**
     * 版本探测线程池最小线程数
     */
    public static final int VERSION_DETECT_THREAD_POOL_MIN = 1;

    /**
     * 版本探测线程池最大线程数
     */
    public static final int VERSION_DETECT_THREAD_POOL_MAX = 4;

    /**
     * 版本探测线程名
     */
    public static final String VERSION_DETECT_THREAD_NAME = "es-route-version-detect";

    // ==================== ES API 常量 ====================

    /**
     * HTTP GET 方法
     */
    public static final String HTTP_METHOD_GET = "GET";

    /**
     * HTTP POST 方法
     */
    public static final String HTTP_METHOD_POST = "POST";

    /**
     * HTTP PUT 方法
     */
    public static final String HTTP_METHOD_PUT = "PUT";

    /**
     * HTTP DELETE 方法
     */
    public static final String HTTP_METHOD_DELETE = "DELETE";

    /**
     * ES 根端点
     */
    public static final String ENDPOINT_ROOT = "/";

    /**
     * ES mapping 端点
     */
    public static final String ENDPOINT_MAPPING = "/_mapping";

    /**
     * ES search 端点
     */
    public static final String ENDPOINT_SEARCH = "/_search";

    /**
     * JSON version 字段名
     */
    public static final String JSON_FIELD_VERSION = "version";

    /**
     * JSON number 字段名
     */
    public static final String JSON_FIELD_NUMBER = "number";

    /**
     * 从 GET / 响应中提取版本号的正则表达式
     * 示例: {"version":{"number":"7.17.9",...}}
     */
    public static final String VERSION_NUMBER_PATTERN_REGEX = "\"version\"\\s*:\\s*\\{.*?\"number\"\\s*:\\s*\"([^\"]+)\"";

    // ==================== ES Client 版本检测 ====================

    /**
     * ES Client 7.x 标记类（7.9+ 才有，6.8.x 不存在）
     */
    public static final String ES_CLIENT_7X_MARKER_CLASS = "org.elasticsearch.client.core.MainResponse";

    /**
     * ES Client 6.x 单数据源降级提示
     */
    public static final String MSG_ES_CLIENT_6X_SINGLE = "ES Client 6.8.x detected (Spring Boot 2.3.x), " +
            "routing proxy disabled, falling back to simple ElasticsearchRestTemplate. " +
            "For multi-datasource routing, upgrade to Spring Boot 2.4+ " +
            "or set elasticsearch.version=7.17.x in your project.";

    /**
     * ES Client 6.x 多数据源报错提示
     */
    public static final String MSG_ES_CLIENT_6X_MULTI = "ES Client 6.8.x detected (Spring Boot 2.3.x). " +
            "Multi-datasource routing requires ES Client 7.9+ (Spring Boot 2.4+). " +
            "Please upgrade Spring Boot or set elasticsearch.version=7.17.x in your project.";

    // ==================== 模板常量 ====================

    /**
     * 模板：数据源前缀 "数据源 [%s] "
     * 参数: datasourceKey
     */
    public static final String TEMPLATE_DATASOURCE_PREFIX = "数据源 [%s] ";

    /**
     * 模板：路由规则前缀 "路由规则 #%d "
     * 参数: ruleIndex
     */
    public static final String TEMPLATE_RULE_PREFIX = "路由规则 #%d ";
}
