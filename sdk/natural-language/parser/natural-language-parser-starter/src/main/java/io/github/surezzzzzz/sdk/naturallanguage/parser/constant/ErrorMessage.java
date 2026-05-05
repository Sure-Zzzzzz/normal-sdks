package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 解析错误 ====================

    public static final String PARSE_EMPTY_QUERY = "查询内容不能为空";
    public static final String PARSE_SYNTAX_ERROR = "查询语法错误：%s";
    public static final String PARSE_UNSUPPORTED_INTENT = "不支持的意图类型：%s";

    // ==================== 关键字错误 ====================

    public static final String KEYWORD_CONFLICT =
            "关键字冲突：'%s' 已注册为 [%s]，无法再注册为 [%s]。来源：%s。" +
            "提示：请先调用 remove 方法移除已有注册。";
    public static final String KEYWORD_EMPTY = "注册的关键字不能为空字符串";
    public static final String KEYWORD_INVALID_ENUM = "无效的枚举值：%s，合法值为：%s";
}
