package io.github.surezzzzzz.sdk.elasticsearch.persistence.constant;

/**
 * Simple Elasticsearch Persistence Constants
 *
 * @author surezzzzzz
 */
public final class SimpleElasticsearchPersistenceConstant {

    private SimpleElasticsearchPersistenceConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==== 配置前缀与开关 ====
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.elasticsearch.persistence";
    public static final String CONFIG_ENABLE = "enable";

    // ==== 异步执行器 ====
    public static final int DEFAULT_ASYNC_EXECUTOR_CORE_SIZE = 4;
    public static final int DEFAULT_ASYNC_EXECUTOR_MAX_SIZE = 16;
    public static final int DEFAULT_ASYNC_EXECUTOR_QUEUE_CAPACITY = 1000;
    public static final String ASYNC_EXECUTOR_BEAN_NAME = "esPersistenceAsyncExecutor";
    public static final String ASYNC_EXECUTOR_THREAD_NAME_PREFIX = "es-persistence-";

    // ==== ES 请求构建 ====
    /**
     * ES 6.x 及以上通用默认 mapping type，7.x 中 deprecated 但仍可用。
     */
    public static final String ES_DEFAULT_TYPE = "_doc";
    public static final String TIMEOUT_MS_SUFFIX = "ms";
    public static final String HTTP_METHOD_GET = "GET";
    public static final String HTTP_METHOD_POST = "POST";
    public static final String PARAM_WAIT_FOR_COMPLETION = "wait_for_completion";
    public static final String PARAM_VALUE_TRUE = "true";
    public static final String PARAM_VALUE_FALSE = "false";

    // ==== ES 路径模板与脚本 ====
    public static final String DEFAULT_SCRIPT_LANG = io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant.SCRIPT_LANG_PAINLESS;
    public static final String TASK_PATH_TEMPLATE = "/_tasks/%s";
    public static final String UPDATE_BY_QUERY_PATH_TEMPLATE = "/%s/_update_by_query";
    public static final String DELETE_BY_QUERY_PATH_TEMPLATE = "/%s/_delete_by_query";

    // ==== 文档 ID 与字段标准化 ====
    public static final String ID_JOIN_DELIMITER = "|";
    public static final String UUID_HYPHEN = "-";
    public static final String EMPTY_STRING = "";
    public static final String DOCUMENT_PRE_PROCESSOR_NULL_RESULT = "DocumentPreProcessor 不能返回 null";
    public static final String HASH_ALGORITHM_SHA1 = "SHA-1";
    public static final String HASH_ALGORITHM_SHA256 = "SHA-256";
    public static final String UTF_8 = "UTF-8";
    public static final char FULL_WIDTH_SPACE = '　';
    public static final char HALF_WIDTH_SPACE = ' ';
    public static final int FULL_WIDTH_CHAR_START = 65281;
    public static final int FULL_WIDTH_CHAR_END = 65374;
    public static final int FULL_WIDTH_TO_HALF_WIDTH_OFFSET = 65248;
    public static final String REGEX_WHITESPACE_GROUP = "\\s+";
    public static final String SINGLE_SPACE = " ";
}
