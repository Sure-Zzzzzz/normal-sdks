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
    /** ES 6.x 及以上通用默认 mapping type，7.x 中 deprecated 但仍可用。 */
    public static final String ES_DEFAULT_TYPE = "_doc";
    public static final String TIMEOUT_MS_SUFFIX = "ms";

    // ==== ES 路径模板与脚本 ====
    public static final String DEFAULT_SCRIPT_LANG = io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant.SCRIPT_LANG_PAINLESS;
    public static final String TASK_PATH_TEMPLATE = "/_tasks/%s";
    public static final String UPDATE_BY_QUERY_PATH_TEMPLATE = "/%s/_update_by_query";
    public static final String DELETE_BY_QUERY_PATH_TEMPLATE = "/%s/_delete_by_query";
}
