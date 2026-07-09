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

    // ========== countOnly 相关 ==========

    /**
     * countOnly 模式不支持 PIT 分页
     *
     * @since 1.6.6
     */
    public static final String COUNT_ONLY_PIT_NOT_SUPPORTED = "countOnly 模式不支持 PIT 分页，请去掉 searchAfterMode=pit 或不传 countOnly=true";

    /**
     * _count 响应中找不到 count 字段
     *
     * @since 1.6.6
     */
    public static final String COUNT_RESPONSE_FIELD_MISSING = "_count 响应中找不到 count 字段";

    /**
     * _count 响应解析失败
     * 参数: 错误原因
     *
     * @since 1.6.6
     */
    public static final String COUNT_RESPONSE_PARSE_FAILED = "_count 响应解析失败: %s";

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

    /**
     * composite 聚合不支持该类型（仅支持 terms、date_histogram、histogram）
     */
    public static final String COMPOSITE_UNSUPPORTED_TYPE = "composite 聚合不支持类型 [%s]，仅支持 terms、date_histogram、histogram";

    /**
     * composite 聚合内部不允许嵌套 bucket 聚合
     */
    public static final String COMPOSITE_NESTED_NOT_ALLOWED = "composite 聚合内部不允许嵌套 bucket 聚合";

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

    // ========== PIT 相关 ==========

    /**
     * pitKeepAlive 未提供
     */
    public static final String PIT_KEEP_ALIVE_REQUIRED = "searchAfterMode=pit 时必须提供 pitKeepAlive，建议值：1m ~ 5m";

    /**
     * pitKeepAlive 超过服务端上限
     */
    public static final String PIT_KEEP_ALIVE_EXCEEDED = "pitKeepAlive [%s] 超过服务端限制，最大允许 %s";

    /**
     * 当前 ES 版本不支持 PIT
     */
    public static final String PIT_NOT_SUPPORTED = "当前 Elasticsearch 不支持 PIT 翻页模式（需要 ES 7.10+），请改用 searchAfterMode=none";

    /**
     * ES 版本信息未就绪
     */
    public static final String PIT_VERSION_NOT_READY = "当前 Elasticsearch 版本信息未就绪，PIT 翻页模式暂不可用，请稍后重试或改用 searchAfterMode=none";

    /**
     * pitKeepAlive 格式不合法
     */
    public static final String PIT_KEEP_ALIVE_INVALID_FORMAT = "pitKeepAlive 格式不合法，支持格式：1d / 1h / 5m / 30s，当前值：%s";

    // ========== 翻页策略相关 ==========

    /**
     * 翻页策略 key 已存在
     */
    public static final String PAGINATION_STRATEGY_DUPLICATE = "翻页策略 [%s] 已存在，不允许覆盖内置策略";

    /**
     * 不支持的翻页策略
     */
    public static final String PAGINATION_STRATEGY_NOT_FOUND = "不支持的翻页策略: %s";

    // ========== 降级相关 ==========

    /**
     * 降级失败
     */
    public static final String DOWNGRADE_FAILED = "查询降级失败，已达到最大降级级别但仍然失败";

    /**
     * 索引路由失败
     */
    public static final String INDEX_ROUTE_FAILED = "索引 [%s] 路由失败";

    /**
     * 不支持的降级级别
     */
    public static final String UNSUPPORTED_DOWNGRADE_LEVEL = "降级级别 [%s] 不适用于日期粒度 [%s]";

    /**
     * 时间字符串格式不合法
     */
    public static final String TIME_RANGE_INVALID_FORMAT = "时间格式不合法，支持格式：30d / 1h / 5m / 30s，当前值：%s";

    /**
     * default-date-range 配置格式不合法
     */
    public static final String DEFAULT_DATE_RANGE_INVALID_FORMAT = "query-limits.default-date-range 格式不合法，支持格式：30d / 1h / 5m / 30s，当前值：%s";

    // ========== 操作符策略相关 ==========

    /**
     * 操作符策略 key 已存在
     */
    public static final String OPERATOR_STRATEGY_DUPLICATE = "操作符策略 [%s] 已存在，不允许覆盖内置策略";

    // ========== 聚合策略相关 ==========

    /**
     * 聚合策略 key 已存在
     */
    public static final String AGG_STRATEGY_DUPLICATE = "聚合策略 [%s] 已存在，不允许覆盖内置策略";

    /**
     * 不支持的 pipeline 聚合类型
     */
    public static final String PIPELINE_UNSUPPORTED_TYPE = "pipeline 聚合不支持类型 [%s]，仅支持 bucket_sort、bucket_selector";

    /**
     * pipeline 聚合挂在不支持的父聚合下
     */
    public static final String PIPELINE_INVALID_PARENT = "pipeline 聚合不支持挂在 composite 聚合下";

    /**
     * bucket_selector 缺少 script 字段
     */
    public static final String PIPELINE_MISSING_SCRIPT = "bucket_selector 必须填写 script 字段";

    /**
     * filter 聚合缺少 query 字段
     */
    public static final String AGG_FILTER_QUERY_REQUIRED = "filter 聚合 [%s] 必须填写 query 字段";

    /**
     * filters 聚合缺少 filters 字段
     */
    public static final String AGG_FILTERS_REQUIRED = "filters 聚合 [%s] 必须填写 filters 字段";

    /**
     * percentile_ranks 聚合缺少 values 字段
     */
    public static final String AGG_PERCENTILE_RANKS_VALUES_REQUIRED = "percentile_ranks 聚合 [%s] 必须填写 values 字段";

    /**
     * 聚合反射调用失败（6.x/7.x 跨版本兼容场景）
     */
    public static final String AGG_REFLECT_INVOKE_FAILED = "聚合反射调用失败：[%s]";

    /**
     * 聚合类跨版本加载失败（6.x/7.x 均找不到目标类）
     */
    public static final String AGG_REFLECT_CLASS_NOT_FOUND = "聚合类跨版本加载失败：[%s]";

    // ========== XContent 反射相关 ==========

    /**
     * XContent API 类不存在
     */
    public static final String XCONTENT_API_NOT_FOUND = "未找到兼容的 XContent API 类，请检查 Elasticsearch client 版本";

    /**
     * SearchModule 构造方法不存在
     */
    public static final String XCONTENT_SEARCH_MODULE_CONSTRUCTOR_NOT_FOUND = "未找到兼容的 SearchModule 构造方法";

    /**
     * NamedXContentRegistry 创建失败
     */
    public static final String XCONTENT_REGISTRY_CREATE_FAILED = "创建 NamedXContentRegistry 失败";

    // ========== 高级表达式相关 ==========

    /**
     * 高级表达式解析失败
     */
    public static final String EXPRESSION_PARSE_FAILED = "高级表达式解析失败：%s";

    /**
     * 表达式超出最大长度限制
     */
    public static final String EXPRESSION_TOO_LONG = "表达式长度超出限制，最大允许 %d 个字符，当前 %d 个字符";

    // ========== scroll 相关 ==========

    /**
     * scrollTtl 未提供
     */
    public static final String SCROLL_TTL_REQUIRED = "scroll 分页必须提供 scrollTtl，建议值：1m ~ 5m";

    /**
     * scrollTtl 超过服务端上限
     */
    public static final String SCROLL_TTL_EXCEEDED = "scrollTtl [%s] 超过服务端限制，最大允许 %s";

    /**
     * scrollTtl 格式不合法
     */
    public static final String SCROLL_TTL_INVALID_FORMAT = "scrollTtl 格式不合法，支持格式：1d / 1h / 5m / 30s，当前值：%s";

    /**
     * scroll 第一页必须指定排序
     */
    public static final String SCROLL_SORT_REQUIRED = "scroll 分页第一页必须指定排序字段";

    /**
     * scroll 不支持 collapse
     */
    public static final String SCROLL_COLLAPSE_NOT_SUPPORTED = "scroll 分页不支持与 collapse（字段折叠）同时使用";

    // ========== 自然语言相关 ==========

    /**
     * NL 解析失败
     */
    public static final String NL_PARSE_FAILED = "自然语言解析失败：%s";

    /**
     * 未指定索引
     */
    public static final String NL_INDEX_NOT_SPECIFIED = "未指定索引，请在自然语言中指定（如：'查询user_profile这个索引'）或通过 dataSource 参数传入";

    /**
     * NL转DSL翻译失败
     */
    public static final String NL_TRANSLATION_FAILED = "自然语言转DSL翻译失败：%s";

    private ErrorMessage() {
        // 私有构造函数，防止实例化
    }
}
