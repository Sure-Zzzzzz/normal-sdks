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
     * ES 默认文档类型（ES 7.x+）
     */
    public static final String ES_DEFAULT_DOC_TYPE = "_doc";

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

    private SimpleElasticsearchSearchConstant() {
        // 私有构造函数，防止实例化
    }
}
