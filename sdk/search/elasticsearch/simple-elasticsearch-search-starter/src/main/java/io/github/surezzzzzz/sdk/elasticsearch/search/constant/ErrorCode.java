package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public class ErrorCode {

    // ========== 配置相关错误码 ==========

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "SEARCH_CONFIG_001";

    /**
     * 索引配置为空
     */
    public static final String INDICES_CONFIG_REQUIRED = "SEARCH_CONFIG_002";

    /**
     * 索引别名重复
     */
    public static final String INDEX_ALIAS_DUPLICATE = "SEARCH_CONFIG_003";

    /**
     * 索引名称为空
     */
    public static final String INDEX_NAME_REQUIRED = "SEARCH_CONFIG_004";

    /**
     * 日期分割配置缺少 date-pattern
     */
    public static final String DATE_PATTERN_REQUIRED = "SEARCH_CONFIG_005";

    /**
     * 索引未配置
     */
    public static final String INDEX_NOT_CONFIGURED = "SEARCH_CONFIG_006";

    /**
     * 敏感字段名为空
     */
    public static final String SENSITIVE_FIELD_NAME_REQUIRED = "SEARCH_CONFIG_007";

    /**
     * 敏感字段策略为空
     */
    public static final String SENSITIVE_FIELD_STRATEGY_REQUIRED = "SEARCH_CONFIG_008";

    /**
     * 敏感字段策略值非法
     */
    public static final String SENSITIVE_FIELD_STRATEGY_INVALID = "SEARCH_CONFIG_009";

    // ========== 查询相关错误码 ==========

    /**
     * 查询执行失败
     */
    public static final String QUERY_EXECUTION_FAILED = "SEARCH_QUERY_001";

    /**
     * 索引别名不能为空
     */
    public static final String INDEX_ALIAS_REQUIRED = "SEARCH_QUERY_002";

    /**
     * 单次查询最大返回数量超限
     */
    public static final String QUERY_SIZE_EXCEEDED = "SEARCH_QUERY_003";

    /**
     * 偏移分页深度超限
     */
    public static final String OFFSET_PAGINATION_EXCEEDED = "SEARCH_QUERY_004";

    /**
     * search_after 分页必须指定排序
     */
    public static final String SEARCH_AFTER_SORT_REQUIRED = "SEARCH_QUERY_005";

    /**
     * collapse 字段折叠必须指定排序
     */
    public static final String COLLAPSE_SORT_REQUIRED = "SEARCH_QUERY_006";

    /**
     * 不支持的操作符
     */
    public static final String UNSUPPORTED_OPERATOR = "SEARCH_QUERY_007";

    /**
     * IN 查询值列表为空
     */
    public static final String IN_VALUES_REQUIRED = "SEARCH_QUERY_008";

    /**
     * BETWEEN 查询值数量错误
     */
    public static final String BETWEEN_VALUES_INVALID = "SEARCH_QUERY_009";

    // ========== 聚合相关错误码 ==========

    /**
     * 聚合查询执行失败
     */
    public static final String AGG_EXECUTION_FAILED = "SEARCH_AGG_001";

    /**
     * 聚合定义不能为空
     */
    public static final String AGG_DEFINITION_REQUIRED = "SEARCH_AGG_002";

    /**
     * 不支持的聚合类型
     */
    public static final String UNSUPPORTED_AGG_TYPE = "SEARCH_AGG_003";

    // ========== Mapping 相关错误码 ==========

    /**
     * 索引不存在或没有 mapping
     */
    public static final String INDEX_MAPPING_NOT_FOUND = "SEARCH_MAPPING_001";

    /**
     * 指定的索引不在匹配列表中
     */
    public static final String SPECIFIC_INDEX_NOT_FOUND = "SEARCH_MAPPING_002";

    /**
     * 加载 mapping 失败
     */
    public static final String LOAD_MAPPING_FAILED = "SEARCH_MAPPING_003";

    /**
     * 刷新 mapping 失败
     */
    public static final String REFRESH_MAPPING_FAILED = "SEARCH_MAPPING_004";

    // ========== 字段相关错误码 ==========

    /**
     * 字段不存在
     */
    public static final String FIELD_NOT_FOUND = "SEARCH_FIELD_001";

    /**
     * 字段不可查询
     */
    public static final String FIELD_NOT_SEARCHABLE = "SEARCH_FIELD_002";

    /**
     * 字段不支持聚合
     */
    public static final String FIELD_NOT_AGGREGATABLE = "SEARCH_FIELD_003";

    // ========== 降级相关错误码 ==========

    /**
     * 降级失败（所有降级级别都失败）
     */
    public static final String DOWNGRADE_FAILED = "SEARCH_DOWNGRADE_001";

    /**
     * 索引路由失败
     */
    public static final String INDEX_ROUTE_FAILED = "SEARCH_DOWNGRADE_002";

    /**
     * 不支持的降级级别
     */
    public static final String UNSUPPORTED_DOWNGRADE_LEVEL = "SEARCH_DOWNGRADE_003";

    // ========== 通用错误码 ==========

    /**
     * 无效参数
     */
    public static final String INVALID_PARAMETER = "SEARCH_COMMON_001";

    // ========== 自然语言转换错误码 ==========

    /**
     * NL转DSL翻译失败
     */
    public static final String NL_TRANSLATION_FAILED = "SEARCH_NL_001";

    private ErrorCode() {
        // 私有构造函数，防止实例化
    }
}
