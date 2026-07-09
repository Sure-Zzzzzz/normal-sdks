package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 通用常量
 *
 * @author surezzzzzz
 */
public class SimpleElasticsearchSearchConstant {

    // ========== 分页相关 ==========

    /**
     * 默认分页类型：offset
     */
    public static final String PAGINATION_TYPE_OFFSET = "offset";

    /**
     * 分页类型：search_after
     */
    public static final String PAGINATION_TYPE_SEARCH_AFTER = "search_after";

    /**
     * 分页类型：scroll
     */
    public static final String PAGINATION_TYPE_SCROLL = "scroll";

    /**
     * 排序方向：升序
     */
    public static final String SORT_ORDER_ASC = "asc";

    /**
     * 排序方向：降序
     */
    public static final String SORT_ORDER_DESC = "desc";

    /**
     * 聚合不返回文档（size=0）
     */
    public static final int AGG_NO_DOCS_SIZE = 0;

    // ========== 逻辑操作符 ==========

    /**
     * 逻辑与
     */
    public static final String LOGIC_AND = "and";

    /**
     * 逻辑或
     */
    public static final String LOGIC_OR = "or";

    /**
     * OR 查询最少匹配数
     */
    public static final int OR_MINIMUM_SHOULD_MATCH = 1;

    // ========== 通配符 ==========

    /**
     * 通配符：*
     */
    public static final String WILDCARD_STAR = "*";

    /**
     * 通配符：?
     */
    public static final String WILDCARD_QUESTION = "?";

    // ========== ES 内部字段 ==========

    /**
     * 文档 ID 字段
     */
    public static final String ES_FIELD_ID = "_id";

    /**
     * 评分字段
     */
    public static final String ES_FIELD_SCORE = "_score";

    // ========== 聚合结果字段名 ==========

    /**
     * 聚合结果字段：key
     */
    public static final String AGG_RESULT_KEY = "key";

    /**
     * 聚合结果字段：count
     */
    public static final String AGG_RESULT_COUNT = "count";

    /**
     * Stats 聚合结果字段：min
     */
    public static final String STATS_RESULT_MIN = "min";

    /**
     * Stats 聚合结果字段：max
     */
    public static final String STATS_RESULT_MAX = "max";

    /**
     * Stats 聚合结果字段：avg
     */
    public static final String STATS_RESULT_AVG = "avg";

    /**
     * Stats 聚合结果字段：sum
     */
    public static final String STATS_RESULT_SUM = "sum";

    /**
     * ExtendedStats 聚合结果字段：sum_of_squares
     */
    public static final String EXTENDED_STATS_SUM_OF_SQUARES = "sum_of_squares";

    /**
     * ExtendedStats 聚合结果字段：variance
     */
    public static final String EXTENDED_STATS_VARIANCE = "variance";

    /**
     * ExtendedStats 聚合结果字段：std_deviation
     */
    public static final String EXTENDED_STATS_STD_DEVIATION = "std_deviation";

    /**
     * ExtendedStats 聚合结果字段：std_deviation_bounds
     */
    public static final String EXTENDED_STATS_STD_DEVIATION_BOUNDS = "std_deviation_bounds";

    /**
     * ExtendedStats std_deviation_bounds 子字段：upper
     */
    public static final String EXTENDED_STATS_BOUNDS_UPPER = "upper";

    /**
     * ExtendedStats std_deviation_bounds 子字段：lower
     */
    public static final String EXTENDED_STATS_BOUNDS_LOWER = "lower";

    // ========== 敏感字段处理 ==========

    /**
     * 默认脱敏字符
     */
    public static final String DEFAULT_MASK_PATTERN = "****";

    /**
     * 默认脱敏起始位置
     */
    public static final int DEFAULT_MASK_START = 0;

    /**
     * 默认脱敏结束位置
     */
    public static final int DEFAULT_MASK_END = 0;

    /**
     * 敏感字段禁止原因
     */
    public static final String SENSITIVE_FIELD_REASON = "sensitive field";

    // ========== 聚合默认值 ==========

    /**
     * Terms 聚合默认 size
     */
    public static final int DEFAULT_TERMS_SIZE = 10;

    /**
     * Histogram 聚合默认 interval
     */
    public static final double DEFAULT_HISTOGRAM_INTERVAL = 1.0;

    // ========== 数组索引 ==========

    /**
     * BETWEEN 查询起始值索引
     */
    public static final int BETWEEN_FROM_INDEX = 0;

    /**
     * BETWEEN 查询结束值索引
     */
    public static final int BETWEEN_TO_INDEX = 1;

    /**
     * BETWEEN 查询所需参数数量
     */
    public static final int BETWEEN_REQUIRED_VALUES = 2;

    // ========== ES 原始 JSON 字段名（ES 响应解析） ==========

    /**
     * ES 响应聚合根字段
     */
    public static final String ES_JSON_AGGREGATIONS = "aggregations";

    /**
     * ES 聚合值字段（Metrics 聚合）
     */
    public static final String ES_JSON_VALUE = "value";

    /**
     * ES percentiles/percentile_ranks 聚合值字段（多值 Map）
     */
    public static final String ES_JSON_VALUES = "values";

    /**
     * ES 聚合 buckets 字段（Bucket 聚合）
     */
    public static final String ES_JSON_BUCKETS = "buckets";

    /**
     * ES bucket key 字段
     */
    public static final String ES_JSON_KEY = "key";

    /**
     * ES bucket key_as_string 字段
     */
    public static final String ES_JSON_KEY_AS_STRING = "key_as_string";

    /**
     * ES bucket doc_count 字段
     */
    public static final String ES_JSON_DOC_COUNT = "doc_count";

    /**
     * ES stats count 字段
     */
    public static final String ES_JSON_COUNT = "count";

    /**
     * ES stats min 字段
     */
    public static final String ES_JSON_MIN = "min";

    /**
     * ES stats max 字段
     */
    public static final String ES_JSON_MAX = "max";

    /**
     * ES stats avg 字段
     */
    public static final String ES_JSON_AVG = "avg";

    /**
     * ES stats sum 字段
     */
    public static final String ES_JSON_SUM = "sum";

    // ========== ES Mapping 字段名 ==========

    /**
     * ES mapping properties 字段
     */
    public static final String ES_MAPPING_PROPERTIES = "properties";

    /**
     * ES mapping type 字段
     */
    public static final String ES_MAPPING_TYPE = "type";

    /**
     * ES mapping format 字段
     */
    public static final String ES_MAPPING_FORMAT = "format";

    /**
     * ES mapping fields 字段（multi-fields，如 text 的 keyword 子字段）
     */
    public static final String ES_MAPPING_FIELDS = "fields";

    /**
     * ES 默认文档类型（ES 7.x+）
     */
    public static final String ES_DEFAULT_DOC_TYPE = "_doc";

    // ========== 子字段相关 ==========

    /**
     * 子字段名：keyword（text 字段的精确匹配子字段）
     */
    public static final String SUB_FIELD_KEYWORD = "keyword";

    /**
     * 模板：keyword 子字段路径
     * 参数: 字段名
     */
    public static final String TEMPLATE_KEYWORD_SUB_FIELD = "%s." + SUB_FIELD_KEYWORD;

    // ========== ES API 参数名 ==========

    /**
     * ES 6.x 不支持的参数：include_type_name
     */
    public static final String ES_PARAM_INCLUDE_TYPE_NAME = "include_type_name";

    /**
     * ES 6.x 不支持的参数：master_timeout
     */
    public static final String ES_PARAM_MASTER_TIMEOUT = "master_timeout";

    /**
     * ES API 参数：ignore_unavailable（忽略不存在的索引）
     */
    public static final String ES_PARAM_IGNORE_UNAVAILABLE = "ignore_unavailable";

    /**
     * ES API 参数：allow_no_indices（允许没有匹配的索引）
     */
    public static final String ES_PARAM_ALLOW_NO_INDICES = "allow_no_indices";

    /**
     * ES API 参数：expand_wildcards（通配符展开范围）
     */
    public static final String ES_PARAM_EXPAND_WILDCARDS = "expand_wildcards";

    /**
     * expand_wildcards 参数值：open（展开 open 状态的索引）
     */
    public static final String ES_WILDCARD_STATE_OPEN = "open";

    /**
     * expand_wildcards 参数值：closed（展开 closed 状态的索引）
     */
    public static final String ES_WILDCARD_STATE_CLOSED = "closed";

    /**
     * 逗号分隔符
     */
    public static final String COMMA = ",";

    /**
     * ES API 参数值：true
     */
    public static final String ES_PARAM_VALUE_TRUE = "true";

    /**
     * ES 6.x 错误信息：unrecognized parameter
     */
    public static final String ES_ERROR_UNRECOGNIZED_PARAMETER = "unrecognized parameter";

    // ========== Java 反射相关常量（XContent API 包路径） ==========

    /**
     * ES 7.x+ XContent API 包路径
     */
    public static final String XCONTENT_PACKAGE_ES7 = "org.elasticsearch.xcontent";

    /**
     * ES 6.x XContent API 包路径
     */
    public static final String XCONTENT_PACKAGE_ES6 = "org.elasticsearch.common.xcontent";

    /**
     * XContentType 类名后缀
     */
    public static final String XCONTENT_CLASS_TYPE = ".XContentType";

    /**
     * XContentFactory 类名后缀
     */
    public static final String XCONTENT_CLASS_FACTORY = ".XContentFactory";

    /**
     * XContentParser 类名后缀
     */
    public static final String XCONTENT_CLASS_PARSER = ".XContentParser";

    /**
     * NamedXContentRegistry 类名后缀
     */
    public static final String XCONTENT_CLASS_REGISTRY = ".NamedXContentRegistry";

    /**
     * DeprecationHandler 类名后缀
     */
    public static final String XCONTENT_CLASS_DEPRECATION_HANDLER = ".DeprecationHandler";

    // ========== Java 反射相关常量（ES 内部类） ==========

    /**
     * SearchModule 类全名
     */
    public static final String ES_CLASS_SEARCH_MODULE = "org.elasticsearch.search.SearchModule";

    /**
     * Settings 类全名
     */
    public static final String ES_CLASS_SETTINGS = "org.elasticsearch.common.settings.Settings";

    // ========== Java 反射相关常量（聚合类包路径，6.x/7.x 差异） ==========

    /**
     * ES 7.x metrics 顶层包（Stats / ExtendedStats / Percentiles 等提到顶层）
     */
    public static final String AGG_PACKAGE_METRICS_ES7 = "org.elasticsearch.search.aggregations.metrics";

    /**
     * ES 6.x metrics 子包（Stats 在 stats 子包，ExtendedStats 在 stats.extended，Percentiles 在 percentiles）
     */
    public static final String AGG_PACKAGE_STATS_ES6 = "org.elasticsearch.search.aggregations.metrics.stats";

    /**
     * ES 6.x ExtendedStats 子包
     */
    public static final String AGG_PACKAGE_EXTENDED_STATS_ES6 = "org.elasticsearch.search.aggregations.metrics.stats.extended";

    /**
     * ES 6.x Percentiles / PercentileRanks / Percentile 子包
     */
    public static final String AGG_PACKAGE_PERCENTILES_ES6 = "org.elasticsearch.search.aggregations.metrics.percentiles";

    /**
     * NumericMetricsAggregation$SingleValue 全名（6.x/7.x 同路径）
     */
    public static final String AGG_CLASS_NUMERIC_SINGLE_VALUE =
            "org.elasticsearch.search.aggregations.metrics.NumericMetricsAggregation$SingleValue";

    /**
     * Stats 类名（不带包）
     */
    public static final String AGG_CLASS_NAME_STATS = "Stats";

    /**
     * ExtendedStats 类名（不带包）
     */
    public static final String AGG_CLASS_NAME_EXTENDED_STATS = "ExtendedStats";

    /**
     * ExtendedStats$Bounds 内部类名（不带包）
     */
    public static final String AGG_CLASS_NAME_EXTENDED_STATS_BOUNDS = "ExtendedStats$Bounds";

    /**
     * Percentiles 类名（不带包）
     */
    public static final String AGG_CLASS_NAME_PERCENTILES = "Percentiles";

    /**
     * Percentile 类名（不带包，Percentiles 聚合迭代元素）
     */
    public static final String AGG_CLASS_NAME_PERCENTILE = "Percentile";

    /**
     * PercentileRanks 类名（不带包）
     */
    public static final String AGG_CLASS_NAME_PERCENTILE_RANKS = "PercentileRanks";

    // ========== 反射方法名常量 ==========

    /**
     * NumericMetricsAggregation.SingleValue.value() 方法名
     */
    public static final String AGG_METHOD_VALUE = "value";

    /**
     * Percentile.getValue() 方法名
     */
    public static final String AGG_METHOD_GET_VALUE = "getValue";

    /**
     * getCount / getMin / getMax / getAvg / getSum / getSumOfSquares / getVariance / getStdDeviation 方法名前缀
     */
    public static final String AGG_METHOD_GET_COUNT = "getCount";
    public static final String AGG_METHOD_GET_MIN = "getMin";
    public static final String AGG_METHOD_GET_MAX = "getMax";
    public static final String AGG_METHOD_GET_AVG = "getAvg";
    public static final String AGG_METHOD_GET_SUM = "getSum";
    public static final String AGG_METHOD_GET_SUM_OF_SQUARES = "getSumOfSquares";
    public static final String AGG_METHOD_GET_VARIANCE = "getVariance";
    public static final String AGG_METHOD_GET_STD_DEVIATION = "getStdDeviation";
    public static final String AGG_METHOD_GET_STD_DEVIATION_BOUND = "getStdDeviationBound";

    /**
     * Percentile.getPercent() / getValue() 方法名
     */
    public static final String AGG_METHOD_GET_PERCENT = "getPercent";

    /**
     * ExtendedStats.Bounds 枚举值：UPPER / LOWER
     */
    public static final String AGG_BOUNDS_UPPER = "UPPER";
    public static final String AGG_BOUNDS_LOWER = "LOWER";

    // ========== Java 反射相关常量（pipeline 聚合类包路径，6.x/7.x 差异） ==========

    /**
     * ES 7.x PipelineAggregatorBuilders 全名（aggregations 顶层）
     */
    public static final String AGG_CLASS_PIPELINE_BUILDERS_ES7 =
            "org.elasticsearch.search.aggregations.PipelineAggregatorBuilders";

    /**
     * ES 6.x PipelineAggregatorBuilders 全名（pipeline 子包）
     */
    public static final String AGG_CLASS_PIPELINE_BUILDERS_ES6 =
            "org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders";

    /**
     * ES 7.x BucketSortPipelineAggregationBuilder 全名（pipeline 包）
     */
    public static final String AGG_CLASS_BUCKET_SORT_ES7 =
            "org.elasticsearch.search.aggregations.pipeline.BucketSortPipelineAggregationBuilder";

    /**
     * ES 6.x BucketSortPipelineAggregationBuilder 全名（pipeline.bucketsort 子包）
     */
    public static final String AGG_CLASS_BUCKET_SORT_ES6 =
            "org.elasticsearch.search.aggregations.pipeline.bucketsort.BucketSortPipelineAggregationBuilder";

    /**
     * PipelineAggregatorBuilders.bucketSort 方法名
     */
    public static final String AGG_METHOD_BUCKET_SORT = "bucketSort";

    /**
     * PipelineAggregatorBuilders.bucketSelector 方法名
     */
    public static final String AGG_METHOD_BUCKET_SELECTOR = "bucketSelector";

    /**
     * BucketSortPipelineAggregationBuilder.size / from 方法名
     */
    public static final String AGG_METHOD_SIZE = "size";
    public static final String AGG_METHOD_FROM = "from";

    // ========== Java 反射相关常量（字段名） ==========

    /**
     * EMPTY 字段名
     */
    public static final String FIELD_EMPTY = "EMPTY";

    /**
     * JSON 字段名
     */
    public static final String FIELD_JSON = "JSON";

    /**
     * THROW_UNSUPPORTED_OPERATION 字段名
     */
    public static final String FIELD_THROW_UNSUPPORTED_OPERATION = "THROW_UNSUPPORTED_OPERATION";

    // ========== Java 反射相关常量（方法名） ==========

    /**
     * getNamedXContents 方法名
     */
    public static final String METHOD_GET_NAMED_XCONTENTS = "getNamedXContents";

    /**
     * xContent 方法名
     */
    public static final String METHOD_XCONTENT = "xContent";

    /**
     * createParser 方法名
     */
    public static final String METHOD_CREATE_PARSER = "createParser";

    /**
     * fromXContent 方法名
     */
    public static final String METHOD_FROM_XCONTENT = "fromXContent";

    /**
     * nextToken 方法名
     *
     * @since 1.6.6
     */
    public static final String METHOD_NEXT_TOKEN = "nextToken";

    /**
     * currentName 方法名
     *
     * @since 1.6.6
     */
    public static final String METHOD_CURRENT_NAME = "currentName";

    /**
     * longValue 方法名
     *
     * @since 1.6.6
     */
    public static final String METHOD_GET_LONG_VALUE = "longValue";

    // ========== Java 反射相关常量（内部类/字段名） ==========

    /**
     * XContentParser.Token 内部类名
     *
     * @since 1.6.6
     */
    public static final String XCONTENT_CLASS_TOKEN = "$Token";

    /**
     * XContentParser.Token.FIELD_NAME 字段名
     *
     * @since 1.6.6
     */
    public static final String FIELD_TOKEN_FIELD_NAME = "FIELD_NAME";

    // ========== 日期间隔 ==========

    /**
     * 日期间隔：秒
     */
    public static final String DATE_INTERVAL_SECOND = "second";

    /**
     * 日期间隔：秒（简写）
     */
    public static final String DATE_INTERVAL_S = "s";

    /**
     * 日期间隔：分钟
     */
    public static final String DATE_INTERVAL_MINUTE = "minute";

    /**
     * 日期间隔：分钟（简写）
     */
    public static final String DATE_INTERVAL_M = "m";

    /**
     * 日期间隔：小时
     */
    public static final String DATE_INTERVAL_HOUR = "hour";

    /**
     * 日期间隔：小时（简写）
     */
    public static final String DATE_INTERVAL_H = "h";

    /**
     * 日期间隔：天
     */
    public static final String DATE_INTERVAL_DAY = "day";

    /**
     * 日期间隔：天（简写）
     */
    public static final String DATE_INTERVAL_D = "d";

    /**
     * 日期间隔：周
     */
    public static final String DATE_INTERVAL_WEEK = "week";

    /**
     * 日期间隔：周（简写）
     */
    public static final String DATE_INTERVAL_W = "w";

    /**
     * 日期间隔：月
     */
    public static final String DATE_INTERVAL_MONTH = "month";

    /**
     * 日期间隔：月（简写）
     */
    public static final String DATE_INTERVAL_MONTH_ABBR = "M";

    /**
     * 日期间隔：季度
     */
    public static final String DATE_INTERVAL_QUARTER = "quarter";

    /**
     * 日期间隔：季度（ES 字符串值，用于 6.8.x 兼容降级）
     */
    public static final String DATE_INTERVAL_QUARTER_VALUE = "1q";

    /**
     * 日期间隔：季度（简写）
     */
    public static final String DATE_INTERVAL_Q = "q";

    /**
     * 日期间隔：年
     */
    public static final String DATE_INTERVAL_YEAR = "year";

    /**
     * 日期间隔：年（简写）
     */
    public static final String DATE_INTERVAL_Y = "y";

    // ========== PIT 相关 ==========

    /**
     * PIT 保活时间默认上限
     */
    public static final String DEFAULT_PIT_MAX_KEEP_ALIVE = "5m";

    /**
     * ES PIT API 路径
     */
    public static final String ES_API_PIT = "/_pit";

    /**
     * ES PIT 查询参数：keep_alive
     */
    public static final String ES_PIT_KEEP_ALIVE_PARAM = "?keep_alive=";

    /**
     * ES PIT JSON 模板：关闭 PIT
     * 参数: pitId
     */
    public static final String ES_PIT_CLOSE_TEMPLATE = "{\"id\":\"%s\"}";

    // ========== scroll 相关 ==========

    /**
     * scroll 保活时间默认上限（与 PIT 保持一致）
     */
    public static final String DEFAULT_SCROLL_MAX_TTL = "5m";

    /**
     * ES scroll API 路径
     */
    public static final String ES_API_SCROLL = "/_search/scroll";

    /**
     * ES scroll JSON 模板：续期翻页
     * 参数: scrollTtl, scrollId
     */
    public static final String ES_SCROLL_CONTINUE_TEMPLATE = "{\"scroll\":\"%s\",\"scroll_id\":\"%s\"}";

    /**
     * ES scroll JSON 模板：清除上下文
     * 参数: scrollId
     */
    public static final String ES_SCROLL_DELETE_TEMPLATE = "{\"scroll_id\":\"%s\"}";

    /**
     * ES scroll 查询参数（初始请求追加到 URL）
     */
    public static final String ES_SCROLL_QUERY_PARAM = "?scroll=";

    // ========== count 相关 ==========

    /**
     * ES count API 路径
     *
     * @since 1.6.6
     */
    public static final String ES_API_COUNT = "/_count";

    /**
     * ES _count API 空查询 JSON（match_all）
     *
     * @since 1.6.6
     */
    public static final String ES_COUNT_EMPTY_QUERY = "{}";

    /**
     * ES _count API query 包装模板
     * 参数: query DSL JSON
     *
     * @since 1.6.6
     */
    public static final String ES_COUNT_QUERY_TEMPLATE = "{\"query\":%s}";

    // ========== 日期边界判断常量 ==========

    /**
     * 日期时间分隔符（ISO 8601 日期与时间的分隔符 "T"）
     *
     * @since 1.6.6
     */
    public static final String DATE_TIME_SEPARATOR = "T";

    /**
     * 日期起始时间后缀（当天零点整）
     *
     * @since 1.6.6
     */
    public static final String DATE_START_OF_DAY = "T00:00:00";

    /**
     * 日期起始时间后缀（当天零点，含毫秒）
     *
     * @since 1.6.6
     */
    public static final String DATE_START_OF_DAY_MILLIS = "T00:00:00.000";

    /**
     * 日期结束时间后缀（当天最后时刻）
     *
     * @since 1.6.6
     */
    public static final String DATE_END_OF_DAY = "T23:59:59";

    /**
     * 日期结束时间后缀（当天最后时刻，含毫秒）
     *
     * @since 1.6.6
     */
    public static final String DATE_END_OF_DAY_MILLIS = "T23:59:59.999";

    // ========== Composite 聚合相关 ==========

    /**
     * composite 聚合默认 size
     */
    public static final int COMPOSITE_DEFAULT_SIZE = 1000;

    /**
     * composite 聚合默认排序方向
     */
    public static final String COMPOSITE_DEFAULT_ORDER = "asc";

    /**
     * ES composite 聚合响应中的 after_key 字段名
     */
    public static final String ES_JSON_AFTER_KEY = "after_key";

    // ========== 异常关键字 ==========

    /**
     * ES too_long_frame_exception 异常关键字，用于降级重试判断
     */
    public static final String TOO_LONG_FRAME_EXCEPTION = "too_long_frame_exception";

    private SimpleElasticsearchSearchConstant() {
        // 私有构造函数，防止实例化
    }
}
