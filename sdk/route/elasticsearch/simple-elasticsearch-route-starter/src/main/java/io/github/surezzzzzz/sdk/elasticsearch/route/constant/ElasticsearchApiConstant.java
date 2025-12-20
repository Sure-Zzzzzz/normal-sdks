package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * Elasticsearch API 常量
 *
 * @author surezzzzzz
 */
public class ElasticsearchApiConstant {

    // ========== HTTP Method ==========

    public static final String HTTP_METHOD_GET = "GET";
    public static final String HTTP_METHOD_POST = "POST";
    public static final String HTTP_METHOD_PUT = "PUT";
    public static final String HTTP_METHOD_DELETE = "DELETE";

    // ========== Endpoint ==========

    public static final String ENDPOINT_ROOT = "/";
    public static final String ENDPOINT_MAPPING = "/_mapping";
    public static final String ENDPOINT_SEARCH = "/_search";

    // ========== JSON Fields ==========

    public static final String JSON_FIELD_VERSION = "version";
    public static final String JSON_FIELD_NUMBER = "number";

    // ========== Regex Patterns ==========

    /**
     * 用于从 GET / 响应中提取版本号
     * 示例: {"version":{"number":"7.17.9",...}}
     */
    public static final String VERSION_NUMBER_PATTERN_REGEX = "\"version\"\\s*:\\s*\\{.*?\"number\"\\s*:\\s*\"([^\"]+)\"";

    private ElasticsearchApiConstant() {
        // 私有构造函数，防止实例化
    }
}
