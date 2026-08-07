package io.github.surezzzzzz.sdk.auth.resource.core.constant;

/**
 * 资源服务核心常量。
 *
 * @author surezzzzzz
 */
public final class SimpleResourceServerConstant {

    /**
     * 来源标识最大Unicode码点数。
     */
    public static final int MAX_SOURCE_ID_CODE_POINT_COUNT = 64;
    /**
     * 主体和请求标识最大Unicode码点数。
     */
    public static final int MAX_IDENTIFIER_CODE_POINT_COUNT = 128;
    /**
     * 属性占位符开始标记。
     */
    public static final String DYNAMIC_EXPRESSION_PROPERTY_PREFIX = "${";
    /**
     * SpEL表达式开始标记。
     */
    public static final String DYNAMIC_EXPRESSION_SPEL_PREFIX = "#{";
    /**
     * 来源标识允许字符。
     */
    public static final String SOURCE_ID_ALLOWED_CHARACTER_PATTERN = "[A-Za-z0-9._-]+";
    /**
     * 标识符禁止模式字符。
     */
    public static final String FORBIDDEN_PATTERN_CHARACTERS = "*?[](){}^$|\\+";
    /**
     * 字段不能为空详情模板。
     */
    public static final String DETAIL_CANNOT_BE_NULL = "%s不能为null";
    /**
     * 字段不能为空或包含首尾空白详情模板。
     */
    public static final String DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE = "%s不能为空且不能包含首尾空白";
    /**
     * 字段不能包含动态表达式详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_DYNAMIC_EXPRESSION = "%s不能包含动态表达式";
    /**
     * 字段不能包含模式字符详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_PATTERN_CHARACTER = "%s不能包含模式字符";
    /**
     * 字段长度超限详情模板。
     */
    public static final String DETAIL_MAXIMUM_CODE_POINT_COUNT = "%s长度不能超过%d个Unicode码点";
    /**
     * 来源标识格式无效详情模板。
     */
    public static final String DETAIL_SOURCE_ID_INVALID = "sourceId格式无效：%s";
    /**
     * 主体与应用授权主体不一致详情。
     */
    public static final String DETAIL_PRINCIPAL_AND_AUTHORIZATION_SUBJECT_MISMATCH = "认证主体与应用授权主体不一致";
    /**
     * 认证结果状态无效详情。
     */
    public static final String DETAIL_AUTHENTICATION_RESULT_INVALID = "认证结果状态与字段不一致";
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";
    /**
     * 帮助类实例化提示。
     */
    public static final String MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE = "帮助类不能实例化";

    private SimpleResourceServerConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
