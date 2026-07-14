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
     * HTTP HEAD 方法
     */
    public static final String HTTP_METHOD_HEAD = "HEAD";

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
     * Elasticsearch refresh 端点
     */
    public static final String ENDPOINT_REFRESH = "/_refresh";

    public static final String ENDPOINT_COUNT = "/_count";
    public static final String ENDPOINT_UPDATE_BY_QUERY = "/_update_by_query";
    public static final String ENDPOINT_DELETE_BY_QUERY = "/_delete_by_query";
    public static final String ENDPOINT_TASKS_TEMPLATE = "/_tasks/%s";
    public static final String ENDPOINT_PIT = "/_pit";
    public static final String ENDPOINT_OPEN_PIT = "/_pit";
    public static final String ENDPOINT_SCROLL = "/_search/scroll";

    public static final String PARAM_WAIT_FOR_COMPLETION = "wait_for_completion";
    public static final String PARAM_IGNORE_THROTTLED = "ignore_throttled";
    public static final String PARAM_IGNORE_UNAVAILABLE = "ignore_unavailable";
    public static final String PARAM_ALLOW_NO_INDICES = "allow_no_indices";
    public static final String PARAM_EXPAND_WILDCARDS = "expand_wildcards";
    public static final String PARAM_INCLUDE_TYPE_NAME = "include_type_name";
    public static final String PARAM_MASTER_TIMEOUT = "master_timeout";
    public static final String PARAM_SCROLL = "scroll";
    public static final String PARAM_SCROLL_SIZE = "scroll_size";
    public static final String PARAM_KEEP_ALIVE = "keep_alive";
    public static final String PARAM_REFRESH = "refresh";
    public static final String PARAM_TIMEOUT = "timeout";
    public static final String PARAM_SLICES = "slices";
    public static final String PARAM_CONFLICTS = "conflicts";

    /**
     * by-query 任务节流参数
     */
    public static final String PARAM_REQUESTS_PER_SECOND = "requests_per_second";

    /**
     * by-query 最大文档数限制
     */
    public static final String PARAM_MAX_DOCS = "max_docs";

    /**
     * by-query 开始前活跃分片数
     */
    public static final String PARAM_WAIT_FOR_ACTIVE_SHARDS = "wait_for_active_shards";

    /**
     * by-query 路由参数
     */
    public static final String PARAM_ROUTING = "routing";

    public static final String PARAM_VALUE_TRUE = "true";
    public static final String PARAM_VALUE_FALSE = "false";
    public static final String PARAM_VALUE_OPEN = "open";
    public static final String PARAM_VALUE_CLOSED = "closed";

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

    public static final int HTTP_STATUS_REQUEST_TIMEOUT = 408;
    public static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;
    public static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;
    public static final int HTTP_STATUS_BAD_GATEWAY = 502;
    public static final int HTTP_STATUS_SERVICE_UNAVAILABLE = 503;
    public static final int HTTP_STATUS_GATEWAY_TIMEOUT = 504;

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

    public static final String JSON_FIELD_ID = "id";
    public static final String JSON_FIELD_TOTAL = "total";
    public static final String JSON_FIELD_VALUE = "value";
    public static final String JSON_FIELD_COUNT = "count";
    public static final String JSON_FIELD_COMPLETED = "completed";
    public static final String JSON_FIELD_TASK = "task";
    public static final String JSON_FIELD_STATUS = "status";
    public static final String JSON_FIELD_RESPONSE = "response";
    public static final String JSON_FIELD_FAILURES = "failures";
    public static final String JSON_FIELD_VERSION_CONFLICTS = "version_conflicts";
    public static final String JSON_FIELD_TOOK = "took";
    public static final String JSON_FIELD_UPDATED = "updated";
    public static final String JSON_FIELD_DELETED = "deleted";
    public static final String JSON_FIELD_SCRIPT = "script";
    public static final String JSON_FIELD_LANG = "lang";
    public static final String JSON_FIELD_PARAMS = "params";
    public static final String JSON_FIELD_CAUSE = "cause";
    public static final String JSON_FIELD_REASON = "reason";
    public static final String JSON_FIELD_INDEX = "index";
    public static final String JSON_FIELD_NODE = "node";
    public static final String JSON_FIELD_AGGREGATIONS = "aggregations";
    public static final String JSON_FIELD_BUCKETS = "buckets";
    public static final String JSON_FIELD_KEY = "key";
    public static final String JSON_FIELD_KEY_AS_STRING = "key_as_string";
    public static final String JSON_FIELD_DOC_COUNT = "doc_count";
    public static final String JSON_FIELD_MIN = "min";
    public static final String JSON_FIELD_MAX = "max";
    public static final String JSON_FIELD_AVG = "avg";
    public static final String JSON_FIELD_SUM = "sum";
    public static final String JSON_FIELD_AFTER_KEY = "after_key";
    public static final String JSON_FIELD_MAPPINGS = "mappings";
    public static final String JSON_FIELD_PROPERTIES = "properties";
    public static final String JSON_FIELD_TYPE = "type";
    public static final String JSON_FIELD_FORMAT = "format";
    public static final String JSON_FIELD_FIELDS = "fields";

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

    public static final String MAPPING_TYPE_DOC = "_doc";
    public static final String TIMEOUT_MS_SUFFIX = "ms";

    public static final String REFRESH_POLICY_TRUE = "true";
    public static final String REFRESH_POLICY_FALSE = "false";
    public static final String REFRESH_POLICY_WAIT_FOR = "wait_for";

    public static final String ES_RESULT_CREATED = "created";
    public static final String ES_RESULT_UPDATED = "updated";
    public static final String ES_RESULT_DELETED = "deleted";
    public static final String ES_RESULT_NOT_FOUND = "not_found";
    public static final String ES_RESULT_NOOP = "noop";

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

    public static final String CLASS_XCONTENT_TYPE_7X = "org.elasticsearch.xcontent.XContentType";
    public static final String CLASS_XCONTENT_TYPE_6X = "org.elasticsearch.common.xcontent.XContentType";
    public static final String CLASS_XCONTENT_FACTORY_7X = "org.elasticsearch.xcontent.XContentFactory";
    public static final String CLASS_XCONTENT_FACTORY_6X = "org.elasticsearch.common.xcontent.XContentFactory";
    public static final String CLASS_NAMED_XCONTENT_REGISTRY_7X = "org.elasticsearch.xcontent.NamedXContentRegistry";
    public static final String CLASS_NAMED_XCONTENT_REGISTRY_6X = "org.elasticsearch.common.xcontent.NamedXContentRegistry";
    public static final String CLASS_DEPRECATION_HANDLER_7X = "org.elasticsearch.xcontent.DeprecationHandler";
    public static final String CLASS_DEPRECATION_HANDLER_6X = "org.elasticsearch.common.xcontent.DeprecationHandler";
    public static final String CLASS_XCONTENT_PARSER_7X = "org.elasticsearch.xcontent.XContentParser";
    public static final String CLASS_XCONTENT_PARSER_6X = "org.elasticsearch.common.xcontent.XContentParser";
    public static final String CLASS_POINT_IN_TIME_BUILDER = "org.elasticsearch.search.builder.PointInTimeBuilder";
    public static final String CLASS_SEARCH_MODULE = "org.elasticsearch.search.SearchModule";
    public static final String CLASS_SETTINGS = "org.elasticsearch.common.settings.Settings";

    public static final String AGG_PACKAGE_METRICS_ES7 = "org.elasticsearch.search.aggregations.metrics";
    public static final String AGG_PACKAGE_STATS_ES6 = "org.elasticsearch.search.aggregations.metrics.stats";
    public static final String AGG_PACKAGE_EXTENDED_STATS_ES6 = "org.elasticsearch.search.aggregations.metrics.stats.extended";
    public static final String AGG_PACKAGE_PERCENTILES_ES6 = "org.elasticsearch.search.aggregations.metrics.percentiles";
    public static final String AGG_CLASS_NUMERIC_SINGLE_VALUE =
            "org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation$SingleValue";
    public static final String AGG_CLASS_NAME_STATS = "Stats";
    public static final String AGG_CLASS_NAME_EXTENDED_STATS = "ExtendedStats";
    public static final String AGG_CLASS_NAME_EXTENDED_STATS_BOUNDS = "ExtendedStats$Bounds";
    public static final String AGG_CLASS_NAME_PERCENTILES = "Percentiles";
    public static final String AGG_CLASS_NAME_PERCENTILE_RANKS = "PercentileRanks";
    public static final String AGG_CLASS_NAME_PERCENTILE = "Percentile";
    public static final String AGG_CLASS_PIPELINE_BUILDERS_ES7 =
            "org.elasticsearch.search.aggregations.PipelineAggregatorBuilders";
    public static final String AGG_CLASS_PIPELINE_BUILDERS_ES6 =
            "org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders";
    public static final String AGG_CLASS_BUCKET_SORT_ES7 =
            "org.elasticsearch.search.aggregations.pipeline.BucketSortPipelineAggregationBuilder";
    public static final String AGG_CLASS_BUCKET_SORT_ES6 =
            "org.elasticsearch.search.aggregations.pipeline.bucketsort.BucketSortPipelineAggregationBuilder";

    /**
     * ElasticsearchRestTemplate client 字段名
     */
    public static final String FIELD_CLIENT = "client";

    /**
     * Jackson FAIL_ON_UNKNOWN_PROPERTIES 字段名
     */
    public static final String FIELD_FAIL_ON_UNKNOWN_PROPERTIES = "FAIL_ON_UNKNOWN_PROPERTIES";

    public static final String FIELD_EMPTY = "EMPTY";
    public static final String FIELD_JSON = "JSON";
    public static final String FIELD_THROW_UNSUPPORTED_OPERATION = "THROW_UNSUPPORTED_OPERATION";
    public static final String FIELD_TOKEN_FIELD_NAME = "FIELD_NAME";

    public static final String AGG_BOUNDS_UPPER = "UPPER";
    public static final String AGG_BOUNDS_LOWER = "LOWER";
    public static final String AGG_COMPOSITE_FIELD_MISSING_BUCKET = "missing_bucket";
    public static final String AGG_COMPOSITE_FIELD_MISSING_ORDER = "missing_order";

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

    /**
     * 配置项：write-index.zone-id
     */
    public static final String CONFIG_WRITE_INDEX_ZONE_ID = "write-index.zone-id";

    /**
     * 配置项：write-index-zone-id
     */
    public static final String CONFIG_WRITE_INDEX_ZONE_ID_LEGACY = "write-index-zone-id";

    /**
     * 配置项：write-index.template
     */
    public static final String CONFIG_WRITE_INDEX_TEMPLATE = "write-index.template";

    /**
     * 配置项：write-index-template
     */
    public static final String CONFIG_WRITE_INDEX_TEMPLATE_LEGACY = "write-index-template";

    /**
     * 配置项：read-index.pattern
     */
    public static final String CONFIG_READ_INDEX_PATTERN = "read-index.pattern";

    /**
     * 配置项：read-index-pattern
     */
    public static final String CONFIG_READ_INDEX_PATTERN_LEGACY = "read-index-pattern";

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

    public static final String METHOD_READ_VALUE = "readValue";

    /**
     * configure 方法名
     */
    public static final String METHOD_CONFIGURE = "configure";

    /**
     * treeToValue 方法名
     */
    public static final String METHOD_TREE_TO_VALUE = "treeToValue";

    public static final String METHOD_XCONTENT = "xContent";
    public static final String METHOD_CREATE_PARSER = "createParser";
    public static final String METHOD_FROM_XCONTENT = "fromXContent";
    public static final String METHOD_GET_NAMED_XCONTENTS = "getNamedXContents";
    public static final String METHOD_NEXT_TOKEN = "nextToken";
    public static final String METHOD_CURRENT_NAME = "currentName";
    public static final String METHOD_LONG_VALUE = "longValue";
    public static final String METHOD_GET_TOTAL_HITS = "getTotalHits";
    public static final String METHOD_VALUE = "value";
    public static final String METHOD_POINT_IN_TIME_BUILDER = "pointInTimeBuilder";
    public static final String METHOD_SET_KEEP_ALIVE = "setKeepAlive";
    public static final String METHOD_GET_SOURCE_AS_MAP = "getSourceAsMap";

    public static final String AGG_METHOD_VALUE = "value";
    public static final String AGG_METHOD_GET_VALUE = "getValue";
    public static final String AGG_METHOD_GET_PERCENT = "getPercent";
    public static final String AGG_METHOD_GET_COUNT = "getCount";
    public static final String AGG_METHOD_GET_MIN = "getMin";
    public static final String AGG_METHOD_GET_MAX = "getMax";
    public static final String AGG_METHOD_GET_AVG = "getAvg";
    public static final String AGG_METHOD_GET_SUM = "getSum";
    public static final String AGG_METHOD_GET_SUM_OF_SQUARES = "getSumOfSquares";
    public static final String AGG_METHOD_GET_VARIANCE = "getVariance";
    public static final String AGG_METHOD_GET_STD_DEVIATION = "getStdDeviation";
    public static final String AGG_METHOD_GET_STD_DEVIATION_BOUND = "getStdDeviationBound";
    public static final String AGG_METHOD_BUCKET_SORT = "bucketSort";
    public static final String AGG_METHOD_BUCKET_SELECTOR = "bucketSelector";
    public static final String AGG_METHOD_SIZE = "size";
    public static final String AGG_METHOD_FROM = "from";

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
