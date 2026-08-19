package io.github.surezzzzzz.sdk.ops.middleware.constant;

/**
 * Middleware Ops Server 固定常量。
 *
 * @author surezzzzzz
 */
public final class SmartMiddlewareOpsServerConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.ops.middleware";
    public static final String LDAP_CONFIG_PREFIX = CONFIG_PREFIX + ".ldap";
    public static final String DEFAULT_API_BASE_PATH = "/api/v1/middleware-ops";
    public static final String DEFAULT_UI_BASE_PATH = "/middleware-ops";
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_AUDIT_READ_ENABLED = true;
    public static final boolean DEFAULT_AUDIT_WRITE_ENABLED = true;
    public static final boolean DEFAULT_LDAP_ENABLED = false;
    public static final String DEFAULT_LDAP_USER_SEARCH_BASE = "";
    public static final String DEFAULT_LDAP_USER_SEARCH_FILTER = "(sAMAccountName={0})";
    public static final String AUDIT_WRITE_INDEX = "middleware-ops-audit";
    public static final String AUDIT_READ_INDEX_PATTERN = "middleware-ops-audit-*";
    public static final String AUDIT_PAGINATION_TYPE = "offset";
    public static final String AUDIT_SORT_FIELD_OCCURRED_AT = "occurredAt";
    public static final String AUDIT_SORT_ORDER_DESC = "desc";
    public static final String AUDIT_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String AUDIT_RANGE_1_DAY = "1d";
    public static final String AUDIT_RANGE_7_DAYS = "7d";
    public static final String AUDIT_RANGE_30_DAYS = "30d";
    public static final String AUDIT_RANGE_90_DAYS = "90d";
    public static final int AUDIT_RANGE_1_DAY_DAYS = 1;
    public static final int AUDIT_RANGE_7_DAYS_DAYS = 7;
    public static final int AUDIT_RANGE_30_DAYS_DAYS = 30;
    public static final int AUDIT_RANGE_90_DAYS_DAYS = 90;
    public static final int DEFAULT_AUDIT_MAX_RANGE_DAYS = AUDIT_RANGE_90_DAYS_DAYS;
    public static final int DEFAULT_AUDIT_MAX_OFFSET = 10000;
    public static final String AUDIT_FIELD_ID = "id";
    public static final String AUDIT_FIELD_OCCURRED_AT = "occurredAt";
    public static final String AUDIT_FIELD_SUBJECT = "subject";
    public static final String AUDIT_FIELD_CAPABILITY = "capability";
    public static final String AUDIT_FIELD_MIDDLEWARE_TYPE = "middlewareType";
    public static final String AUDIT_FIELD_DATASOURCE_KEY = "datasourceKey";
    public static final String AUDIT_FIELD_CLUSTER_TAG = "clusterTag";
    public static final String AUDIT_FIELD_RESOURCE_DIGEST = "resourceDigest";
    public static final String AUDIT_FIELD_HTTP_STATUS = "httpStatus";
    public static final String AUDIT_FIELD_DURATION_MILLIS = "durationMillis";
    public static final String AUDIT_FIELD_ELASTICSEARCH_INDEX = "elasticsearchIndex";
    public static final String AUDIT_FIELD_ELASTICSEARCH_DSL = "elasticsearchDsl";
    public static final String AUDIT_FIELD_MYSQL_SQL = "mysqlSql";
    public static final String AUDIT_FIELD_REDIS_KEY = "redisKey";
    public static final String AUDIT_FIELD_REDIS_FIELD = "redisField";
    public static final String AUDIT_FIELD_KAFKA_TOPIC = "kafkaTopic";
    public static final String AUDIT_FIELD_KAFKA_GROUP_ID = "kafkaGroupId";
    public static final String AUDIT_FIELD_PAGE = "page";
    public static final String AUDIT_FIELD_SIZE = "size";
    public static final String AUDIT_FIELD_OFFSET = "offset";
    public static final int DEFAULT_RESULT_SIZE = 50;
    public static final int MAX_RESULT_SIZE = 200;
    public static final int DEFAULT_MAX_DSL_LENGTH = 8192;
    public static final int DEFAULT_MAX_SQL_LENGTH = 8192;
    public static final int DEFAULT_MAX_COLUMNS = 40;
    public static final int DEFAULT_MAX_CELL_LENGTH = 1024;
    public static final int DEFAULT_MAX_KEY_LENGTH = 256;
    public static final int MAX_ELASTICSEARCH_INDEX_LIST_SIZE = 100;
    public static final int MAX_ELASTICSEARCH_FIELD_CAPABILITIES_SIZE = 200;
    public static final int MAX_ELASTICSEARCH_INDEX_RESPONSE_LENGTH = 131072;
    public static final int MAX_ELASTICSEARCH_FIELD_CAPABILITIES_RESPONSE_LENGTH = 262144;
    public static final int MAX_ELASTICSEARCH_DOCUMENT_RESPONSE_LENGTH = 262144;
    public static final int DEFAULT_MAX_VALUE_LENGTH = 4096;
    public static final int DEFAULT_ELASTICSEARCH_MAX_OFFSET = 10000;
    public static final long DEFAULT_DEADLINE_MILLIS = 5000L;
    public static final int DEFAULT_GLOBAL_CONCURRENCY = 16;
    public static final int DEFAULT_DATASOURCE_CONCURRENCY = 4;
    public static final String CACHE_CONTROL_NO_STORE = "no-store";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MEDIA_TYPE_APPLICATION_JSON = "application/json";
    public static final String EMPTY_VALUE = "";
    public static final String API_UNAUTHENTICATED_MESSAGE = "未认证";
    public static final String API_UNAUTHENTICATED_RESPONSE_TEMPLATE = "{\"message\":\"%s\",\"timestamp\":\"%s\",\"requestId\":\"%s\"}";

    private SmartMiddlewareOpsServerConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
