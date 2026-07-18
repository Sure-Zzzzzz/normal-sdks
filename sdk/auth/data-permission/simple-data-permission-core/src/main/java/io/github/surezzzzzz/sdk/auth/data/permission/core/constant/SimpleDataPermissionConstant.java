package io.github.surezzzzzz.sdk.auth.data.permission.core.constant;

/**
 * 数据权限常量。
 *
 * @author surezzzzzz
 */
public final class SimpleDataPermissionConstant {

    /**
     * 协议名称。
     */
    public static final String PROTOCOL = "simple-data-permission";
    /**
     * 协议版本。
     */
    public static final String VERSION = "1.0";

    /**
     * protocol 字段。
     */
    public static final String FIELD_PROTOCOL = "protocol";
    /**
     * version 字段。
     */
    public static final String FIELD_VERSION = "version";
    /**
     * grants 字段。
     */
    public static final String FIELD_GRANTS = "grants";
    /**
     * resource 字段。
     */
    public static final String FIELD_RESOURCE = "resource";
    /**
     * actions 字段。
     */
    public static final String FIELD_ACTIONS = "actions";
    /**
     * all 字段。
     */
    public static final String FIELD_ALL = "all";
    /**
     * constraints 字段。
     */
    public static final String FIELD_CONSTRAINTS = "constraints";
    /**
     * dimension 字段。
     */
    public static final String FIELD_DIMENSION = "dimension";
    /**
     * operator 字段。
     */
    public static final String FIELD_OPERATOR = "operator";
    /**
     * values 字段。
     */
    public static final String FIELD_VALUES = "values";

    /**
     * 读取动作。
     */
    public static final String ACTION_READ = "read";
    /**
     * 导出动作。
     */
    public static final String ACTION_EXPORT = "export";
    /**
     * 属性占位符开始标记。
     */
    public static final String DYNAMIC_EXPRESSION_PROPERTY_PREFIX = "${";
    /**
     * 表达式开始标记。
     */
    public static final String DYNAMIC_EXPRESSION_SPEL_PREFIX = "#{";
    /**
     * 不允许出现在精确字面量中的模式字符。
     */
    public static final String FORBIDDEN_PATTERN_CHARACTERS = "*?[](){}^$|\\+";

    /**
     * 单个文档的最大授权项数量。
     */
    public static final int MAX_GRANT_COUNT = 64;
    /**
     * 单个授权项的最大动作数量。
     */
    public static final int MAX_ACTION_COUNT = 16;
    /**
     * 单个授权项的最大约束数量。
     */
    public static final int MAX_CONSTRAINT_COUNT = 16;
    /**
     * 单个约束的最大值数量。
     */
    public static final int MAX_VALUE_COUNT = 128;
    /**
     * 标识符最大 Unicode 码点数量。
     */
    public static final int MAX_IDENTIFIER_CODE_POINT_COUNT = 128;
    /**
     * 约束值最大 Unicode 码点数量。
     */
    public static final int MAX_VALUE_CODE_POINT_COUNT = 512;

    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";
    /**
     * 工具类实例化提示。
     */
    public static final String MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE = "工具类不能实例化";
    /**
     * 非空校验详情模板。
     */
    public static final String DETAIL_CANNOT_BE_NULL = "%s不能为null";
    /**
     * 空值校验详情模板。
     */
    public static final String DETAIL_CANNOT_BE_EMPTY = "%s不能为空";
    /**
     * 空白校验详情模板。
     */
    public static final String DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE = "%s不能为空且不能包含首尾空白";
    /**
     * 动态表达式校验详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_DYNAMIC_EXPRESSION = "%s不能包含动态表达式";
    /**
     * 非法 Unicode 代理项校验详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_ISOLATED_SURROGATE = "%s不能包含孤立Unicode代理项";
    /**
     * 模式字符校验详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_PATTERN_CHARACTER = "%s不能包含模式字符";
    /**
     * 集合元素校验详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_NULL = "%s不能包含null";
    /**
     * 数量上限校验详情模板。
     */
    public static final String DETAIL_MAXIMUM_COUNT = "%s数量不能超过%d";
    /**
     * 码点上限校验详情模板。
     */
    public static final String DETAIL_MAXIMUM_CODE_POINT_COUNT = "%s长度不能超过%d个Unicode码点";
    /**
     * 全量授权约束校验详情。
     */
    public static final String DETAIL_ALL_GRANT_CANNOT_CONTAIN_CONSTRAINT = "全量授权不能包含约束";
    /**
     * 不支持操作符校验详情模板。
     */
    public static final String DETAIL_UNSUPPORTED_CONSTRAINT_OPERATOR = "不支持的约束操作符：%s";
    /**
     * 受限授权约束校验详情。
     */
    public static final String DETAIL_RESTRICTED_GRANT_MUST_CONTAIN_CONSTRAINT = "受限授权必须包含约束";
    /**
     * 重复维度校验详情模板。
     */
    public static final String DETAIL_DUPLICATE_CONSTRAINT_DIMENSION = "同一授权项中的约束维度不能重复：%s";
    /**
     * 访问结果校验详情。
     */
    public static final String DETAIL_ACCESS_OUTCOME_CANNOT_BE_NULL = "访问结果不能为null";
    /**
     * 命中授权项校验详情。
     */
    public static final String DETAIL_MATCHED_GRANT_CANNOT_BE_NULL = "命中授权项不能为null";
    /**
     * 计划命中授权项校验详情。
     */
    public static final String DETAIL_OUTCOME_CANNOT_CONTAIN_MATCHED_GRANT = "%s不能包含命中授权项";
    /**
     * 受限计划校验详情。
     */
    public static final String DETAIL_RESTRICTED_PLAN_MUST_CONTAIN_MATCHED_GRANT = "受限放行必须包含命中授权项";
    /**
     * 受限计划全量授权校验详情。
     */
    public static final String DETAIL_RESTRICTED_PLAN_CANNOT_CONTAIN_ALL_GRANT = "受限放行不能包含全量授权项";
    /**
     * 未支持访问结果校验详情模板。
     */
    public static final String DETAIL_UNSUPPORTED_ACCESS_OUTCOME = "不支持的访问结果：%s";
    /**
     * 授权请求校验详情。
     */
    public static final String DETAIL_PERMISSION_REQUEST_CANNOT_BE_NULL = "授权请求不能为null";
    /**
     * 授权文档校验详情。
     */
    public static final String DETAIL_DOCUMENT_CANNOT_BE_NULL = "授权文档不能为null";

    private SimpleDataPermissionConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
