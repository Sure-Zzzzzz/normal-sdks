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
     * 默认异步写线程池 key
     */
    public static final String DEFAULT_ASYNC_WRITE_EXECUTOR_KEY = "default";

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
     * Elasticsearch 文档端点片段
     */
    public static final String ENDPOINT_DOC_TYPE = "/_doc/";

    /**
     * HTTP Content-Type 头
     */
    public static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";

    /**
     * JSON 内容类型
     */
    public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    /**
     * HTTP 200 状态码
     */
    public static final int HTTP_STATUS_OK = 200;

    /**
     * HTTP 400 状态码
     */
    public static final int HTTP_STATUS_BAD_REQUEST = 400;

    /**
     * HTTP 404 状态码
     */
    public static final int HTTP_STATUS_NOT_FOUND = 404;

    /**
     * HTTP 3xx 状态码下界
     */
    public static final int HTTP_STATUS_REDIRECT_MIN = 300;

    /**
     * 读取 HTTP 响应缓冲区大小
     */
    public static final int HTTP_READ_BUFFER_SIZE = 1024;

    /**
     * Spring Data Elasticsearch 3.x HTTP 兼容调用超时时间（毫秒）
     */
    public static final int ES3X_COMPAT_HTTP_TIMEOUT_MS = 5000;

    /**
     * JSON version 字段名
     */
    public static final String JSON_FIELD_VERSION = "version";

    /**
     * JSON number 字段名
     */
    public static final String JSON_FIELD_NUMBER = "number";

    /**
     * JSON _source 字段名
     */
    public static final String JSON_FIELD_SOURCE = "_source";

    /**
     * JSON hits 字段名
     */
    public static final String JSON_FIELD_HITS = "hits";

    /**
     * JSON query 字段名
     */
    public static final String JSON_FIELD_QUERY = "query";

    /**
     * JSON ids 字段名
     */
    public static final String JSON_FIELD_IDS = "ids";

    /**
     * JSON values 字段名
     */
    public static final String JSON_FIELD_VALUES = "values";

    /**
     * 索引已存在异常类型
     */
    public static final String ES_EXCEPTION_RESOURCE_ALREADY_EXISTS = "resource_already_exists_exception";

    /**
     * 从 GET / 响应中提取版本号的正则表达式
     * 示例: {"version":{"number":"7.17.9",...}}
     */
    public static final String VERSION_NUMBER_PATTERN_REGEX = "\"version\"\\s*:\\s*\\{.*?\"number\"\\s*:\\s*\"([^\"]+)\"";

    // ==================== Spring Data API 兼容检测 ====================

    /**
     * RestHighLevelClient 7.x 标记类，用于判断当前依赖是否支持 Spring Data Elasticsearch 4.x 常用构造路径。
     */
    public static final String ES_CLIENT_7X_MARKER_CLASS = "org.elasticsearch.client.core.MainResponse";

    /**
     * Spring Data IndexCoordinates 类名
     */
    public static final String CLASS_INDEX_COORDINATES = "org.springframework.data.elasticsearch.core.mapping.IndexCoordinates";

    /**
     * Spring Data ElasticsearchRestTemplate 类名
     */
    public static final String CLASS_ELASTICSEARCH_REST_TEMPLATE = "org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate";

    /**
     * SpringBootVersion 类名
     */
    public static final String CLASS_SPRING_BOOT_VERSION = "org.springframework.boot.SpringBootVersion";

    /**
     * 未知版本号
     */
    public static final String VERSION_UNKNOWN = "unknown";

    /**
     * Spring Data IndexQuery 类名
     */
    public static final String CLASS_INDEX_QUERY = "org.springframework.data.elasticsearch.core.query.IndexQuery";

    /**
     * Jackson ObjectMapper 类名
     */
    public static final String CLASS_JACKSON_OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";

    /**
     * Jackson TreeNode 类名
     */
    public static final String CLASS_JACKSON_TREE_NODE = "com.fasterxml.jackson.core.TreeNode";

    /**
     * Jackson DeserializationFeature 类名
     */
    public static final String CLASS_JACKSON_DESERIALIZATION_FEATURE = "com.fasterxml.jackson.databind.DeserializationFeature";

    /**
     * ElasticsearchRestTemplate client 字段名
     */
    public static final String FIELD_CLIENT = "client";

    /**
     * Jackson FAIL_ON_UNKNOWN_PROPERTIES 字段名
     */
    public static final String FIELD_FAIL_ON_UNKNOWN_PROPERTIES = "FAIL_ON_UNKNOWN_PROPERTIES";

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

    // ==================== 异步写线程池 ====================

    /**
     * 异步写默认线程池大小
     */
    public static final int DEFAULT_ASYNC_WRITE_THREAD_POOL_SIZE = 8;

    /**
     * 异步写线程池最大线程数倍率（maxPoolSize = corePoolSize * MAX_POOL_MULTIPLIER）
     */
    public static final int ASYNC_WRITE_MAX_POOL_MULTIPLIER = 2;

    /**
     * 异步写线程池队列容量
     */
    public static final int ASYNC_WRITE_QUEUE_CAPACITY = 1000;

    /**
     * 异步写线程池 keepAlive 时间（秒）
     */
    public static final long ASYNC_WRITE_KEEP_ALIVE_SECONDS = 60L;

    /**
     * 异步写线程池 shutdown 等待时间（秒）
     */
    public static final int ASYNC_WRITE_SHUTDOWN_AWAIT_SECONDS = 30;

    /**
     * 异步写线程名前缀
     */
    public static final String ASYNC_WRITE_THREAD_NAME_PREFIX = "es-route-async-write-";

    // ==================== 写/读操作方法名单 ====================

    /**
     * save 方法名
     */
    public static final String METHOD_SAVE = "save";

    /**
     * index 方法名
     */
    public static final String METHOD_INDEX = "index";

    /**
     * get 方法名
     */
    public static final String METHOD_GET = "get";

    /**
     * size 方法名
     */
    public static final String METHOD_SIZE = "size";

    /**
     * createIndex 方法名
     */
    public static final String METHOD_CREATE_INDEX = "createIndex";

    /**
     * deleteIndex 方法名
     */
    public static final String METHOD_DELETE_INDEX = "deleteIndex";

    /**
     * indexExists 方法名
     */
    public static final String METHOD_INDEX_EXISTS = "indexExists";

    /**
     * indexOps 方法名
     */
    public static final String METHOD_INDEX_OPS = "indexOps";

    /**
     * getId 方法名
     */
    public static final String METHOD_GET_ID = "getId";

    /**
     * getIndexName 方法名
     */
    public static final String METHOD_GET_INDEX_NAME = "getIndexName";

    /**
     * getObject 方法名
     */
    public static final String METHOD_GET_OBJECT = "getObject";

    /**
     * getClient 方法名
     */
    public static final String METHOD_GET_CLIENT = "getClient";

    /**
     * getHost 方法名
     */
    public static final String METHOD_GET_HOST = "getHost";

    /**
     * toURI 方法名
     */
    public static final String METHOD_TO_URI = "toURI";

    /**
     * of 方法名
     */
    public static final String METHOD_OF = "of";

    /**
     * version 方法名
     */
    public static final String METHOD_VERSION = "version";

    /**
     * writeValueAsString 方法名
     */
    public static final String METHOD_WRITE_VALUE_AS_STRING = "writeValueAsString";

    /**
     * readTree 方法名
     */
    public static final String METHOD_READ_TREE = "readTree";

    /**
     * configure 方法名
     */
    public static final String METHOD_CONFIGURE = "configure";

    /**
     * treeToValue 方法名
     */
    public static final String METHOD_TREE_TO_VALUE = "treeToValue";

    /**
     * 写操作方法名单
     */
    public static final String WRITE_METHODS =
            "save,index,bulkIndex,bulkUpdate,update,updateByQuery,delete";

    /**
     * 读操作方法名单
     */
    public static final String READ_METHODS =
            "search,searchOne,searchForStream,count,multiSearch,suggest,get,multiGet,exists,searchScroll,searchScrollContinue,searchScrollClear";
}
