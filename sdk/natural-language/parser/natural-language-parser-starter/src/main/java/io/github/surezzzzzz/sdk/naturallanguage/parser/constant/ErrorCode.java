package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 解析错误 ====================

    /**
     * 空查询
     */
    public static final String PARSE_EMPTY_QUERY = "PARSE_001";

    /**
     * 语法错误
     */
    public static final String PARSE_SYNTAX_ERROR = "PARSE_002";

    /**
     * 不支持的意图类型
     */
    public static final String PARSE_UNSUPPORTED_INTENT = "PARSE_003";

    // ==================== 关键字错误 ====================

    /**
     * 关键字冲突（同一词映射到两种类型）
     */
    public static final String KEYWORD_CONFLICT = "KEYWORD_001";

    /**
     * 关键字为空
     */
    public static final String KEYWORD_EMPTY = "KEYWORD_002";

    /**
     * 枚举值不存在
     */
    public static final String KEYWORD_INVALID_ENUM = "KEYWORD_003";
}
