package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.constant;

/**
 * simple-elasticsearch-search-metrics 常量
 *
 * @author surezzzzzz
 */
public final class SimpleElasticsearchSearchMetricsConstant {

    private SimpleElasticsearchSearchMetricsConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.metrics.elasticsearch.search";

    /**
     * me 标签默认值
     */
    public static final String DEFAULT_ME = "unknown";

    // ==================== 指标名称 ====================

    /**
     * 查询/聚合请求计数指标
     */
    public static final String METRIC_REQUEST_TOTAL = "simple_elasticsearch_search_request_total";

    /**
     * 查询/聚合请求耗时指标
     */
    public static final String METRIC_REQUEST_SECONDS = "simple_elasticsearch_search_request_seconds";

    // ==================== 标签名 ====================

    /**
     * 操作类型标签（query / agg）
     */
    public static final String TAG_EVENT_TYPE = "eventType";

    /**
     * 结果标签（success / failure）
     */
    public static final String TAG_RESULT = "result";

    /**
     * 来源端点标签
     */
    public static final String TAG_SOURCE_TYPE = "sourceType";

    /**
     * 降级级别标签
     */
    public static final String TAG_DOWNGRADE_LEVEL = "downgradeLevel";

    /**
     * 业务模块标识标签
     */
    public static final String TAG_ME = "me";

    // ==================== 标签值 ====================

    /**
     * 操作类型：查询
     */
    public static final String EVENT_TYPE_QUERY = "query";

    /**
     * 操作类型：聚合
     */
    public static final String EVENT_TYPE_AGG = "agg";

    /**
     * 结果：成功
     */
    public static final String RESULT_SUCCESS = "success";

    /**
     * 结果：失败
     */
    public static final String RESULT_FAILURE = "failure";

    /**
     * sourceType 未知时的默认值
     */
    public static final String SOURCE_TYPE_UNKNOWN = "unknown";

    /**
     * 降级级别 0（未降级，或失败事件不可知时的默认值）
     */
    public static final String DOWNGRADE_LEVEL_ZERO = "0";
}
