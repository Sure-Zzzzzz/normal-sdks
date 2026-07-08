package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant;

/**
 * Simple Elasticsearch Persistence Core Constants
 *
 * @author surezzzzzz
 */
public final class SimpleElasticsearchPersistenceCoreConstant {

    private SimpleElasticsearchPersistenceCoreConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String SCRIPT_LANG_PAINLESS = "painless";

    public static final String ES_RESULT_CREATED = "created";
    public static final String ES_RESULT_UPDATED = "updated";
    public static final String ES_RESULT_DELETED = "deleted";
    public static final String ES_RESULT_NOT_FOUND = "not_found";
    public static final String ES_RESULT_NOOP = "noop";

    public static final String REFRESH_POLICY_TRUE = "true";
    public static final String REFRESH_POLICY_FALSE = "false";
    public static final String REFRESH_POLICY_WAIT_FOR = "wait_for";

    public static final String BULK_PARTIAL_EXECUTION_FAILED = "Bulk 分批写入部分批次已提交，后续批次执行失败";

    public static final int HTTP_STATUS_BAD_REQUEST = 400;
    public static final int HTTP_STATUS_NOT_FOUND = 404;
    public static final int HTTP_STATUS_CONFLICT = 409;
    public static final int HTTP_STATUS_REQUEST_TIMEOUT = 408;
    public static final int HTTP_STATUS_TOO_MANY_REQUESTS = 429;
    public static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;
    public static final int HTTP_STATUS_BAD_GATEWAY = 502;
    public static final int HTTP_STATUS_SERVICE_UNAVAILABLE = 503;
    public static final int HTTP_STATUS_GATEWAY_TIMEOUT = 504;
}
