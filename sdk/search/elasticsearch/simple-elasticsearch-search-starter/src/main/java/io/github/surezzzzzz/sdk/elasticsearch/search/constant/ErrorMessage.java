package io.github.surezzzzzz.sdk.elasticsearch.search.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public class ErrorMessage {

    // ========== 查询相关 ==========

    /**
     * 查询执行失败
     */
    public static final String QUERY_EXECUTION_FAILED = "查询执行失败";

    /**
     * 索引别名不能为空
     */
    public static final String INDEX_ALIAS_REQUIRED = "索引别名不能为空";

    /**
     * 单次查询最大返回数量超限
     */
    public static final String QUERY_SIZE_EXCEEDED = "单次查询最大返回数量为 %d";

    /**
     * 偏移分页深度超限
     */
    public static final String OFFSET_PAGINATION_EXCEEDED = "偏移分页深度超过限制 %d，请使用 search_after 分页";

    /**
     * search_after 分页必须指定排序
     */
    public static final String SEARCH_AFTER_SORT_REQUIRED = "search_after 分页必须指定排序字段";

    // ========== 聚合相关 ==========

    /**
     * 聚合查询执行失败
     */
    public static final String AGG_EXECUTION_FAILED = "聚合查询执行失败";

    /**
     * 聚合定义不能为空
     */
    public static final String AGG_DEFINITION_REQUIRED = "聚合定义不能为空";

    // ========== 字段相关 ==========

    /**
     * 字段不存在
     */
    public static final String FIELD_NOT_FOUND = "字段 [%s] 不存在";

    /**
     * 字段不可查询
     */
    public static final String FIELD_NOT_SEARCHABLE = "字段 [%s] %s";

    /**
     * 字段不支持聚合
     */
    public static final String FIELD_NOT_AGGREGATABLE = "字段 [%s] 不支持聚合";

    // ========== 操作符相关 ==========

    /**
     * 不支持的操作符
     */
    public static final String UNSUPPORTED_OPERATOR = "不支持的操作符: %s";

    /**
     * IN 查询值列表为空
     */
    public static final String IN_VALUES_REQUIRED = "IN 查询的值列表不能为空";

    /**
     * BETWEEN 查询值数量错误
     */
    public static final String BETWEEN_VALUES_INVALID = "BETWEEN 查询需要两个值: [from, to]";

    /**
     * 不支持的聚合类型
     */
    public static final String UNSUPPORTED_AGG_TYPE = "不支持的聚合类型: %s";

    // ========== 配置相关 ==========

    /**
     * 索引配置为空
     */
    public static final String INDICES_CONFIG_REQUIRED = "配置项 'indices' 不能为空，至少需要配置一个索引";

    /**
     * 索引别名重复
     */
    public static final String INDEX_ALIAS_DUPLICATE = "索引别名 [%s] 重复，每个索引必须有唯一的别名";

    /**
     * 索引名称为空
     */
    public static final String INDEX_NAME_REQUIRED = "索引配置中的 'name' 不能为空";

    /**
     * 日期分割配置缺少 date-pattern
     */
    public static final String DATE_PATTERN_REQUIRED = "索引 [%s] 启用了 date-split，必须配置 'date-pattern'";

    /**
     * 敏感字段名为空
     */
    public static final String SENSITIVE_FIELD_NAME_REQUIRED = "索引 [%s] 的敏感字段配置中 'field' 不能为空";

    /**
     * 敏感字段策略为空
     */
    public static final String SENSITIVE_FIELD_STRATEGY_REQUIRED = "索引 [%s] 的敏感字段 [%s] 必须配置 'strategy'";

    /**
     * 敏感字段策略值非法
     */
    public static final String SENSITIVE_FIELD_STRATEGY_INVALID = "索引 [%s] 的敏感字段 [%s] 的 strategy 只能是 'forbidden' 或 'mask'，当前值: %s";

    /**
     * 索引未配置
     */
    public static final String INDEX_NOT_CONFIGURED = "索引别名 [%s] 未配置";

    /**
     * 索引不存在或没有 mapping
     */
    public static final String INDEX_MAPPING_NOT_FOUND = "索引 [%s] 不存在或没有 mapping";

    /**
     * 指定的索引不在匹配列表中
     */
    public static final String SPECIFIC_INDEX_NOT_FOUND = "指定的索引 [%s] 不存在于匹配的索引列表中: %s";

    /**
     * 加载 mapping 失败
     */
    public static final String LOAD_MAPPING_FAILED = "索引 [%s] 的 mapping 加载失败";

    /**
     * 刷新 mapping 失败
     */
    public static final String REFRESH_MAPPING_FAILED = "索引 [%s] 的 mapping 刷新失败";

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "Simple Elasticsearch Search 配置验证失败，请检查配置文件";

    private ErrorMessage() {
        // 私有构造函数，防止实例化
    }
}
