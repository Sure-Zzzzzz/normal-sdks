package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * Elasticsearch Route Jakarta 实现常量。
 *
 * @author surezzzzzz
 */
public final class SimpleElasticsearchRouteConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.elasticsearch.route";
    public static final String BEAN_ASYNC_WRITE_EXECUTOR_MAP = "simpleElasticsearchRouteAsyncWriteExecutorMap";
    public static final String BEAN_ELASTICSEARCH_TEMPLATE = "elasticsearchTemplate";
    public static final String DEFAULT_DATASOURCE_KEY = "primary";
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 60000;
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 300;
    public static final int DEFAULT_MAX_CONN_TOTAL = 100;
    public static final int DEFAULT_MAX_CONN_PER_ROUTE = 10;
    public static final boolean DEFAULT_ENABLE_CONNECTION_REUSE = true;
    public static final int DEFAULT_VERSION_DETECT_TIMEOUT_MS = 1500;
    public static final int DEFAULT_ASYNC_WRITE_THREAD_POOL_SIZE = 8;
    public static final int PRIORITY_MIN = 1;
    public static final int PRIORITY_MAX = 10000;
    public static final int PRIORITY_DEFAULT = 100;

    public static final int DEFAULT_HTTP_PORT = 9200;
    public static final int DEFAULT_HTTPS_PORT = 443;
    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_HTTPS = "https";
    public static final String HTTP_METHOD_GET = "GET";
    public static final String ENDPOINT_ROOT = "/";
    public static final int HTTP_STATUS_OK = 200;
    public static final int HTTP_STATUS_NOT_FOUND = 404;
    public static final String VERSION_NUMBER_PATTERN_REGEX =
            "\"version\"\\s*:\\s*\\{.*?\"number\"\\s*:\\s*\"([^\"]+)\"";

    public static final String CONFIG_WRITE_INDEX_ZONE_ID = "write-index.zone-id";
    public static final String CONFIG_WRITE_INDEX_ZONE_ID_LEGACY = "write-index-zone-id";
    public static final String CONFIG_WRITE_INDEX_TEMPLATE = "write-index.template";
    public static final String CONFIG_WRITE_INDEX_TEMPLATE_LEGACY = "write-index-template";
    public static final String CONFIG_READ_INDEX_PATTERN = "read-index.pattern";
    public static final String CONFIG_READ_INDEX_PATTERN_LEGACY = "read-index-pattern";
    public static final String TEMPLATE_DATASOURCE_PREFIX = "数据源 [%s] ";
    public static final String TEMPLATE_RULE_PREFIX = "路由规则 #%d ";

    public static final int ASYNC_WRITE_MAX_POOL_MULTIPLIER = 2;
    public static final int ASYNC_WRITE_QUEUE_CAPACITY = 1000;
    public static final long ASYNC_WRITE_KEEP_ALIVE_SECONDS = 60L;
    public static final int ASYNC_WRITE_SHUTDOWN_AWAIT_SECONDS = 30;
    public static final String ASYNC_WRITE_THREAD_NAME_PREFIX = "es-route-async-write-";
    public static final int PIT_ROUTE_CACHE_CAPACITY = 4096;

    public static final String METHOD_SAVE = "save";
    public static final String METHOD_BULK_INDEX = "bulkIndex";
    public static final String METHOD_BULK_UPDATE = "bulkUpdate";
    public static final String METHOD_UPDATE = "update";
    public static final String METHOD_DELETE = "delete";
    public static final String METHOD_EXISTS = "exists";
    public static final String METHOD_COUNT = "count";
    public static final String METHOD_SEARCH = "search";
    public static final String METHOD_SEARCH_ONE = "searchOne";
    public static final String METHOD_SEARCH_FOR_STREAM = "searchForStream";
    public static final String METHOD_MULTI_SEARCH = "multiSearch";
    public static final String METHOD_GET = "get";
    public static final String METHOD_MULTI_GET = "multiGet";
    public static final String METHOD_INDEX_OPS = "indexOps";
    public static final String METHOD_OPEN_POINT_IN_TIME = "openPointInTime";
    public static final String METHOD_CLOSE_POINT_IN_TIME = "closePointInTime";
    public static final String METHOD_REINDEX = "reindex";
    public static final String METHOD_SUBMIT_REINDEX = "submitReindex";
    public static final String METHOD_WITH_ROUTING = "withRouting";
    public static final String METHOD_WITH_REFRESH_POLICY = "withRefreshPolicy";

    public static final String WRITE_METHODS =
            "save,index,bulkIndex,bulkUpdate,update,updateByQuery,delete";
    public static final String READ_METHODS =
            "search,searchOne,searchForStream,count,multiSearch,get,multiGet,exists,openPointInTime";

    private SimpleElasticsearchRouteConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
